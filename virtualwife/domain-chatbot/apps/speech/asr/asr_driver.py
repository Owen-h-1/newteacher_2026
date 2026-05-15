import logging
import asyncio
import json
import base64
import time
from typing import Optional, AsyncGenerator
from abc import ABC, abstractmethod

logger = logging.getLogger(__name__)


class BaseASR(ABC):
    @abstractmethod
    async def transcribe(self, audio_data: bytes) -> str:
        pass

    @abstractmethod
    def get_name(self) -> str:
        pass


class OpenAIWhisperASR(BaseASR):
    def __init__(self, api_key: str, base_url: str = ""):
        self.api_key = api_key
        self.base_url = base_url or "https://api.openai.com/v1"
        self.model = "whisper-1"

    async def transcribe(self, audio_data: bytes) -> str:
        import aiohttp
        
        url = f"{self.base_url}/audio/transcriptions"
        headers = {
            "Authorization": f"Bearer {self.api_key}"
        }
        
        form_data = aiohttp.FormData()
        form_data.add_field('file', audio_data, filename='audio.webm', content_type='audio/webm')
        form_data.add_field('model', self.model)
        form_data.add_field('language', 'zh')
        
        try:
            async with aiohttp.ClientSession() as session:
                async with session.post(url, headers=headers, data=form_data, timeout=30) as response:
                    if response.status == 200:
                        result = await response.json()
                        return result.get("text", "")
                    else:
                        error_text = await response.text()
                        logger.error(f"OpenAI Whisper API error: {error_text}")
                        return ""
        except Exception as e:
            logger.error(f"OpenAI Whisper transcription error: {e}")
            return ""

    def get_name(self) -> str:
        return "OpenAI Whisper"


class FunASR(BaseASR):
    def __init__(self, server_url: str = "http://localhost:10095"):
        self.server_url = server_url

    async def transcribe(self, audio_data: bytes) -> str:
        import aiohttp
        
        url = f"{self.server_url}/asr"
        
        try:
            async with aiohttp.ClientSession() as session:
                async with session.post(url, data=audio_data, timeout=30) as response:
                    if response.status == 200:
                        result = await response.json()
                        return result.get("text", "")
                    else:
                        logger.error(f"FunASR error: {response.status}")
                        return ""
        except Exception as e:
            logger.error(f"FunASR transcription error: {e}")
            return ""

    def get_name(self) -> str:
        return "FunASR"


class ASRDriver:
    _instance = None
    _asr_cache = {}

    def __new__(cls):
        if cls._instance is None:
            cls._instance = super().__new__(cls)
        return cls._instance

    def get_asr(self, asr_type: str, **kwargs) -> BaseASR:
        cache_key = f"{asr_type}_{hash(frozenset(kwargs.items()))}"
        
        if cache_key in self._asr_cache:
            return self._asr_cache[cache_key]
        
        if asr_type == "openai":
            asr = OpenAIWhisperASR(
                api_key=kwargs.get("api_key", ""),
                base_url=kwargs.get("base_url", "")
            )
        elif asr_type == "funasr":
            asr = FunASR(
                server_url=kwargs.get("server_url", "http://localhost:10095")
            )
        else:
            raise ValueError(f"Unknown ASR type: {asr_type}")
        
        self._asr_cache[cache_key] = asr
        return asr

    def get_available_asr_types(self) -> list:
        return [
            {"id": "openai", "name": "OpenAI Whisper", "description": "高精度语音识别，需要API密钥"},
            {"id": "funasr", "name": "FunASR", "description": "开源语音识别，支持本地部署"}
        ]
