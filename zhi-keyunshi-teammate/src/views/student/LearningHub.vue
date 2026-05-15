<template>
  <div class="learning-hub">
    <div class="welcome-section">
      <div class="welcome-text">
        <h1>{{ greeting }}，{{ overview?.user?.name || "同学" }}</h1>
        <p class="date">{{ currentDate }} · {{ overview?.user?.className || "—" }}</p>
      </div>
      <div class="stats-badge">
        <div class="stat-item">
          <span class="stat-label">今日待学</span>
          <span class="stat-number">{{ overview?.stats?.todayPending ?? "—" }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">已完成</span>
          <span class="stat-number">{{ overview?.stats?.completed ?? 0 }}</span>
        </div>
      </div>
    </div>

    <div class="dashboard-grid">
      <div class="left-column">
        <div class="card ai-assistant-wrapper">
          <AIAssistantPanel :student-name="overview?.user?.name || '同学'" />
        </div>

        <div class="card todo-card">
          <div class="card-header">
            <h3><i class="fas fa-tasks"></i> 待办作业</h3>
            <router-link to="/student/homework" class="more-link"
              >全部 <i class="fas fa-chevron-right"></i
            ></router-link>
          </div>
          <div class="todo-list">
            <div
              v-for="t in overview?.todoHomework || []"
              :key="t.id"
              class="todo-item"
              :class="{ urgent: t.isUrgent }"
            >
              <div class="todo-content">
                <span class="todo-title">{{ t.subject }} · {{ t.title }}</span>
                <span class="todo-meta"
                  >截止 {{ t.deadline
                  }}<template v-if="t.questionCount"> · {{ t.questionCount }}题</template></span
                >
              </div>
              <span v-if="t.isUrgent" class="todo-badge urgent">紧急</span>
            </div>
            <div v-if="!(overview?.todoHomework || []).length" class="todo-item">
              <div class="todo-content">
                <span class="todo-title">暂无待办作业</span>
                <span class="todo-meta">前往作业中心查看最新任务</span>
              </div>
            </div>
          </div>
        </div>

        <div class="card goal-card">
          <div class="card-header">
            <h3><i class="fas fa-bullseye"></i> 今日目标（自定义）</h3>
            <span class="badge-sm">{{ completedGoalCount }}/{{ todayGoals.length }} 完成</span>
          </div>
          <div class="goal-input-row">
            <input
              v-model.trim="newGoalText"
              class="goal-input"
              type="text"
              maxlength="40"
              placeholder="例如：完成数学口算20题"
              @keyup.enter="addTodayGoal"
            />
            <button class="btn-add-goal" @click="addTodayGoal">添加</button>
          </div>
          <div class="goal-list">
            <div v-for="g in todayGoals" :key="g.id" class="goal-item" :class="{ done: g.done }">
              <span class="goal-title">{{ g.title }}</span>
              <button class="goal-action-btn complete" @click="toggleTodayGoal(g.id)">
                {{ g.done ? "未完成" : "完成" }}
              </button>
              <button class="goal-action-btn delete" @click="removeTodayGoal(g.id)">删除</button>
            </div>
            <div v-if="!todayGoals.length" class="goal-empty">还没有目标，先添加一个吧</div>
          </div>
        </div>
      </div>

      <div class="right-column">
        <div class="card message-card">
          <div class="card-header">
            <h3><i class="fas fa-bell"></i> 家校消息提醒</h3>
            <span class="badge-sm">未读 {{ unreadMessageCount }} 条</span>
          </div>
          <div class="message-list">
            <div v-for="m in schoolMessages" :key="m.id" class="message-item" :class="{ read: m.read }">
              <span class="message-tag" :class="m.type">{{ m.type === "homework" ? "作业" : "公告" }}</span>
              <div class="message-content">
                <span class="message-title">{{ m.title }}</span>
                <span class="message-meta">{{ m.teacher }} · {{ formatMessageTime(m.time) }}</span>
              </div>
              <button
                class="btn-read"
                :disabled="m.read"
                @click="markMessageRead(m.id)"
              >
                {{ m.read ? "已读" : "标记已读" }}
              </button>
            </div>
          </div>
        </div>

        <div class="card ai-chat-card">
          <div class="card-header">
            <h3><i class="fas fa-comment-dots"></i> AI 学习助手</h3>
            <span class="badge-sm">体验版</span>
          </div>
          <div class="chat-list">
            <div v-for="msg in chatMessages" :key="msg.id" class="chat-item" :class="msg.role">
              {{ msg.text }}
            </div>
          </div>
          <div class="chat-input-row">
            <input
              v-model.trim="chatInput"
              class="chat-input"
              type="text"
              maxlength="80"
              placeholder="问我：今天先学什么？"
              @keyup.enter="sendChat"
            />
            <button class="btn-send-chat" @click="sendChat">发送</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from "vue";
import { fetchStudentOverview, markStudentMessageRead as markStudentMessageReadApi } from "@/utils/api";
import AIAssistantPanel from "@/components/AIAssistantPanel.vue";

const overview = ref(null);

const todayGoals = ref([
  { id: 1, title: "完成语文阅读 1 篇", done: false },
  { id: 2, title: "订正昨天错题 2 道", done: false },
]);
const newGoalText = ref("");
const completedGoalCount = computed(() => todayGoals.value.filter((g) => g.done).length);

const schoolMessages = ref([]);
const unreadMessageCount = computed(() => schoolMessages.value.filter((m) => !m.read).length);

const chatMessages = ref([
  { id: 1, role: "assistant", text: "你好，我是学习助手。你可以问我“今天先做什么作业？”" },
]);
const chatInput = ref("");

const greeting = computed(() => {
  const h = new Date().getHours();
  if (h < 12) return "上午好";
  if (h < 18) return "下午好";
  return "晚上好";
});

const currentDate = computed(() => {
  const date = new Date();
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}.${month}.${day}`;
});

const loadOverview = async () => {
  try {
    const data = await fetchStudentOverview();
    overview.value = data;
    schoolMessages.value = data?.schoolMessages || [];
  } catch {
    overview.value = null;
    schoolMessages.value = [];
  }
};

const addTodayGoal = () => {
  if (!newGoalText.value) return;
  todayGoals.value.unshift({
    id: Date.now(),
    title: newGoalText.value,
    done: false,
  });
  newGoalText.value = "";
};

const toggleTodayGoal = (id) => {
  todayGoals.value = todayGoals.value.map((g) =>
    g.id === id ? { ...g, done: !g.done } : g
  );
};

const removeTodayGoal = (id) => {
  todayGoals.value = todayGoals.value.filter((g) => g.id !== id);
};

const markMessageRead = async (id) => {
  try {
    await markStudentMessageReadApi(id);
    schoolMessages.value = schoolMessages.value.map((m) =>
      m.id === id ? { ...m, read: true } : m
    );
  } catch {
    // ignore
  }
};

const formatMessageTime = (v) => {
  const d = new Date(v);
  if (Number.isNaN(d.getTime())) return String(v || "");
  const p = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(
    d.getMinutes()
  )}:${p(d.getSeconds())}`;
};

const buildAssistantReply = (text) => {
  if (/作业|先学/.test(text)) return "建议先完成“紧急”作业，再处理今日目标里的学习任务。";
  if (/英语|单词/.test(text)) return "可以先做 10 分钟单词拼写，再进行 5 分钟口语跟读。";
  if (/数学|口算/.test(text)) return "先热身口算，再做两道应用题，最后订正错题效果更好。";
  return "你可以告诉我学科或目标，我会给你一个简短学习计划。";
};

const sendChat = () => {
  const text = chatInput.value;
  if (!text) return;
  chatMessages.value.push({ id: Date.now(), role: "user", text });
  chatMessages.value.push({
    id: Date.now() + 1,
    role: "assistant",
    text: buildAssistantReply(text),
  });
  chatInput.value = "";
};

onMounted(loadOverview);
</script>

<style scoped lang="scss">
.learning-hub {
  padding: 12px 0 22px;
  background: linear-gradient(180deg, #f3f8ff 0%, #f8fbff 55%, #ffffff 100%);
  border-radius: 16px;
}

.welcome-section {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: flex-start;
  margin-bottom: 20px;
  background: linear-gradient(135deg, #ffffff 0%, #eef5ff 100%);
  border-radius: 20px;
  padding: 20px 22px;
  border: 1px solid rgba(30, 109, 242, 0.14);
  box-shadow: 0 8px 24px rgba(26, 72, 138, 0.08);
  gap: 12px;
}
.welcome-text h1 {
  margin: 0 0 6px;
  font-size: 22px;
  color: #0b2b4a;
}
.welcome-text .date {
  color: #5e7e9c;
}
.stats-badge {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
}
.stat-item {
  display: flex;
  flex-direction: row;
  align-items: baseline;
  gap: 10px;
}
.stat-label {
  font-size: 12px;
  color: #6c8aa7;
}
.stat-number {
  font-size: 24px;
  font-weight: 700;
  color: #1e6df2;
}

.dashboard-grid {
  display: flex;
  flex-direction: column;
  gap: 0;
}
.left-column,
.right-column {
  width: 100%;
}
.card {
  background: #fff;
  border-radius: 18px;
  padding: 18px 18px 17px;
  border: 1px solid rgba(30, 109, 242, 0.12);
  margin-bottom: 20px;
  box-shadow: 0 10px 22px rgba(29, 74, 138, 0.07);
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}
.card:hover {
  transform: translateY(-1px);
  box-shadow: 0 14px 26px rgba(29, 74, 138, 0.1);
  border-color: rgba(30, 109, 242, 0.2);
}
.ai-assistant-wrapper {
  padding: 0;
  background: transparent;
  border: none;
  box-shadow: none;
  
  &:hover {
    transform: none;
    box-shadow: none;
    border-color: transparent;
  }
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}
.card-header h3 {
  margin: 0;
  font-size: 16px;
  color: #0b2b4a;
  display: flex;
  align-items: center;
  gap: 8px;
}
.card-header h3 i {
  color: #1e6df2;
}
.badge-sm {
  background: #e8f1ff;
  color: #1e6df2;
  border-radius: 30px;
  padding: 4px 10px;
  font-size: 12px;
  font-weight: 600;
}
.more-link {
  color: #1e6df2;
  font-size: 13px;
  text-decoration: none;
  font-weight: 600;
}
.more-link:hover {
  text-decoration: underline;
}

.todo-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.todo-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid #edf2f7;
}
.todo-item:hover {
  background: #f9fcff;
  border-radius: 10px;
  padding-left: 8px;
  padding-right: 8px;
}
.todo-item:last-child {
  border-bottom: none;
}
.todo-title {
  font-weight: 600;
  color: #0b2b4a;
  font-size: 14px;
}
.todo-meta {
  font-size: 13px;
  color: #5e7e9c;
}
.todo-badge.urgent {
  background: #ffe0e0;
  color: #c03838;
  border-radius: 20px;
  padding: 2px 10px;
  font-size: 12px;
}

.goal-input-row {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.goal-input {
  flex: 1;
  border: 1px solid #d8e4f0;
  border-radius: 10px;
  padding: 8px 10px;
  background: #fbfdff;
}
.goal-input:focus {
  outline: none;
  border-color: #79a9f7;
  box-shadow: 0 0 0 3px rgba(30, 109, 242, 0.14);
}
.btn-add-goal {
  border: none;
  background: linear-gradient(135deg, #1e6df2, #4789f5);
  color: #fff;
  border-radius: 10px;
  padding: 0 14px;
  cursor: pointer;
  font-weight: 600;
}
.btn-add-goal:hover {
  filter: brightness(1.02);
}
.goal-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.goal-item {
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid #edf2f7;
  border-radius: 12px;
  padding: 9px 10px;
  background: #fff;
}
.goal-item.done {
  background: #f2fbf5;
  border-color: #c9efd6;
}
.goal-title {
  flex: 1;
  color: #0b2b4a;
  font-size: 14px;
}
.goal-action-btn {
  border: none;
  border-radius: 8px;
  padding: 4px 10px;
  font-size: 12px;
  cursor: pointer;
}
.goal-action-btn.complete {
  background: #e8f1ff;
  color: #1e6df2;
}
.goal-item.done .goal-action-btn.complete {
  background: #eaf8ef;
  color: #2e9d58;
}
.goal-action-btn.delete {
  background: #fff1f1;
  color: #cb4545;
}
.goal-empty {
  color: #7a95af;
  font-size: 13px;
}

.message-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.message-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  background: #f8fcff;
  border-radius: 12px;
  border: 1px solid rgba(30, 109, 242, 0.08);
}
.message-item.read {
  opacity: 0.72;
}
.message-tag {
  height: 22px;
  line-height: 22px;
  padding: 0 8px;
  border-radius: 999px;
  font-size: 12px;
  color: #fff;
}
.message-tag.homework { background: #1e6df2; }
.message-tag.notice { background: #ff9f1c; }
.message-content {
  display: flex;
  flex-direction: column;
}
.message-title {
  color: #0b2b4a;
  font-weight: 600;
  font-size: 14px;
}
.message-meta {
  font-size: 13px;
  color: #6f87a0;
}
.btn-read {
  margin-left: auto;
  border: none;
  border-radius: 8px;
  background: linear-gradient(135deg, #e8f1ff, #d9eaff);
  color: #1e6df2;
  font-size: 12px;
  padding: 5px 10px;
  cursor: pointer;
  font-weight: 600;
}
.btn-read:disabled {
  background: #edf2f7;
  color: #8aa0b5;
  cursor: default;
}

.chat-list {
  max-height: 240px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 12px;
  padding-right: 4px;
}
.chat-item {
  max-width: 88%;
  padding: 10px 12px;
  border-radius: 13px;
  font-size: 14px;
  line-height: 1.45;
}
.chat-item.assistant {
  background: #eff5ff;
  color: #0b2b4a;
  border: 1px solid #dbeaff;
}
.chat-item.user {
  margin-left: auto;
  background: linear-gradient(135deg, #1e6df2, #4a8cf6);
  color: #fff;
}
.chat-input-row {
  display: flex;
  gap: 8px;
}
.chat-input {
  flex: 1;
  border: 1px solid #d8e4f0;
  border-radius: 10px;
  padding: 8px 10px;
  background: #fbfdff;
}
.chat-input:focus {
  outline: none;
  border-color: #79a9f7;
  box-shadow: 0 0 0 3px rgba(30, 109, 242, 0.14);
}
.btn-send-chat {
  border: none;
  background: linear-gradient(135deg, #1e6df2, #4a8cf6);
  color: #fff;
  border-radius: 10px;
  padding: 0 14px;
  cursor: pointer;
  font-weight: 600;
}
.btn-send-chat:hover {
  filter: brightness(1.03);
}

@media (max-width: 1000px) {
  .welcome-section {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
}
</style>
