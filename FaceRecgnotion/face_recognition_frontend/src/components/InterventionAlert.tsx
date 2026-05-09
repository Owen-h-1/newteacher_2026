import React from 'react';

interface InterventionAlertProps {
  intervention: {
    state: string;
    suggestions: string[];
    confidence: number;
  } | null;
  onTrigger: () => void;
  onDismiss: () => void;
}

const InterventionAlert: React.FC<InterventionAlertProps> = ({
  intervention,
  onTrigger,
  onDismiss
}) => {
  if (!intervention) {
    return null;
  }

  const stateTitles: Record<string, string> = {
    frustrated: '学生遇到困难',
    confused: '学生感到困惑',
    disengaged: '学生注意力分散'
  };

  const stateEmojis: Record<string, string> = {
    frustrated: '😰',
    confused: '❓',
    disengaged: '💤'
  };

  return (
    <div className="bg-gradient-to-r from-yellow-400 to-orange-500 rounded-lg p-6 text-white shadow-lg animate-pulse">
      <div className="flex items-start space-x-4">
        <div className="text-4xl">
          {stateEmojis[intervention.state] || '⚠️'}
        </div>
        <div className="flex-1">
          <h3 className="text-xl font-bold mb-2">
            {stateTitles[intervention.state] || '需要注意'}
          </h3>
          <p className="text-sm opacity-90 mb-3">
            置信度: {(intervention.confidence * 100).toFixed(1)}%
          </p>
          <div className="space-y-2 mb-4">
            <p className="font-semibold">建议措施:</p>
            <ul className="list-disc list-inside text-sm space-y-1">
              {intervention.suggestions.map((suggestion, index) => (
                <li key={index}>{suggestion}</li>
              ))}
            </ul>
          </div>
          <div className="flex space-x-3">
            <button
              onClick={onTrigger}
              className="bg-white text-orange-600 px-4 py-2 rounded-lg font-semibold hover:bg-orange-50 transition-colors"
            >
              触发知识点讲解
            </button>
            <button
              onClick={onDismiss}
              className="bg-orange-600 bg-opacity-30 border border-white border-opacity-50 px-4 py-2 rounded-lg font-semibold hover:bg-opacity-50 transition-colors"
            >
              忽略
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default InterventionAlert;
