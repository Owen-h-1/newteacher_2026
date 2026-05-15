<template>
  <div class="ai-assistant-panel">
    <div class="assistant-header">
      <div class="avatar">
        <i class="fas fa-robot"></i>
      </div>
      <div class="title">
        <h3>AI助教·小智</h3>
        <span class="status" :class="{ online: isOnline }">
          {{ isOnline ? '在线' : '离线' }}
        </span>
      </div>
      <button class="btn-expand" @click="toggleExpand" :title="isExpanded ? '收起' : '展开全屏'">
        <i :class="isExpanded ? 'fas fa-compress' : 'fas fa-expand'"></i>
        <span class="expand-text">{{ isExpanded ? '收起' : '展开' }}</span>
      </button>
    </div>

    <div class="chat-container" ref="chatContainer" :class="{ expanded: isExpanded }">
      <div v-for="msg in messages" :key="msg.id" class="message" :class="msg.role">
        <div class="message-avatar">
          <i :class="msg.role === 'user' ? 'fas fa-user' : 'fas fa-robot'"></i>
        </div>
        <div class="message-content">
          <p>{{ msg.content }}</p>
          <span class="time">{{ formatTime(msg.timestamp) }}</span>
        </div>
      </div>
      <div v-if="isTyping" class="message assistant typing">
        <div class="message-avatar">
          <i class="fas fa-robot"></i>
        </div>
        <div class="message-content">
          <div class="typing-indicator">
            <span></span><span></span><span></span>
          </div>
        </div>
      </div>
    </div>

    <div v-if="isExpanded" class="expanded-features">
      <div class="feature-cards">
        <div class="feature-card" @click="sendQuickMessage('帮我制定今天的学习计划')">
          <i class="fas fa-calendar-check"></i>
          <span>学习计划</span>
        </div>
        <div class="feature-card" @click="sendQuickMessage('我想复习今天的课程内容')">
          <i class="fas fa-book-reader"></i>
          <span>课程复习</span>
        </div>
        <div class="feature-card" @click="sendQuickMessage('请帮我检查一下作业')">
          <i class="fas fa-tasks"></i>
          <span>作业检查</span>
        </div>
        <div class="feature-card" @click="sendQuickMessage('我有些知识点不太理解')">
          <i class="fas fa-lightbulb"></i>
          <span>答疑解惑</span>
        </div>
        <div class="feature-card digital-human" @click="openDigitalHuman">
          <i class="fas fa-user-tie"></i>
          <span>AI数字人老师</span>
        </div>
      </div>
    </div>

    <div class="input-area">
      <div class="quick-actions" v-if="!isExpanded">
        <button v-for="action in quickActions" :key="action.text" 
                class="quick-btn" @click="sendQuickMessage(action.text)">
          {{ action.label }}
        </button>
      </div>
      <div class="input-row">
        <input
          v-model="inputText"
          type="text"
          placeholder="问我任何学习问题..."
          @keyup.enter="sendMessage"
          :disabled="isTyping"
        />
        <button class="btn-send" @click="sendMessage" :disabled="!inputText.trim() || isTyping">
          <i class="fas fa-paper-plane"></i>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue';

const props = defineProps({
  studentName: {
    type: String,
    default: '同学'
  }
});

const messages = ref([
  {
    id: 1,
    role: 'assistant',
    content: `你好${props.studentName}！我是AI助教小智🤖\n\n我可以帮你：\n• 解答学习疑问\n• 制定学习计划\n• 检查作业\n• 复习知识点\n\n有什么可以帮你的吗？`,
    timestamp: new Date()
  }
]);

const inputText = ref('');
const isTyping = ref(false);
const isOnline = ref(true);
const chatContainer = ref(null);
const isExpanded = ref(false);

const quickActions = [
  { label: '今日作业', text: '今天有什么作业需要完成？' },
  { label: '学习方法', text: '有什么好的学习方法推荐吗？' },
  { label: '错题辅导', text: '我有道题不会做，能帮我讲解一下吗？' }
];

