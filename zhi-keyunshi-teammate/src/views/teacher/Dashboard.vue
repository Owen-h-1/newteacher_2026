<template>
  <div class="dashboard-page">
    <!-- 欢迎区域 + 教师信息 -->
    <div class="welcome-section">
      <div class="welcome-text">
        <h1>{{ greeting }}，{{ overview.teacher?.name || "老师" }}</h1>
        <p class="date">{{ currentDate }} · 欢迎回来</p>
      </div>
      <div class="teacher-avatar">
        <i class="fas fa-user-circle"></i>
      </div>
    </div>
    <div v-if="statusMsg" class="status-msg">{{ statusMsg }}</div>

    <div class="dashboard-grid">
      <!-- 左侧列 -->
      <div class="left-column">
        <div class="card todo-card">
          <div class="card-header">
            <h3><i class="fas fa-list-check"></i> 待办事项</h3>
            <span class="badge-sm">{{ todos.filter((x) => !x.done).length }} 项未完成</span>
          </div>
          <div class="todo-input-row">
            <input v-model.trim="todoDraft.title" type="text" class="todo-input" placeholder="新增待办标题" />
            <input v-model.trim="todoDraft.meta" type="text" class="todo-input meta" placeholder="备注（可选）" />
            <label class="todo-urgent-flag">
              <input type="checkbox" v-model="todoDraft.urgent" />
              紧急
            </label>
            <button class="btn-add-todo" @click="addTodo">添加</button>
          </div>
          <div class="todo-list">
            <div
              v-for="todo in todos"
              :key="todo.id"
              class="todo-item"
              :class="{ urgent: todo.urgent }"
            >
              <div class="todo-check">
                <button class="todo-toggle-btn text" @click="toggleTodo(todo)">
                  {{ todo.done ? "撤销完成" : "完成" }}
                </button>
              </div>
              <div class="todo-content">
                <span class="todo-title" :class="{ done: todo.done }">{{ todo.title }}</span>
                <span class="todo-meta">{{ todo.meta }}</span>
              </div>
              <div class="todo-actions">
                <span v-if="todo.urgent" class="todo-badge urgent">紧急</span>
                <button class="todo-delete-btn text" @click="removeTodo(todo.id)">删除</button>
              </div>
            </div>
          </div>
        </div>

        <div class="card message-send-card">
          <div class="card-header">
            <h3><i class="fas fa-paper-plane"></i> 消息发布</h3>
          </div>
          <div class="message-form">
            <select v-model="messageDraft.classId" class="message-input">
              <option value="" disabled>选择班级</option>
              <option v-for="c in classes" :key="c.classId" :value="c.classId">{{ c.className }}</option>
            </select>
            <input v-model.trim="messageDraft.title" class="message-input" type="text" placeholder="消息标题" />
            <textarea v-model.trim="messageDraft.content" class="message-input" rows="3" placeholder="消息内容（可选）"></textarea>
            <button class="btn-send-message" @click="sendMessageToStudents">发送到家校提醒</button>
          </div>
        </div>
      </div>

      <!-- 右侧列 -->
      <div class="right-column">
        <!-- 班级出勤概况（小学班级） -->
        <div class="card attendance-card">
          <div class="card-header">
            <h3><i class="fas fa-user-graduate"></i> 学生概况</h3>
            <router-link to="/teacher/student-data" class="more-link"
              >详细分析</router-link
            >
          </div>
          <div class="class-summary">
            <div v-for="(item, idx) in overview.classSummary" :key="item.classId || idx" class="class-item">
              <div class="class-name">{{ normalizeClassName(item.className) }}</div>
              <div class="class-stats">
                <span>应到 {{ item.should }}</span>
                <span class="attended">已到 {{ item.arrived }}</span>
                <span class="absent">缺勤 {{ item.absent }}</span>
              </div>
              <div class="progress-bar">
                <div class="progress-fill" :style="{ width: item.rate + '%' }"></div>
              </div>
            </div>
          </div>
        </div>

        <!-- 薄弱知识点预警（小学课程） -->
        <div class="card weak-warning">
          <div class="warning-header">
            <i class="fas fa-exclamation-triangle"></i>
            <span>班级薄弱知识点预警</span>
          </div>
          <div class="warning-meta">
            <span>数据来源：{{ warningSourceText }}</span>
            <span>条数：{{ warningCount }}</span>
          </div>
          <div class="weak-list">
            <div v-for="(w, idx) in overview.warnings" :key="idx" class="weak-item">
              <span class="weak-name">{{ w.name }}</span>
              <span class="weak-class">{{ w.className }}</span>
              <span class="weak-rate">错误率 {{ w.errorRate }}%</span>
            </div>
            <div v-if="!warningCount" class="weak-empty">
              暂无可展示的薄弱点统计。请先让学生完成一次自测或作业并提交后，再刷新查看。
            </div>
          </div>
          <div class="agent-result-box">
            <div class="agent-result-title">
              <i class="fas fa-brain"></i>
              <span>智能体分析结果（预留）</span>
            </div>
            <div class="agent-result-content">
              {{ agentAnalysisResult || "点击下方“AI 智能分析”后，这里会展示真实服务返回的分析结果。" }}
            </div>
          </div>
          <button class="btn-weak-link" :disabled="agentAnalyzing" @click="runMockAgentAnalysis">
            <i class="fas fa-robot"></i>
            {{ agentAnalyzing ? "AI 分析中..." : "AI 智能分析" }}
          </button>
        </div>

      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import {
  fetchTeacherOverview,
  fetchSigninClasses,
  fetchTeacherTodos,
  createTeacherTodo,
  updateTeacherTodo,
  deleteTeacherTodo,
  sendTeacherMessage,
  generateTeacherWarningAgentPlan,
} from "@/utils/api";

