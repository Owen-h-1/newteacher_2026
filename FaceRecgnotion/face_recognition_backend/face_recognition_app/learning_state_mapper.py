from typing import Dict, List, Optional
from collections import deque
import time


class LearningStateMapper:
    def __init__(self):
        self.expression_to_state = {
            'happy': 'engaged',
            'sad': 'frustrated',
            'angry': 'frustrated',
            'surprised': 'curious',
            'neutral': 'neutral',
            'confused': 'confused',
            'bored': 'disengaged',
            'focused': 'engaged',
        }
        
        self.state_descriptions = {
            'engaged': '积极参与',
            'frustrated': '感到困难',
            'curious': '好奇探索',
            'neutral': '状态稳定',
            'confused': '困惑不解',
            'disengaged': '注意力分散',
        }
        
        self.state_to_suggestions = {
            'engaged': [
                '继续当前主题，增加难度',
                '引入拓展知识',
            ],
            'frustrated': [
                '放慢节奏，重新讲解难点',
                '提供更简单的例子',
            ],
            'curious': [
                '深入探讨相关话题',
                '鼓励提问',
            ],
            'confused': [
                '重新解释概念',
                '使用不同的方法讲解',
            ],
            'disengaged': [
                '变换教学方式',
                '引入互动元素',
            ],
            'neutral': [
                '保持当前节奏',
                '检查理解程度',
            ],
        }
        
        self.expression_window = deque(maxlen=30)
        self.last_trigger_time = 0
        self.trigger_cooldown = 10
        
    def update_expression(self, expression: str, confidence: float):
        timestamp = time.time()
        self.expression_window.append({
            'expression': expression,
            'confidence': confidence,
            'timestamp': timestamp
        })
    
    def get_current_state(self) -> Dict:
        if not self.expression_window:
            return {
                'state': 'neutral',
                'confidence': 0.5,
                'description': self.state_descriptions['neutral']
            }
        
        state_counts = {}
        for expr_data in self.expression_window:
            state = self.expression_to_state.get(expr_data['expression'], 'neutral')
            if state not in state_counts:
                state_counts[state] = 0
            state_counts[state] += expr_data['confidence']
        
        if not state_counts:
            return {
                'state': 'neutral',
                'confidence': 0.5,
                'description': self.state_descriptions['neutral']
            }
        
        dominant_state = max(state_counts.items(), key=lambda x: x[1])
        state, total_confidence = dominant_state
        avg_confidence = total_confidence / len(self.expression_window)
        
        return {
            'state': state,
            'confidence': min(avg_confidence, 1.0),
            'description': self.state_descriptions.get(state, '未知状态')
        }
    
    def should_trigger_intervention(self) -> Optional[Dict]:
        current_time = time.time()
        
        if current_time - self.last_trigger_time < self.trigger_cooldown:
            return None
        
        state_data = self.get_current_state()
        state = state_data['state']
        
        if state in ['frustrated', 'confused', 'disengaged'] and state_data['confidence'] > 0.6:
            self.last_trigger_time = current_time
            return {
                'trigger': True,
                'state': state,
                'suggestions': self.state_to_suggestions.get(state, []),
                'confidence': state_data['confidence']
            }
        
        return None
    
    def get_suggestions(self, state: Optional[str] = None) -> List[str]:
        if state is None:
            state = self.get_current_state()['state']
        return self.state_to_suggestions.get(state, [])
    
    def calculate_engagement_score(self) -> float:
        state_data = self.get_current_state()
        state = state_data['state']
        
        state_scores = {
            'engaged': 0.9,
            'focused': 0.85,
            'curious': 0.75,
            'neutral': 0.5,
            'confused': 0.3,
            'frustrated': 0.25,
            'disengaged': 0.15,
        }
        
        base_score = state_scores.get(state, 0.5)
        confidence = state_data['confidence']
        
        return base_score * confidence + 0.5 * (1 - confidence)
    
    def reset(self):
        self.expression_window.clear()
        self.last_trigger_time = 0
