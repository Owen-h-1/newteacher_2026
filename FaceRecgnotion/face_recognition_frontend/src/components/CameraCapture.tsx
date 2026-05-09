import React, { useRef, useEffect, useState } from 'react';

interface CameraCaptureProps {
  onFrameCapture: (imageData: string) => void;
  isActive: boolean;
}

const CameraCapture: React.FC<CameraCaptureProps> = ({ onFrameCapture, isActive }) => {
  const videoRef = useRef<HTMLVideoElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const [cameraReady, setCameraReady] = useState(false);
  const intervalRef = useRef<NodeJS.Timeout | null>(null);

  useEffect(() => {
    if (isActive) {
      startCamera();
    } else {
      stopCamera();
    }

    return () => {
      stopCamera();
    };
  }, [isActive]);

  const startCamera = async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: {
          width: { ideal: 640 },
          height: { ideal: 480 },
          facingMode: 'user'
        }
      });
      
      streamRef.current = stream;
      if (videoRef.current) {
        videoRef.current.srcObject = stream;
        videoRef.current.play();
        setCameraReady(true);
        
        setTimeout(() => {
          startFrameCapture();
        }, 500);
      }
    } catch (error) {
      console.error('无法访问摄像头:', error);
    }
  };

  const stopCamera = () => {
    if (intervalRef.current) {
      clearInterval(intervalRef.current);
      intervalRef.current = null;
    }
    if (streamRef.current) {
      streamRef.current.getTracks().forEach(track => track.stop());
      streamRef.current = null;
    }
    setCameraReady(false);
  };

  const startFrameCapture = () => {
    if (intervalRef.current) {
      clearInterval(intervalRef.current);
    }
    
    intervalRef.current = setInterval(() => {
      captureFrame();
    }, 300);
  };

  const captureFrame = () => {
    if (videoRef.current && canvasRef.current) {
      const video = videoRef.current;
      const canvas = canvasRef.current;
      const ctx = canvas.getContext('2d');
      
      if (ctx) {
        canvas.width = video.videoWidth;
        canvas.height = video.videoHeight;
        ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
        
        const imageData = canvas.toDataURL('image/jpeg', 0.6);
        onFrameCapture(imageData);
      }
    }
  };

  return (
    <div className="relative">
      <video
        ref={videoRef}
        className="w-full rounded-lg mirror-mode"
        playsInline
        muted
      />
      <canvas
        ref={canvasRef}
        className="hidden"
      />
      {!cameraReady && isActive && (
        <div className="absolute inset-0 flex items-center justify-center bg-black bg-opacity-50 rounded-lg">
          <p className="text-white text-lg">正在启动摄像头...</p>
        </div>
      )}
    </div>
  );
};

export default CameraCapture;