const overview = reactive({
  teacher: { name: "老师" },
  stats: {
    todayCourses: 0,
    finishedCourses: 0,
    totalStudents: 0,
    classCount: 0,
    pendingReview: 0,
    todoCount: 0,
    urgentTodo: 0,
  },
  todos: [],
  schedules: [],
  classSummary: [],
  warnings: [],
});
const classes = ref([]);
const todos = ref([]);
const statusMsg = ref("");
const agentAnalysisResult = ref("");
const agentAnalyzing = ref(false);
const todoDraft = reactive({ title: "", meta: "", urgent: false });
const messageDraft = reactive({ classId: "", title: "", content: "" });
const warningCount = computed(() =>
  Array.isArray(overview.warnings) ? overview.warnings.length : 0,
);
const warningSourceText = computed(() => {
  const src = String(overview.warningSource?.basedOn || "");
  if (src === "wrong-question-records") return "错题库（老师名下班级）";
  if (src === "submitted-homework-answers") return "作业提交统计";
  if (src === "wrong-question-records-global") return "错题库（全库兜底）";
  if (src === "student-board-weak-points") return "学生看板薄弱点（兜底）";
  return "未知";
});

const greeting = computed(() => {
  const hour = new Date().getHours();
  if (hour < 12) return "上午好";
  if (hour < 18) return "下午好";
  return "晚上好";
});

