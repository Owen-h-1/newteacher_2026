import { useCallback, useEffect, useRef, useState } from 'react';
import {
  VoiceSessionState,
  VoiceMessage,
  VoiceConfig,
  AudioConstraints,
  DEFAULT_AUDIO_CONSTRAINTS,
  DEFAULT_VOICE_CONFIG
} from './voiceTypes';

const RECONNECT_DELAY = 1000;
const MAX_RECONNECT_ATTEMPTS = 5;
const AUDIO_CHUNK_SIZE = 4096;
const SAMPLE_RATE = 48000;

export class VoiceChatManager {
  private ws: WebSocket | null = null;
  private audioContext: AudioContext | null = null;
  private mediaStream: MediaStream | null = null;
  private audioWorkletNode: AudioWorkletNode | null = null;
  private analyser: AnalyserNode | null = null;
  private state: VoiceSessionState = 'idle';
  private reconnectAttempts = 0;
  private isConnecting = false;
  private audioChunks: Int16Array[] = [];
  private config: VoiceConfig = DEFAULT_VOICE_CONFIG;
  private onStateChange?: (state: VoiceSessionState) => void;
  private onMessage?: (message: VoiceMessage) => void;
  private onError?: (error: string) => void;
  private onAudioLevel?: (level: number) => void;
  private onTranscribed?: (text: string) => void;
  private heartbeatInterval: NodeJS.Timeout | null = null;

  constructor(
    onStateChange?: (state: VoiceSessionState) => void,
    onMessage?: (message: VoiceMessage) => void,
    onError?: (error: string) => void,
    onAudioLevel?: (level: number) => void,
    onTranscribed?: (text: string) => void
  ) {
    this.onStateChange = onStateChange;
    this.onMessage = onMessage;
    this.onError = onError;
    this.onAudioLevel = onAudioLevel;
    this.onTranscribed = onTranscribed;
  }

  async connect(): Promise<boolean> {
    if (this.ws?.readyState === WebSocket.OPEN) {
      return true;
    }

    if (this.isConnecting) {
      return false;
    }

    this.isConnecting = true;

    return new Promise((resolve) => {
      try {
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const wsUrl = `${protocol}//${window.location.host}/api/chatbot/voice/`;
        
        this.ws = new WebSocket(wsUrl);

        this.ws.onopen = () => {
          console.log('Voice WebSocket connected');
          this.isConnecting = false;
          this.reconnectAttempts = 0;
          this.startHeartbeat();
          resolve(true);
        };

        this.ws.onmessage = (event) => {
          try {
            const message: VoiceMessage = JSON.parse(event.data);
            this.handleMessage(message);
          } catch (e) {
            console.error('Failed to parse voice message:', e);
          }
        };

        this.ws.onerror = (error) => {
          console.error('Voice WebSocket error:', error);
          this.isConnecting = false;
          this.onError?.('WebSocket连接错误');
          resolve(false);
        };

        this.ws.onclose = (event) => {
          console.log('Voice WebSocket closed:', event.code);
          this.isConnecting = false;
          this.stopHeartbeat();
          
          if (this.state !== 'idle' && this.reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            this.scheduleReconnect();
          }
        };

      } catch (error) {
        console.error('Failed to create WebSocket:', error);
        this.isConnecting = false;
        resolve(false);
      }
    });
  }

  private startHeartbeat() {
    this.heartbeatInterval = setInterval(() => {
      if (this.ws?.readyState === WebSocket.OPEN) {
        this.ws.send(JSON.stringify({ type: 'ping' }));
      }
    }, 30000);
  }

  private stopHeartbeat() {
    if (this.heartbeatInterval) {
      clearInterval(this.heartbeatInterval);
      this.heartbeatInterval = null;
    }
  }

  private scheduleReconnect() {
    this.reconnectAttempts++;
    const delay = RECONNECT_DELAY * Math.pow(2, this.reconnectAttempts - 1);
    
    console.log(`Scheduling reconnect attempt ${this.reconnectAttempts} in ${delay}ms`);
    
    setTimeout(async () => {
      const connected = await this.connect();
      if (connected && this.state !== 'idle') {
        await this.startSession();
      }
    }, delay);
  }

  private handleMessage(message: VoiceMessage) {
    if (message.state) {
      this.state = message.state;
      this.onStateChange?.(message.state);
    }
    
    if (message.type === 'transcribed' && message.text) {
      this.onTranscribed?.(message.text);
    }
    
    this.onMessage?.(message);
  }

  async startSession(config?: Partial<VoiceConfig>): Promise<boolean> {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
      const connected = await this.connect();
      if (!connected) return false;
    }

    if (config) {
      this.config = { ...this.config, ...config };
    }

    this.ws?.send(JSON.stringify({
      type: 'start_session',
      ...this.config
    }));