const formatTime = (date) => {
  const d = new Date(date);
  return `${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`;
};

const scrollToBottom = () => {
  nextTick(() => {
    if (chatContainer.value) {
      chatContainer.value.scrollTop = chatContainer.value.scrollHeight;
    }
  });
};

const generateResponse = (userMessage) => {
  const msg = userMessage.toLowerCase();
  
  if (msg.includes('作业') || msg.includes('任务')) {
    return '建议你先查看今日待办作业，优先完成紧急任务。\n\n💡 小贴士：做完作业后可以来找我检查哦！';
  }
  if (msg.includes('学习计划') || msg.includes('计划') || msg.includes('安排')) {
    return '好的！我来帮你制定学习计划：\n\n📚 **今日计划建议**：\n1. 先完成老师布置的作业\n2. 复习今天学的重点知识\n3. 预习明天要学的内容\n4. 留30分钟阅读课外书\n\n需要我帮你调整具体的时间安排吗？';
  }
  if (msg.includes('复习') || msg.includes('课程')) {
    return '复习是学习的重要环节！\n\n📖 **复习方法推荐**：\n1. 先回顾课堂笔记和重点\nn2. 做几道相关练习题巩固\n3. 整理错题本，分析错误原因\n4. 尝试用自己的话复述知识点\n\n你想复习哪个科目呢？';
  }
  if (msg.includes('检查') || msg.includes('作业')) {
    return '好的！请把你的作业内容或题目发给我，我会帮你：\n\n✅ 检查答案是否正确\n✅ 指出可能的错误\n✅ 提供解题思路\n✅ 推荐类似练习题\n\n记住，理解比答案更重要哦～';
  }
  if (msg.includes('学习方法') || msg.includes('技巧')) {
    return '好的学习方法包括：\n\n🎯 **高效学习法**：\n1. **番茄工作法** - 25分钟专注+5分钟休息\n2. **费曼学习法** - 用自己的话教别人\n3. **间隔重复** - 定期回顾已学内容\n4. **主动回忆** - 合上书本回忆要点\n\n需要我详细讲解某个方法吗？';
  }
  if (msg.includes('错题') || msg.includes('不会') || msg.includes('讲解')) {
    return '没问题！请把题目发给我，我会帮你：\n\n🔍 分析解题思路\n📝 解释涉及的知识点\n💡 提供类似的练习题\n⚠️ 指出常见的易错点\n\n记住，理解比答案更重要哦～';
  }
  if (msg.includes('数学') || msg.includes('计算')) {
    return '数学学习建议：\n\n🔢 **数学提升攻略**：\n1. 先理解概念和公式推导\n2. 多做例题熟悉解题步骤\n3. 整理常见题型和解题模板\n4. 遇到难题先拆分成小问题\n\n有什么具体的数学问题可以问我！';
  }
  if (msg.includes('英语') || msg.includes('单词')) {
    return '英语学习小技巧：\n\n🌟 **英语进阶指南**：\n1. 每天背10个新单词，用造句加深记忆\n2. 多听多读培养语感\n3. 尝试用英语写简短的日记\n4. 看英文视频/电影练听力\n\n加油！坚持就会有进步！';
  }
  if (msg.includes('语文') || msg.includes('阅读')) {
    return '语文学习重在积累：\n\n📚 **语文提升方法**：\n1. 多读优秀文章，积累好词好句\n2. 练习写作表达，从短文开始\n3. 背诵古诗词，理解意境和情感\n4. 做阅读理解时学会找关键信息\n\n有什么具体的语文问题可以问我～';
  }
  if (msg.includes('知识点') || msg.includes('不懂') || msg.includes('疑惑')) {
    return '别担心，学习中有疑问是很正常的！\n\n💡 我可以这样帮你：\n1. 用简单易懂的方式解释概念\n2. 举例说明抽象的知识点\n3. 推荐相关的学习资源\n4. 设计练习题帮你巩固\n\n你想了解哪个方面的内容呢？';
  }
  
  return '这是个好问题！🤔\n\n我是你的AI学习助手，可以帮你：\n• 解答各科学习疑问\n• 提供学习方法和建议\n• 检查和辅导作业\n• 制定个性化学习计划\n\n请告诉我你具体想了解什么，我会尽力帮助你！';
};