function getTodayKey() {
  const d = new Date();
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

function resetClassSummaryIfNewDay() {
  const key = "teacher_dashboard_summary_date";
  const today = getTodayKey();
  const last = localStorage.getItem(key) || "";
  if (last === today) return;
  overview.classSummary = (overview.classSummary || []).map((item) => ({
    ...item,
    classId: item.classId,
    className: item.className,
    arrived: 0,
    absent: Number(item.should || 0),
    rate: 0,
  }));
  localStorage.setItem(key, today);
}

async function loadOverview() {
  try {
    const data = await fetchTeacherOverview();
    Object.assign(overview, data);
    resetClassSummaryIfNewDay();
  } catch (e) {
    console.error(e);
    statusMsg.value = e.message || "教师总览加载失败，请确认后端已启动并重新登录";
  }
}

async function loadClasses() {
  try {
    const data = await fetchSigninClasses();
    classes.value = data.classes || [];
    if (!messageDraft.classId && classes.value.length) {
      messageDraft.classId = classes.value[0].classId;
    }
  } catch {
    classes.value = [];
  }
}

async function loadTodos() {
  try {
    const data = await fetchTeacherTodos();
    todos.value = data.list || [];
  } catch (e) {
    todos.value = [];
    statusMsg.value = e.message || "待办事项加载失败，请重启后端后重试";
  }
}

async function addTodo() {
  const title = String(todoDraft.title || "").trim();
  if (!title) {
    statusMsg.value = "请先输入待办标题";
    return;
  }
  try {
    await createTeacherTodo({
      title,
      meta: String(todoDraft.meta || "").trim(),
      urgent: !!todoDraft.urgent,
    });
    todoDraft.title = "";
    todoDraft.meta = "";
    todoDraft.urgent = false;
    statusMsg.value = "待办已添加";
    await loadTodos();
  } catch (e) {
    statusMsg.value = e.message || "待办添加失败";
  }
}

async function toggleTodo(todo) {
  await updateTeacherTodo(todo.id, { done: !todo.done });
  await loadTodos();
}

async function removeTodo(id) {
  await deleteTeacherTodo(id);
  await loadTodos();
}

async function sendMessageToStudents() {
  const classId = String(messageDraft.classId || "").trim();
  const title = String(messageDraft.title || "").trim();
  if (!classId || !title) {
    statusMsg.value = "请选择班级并填写消息标题";
    return;
  }
  try {
    await sendTeacherMessage({
      classId,
      title,
      content: String(messageDraft.content || "").trim(),
      type: "notice",
    });
    messageDraft.title = "";
    messageDraft.content = "";
    statusMsg.value = "消息已发布，学生端可在家校提醒查看";
  } catch (e) {
    statusMsg.value = e.message || "消息发布失败";
  }
}

async function runMockAgentAnalysis() {
  if (agentAnalyzing.value) return;
  agentAnalyzing.value = true;
  statusMsg.value = "智能体正在分析班级薄弱知识点，请稍候...";
  agentAnalysisResult.value = "";
  try {
    const clsLabel =
      classes.value.find((c) => c.classId === messageDraft.classId)?.className || "";
    const data = await generateTeacherWarningAgentPlan({
      className: clsLabel,
      warnings: overview.warnings || [],
    });
    agentAnalysisResult.value = String(data.summary || data.result || data.message || "").trim();
    if (!agentAnalysisResult.value) {
      agentAnalysisResult.value = JSON.stringify(data, null, 2);
    }
    statusMsg.value = "智能体分析完成";
  } catch (e) {
    statusMsg.value = e.message || "智能体分析失败，请稍后重试";
  } finally {
    agentAnalyzing.value = false;
  }
}

onMounted(async () => {
  await Promise.all([loadOverview(), loadClasses(), loadTodos()]);
});

const currentDate = computed(() => {
  const date = new Date();
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  const weekdays = ["星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六"];
  const weekday = weekdays[date.getDay()];
  return `${year}.${month}.${day} ${weekday}`;
});

const currentDateShort = computed(() => {
  const date = new Date();
  return `${date.getMonth() + 1}月${date.getDate()}日`;
});

const normalizeClassName = (v) => String(v || "").replace(/\s*·\s*数学\s*$/u, "");
</script>

<style scoped lang="scss">
.dashboard-page {
  padding: 8px 0;
}

/* 欢迎区域 */
.welcome-section {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 32px;
  background: white;
  border-radius: 28px;
  padding: 24px 32px;
  box-shadow: 0 8px 20px rgba(0, 20, 40, 0.04);
  border: 1px solid rgba(30, 109, 242, 0.1);
}
.welcome-text {
  h1 {
    font-size: 26px;
    font-weight: 700;
    color: #0b2b4a;
    margin-bottom: 8px;
  }
  .date {
    color: #5e7e9c;
    font-size: 15px;
  }
}
.teacher-avatar {
  i {
    font-size: 56px;
    color: #1e6df2;
    background: #e8f1ff;
    border-radius: 50%;
    padding: 4px;
  }
}
.status-msg {
  margin: -14px 0 18px;
  padding: 8px 12px;
  border-radius: 10px;
  background: #eef5ff;
  border: 1px solid #d6e6ff;
  color: #28527d;
  font-size: 13px;
}

.todo-input-row {
  display: grid;
  grid-template-columns: 1.2fr 1fr auto auto;
  gap: 8px;
  margin-bottom: 10px;
}
.todo-input,
.message-input {
  border: 1px solid #d8e4f4;
  border-radius: 10px;
  padding: 8px 10px;
}
.todo-urgent-flag {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #4e6d8b;
  font-size: 13px;
}
.btn-add-todo,
.btn-send-message {
  border: none;
  background: #1e6df2;
  color: #fff;
  border-radius: 10px;
  padding: 8px 12px;
  cursor: pointer;
  font-weight: 600;
}
.todo-toggle-btn,
.todo-delete-btn {
  border: none;
  background: transparent;
  color: #6b84a1;
  cursor: pointer;
}
.todo-toggle-btn.text,
.todo-delete-btn.text {
  border: 1px solid #cfe0f7;
  background: #fff;
  border-radius: 8px;
  padding: 4px 8px;
  font-size: 12px;
  color: #244a70;
}
.todo-delete-btn.text {
  border-color: #f2c7c4;
  color: #b42318;
}
.todo-title.done {
  text-decoration: line-through;
  color: #8aa0b5;
}
.todo-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.message-form {
  display: grid;
  gap: 8px;
}

/* 统计卡片网格 */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 24px;
  margin-bottom: 32px;
}
.stat-card {
  background: white;
  border-radius: 24px;
  padding: 24px;
  display: flex;
  align-items: center;
  gap: 20px;
  box-shadow: 0 8px 20px rgba(0, 20, 40, 0.04);
  border: 1px solid rgba(30, 109, 242, 0.1);
  transition: 0.2s;
  &:hover {
    box-shadow: 0 12px 30px rgba(30, 109, 242, 0.08);
  }
  .stat-icon {
    width: 60px;
    height: 60px;
    border-radius: 18px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 28px;
    &.blue {
      background: rgba(30, 109, 242, 0.12);
      color: #1e6df2;
    }
    &.green {
      background: rgba(46, 184, 92, 0.12);
      color: #2eb85c;
    }
    &.orange {
      background: rgba(255, 159, 28, 0.12);
      color: #ff9f1c;
    }
    &.purple {
      background: rgba(111, 66, 193, 0.12);
      color: #6f42c1;
    }
  }
  .stat-info {
    flex: 1;
    display: flex;
    flex-direction: column;
    .stat-label {
      font-size: 14px;
      color: #5e7e9c;
      margin-bottom: 6px;
    }
    .stat-number {
      font-size: 32px;
      font-weight: 700;
      color: #0b2b4a;
      line-height: 1;
      margin-bottom: 6px;
    }
    .stat-trend {
      font-size: 13px;
      color: #5e7e9c;
      &.warning {
        color: #ff9f1c;
      }
    }
  }
}