    return true;
  }

  endSession() {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify({ type: 'end_session' }));
    }
    this.state = 'idle';
    this.onStateChange?.('idle');
  }

  cancel() {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify({ type: 'cancel' }));
    }
  }

  updateConfig(config: Partial<VoiceConfig>) {
    this.config = { ...this.config, ...config };
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify({
        type: 'config',
        ...this.config
      }));
    }
  }

  async startAudioCapture(constraints: AudioConstraints = DEFAULT_AUDIO_CONSTRAINTS): Promise<boolean> {
    try {
      if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
        throw new Error('浏览器不支持麦克风访问，请使用现代浏览器');
      }

      if (location.protocol !== 'https:' && location.hostname !== 'localhost' && location.hostname !== '127.0.0.1') {
        throw new Error('麦克风访问需要HTTPS或localhost环境');
      }

      this.audioContext = new AudioContext({ sampleRate: SAMPLE_RATE });
      
      if (this.audioContext.state === 'suspended') {
        await this.audioContext.resume();
      }

      this.mediaStream = await navigator.mediaDevices.getUserMedia({
        audio: {
          echoCancellation: constraints.echoCancellation,
          noiseSuppression: constraints.noiseSuppression,
          autoGainControl: constraints.autoGainControl,
          sampleRate: constraints.sampleRate,
          channelCount: constraints.channelCount
        }
      }).catch((err) => {
        if (err.name === 'NotAllowedError' || err.name === 'PermissionDeniedError') {
          throw new Error('麦克风权限被拒绝，请在浏览器设置中允许访问麦克风');
        } else if (err.name === 'NotFoundError') {
          throw new Error('未找到麦克风设备，请检查麦克风是否已连接');
        } else if (err.name === 'NotReadableError') {
          throw new Error('麦克风被其他应用程序占用，请关闭其他应用后重试');
        } else {
          throw new Error(`麦克风访问失败: ${err.message}`);
        }
      });

      const source = this.audioContext.createMediaStreamSource(this.mediaStream);
      
      this.analyser = this.audioContext.createAnalyser();
      this.analyser.fftSize = 256;
      source.connect(this.analyser);
      
      try {
        await this.audioContext.audioWorklet.addModule('/audio-processor.js');
      } catch (workletError) {
        console.error('Failed to load audio worklet:', workletError);
        throw new Error('音频处理器加载失败，请刷新页面重试');
      }
      
      this.audioWorkletNode = new AudioWorkletNode(this.audioContext, 'audio-processor');
      
      this.audioWorkletNode.port.onmessage = (event) => {
        if (event.data.type === 'audio') {
          this.sendAudioData(event.data.buffer);
        }
      };
      
      this.audioWorkletNode.onprocessorerror = (event) => {
        console.error('Audio worklet processor error:', event);
        this.onError?.('音频处理器发生错误');
      };
      
      source.connect(this.audioWorkletNode);
      this.audioWorkletNode.connect(this.audioContext.destination);
      
      this.startAudioLevelMonitor();
      
      return true;
    } catch (error) {
      console.error('Failed to start audio capture:', error);
      const errorMessage = error instanceof Error ? error.message : '无法访问麦克风，请检查权限设置';
      this.onError?.(errorMessage);
      this.stopAudioCapture();
      return false;
    }
  }

  private startAudioLevelMonitor() {
    if (!this.analyser) return;
    
    const dataArray = new Uint8Array(this.analyser.frequencyBinCount);
    
    const monitor = () => {
      if (!this.analyser || this.state === 'idle') return;
      
      this.analyser.getByteFrequencyData(dataArray);
      const average = dataArray.reduce((a, b) => a + b) / dataArray.length;
      const normalizedLevel = average / 255;
      
      this.onAudioLevel?.(normalizedLevel);
      
      requestAnimationFrame(monitor);
    };
    
    monitor();
  }

  private sendAudioData(buffer: ArrayBuffer) {
    if (this.ws?.readyState === WebSocket.OPEN && this.state === 'listening') {
      this.ws.send(buffer);
    }
  }

  stopAudioCapture() {
    if (this.audioWorkletNode) {
      this.audioWorkletNode.disconnect();
      this.audioWorkletNode = null;
    }
    
    if (this.mediaStream) {
      this.mediaStream.getTracks().forEach(track => track.stop());
      this.mediaStream = null;
    }
    
    if (this.audioContext) {
      this.audioContext.close();
      this.audioContext = null;
    }
    
    this.analyser = null;
  }

  disconnect() {
    this.stopHeartbeat();
    this.stopAudioCapture();
    this.endSession();
    
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
    
    this.state = 'idle';
    this.onStateChange?.('idle');
  }

  getState(): VoiceSessionState {
    return this.state;
  }

  isConnected(): boolean {
    return this.ws?.readyState === WebSocket.OPEN;
  }
}

export function useVoiceChat(onTranscribed?: (text: string) => void) {
  const managerRef = useRef<VoiceChatManager | null>(null);
  const [state, setState] = useState<VoiceSessionState>('idle');
  const [isConnected, setIsConnected] = useState(false);
  const [audioLevel, setAudioLevel] = useState(0);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    managerRef.current = new VoiceChatManager(
      setState,
      undefined,
      setError,
      setAudioLevel,
      onTranscribed
    );

    return () => {
      managerRef.current?.disconnect();
    };
  }, [onTranscribed]);

  const connect = useCallback(async () => {
    const result = await managerRef.current?.connect() || false;
    setIsConnected(result);
    return result;
  }, []);

  const startSession = useCallback(async (config?: Partial<VoiceConfig>) => {
    return managerRef.current?.startSession(config) || false;
  }, []);

  const endSession = useCallback(() => {
    managerRef.current?.endSession();
  }, []);

  const startAudioCapture = useCallback(async () => {
    return managerRef.current?.startAudioCapture() || false;
  }, []);

  const stopAudioCapture = useCallback(() => {
    managerRef.current?.stopAudioCapture();
  }, []);

  const cancel = useCallback(() => {
    managerRef.current?.cancel();
  }, []);

  const disconnect = useCallback(() => {
    managerRef.current?.disconnect();
    setIsConnected(false);
  }, []);

  return {
    state,
    isConnected,
    audioLevel,
    error,
    connect,
    startSession,
    endSession,
    startAudioCapture,
    stopAudioCapture,
    cancel,
    disconnect
  };
}
