<template>
  <div class="learning-hub">
    <div class="floating-sky" aria-hidden="true"></div>

    <div class="welcome-section">
      <div class="welcome-text">
        <h1>{{ greeting }}，{{ overview?.user?.name || "同学" }}</h1>
        <p class="date" v-if="currentDate">{{ currentDate }} · {{ overview?.user?.className || "—" }}</p>
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
      <div class="welcome-art" aria-hidden="true">
        <img class="hero-illustration" src="@/assets/fantasy-academy.svg" alt="魔法学院插画" />
      </div>
    </div>
    <div class="dashboard-grid">
      <div class="left-column">
        <div class="card todo-card">
          <div class="card-header">
            <span class="module-title">作业森林</span>
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
                  >截止时间 {{ formatDeadlineTime(t.deadline)
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
            <span class="module-title">魔法目标书</span>
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
            <span class="module-title message-title-label">信使塔</span>
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
            <span class="module-title ai-title-label">指引台</span>
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

const formatDeadlineTime = (v) => {
  const d = new Date(v);
  if (Number.isNaN(d.getTime())) return String(v || "");
  const p = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(
    d.getMinutes()
  )}`;
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
  padding: 16px 0 28px;
  background:
    radial-gradient(circle at 10% 10%, rgba(255, 255, 255, 0.95) 0, rgba(255, 255, 255, 0.58) 14%, transparent 38%),
    radial-gradient(circle at 88% 8%, rgba(255, 220, 148, 0.18) 0, transparent 20%),
    linear-gradient(180deg, #2a5e8f 0%, #eaf4ff 22%, #f6faff 100%);
  border-radius: 24px;
  position: relative;
  overflow: hidden;
  font-family: "Inter", "Microsoft YaHei", "PingFang SC", sans-serif;
  color: #10263d;
}

.floating-sky {
  display: none;
}

.learning-hub::before,
.learning-hub::after {
  display: none;
}

.welcome-section {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: flex-start;
  margin-bottom: 22px;
  background: linear-gradient(140deg, #ffffff 0%, #f7fbff 100%);
  border-radius: 28px;
  padding: 26px 28px;
  border: 1px solid rgba(33, 111, 227, 0.14);
  box-shadow: 0 16px 38px rgba(16, 38, 61, 0.12);
  gap: 12px;
  overflow: hidden;
  min-height: 270px;
}

.welcome-section::before {
  content: "";
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 18% 76%, rgba(255, 255, 255, 0.8) 0%, transparent 38%),
    radial-gradient(circle at 84% 18%, rgba(255, 233, 178, 0.18) 0%, transparent 18%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.12) 0%, rgba(86, 132, 181, 0.06) 100%);
}

.welcome-text h1 {
  position: relative;
  z-index: 1;
  margin: 0 0 6px;
  font-size: 40px;
  line-height: 1.06;
  font-weight: 800;
  color: #0b2742;
  letter-spacing: -0.035em;
}

.welcome-text .date {
  position: relative;
  z-index: 1;
  color: #4f6b85;
  font-size: 15px;
  font-weight: 600;
}

.stats-badge {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  gap: 12px;
  width: 100%;
}

.welcome-art {
  position: absolute;
  right: 8px;
  bottom: 0;
  width: min(44vw, 540px);
  height: min(100%, 250px);
  z-index: 1;
}

.hero-illustration {
  width: 100%;
  height: 100%;
  object-fit: contain;
  filter: drop-shadow(0 16px 28px rgba(14, 41, 84, 0.24));
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  padding: 12px 14px;
  min-width: 124px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid rgba(33, 111, 227, 0.12);
  backdrop-filter: blur(6px);
  box-shadow: 0 8px 18px rgba(16, 38, 61, 0.06);
}

.stat-label {
  font-size: 12px;
  color: #567088;
  font-weight: 700;
}

.stat-number {
  font-size: 28px;
  font-weight: 800;
  color: #0b2742;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  gap: 24px;
}

.left-column,
.right-column {
  width: 100%;
}

.card {
  position: relative;
  background: linear-gradient(180deg, #ffffff 0%, #fbfdff 100%);
  border-radius: 24px;
  padding: 20px 20px 18px;
  border: 1px solid rgba(33, 111, 227, 0.1);
  margin-bottom: 18px;
  box-shadow: 0 10px 24px rgba(16, 38, 61, 0.08);
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
  overflow: hidden;
}

.card::before {
  content: "";
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, rgba(255,255,255,0.72), rgba(255,255,255,0));
  pointer-events: none;
}

.card:hover {
  transform: translateY(-1px);
  box-shadow: 0 16px 28px rgba(16, 38, 61, 0.12);
  border-color: rgba(33, 111, 227, 0.22);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.card-header .module-title {
  flex: 0 0 auto;
}

.module-title {
  margin: 0;
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 0;
  font-size: 22px;
  line-height: 1.1;
  font-weight: 800;
  letter-spacing: -0.025em;
  color: #ffffff;
  z-index: 0;
}

.module-title::before {
  content: "";
  position: absolute;
  inset: 50% -12px auto -12px;
  height: 25px;
  transform: translateY(-50%);
  border-radius: 999px;
  background: linear-gradient(135deg, #0f4a8a, #133e74);
  box-shadow: 0 8px 18px rgba(15, 74, 138, 0.24), inset 0 0 0 1px rgba(255, 255, 255, 0.12);
  z-index: -1;
}

.module-title::after {
  content: "";
  display: none;
}

.badge-sm {
  background: #0f4a8a;
  color: #ffffff;
  border-radius: 30px;
  padding: 6px 14px;
  font-size: 13px;
  font-weight: 700;
}

.more-link {
  color: #0b1f33;
  font-size: 13px;
  text-decoration: none;
  font-weight: 700;
}

.todo-list,
.goal-list,
.message-list,
.chat-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.todo-item,
.goal-item,
.message-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 16px;
  border-radius: 16px;
  border: 1px solid rgba(33, 111, 227, 0.1);
  background: #ffffff;
}

.todo-item:hover,
.goal-item:hover,
.message-item:hover {
  background: #f7fbff;
}

.todo-title,
.goal-title,
.message-title,
.chat-item.assistant,
.goal-empty,
.more-link,
.badge-sm {
  color: #f1f9ff;
  font-weight: 700;
}

.todo-title {
  font-size: 18px;
}

.todo-meta,
.message-meta,
.date,
.stat-label,
.chat-input::placeholder,
.goal-input::placeholder {
  font-size: 14px;
  color: #d8efff;
  font-weight: 600;
}

.todo-badge.urgent {
  background: #ffe0e0;
  color: #8f2f2f;
  border-radius: 20px;
  padding: 2px 10px;
  font-size: 12px;
  font-weight: 700;
}

.goal-input-row,
.chat-input-row {
  display: flex;
  gap: 8px;
}

.goal-input,
.chat-input {
  flex: 1;
  border: 1px solid #cfddee;
  border-radius: 12px;
  padding: 12px 14px;
  background: #ffffff;
  color: #0b2742;
  font-weight: 600;
}

.goal-input::placeholder,
.chat-input::placeholder {
  color: #7f95a8;
}

.goal-input:focus,
.chat-input:focus {
  outline: none;
  border-color: #4f8ff0;
  box-shadow: 0 0 0 3px rgba(33, 111, 227, 0.14);
}

.btn-add-goal,
.btn-send-chat {
  border: none;
  background: linear-gradient(135deg, #1e6df2, #4a8cf6);
  color: #fff;
  border-radius: 12px;
  padding: 0 18px;
  cursor: pointer;
  font-weight: 700;
  font-size: 15px;
}

.goal-action-btn,
.btn-read {
  border: none;
  border-radius: 8px;
  padding: 6px 12px;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

.goal-action-btn.complete,
.btn-read {
  background: #e8f1ff;
  color: #1e6df2;
}

.goal-action-btn.delete {
  background: #fff1f1;
  color: #cb4545;
}

.goal-item.done {
  background: #f5f9ff;
  border-color: rgba(33, 111, 227, 0.14);
}

.goal-empty {
  color: #f1f9ff;
  font-size: 13px;
  font-weight: 700;
}

.message-card,
.todo-card,
.goal-card,
.ai-chat-card {
  background: linear-gradient(150deg, #ffffff 0%, #fbfdff 100%);
}

.message-item.read {
  opacity: 0.78;
}

.message-tag {
  height: 22px;
  line-height: 22px;
  padding: 0 8px;
  border-radius: 999px;
  font-size: 12px;
  color: #fff;
  font-weight: 700;
}

.message-tag.homework { background: #1e6df2; }
.message-tag.notice { background: #ff9f1c; }

.btn-read:disabled {
  background: #edf2f7;
  color: #8aa0b5;
  cursor: default;
}

.chat-item {
  max-width: 90%;
  padding: 12px 14px;
  border-radius: 14px;
  font-size: 15px;
  line-height: 1.5;
}

.chat-item.assistant {
  background: #ffffff;
  color: #163a73;
  border: 1px solid #dbeaff;
  font-weight: 600;
}

.chat-item.user {
  margin-left: auto;
  background: linear-gradient(135deg, #1e6df2, #4a8cf6);
  color: #fff;
  font-weight: 600;
}

.chat-list {
  max-height: 240px;
  overflow-y: auto;
  padding-right: 4px;
  margin-bottom: 12px;
}

.btn-add-goal:hover,
.btn-send-chat:hover {
  filter: brightness(1.04);
}

@media (max-width: 1000px) {
  .welcome-section {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .welcome-art {
    position: relative;
    right: auto;
    bottom: auto;
    width: 100%;
    height: 220px;
    margin-top: 6px;
  }

  .dashboard-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .welcome-text h1 {
    font-size: 28px;
  }

  .welcome-section {
    padding: 18px;
  }

  .module-title {
    font-size: 15px;
  }

  .todo-title,
  .goal-title,
  .message-title {
    font-size: 16px;
  }

  .todo-meta,
  .message-meta,
  .chat-item {
    font-size: 13px;
  }

  .badge-sm {
    font-size: 12px;
  }

  .goal-item,
  .todo-item,
  .message-item {
    flex-direction: column;
    align-items: flex-start;
  }

  .btn-read {
    margin-left: 0;
  }

  .chat-input-row,
  .goal-input-row {
    flex-direction: column;
  }
}
</style>