/* 两栏布局 */
.dashboard-grid {
  display: grid;
  grid-template-columns: 1fr 0.9fr;
  gap: 28px;
}

/* 通用卡片样式 */
.card {
  background: white;
  border-radius: 28px;
  padding: 24px;
  box-shadow: 0 12px 30px rgba(0, 20, 40, 0.04);
  border: 1px solid rgba(30, 109, 242, 0.1);
  margin-bottom: 28px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
  h3 {
    font-size: 18px;
    font-weight: 700;
    color: #0b2b4a;
    display: flex;
    align-items: center;
    gap: 10px;
    i {
      color: #1e6df2;
    }
  }
  .more-link {
    color: #1e6df2;
    font-size: 14px;
    font-weight: 600;
    text-decoration: none;
    display: flex;
    align-items: center;
    gap: 6px;
    &:hover {
      text-decoration: underline;
    }
  }
  .badge-sm {
    background: #e8f1ff;
    color: #1e6df2;
    padding: 6px 16px;
    border-radius: 40px;
    font-size: 13px;
    font-weight: 600;
  }
}
.card-footer {
  margin-top: 20px;
  text-align: right;
  .view-all {
    color: #1e6df2;
    font-size: 14px;
    font-weight: 600;
    text-decoration: none;
    display: inline-flex;
    align-items: center;
    gap: 6px;
    &:hover {
      text-decoration: underline;
    }
  }
}

/* 待办事项列表 */
.todo-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.todo-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 8px 0;
  border-bottom: 1px solid #edf2f7;
  &:last-child {
    border-bottom: none;
  }
  .todo-check {
    i {
      font-size: 20px;
      color: #b8c9dd;
      cursor: pointer;
      &:hover {
        color: #1e6df2;
      }
    }
  }
  .todo-content {
    flex: 1;
    display: flex;
    flex-direction: column;
    .todo-title {
      font-weight: 600;
      color: #0b2b4a;
      margin-bottom: 4px;
    }
    .todo-meta {
      font-size: 12px;
      color: #5e7e9c;
    }
  }
  .todo-badge {
    font-size: 12px;
    font-weight: 700;
    padding: 4px 12px;
    border-radius: 40px;
    background: #ffe0e0;
    color: #cc3b3b;
    &.urgent {
      background: #ffe0e0;
      color: #cc3b3b;
    }
  }
  &.urgent .todo-title {
    color: #cc3b3b;
  }
}

