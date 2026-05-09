import React from 'react';

interface ExpressionDisplayProps {
  expression: string;
  confidence: number;
  learningState: string;
  learningStateDesc: string;
}

const expressionEmojis: Record<string, string> = {
  happy: '😊',
  sad: '😢',
  angry: '😠',
  surprised: '😲',
  neutral: '😐',
  confused: '😕',
  bored: '😴',
  focused: '🤔'
};

const expressionNames: Record<string, string> = {
  happy: '开心',
  sad: '悲伤',
  angry: '愤怒',
  surprised: '惊讶',
  neutral: '中性',
  confused: '困惑',
  bored: '无聊',
  focused: '专注'
};

const stateColors: Record<string, string> = {
  engaged: 'bg-green-100 text-green-800 border-green-300',
  frustrated: 'bg-red-100 text-red-800 border-red-300',
  curious: 'bg-yellow-100 text-yellow-800 border-yellow-300',
  neutral: 'bg-gray-100 text-gray-800 border-gray-300',
  confused: 'bg-orange-100 text-orange-800 border-orange-300',
  disengaged: 'bg-blue-100 text-blue-800 border-blue-300'
};

const ExpressionDisplay: React.FC<ExpressionDisplayProps> = ({
  expression,
  confidence,
  learningState,
  learningStateDesc
}) => {
  return (
    <div className="space-y-4">
      <div className="flex items-center space-x-4 p-4 bg-white rounded-lg shadow">
        <div className="text-6xl">
          {expressionEmojis[expression] || '😐'}
        </div>
        <div>
          <h3 className="text-xl font-semibold text-gray-800">
            {expressionNames[expression] || expression}
          </h3>
          <p className="text-sm text-gray-600">
            置信度: {(confidence * 100).toFixed(1)}%
          </p>
        </div>
      </div>

      <div className={`p-4 rounded-lg border-2 ${stateColors[learningState] || stateColors.neutral}`}>
        <h4 className="font-semibold mb-1">学习状态</h4>
        <p className="text-lg">{learningStateDesc}</p>
      </div>
    </div>
  );
};

export default ExpressionDisplay;