const sendMessage = async () => {
  const text = inputText.value.trim();
  if (!text || isTyping.value) return;

  messages.value.push({
    id: Date.now(),
    role: 'user',
    content: text,
    timestamp: new Date()
  });

  inputText.value = '';
  scrollToBottom();

  isTyping.value = true;
  
  await new Promise(resolve => setTimeout(resolve, 800 + Math.random() * 1200));
  
  const response = generateResponse(text);
  
  messages.value.push({
    id: Date.now() + 1,
    role: 'assistant',
    content: response,
    timestamp: new Date()
  });

  isTyping.value = false;
  scrollToBottom();
};

const sendQuickMessage = (text) => {
  inputText.value = text;
  sendMessage();
};

const toggleExpand = () => {
  isExpanded.value = !isExpanded.value;
  scrollToBottom();
};

const openDigitalHuman = () => {
  window.open('http://localhost:8888', '_blank');
};

onMounted(() => {
  scrollToBottom();
});
</script>

<style scoped lang="scss">
.ai-assistant-panel {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 20px;
  padding: 16px;
  color: white;
  box-shadow: 0 10px 30px rgba(102, 126, 234, 0.3);
  transition: all 0.3s ease;
  
  &.expanded-mode {
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    z-index: 9999;
    border-radius: 0;
    padding: 20px;
  }
}

.assistant-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  
  .avatar {
    width: 48px;
    height: 48px;
    background: rgba(255, 255, 255, 0.2);
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 24px;
    animation: pulse 2s infinite;
  }
  
  .title {
    flex: 1;
    
    h3 {
      margin: 0;
      font-size: 18px;
      font-weight: 600;
    }
    
    .status {
      font-size: 12px;
      opacity: 0.8;
      
      &.online {
        color: #4ade80;
      }
    }
  }
  
  .btn-expand {
    background: linear-gradient(135deg, rgba(255, 255, 255, 0.3), rgba(255, 255, 255, 0.15));
    border: 2px solid rgba(255, 255, 255, 0.4);
    border-radius: 20px;
    color: white;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
    padding: 8px 14px;
    transition: all 0.3s;
    font-size: 13px;
    font-weight: 600;
    animation: pulseGlow 2s infinite;
    
    i {
      font-size: 14px;
    }
    
    .expand-text {
      display: none;
      
      @media (min-width: 400px) {
        display: inline;
      }
    }
    
    &:hover {
      background: linear-gradient(135deg, rgba(255, 255, 255, 0.45), rgba(255, 255, 255, 0.25));
      border-color: rgba(255, 255, 255, 0.6);
      transform: scale(1.08);
      box-shadow: 0 4px 15px rgba(255, 255, 255, 0.3);
    }
  }
}

@keyframes pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.05); }
}

@keyframes pulseGlow {
  0%, 100% { 
    box-shadow: 0 0 5px rgba(255, 255, 255, 0.3);
  }
  50% { 
    box-shadow: 0 0 20px rgba(255, 255, 255, 0.6), 0 0 30px rgba(255, 255, 255, 0.3);
  }
}