/* 今日课程安排 */
.schedule-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.schedule-item {
  display: flex;
  align-items: center;
  gap: 20px;
  .schedule-time {
    width: 120px;
    font-weight: 600;
    color: #0b2b4a;
    font-size: 14px;
  }
  .schedule-info {
    flex: 1;
    display: flex;
    flex-direction: column;
    .course-name {
      font-weight: 600;
      color: #0b2b4a;
      margin-bottom: 4px;
    }
    .course-class {
      font-size: 12px;
      color: #5e7e9c;
    }
  }
  .schedule-status {
    font-size: 13px;
    font-weight: 600;
    padding: 4px 14px;
    border-radius: 40px;
    &.finished {
      background: #e3f2e9;
      color: #1e7b4c;
    }
    &.waiting {
      background: #fff2d6;
      color: #b9770e;
    }
  }
}

/* 班级出勤概况 */
.class-summary {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.class-item {
  .class-name {
    font-weight: 600;
    color: #0b2b4a;
    margin-bottom: 10px;
  }
  .class-stats {
    display: flex;
    gap: 16px;
    font-size: 13px;
    color: #5e7e9c;
    margin-bottom: 10px;
    .attended {
      color: #2eb85c;
      font-weight: 600;
    }
    .absent {
      color: #ff4d4d;
      font-weight: 600;
    }
  }
  .progress-bar {
    width: 100%;
    height: 8px;
    background: #e2eaf2;
    border-radius: 20px;
    overflow: hidden;
    .progress-fill {
      height: 100%;
      background: linear-gradient(90deg, #1e6df2, #0a4bb0);
      border-radius: 20px;
    }
  }
}

/* 薄弱知识点预警 */
.weak-warning {
  background: #fff8e6;
  border-left: 8px solid #ff9f1c;
}
.warning-header {
  display: flex;
  align-items: center;
  gap: 12px;
  font-weight: 700;
  color: #b9770e;
  margin-bottom: 18px;
  i {
    font-size: 22px;
  }
}
.warning-meta {
  display: flex;
  gap: 12px;
  margin-bottom: 10px;
  color: #7c5a20;
  font-size: 12px;
}
.weak-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-bottom: 20px;
}
.weak-empty {
  padding: 10px 12px;
  border: 1px dashed #f0b36a;
  border-radius: 10px;
  font-size: 13px;
  color: #7a6338;
  background: #fffdf7;
}
.weak-item {
  display: flex;
  align-items: center;
  gap: 12px;
  .weak-name {
    font-weight: 600;
    color: #0b2b4a;
    flex: 1;
  }
  .weak-class {
    font-size: 12px;
    color: #5e7e9c;
    background: #edf2f7;
    padding: 2px 10px;
    border-radius: 40px;
  }
  .weak-rate {
    font-size: 13px;
    font-weight: 700;
    color: #cc3b3b;
  }
}
.agent-result-box {
  background: #fff;
  border: 1px dashed #f0b36a;
  border-radius: 14px;
  padding: 12px;
  margin-bottom: 14px;
}
.agent-result-title {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #b9770e;
  font-size: 13px;
  font-weight: 700;
  margin-bottom: 8px;
}
.agent-result-content {
  min-height: 72px;
  line-height: 1.6;
  color: #35516d;
  font-size: 13px;
  white-space: pre-wrap;
}
.btn-weak-link {
  display: block;
  background: #1e6df2;
  color: white;
  text-align: center;
  padding: 12px;
  border-radius: 40px;
  font-weight: 700;
  font-size: 14px;
  text-decoration: none;
  i {
    margin-right: 8px;
  }
  &:disabled {
    background: #8ab0f5;
    cursor: not-allowed;
  }
  &:hover {
    background: #0a4bb0;
  }
}

/* 响应式 */
@media (max-width: 1200px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .dashboard-grid {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 768px) {
  .stats-grid {
    grid-template-columns: 1fr;
  }
  .welcome-section {
    flex-direction: column;
    align-items: flex-start;
    gap: 16px;
  }
}
</style>
