import json
import logging
import asyncio
import base64
import os
from channels.generic.websocket import AsyncWebsocketConsumer
from asgiref.sync import sync_to_async
from typing import Optional
import time

from .vad import VAD, AudioProcessor
from .asr_driver import ASRDriver

logger = logging.getLogger(__name__)


class VoiceSessionState:
    IDLE = "idle"
    LISTENING = "listening"
    PROCESSING = "processing"
    SPEAKING = "speaking"


class RealtimeVoiceConsumer(AsyncWebsocketConsumer):
    voice_channel = "voice_channel"
    
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.session_state = VoiceSessionState.IDLE
        self.audio_processor = AudioProcessor(sample_rate=16000)
        self.audio_buffer = []
        self.last_activity_time = time.time()
        self.session_id = None
        self.asr_type = "openai"
        self.tts_type = "Edge"
        self.tts_voice_id = "zh-CN-XiaoxiaoNeural"
        self.asr_driver = ASRDriver()
        self._is_connected = False
        self._reconnect_count = 0
        self._max_reconnect = 3

    async def connect(self):
        await self.accept()
        self._is_connected = True
        
        await self.channel_layer.group_add(
            self.voice_channel,
            self.channel_name
        )
        
        logger.info(f"Voice WebSocket connected: {self.channel_name}")
        
        await self.send(json.dumps({
            "type": "connected",
            "session_id": str(id(self)),
            "state": self.session_state,
            "message": "实时语音连接已建立"
        }))

    async def disconnect(self, close_code):
        self._is_connected = False
        
        await self.channel_layer.group_discard(
            self.voice_channel,
            self.channel_name
        )
        
        self.audio_processor.vad.reset()
        self.audio_buffer = []
        
        logger.info(f"Voice WebSocket disconnected: {close_code}")

    async def receive(self, text_data=None, bytes_data=None):
        try:
            self.last_activity_time = time.time()
            
            if bytes_data:
                await self._handle_audio_data(bytes_data)
            elif text_data:
                data = json.loads(text_data)
                await self._handle_control_message(data)
                
        except Exception as e:
            logger.error(f"Error in receive: {e}")
            await self.send(json.dumps({
                "type": "error",
                "message": str(e)
            }))

    async def _handle_control_message(self, data: dict):
        message_type = data.get("type", "")
        
        if message_type == "start_session":
            await self._start_voice_session(data)
        elif message_type == "end_session":
            await self._end_voice_session()
        elif message_type == "cancel":
            await self._cancel_current_operation()
        elif message_type == "config":
            await self._update_config(data)
        elif message_type == "ping":
            await self.send(json.dumps({"type": "pong"}))

    async def _start_voice_session(self, config: dict):
        self.session_state = VoiceSessionState.LISTENING
        self.asr_type = config.get("asr_type", "openai")
        self.tts_type = config.get("tts_type", "Edge")
        self.tts_voice_id = config.get("tts_voice_id", "zh-CN-XiaoxiaoNeural")
        
        self.audio_processor.vad.reset()
        self.audio_buffer = []
        
        await self.send(json.dumps({
            "type": "session_started",
            "state": self.session_state,
            "message": "开始监听语音..."
        }))
        
        logger.info(f"Voice session started with ASR: {self.asr_type}")

    async def _end_voice_session(self):
        self.session_state = VoiceSessionState.IDLE
        
        if self.audio_buffer:
            await self._process_speech(b''.join(self.audio_buffer))
            self.audio_buffer = []
        
        await self.send(json.dumps({
            "type": "session_ended",
            "state": self.session_state
        }))

    async def _cancel_current_operation(self):
        self.session_state = VoiceSessionState.IDLE
        self.audio_buffer = []
        self.audio_processor.vad.reset()
        
        await self.send(json.dumps({
            "type": "cancelled",
            "state": self.session_state
        }))

    async def _update_config(self, config: dict):
        if "asr_type" in config:
            self.asr_type = config["asr_type"]
        if "tts_type" in config:
            self.tts_type = config["tts_type"]
        if "tts_voice_id" in config:
            self.tts_voice_id = config["tts_voice_id"]
        
        await self.send(json.dumps({
            "type": "config_updated",
            "config": {
                "asr_type": self.asr_type,
                "tts_type": self.tts_type,
                "tts_voice_id": self.tts_voice_id
            }
        }))

    async def _handle_audio_data(self, audio_data: bytes):
        if self.session_state != VoiceSessionState.LISTENING:
            return
        
        try:
            result = self.audio_processor.process_audio(audio_data, from_rate=48000)
            
            if result["is_speech"]:
                self.audio_buffer.append(result["processed_audio"])
                
                if result["speech_start"]:
                    await self.send(json.dumps({
                        "type": "speech_start",
                        "state": VoiceSessionState.LISTENING
                    }))
            
            if result["speech_end"] and result["should_send"]:
                self.session_state = VoiceSessionState.PROCESSING
                
                await self.send(json.dumps({
                    "type": "speech_end",
                    "state": self.session_state
                }))
                
                speech_audio = self.audio_processor.vad.get_speech_buffer()
                self.audio_processor.vad.clear_buffer()
                
                if speech_audio:
                    await self._process_speech(speech_audio)
                
        except Exception as e:
            logger.error(f"Error handling audio data: {e}")

    async def _process_speech(self, audio_data: bytes):
        try:
            await self.send(json.dumps({
                "type": "transcribing",
                "state": VoiceSessionState.PROCESSING
            }))
            
            text = await self._transcribe_audio(audio_data)
            
            if text:
                await self.send(json.dumps({
                    "type": "transcribed",
                    "text": text
                }))
                
                await self._process_chat(text)
            else:
                self.session_state = VoiceSessionState.LISTENING
                await self.send(json.dumps({
                    "type": "transcription_empty",
                    "state": self.session_state
                }))
                
        except Exception as e:
            logger.error(f"Error processing speech: {e}")
            self.session_state = VoiceSessionState.LISTENING
            await self.send(json.dumps({
                "type": "error",
                "message": f"语音处理错误: {str(e)}",
                "state": self.session_state
            }))

    async def _transcribe_audio(self, audio_data: bytes) -> str:
        try:
            from ..config import singleton_sys_config
            
            api_key = os.environ.get("OPENAI_API_KEY", "")
            base_url = os.environ.get("OPENAI_BASE_URL", "")
            
            asr = self.asr_driver.get_asr(
                self.asr_type,
                api_key=api_key,
                base_url=base_url
            )
            
            text = await asr.transcribe(audio_data)
            return text.strip()
            
        except Exception as e:
            logger.error(f"Transcription error: {e}")
            return ""

    async def _process_chat(self, user_text: str):
        try:
            from ..config import singleton_sys_config
            from ..process.process import process_core
            from ..output.realtime_message_queue import realtime_callback
            
            you_name = singleton_sys_config.yourName
            
            await self.send(json.dumps({
                "type": "chat_processing",
                "state": VoiceSessionState.PROCESSING
            }))
            
            self.session_state = VoiceSessionState.SPEAKING
            
            process_core.chat(you_name=you_name, query=user_text)
            
        except Exception as e:
            logger.error(f"Chat processing error: {e}")
            self.session_state = VoiceSessionState.LISTENING
            await self.send(json.dumps({
                "type": "error",
                "message": f"对话处理错误: {str(e)}",
                "state": self.session_state
            }))

    async def voice_message(self, event):
        message = event["message"]
        await self.send(json.dumps(message))