.chat-container {
  background: rgba(255, 255, 255, 0.1);
  border-radius: 16px;
  padding: 12px;
  height: 200px;
  overflow-y: auto;
  margin-bottom: 12px;
  transition: all 0.3s ease;
  
  &.expanded {
    height: 400px;
    
    @media (min-height: 600px) {
      height: calc(100vh - 280px);
    }
  }
  
  &::-webkit-scrollbar {
    width: 6px;
  }
  
  &::-webkit-scrollbar-thumb {
    background: rgba(255, 255, 255, 0.3);
    border-radius: 3px;
  }
}

.message {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  animation: fadeIn 0.3s ease;
  
  &.user {
    flex-direction: row-reverse;
    
    .message-content {
      background: rgba(255, 255, 255, 0.95);
      color: #333;
    }
  }
  
  &.assistant {
    .message-content {
      background: rgba(255, 255, 255, 0.2);
      white-space: pre-line;
    }
  }
  
  .message-avatar {
    width: 28px;
    height: 28px;
    background: rgba(255, 255, 255, 0.3);
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 12px;
    flex-shrink: 0;
  }
  
  .message-content {
    max-width: 85%;
    padding: 10px 14px;
    border-radius: 14px;
    
    p {
      margin: 0;
      font-size: 14px;
      line-height: 1.6;
    }
    
    .time {
      font-size: 10px;
      opacity: 0.6;
      display: block;
      margin-top: 4px;
    }
  }
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.typing-indicator {
  display: flex;
  gap: 4px;
  
  span {
    width: 8px;
    height: 8px;
    background: rgba(255, 255, 255, 0.6);
    border-radius: 50%;
    animation: typing 1.4s infinite ease-in-out;
    
    &:nth-child(2) {
      animation-delay: 0.2s;
    }
    
    &:nth-child(3) {
      animation-delay: 0.4s;
    }
  }
}

@keyframes typing {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-6px); }
}

.expanded-features {
  margin-bottom: 12px;
  
  .feature-cards {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 8px;
  }
  
  .feature-card {
    background: rgba(255, 255, 255, 0.15);
    border-radius: 12px;
    padding: 12px 8px;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 6px;
    cursor: pointer;
    transition: all 0.2s;
    
    i {
      font-size: 20px;
    }
    
    span {
      font-size: 11px;
      font-weight: 500;
    }
    
    &:hover {
      background: rgba(255, 255, 255, 0.25);
      transform: translateY(-2px);
    }
    
    &.digital-human {
      background: linear-gradient(135deg, #ff6b6b, #feca57);
      
      &:hover {
        background: linear-gradient(135deg, #ff5252, #ff9f43);
        box-shadow: 0 4px 15px rgba(255, 107, 107, 0.4);
      }
    }
  }
}

.input-area {
  .quick-actions {
    display: flex;
    gap: 8px;
    margin-bottom: 10px;
    flex-wrap: wrap;
    
    .quick-btn {
      background: rgba(255, 255, 255, 0.2);
      border: none;
      border-radius: 20px;
      padding: 6px 14px;
      color: white;
      font-size: 12px;
      cursor: pointer;
      transition: all 0.2s;
      
      &:hover {
        background: rgba(255, 255, 255, 0.3);
      }
    }
  }
  
  .input-row {
    display: flex;
    gap: 8px;
    
    input {
      flex: 1;
      background: rgba(255, 255, 255, 0.95);
      border: none;
      border-radius: 12px;
      padding: 10px 14px;
      font-size: 14px;
      color: #333;
      
      &::placeholder {
        color: #999;
      }
      
      &:focus {
        outline: none;
        box-shadow: 0 0 0 3px rgba(255, 255, 255, 0.3);
      }
    }
    
    .btn-send {
      width: 44px;
      height: 44px;
      background: rgba(255, 255, 255, 0.95);
      border: none;
      border-radius: 12px;
      color: #667eea;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 16px;
      transition: all 0.2s;
      
      &:hover:not(:disabled) {
        background: white;
        transform: scale(1.05);
      }
      
      &:disabled {
        opacity: 0.5;
        cursor: not-allowed;
      }
    }
  }
}
</style>
