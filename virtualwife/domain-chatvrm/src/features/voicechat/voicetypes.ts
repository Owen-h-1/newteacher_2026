export type VoiceSessionState = 'idle' | 'listening' | 'processing' | 'speaking';

export type VoiceMessageType = 
  | 'connected'
  | 'session_started'
  | 'session_ended'
  | 'speech_start'
  | 'speech_end'
  | 'transcribing'
  | 'transcribed'
  | 'transcription_empty'
  | 'chat_processing'
  | 'error'
  | 'cancelled'
  | 'config_updated'
  | 'pong';

export interface VoiceMessage {
  type: VoiceMessageType;
  session_id?: string;
  state?: VoiceSessionState;
  message?: string;
  text?: string;
  config?: VoiceConfig;
}

export interface VoiceConfig {
  asr_type: string;
  tts_type: string;
  tts_voice_id: string;
}

export interface AudioConstraints {
  echoCancellation: boolean;
  noiseSuppression: boolean;
  autoGainControl: boolean;
  sampleRate: number;
  channelCount: number;
}

export const DEFAULT_AUDIO_CONSTRAINTS: AudioConstraints = {
  echoCancellation: true,
  noiseSuppression: true,
  autoGainControl: true,
  sampleRate: 48000,
  channelCount: 1
};

export const DEFAULT_VOICE_CONFIG: VoiceConfig = {
  asr_type: 'openai',
  tts_type: 'Edge',
  tts_voice_id: 'zh-CN-XiaoxiaoNeural'
};
