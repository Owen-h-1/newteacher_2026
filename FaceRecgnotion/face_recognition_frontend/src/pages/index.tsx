import React, { useState, useEffect, useRef } from 'react';
import CameraCapture from '../components/CameraCapture';
import ExpressionDisplay from '../components/ExpressionDisplay';
import EngagementChart from '../components/EngagementChart';
import InterventionAlert from '../components/InterventionAlert';

const API_BASE_URL = 'http://localhost:8080/api';

interface EngagementData {
  time: string;
  engagement: number;
}

const HomePage: React.FC = () => {
  const [isSessionActive, setIsSessionActive] = useState(false);
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [currentExpression, setCurrentExpression] = useState('neutral');
  const [currentConfidence, setCurrentConfidence] = useState(0.5);
  const [learningState, setLearningState] = useState('neutral');
  const [learningStateDesc, setLearningStateDesc] = useState('状态稳定');
  const [engagementScore, setEngagementScore] = useState(0.5);
  const [engagementHistory, setEngagementHistory] = useState<EngagementData[]>([]);
  const [intervention, setIntervention] = useState<any>(null);
  const [isAnalyzing, setIsAnalyzing] = useState(false);

  const startSession = async () => {
    try {
      const response = await fetch(`${API_BASE_URL}/session/start`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
      });
      const data = await response.json();
      if (data.success) {
        setSessionId(data.session_id);
        setIsSessionActive(true);
        setEngagementHistory([]);
      }
    } catch (error) {
      console.error('Failed to start session:', error);
    }
  };

  const endSession = async () => {
    if (!sessionId) return;
    
    try {
      const response = await fetch(`${API_BASE_URL}/session/end`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ session_id: sessionId }),
      });
      const data = await response.json();
      if (data.success) {
        setIsSessionActive(false);
        setSessionId(null);
        setIntervention(null);
      }
    } catch (error) {
      console.error('Failed to end session:', error);
    }
  };

  const analyzeFrame = async (imageData: string) => {
    if (!sessionId || isAnalyzing) return;
    
    setIsAnalyzing(true);
    
    try {
      const response = await fetch(`${API_BASE_URL}/analyze`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          session_id: sessionId,
          image: imageData,
        }),
      });
      const data = await response.json();
      
      if (data.success) {
        setCurrentExpression(data.expression);
        setCurrentConfidence(data.confidence);
        setLearningState(data.learning_state.state);
        setLearningStateDesc(data.learning_state.description);
        setEngagementScore(data.engagement_score);
        
        const now = new Date();
        const timeStr = now.toLocaleTimeString('zh-CN', { 
          hour: '2-digit', 
          minute: '2-digit', 
          second: '2-digit' 
        });
        
        setEngagementHistory(prev => {
          const newHistory = [...prev, { time: timeStr, engagement: data.engagement_score }];
          return newHistory.slice(-30);
        });
        
        if (data.intervention) {
          setIntervention(data.intervention);
        }
      }
    } catch (error) {
      console.error('Failed to analyze frame:', error);
    } finally {
      setIsAnalyzing(false);
    }
  };

  const handleTriggerIntervention = async () => {
    setIntervention(null);
  };

  const handleDismissIntervention = () => {
    setIntervention(null);
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-blue-50 to-indigo-100 p-6">
      <div className="max-w-7xl mx-auto">
        <div className="text-center mb-8">
          <h1 className="text-4xl font-bold text-gray-800 mb-2">
            🎓 智能学习表情分析系统
          </h1>
          <p className="text-gray-600">
            通过面部表情识别，实现个性化学习体验
          </p>
        </div>

        {intervention && (
          <div className="mb-6">
            <InterventionAlert
              intervention={intervention}
              onTrigger={handleTriggerIntervention}
              onDismiss={handleDismissIntervention}
            />
          </div>
        )}

        <div className="flex justify-center mb-6">
          {!isSessionActive ? (
            <button
              onClick={startSession}
              className="bg-gradient-to-r from-green-500 to-emerald-600 text-white px-8 py-3 rounded-lg font-semibold text-lg shadow-lg hover:from-green-600 hover:to-emerald-700 transform hover:scale-105 transition-all"
            >
              🎯 开始学习会话
            </button>
          ) : (
            <button
              onClick={endSession}
              className="bg-gradient-to-r from-red-500 to-rose-600 text-white px-8 py-3 rounded-lg font-semibold text-lg shadow-lg hover:from-red-600 hover:to-rose-700 transform hover:scale-105 transition-all"
            >
              🛑 结束学习会话
            </button>
          )}
        </div>

        {isSessionActive && (
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            <div className="lg:col-span-1 space-y-6">
              <div className="bg-white rounded-lg shadow-lg overflow-hidden">
                <div className="p-4 bg-gradient-to-r from-indigo-500 to-purple-600">
                  <h2 className="text-xl font-bold text-white">📷 摄像头</h2>
                </div>
                <div className="p-4">
                  <CameraCapture
                    onFrameCapture={analyzeFrame}
                    isActive={isSessionActive}
                  />
                </div>
              </div>

              <ExpressionDisplay
                expression={currentExpression}
                confidence={currentConfidence}
                learningState={learningState}
                learningStateDesc={learningStateDesc}
              />
            </div>

            <div className="lg:col-span-2 space-y-6">
              <div className="bg-white rounded-lg shadow-lg p-6">
                <h2 className="text-xl font-bold text-gray-800 mb-4">
                  📊 学习参与度
                </h2>
                <div className="flex items-center justify-center mb-6">
                  <div className="text-center">
                    <div className="text-6xl font-bold text-indigo-600 mb-2">
                      {(engagementScore * 100).toFixed(0)}%
                    </div>
                    <div className="text-gray-600">当前参与度</div>
                  </div>
                </div>
                <EngagementChart data={engagementHistory} />
              </div>

              <div className="bg-white rounded-lg shadow-lg p-6">
                <h2 className="text-xl font-bold text-gray-800 mb-4">
                  💡 功能说明
                </h2>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div className="p-4 bg-blue-50 rounded-lg">
                    <h3 className="font-semibold text-blue-800 mb-2">🎭 表情识别</h3>
                    <p className="text-sm text-blue-600">实时识别8种面部表情</p>
                  </div>
                  <div className="p-4 bg-green-50 rounded-lg">
                    <h3 className="font-semibold text-green-800 mb-2">📈 状态分析</h3>
                    <p className="text-sm text-green-600">分析学习状态变化</p>
                  </div>
                  <div className="p-4 bg-yellow-50 rounded-lg">
                    <h3 className="font-semibold text-yellow-800 mb-2">🔔 智能提示</h3>
                    <p className="text-sm text-yellow-600">根据状态提供干预建议</p>
                  </div>
                  <div className="p-4 bg-purple-50 rounded-lg">
                    <h3 className="font-semibold text-purple-800 mb-2">📚 知识触发</h3>
                    <p className="text-sm text-purple-600">自动触发知识点讲解</p>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}

        {!isSessionActive && (
          <div className="bg-white rounded-lg shadow-lg p-8 text-center">
            <div className="text-8xl mb-4">📖</div>
            <h2 className="text-2xl font-bold text-gray-800 mb-2">
              准备开始学习
            </h2>
            <p className="text-gray-600 mb-6">
              点击"开始学习会话"按钮，启动摄像头和表情分析功能
            </p>
            <div className="max-w-md mx-auto text-left">
              <h3 className="font-semibold text-gray-800 mb-3">使用前准备：</h3>
              <ul className="list-disc list-inside space-y-2 text-gray-600">
                <li>确保摄像头已连接并允许浏览器访问</li>
                <li>保持良好的光照环境</li>
                <li>坐在摄像头前适当距离</li>
                <li>确保后端服务正在运行</li>
              </ul>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default HomePage;
