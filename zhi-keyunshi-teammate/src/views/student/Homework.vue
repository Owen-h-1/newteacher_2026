<template>
  <div class="homework-page">
    <!-- 页面标题区域 -->
    <div class="page-header">
      <div class="title-text">
        <h1>作业与任务</h1>
        <p class="subtitle">查看并完成各科教师发布的作业</p>
      </div>
      <div class="stats-badge">
        <div class="stat-item">
          <span class="stat-label">待完成</span>
          <span class="stat-number">{{ pendingCount }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">已完成</span>
          <span class="stat-number">{{ completedCount }}</span>
        </div>
      </div>
    </div>

    <!-- 标签切换：待完成 / 已完成 -->
    <div class="tab-header">
      <button
        class="tab-btn"
        :class="{ active: activeTab === 'pending' }"
        @click="activeTab = 'pending'"
      >
        <i class="fas fa-clock"></i> 待完成 ({{ pendingCount }})
      </button>
      <button
        class="tab-btn"
        :class="{ active: activeTab === 'completed' }"
        @click="activeTab = 'completed'"
      >
        <i class="fas fa-check-circle"></i> 已完成 ({{ completedCount }})
      </button>
    </div>

    <!-- 作业列表（根据当前标签动态显示） -->
    <div class="homework-list">
      <div v-if="currentList.length === 0" class="empty-state">
        <i :class="activeTab === 'pending' ? 'fas fa-check-circle' : 'fas fa-tasks'"></i>
        <p>
          {{
            activeTab === "pending"
              ? "暂无待完成作业，可以去自主练习～"
              : "暂无已完成作业"
          }}
        </p>
        <router-link
          to="/student/selftest"
          class="btn-link"
          v-if="activeTab === 'pending'"
        >
          前往自主练习 <i class="fas fa-arrow-right"></i>
        </router-link>
      </div>

      <div v-else class="homework-cards">
        <div
          v-for="item in currentList"
          :key="item.id"
          class="homework-card"
          :class="item.subjectClass"
        >
          <div class="card-header">
            <div class="subject-tag" :class="item.subjectClass">
              <i :class="item.subjectIcon"></i> {{ item.subject }}
            </div>
            <span class="deadline" :class="{ urgent: item.isUrgent }">
              <i class="fas fa-calendar-alt"></i> {{ item.deadline }}
              <span v-if="item.isUrgent" class="urgent-badge">紧急</span>
            </span>
          </div>
          <h3 class="homework-title">{{ item.title }}</h3>
          <div class="homework-meta">
            <span
              ><i class="fas fa-file-alt"></i> {{ item.type }} ·
              {{ item.questionCount }}题</span
            >
            <span><i class="fas fa-star"></i> 难度 {{ item.difficultyText }}</span>
            <span v-if="activeTab === 'completed' && item.teacherGrade" class="hw-grade"
              ><i class="fas fa-award"></i> 教师评级 {{ item.teacherGrade }}</span
            >
          </div>
          <div class="homework-desc">{{ item.description }}</div>
          <div class="card-footer">
            <div class="teacher-info"><i class="fas fa-user"></i> {{ item.teacher }}</div>
            <div class="card-actions">
              <button
                class="btn-do"
                :class="item.subjectClass"
                @click="startHomework(item)"
              >
                <i :class="activeTab === 'pending' ? 'fas fa-play' : 'fas fa-redo-alt'"></i>
                {{ activeTab === "pending" ? "开始作业" : "再次练习" }}
              </button>
              <button
                v-if="activeTab === 'completed'"
                class="btn-review"
                @click="viewAnswers(item)"
              >
                <i class="fas fa-eye"></i> 查看答案
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 作业详情弹窗（模拟） -->
    <div
      v-if="showHomeworkModal"
      class="modal-overlay"
      @click.self="showHomeworkModal = false"
    >
      <div class="modal-card">
        <div class="modal-header">
          <div class="modal-icon" :class="currentHomework?.subjectClass">
            <i :class="currentHomework?.subjectIcon"></i>
          </div>
          <h3>{{ currentHomework?.title }}</h3>
          <button class="close-btn" @click="showHomeworkModal = false">
            <i class="fas fa-times"></i>
          </button>
        </div>
        <div class="modal-body">
          <div class="homework-detail">
            <div class="detail-row">
              <span class="detail-label">学科</span>
              <span class="detail-value">{{ currentHomework?.subject }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">教师</span>
              <span class="detail-value">{{ currentHomework?.teacher }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">截止时间</span>
              <span class="detail-value" :class="{ urgent: currentHomework?.isUrgent }">
                {{ currentHomework?.deadline }}
              </span>
            </div>
            <div class="detail-row">
              <span class="detail-label">题目数量</span>
              <span class="detail-value">{{ currentHomework?.questionCount }}题</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">作业描述</span>
              <span class="detail-value">{{ currentHomework?.description }}</span>
            </div>
          </div>
          <div class="modal-actions">
            <button
              class="btn-start"
              :class="currentHomework?.subjectClass"
              @click="doHomework"
            >
              <i class="fas fa-play"></i> 开始答题
            </button>
            <button class="btn-cancel" @click="showHomeworkModal = false">稍后</button>
          </div>
        </div>
      </div>
    </div>

    <!-- Toast 反馈 -->
    <div v-if="toast.show" class="toast-message" :class="toast.type">
      <i :class="toast.icon"></i> {{ toast.text }}
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import { fetchHomeworkList } from "@/utils/api";

const route = useRoute();
const router = useRouter();

// ---------- 标签页状态 ----------
const activeTab = ref("pending"); // 'pending' 或 'completed'

// ---------- 作业数据（后端接口）----------
const homeworkData = ref([]);

const loadHomework = async () => {
  try {
    const data = await fetchHomeworkList();
    homeworkData.value = data.list || [];
  } catch (err) {
    showToast("info", err.message || "获取作业失败", "fa-circle-exclamation");
  }
};

// 计算待完成和已完成数量
const pendingCount = computed(() => {
  return homeworkData.value.filter((item) => item.status === "pending").length;
});
const completedCount = computed(() => {
  return homeworkData.value.filter((item) => item.status === "completed").length;
});

// 当前显示的作业列表（根据标签）
const currentList = computed(() => {
  return homeworkData.value.filter(
    (item) => item.status === (activeTab.value === "pending" ? "pending" : "completed")
  );
});

// ---------- 作业详情弹窗 ----------
const showHomeworkModal = ref(false);
const currentHomework = ref(null);

const startHomework = (item) => {
  if (activeTab.value === "pending") {
    // 待完成作业：显示详情弹窗
    currentHomework.value = item;
    showHomeworkModal.value = true;
  } else {
    router.push(`/student/homework/${encodeURIComponent(item.id)}/practice`);
  }
};

const doHomework = () => {
  if (!currentHomework.value) return;
  showHomeworkModal.value = false;
  router.push(`/student/homework/${encodeURIComponent(currentHomework.value.id)}/practice`);
};

const viewAnswers = (item) => {
  router.push(`/student/homework/${encodeURIComponent(item.id)}/practice?showAnswers=1`);
};

// ---------- Toast 反馈 ----------
const toast = reactive({
  show: false,
  text: "",
  icon: "",
  type: "",
});
let toastTimer = null;
const showToast = (type, text, icon) => {
  if (toastTimer) clearTimeout(toastTimer);
  toast.show = true;
  toast.text = text;
  toast.icon = icon;
  toast.type = type;
  toastTimer = setTimeout(() => {
    toast.show = false;
  }, 3000);
};

onMounted(() => {
  loadHomework().then(() => {
    const tab = String(route.query.tab || "");
    const completedId = String(route.query.completedId || "");
    const completedItem = completedId
      ? homeworkData.value.find((x) => String(x.id) === completedId && x.status === "completed")
      : null;

    if (tab === "completed") {
      activeTab.value = "completed";
    }
    if (completedItem) {
      showToast("success", "作业已完成，已移动到已完成列表", "fa-check-circle");
    }
    if (tab || completedId) {
      router.replace({ path: "/student/homework" });
    }
  });
});
</script>

<style scoped lang="scss">
.homework-page {
  padding: 8px 0;
  position: relative;
  overflow: hidden;
}

.homework-page::before {
  content: "🍃 ✨ 🍃";
  position: absolute;
  top: 4px;
  right: 10px;
  font-size: 16px;
  opacity: 0.22;
}

.homework-page::after {
  content: "🌈 🍬 ⭐";
  position: absolute;
  bottom: 8px;
  left: 10px;
  font-size: 16px;
  opacity: 0.22;
}

.practice-panel {
  display: grid;
  grid-template-columns: 260px 1fr;
  gap: 14px;
  margin-bottom: 18px;
}
.practice-assistant,
.practice-main {
  background: #fff;
  border: 1px solid rgba(30, 109, 242, 0.14);
  border-radius: 14px;
  padding: 14px;
}
.assistant-title {
  font-weight: 700;
  color: #1e6df2;
  margin-bottom: 8px;
}
.practice-assistant ul {
  margin: 10px 0 0;
  padding-left: 18px;
  color: #466588;
}
.practice-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}
.question-sheet {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.question-row {
  border: 1px solid #e8eef7;
  border-radius: 10px;
  padding: 10px;
}
.question-title {
  font-weight: 600;
  color: #0b2b4a;
  margin-bottom: 6px;
}
.question-options {
  white-space: pre-wrap;
  color: #577695;
  margin-bottom: 6px;
}
.choice-options {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.choice-item {
  display: flex;
  gap: 8px;
  align-items: center;
  background: #f7faff;
  border: 1px solid #e2ebf8;
  border-radius: 8px;
  padding: 6px 8px;
}
.answer-input {
  width: 100%;
  min-height: 72px;
  border: 1px solid #d7e3f3;
  border-radius: 8px;
  padding: 8px 10px;
}
.answer-input.input-line {
  min-height: 0;
  height: 38px;
}
.answer-ref {
  margin-top: 8px;
  color: #2e9d58;
  background: #ebfaef;
  border-radius: 8px;
  padding: 6px 8px;
}
.judge-chip {
  display: inline-block;
  font-size: 12px;
  border-radius: 999px;
  padding: 2px 8px;
  margin-bottom: 6px;
}
.judge-chip.ok {
  color: #1e8e4d;
  background: #e9f8ef;
}
.judge-chip.bad {
  color: #b2600b;
  background: #fff5e7;
}
.judge-chip.empty {
  color: #6c829b;
  background: #eef3f9;
}
.practice-actions {
  display: flex;
  gap: 10px;
  margin-top: 8px;
}
.card-actions {
  display: flex;
  gap: 8px;
}

/* ========== 页面头部 ========== */
.page-header {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 28px;
  flex-wrap: wrap;
  background: transparent;
  border-radius: 0;
  padding: 0 0 8px;
  box-shadow: none;
  border: none;

  .title-text {
    flex: 1;
    h1 {
      font-size: 26px;
      font-weight: 700;
      color: #0b2b4a;
      margin-bottom: 6px;
    }
    .subtitle {
      color: #5e7e9c;
      font-size: 15px;
    }
  }
  .stats-badge {
    display: flex;
    gap: 24px;
    .stat-item {
      display: flex;
      flex-direction: column;
      align-items: flex-end;
      background: transparent;
      border: none;
      box-shadow: none;
      padding: 0;
      .stat-label {
        font-size: 13px;
        color: #5e7e9c;
      }
      .stat-number {
        font-size: 28px;
        font-weight: 700;
        color: #1e6df2;
        line-height: 1;
      }
    }
  }
}

/* ========== 标签切换 ========== */
.tab-header {
  display: flex;
  gap: 16px;
  margin-bottom: 28px;
  border-bottom: 2px solid #dcead8;
  padding-bottom: 12px;
}
.tab-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 24px;
  border-radius: 40px;
  font-weight: 600;
  font-size: 15px;
  border: none;
  background: transparent;
  color: #4a668a;
  cursor: pointer;
  transition: 0.2s;
  i {
    font-size: 16px;
  }
  &:hover {
    background: #eef8e8;
    color: #2d7d3b;
  }
  &.active {
    background: linear-gradient(135deg, #4b8f52, #2d6f40);
    color: white;
    i {
      color: white;
    }
  }
}

/* ========== 作业列表 ========== */
.homework-list {
  min-height: 400px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 20px;
  background: linear-gradient(150deg, #f2ffe9 0%, #f4f0ff 100%);
  border-radius: 28px;
  border: 1px dashed rgba(83, 145, 89, 0.36);
  i {
    font-size: 64px;
    color: #b8d6ff;
    margin-bottom: 20px;
  }
  p {
    font-size: 16px;
    color: #5e7e9c;
    margin-bottom: 20px;
  }
  .btn-link {
    background: #1e6df2;
    color: white;
    padding: 12px 32px;
    border-radius: 40px;
    font-weight: 600;
    text-decoration: none;
    display: inline-flex;
    align-items: center;
    gap: 8px;
    transition: 0.2s;
    &:hover {
      background: #0a4bb0;
      transform: scale(1.02);
    }
  }
}

.homework-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
  gap: 24px;
}

.homework-card {
  background: linear-gradient(155deg, #fffef6 0%, #f6ffef 100%);
  border-radius: 28px;
  padding: 24px;
  box-shadow: 0 12px 30px rgba(0, 20, 40, 0.04);
  border: 1px solid rgba(103, 156, 108, 0.18);
  transition: 0.25s;
  display: flex;
  flex-direction: column;
  position: relative;
  overflow: hidden;

  &::before {
    content: "";
    position: absolute;
    top: 0;
    left: 0;
    width: 6px;
    height: 100%;
  }

  &.math::before {
    background: #1e6df2;
  }
  &.chinese::before {
    background: #2eb85c;
  }
  &.english::before {
    background: #ff9f1c;
  }
  &.science::before {
    background: #6f42c1;
  }
  &.art::before {
    background: #e83e8c;
  }

  &:hover {
    transform: translateY(-4px);
    box-shadow: 0 18px 36px rgba(63, 122, 70, 0.12);
  }

  .card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 16px;
    padding-left: 6px;
  }
  .subject-tag {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 6px 16px;
    border-radius: 40px;
    font-size: 13px;
    font-weight: 600;
    color: white;
    &.math {
      background: #1e6df2;
    }
    &.chinese {
      background: #2eb85c;
    }
    &.english {
      background: #ff9f1c;
    }
    &.science {
      background: #6f42c1;
    }
    &.art {
      background: #e83e8c;
    }
  }
  .deadline {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
    color: #5e7e9c;
    &.urgent {
      color: #cc3b3b;
      font-weight: 600;
    }
    .urgent-badge {
      background: #ffe0e0;
      color: #cc3b3b;
      padding: 2px 10px;
      border-radius: 40px;
      font-size: 12px;
      font-weight: 700;
      margin-left: 4px;
    }
  }
  .homework-title {
    font-size: 18px;
    font-weight: 700;
    color: #0b2b4a;
    margin-bottom: 12px;
    padding-left: 6px;
    line-height: 1.4;
  }
  .homework-meta {
    display: flex;
    flex-wrap: wrap;
    gap: 20px;
    margin-bottom: 16px;
    padding-left: 6px;
    font-size: 13px;
    color: #5e7e9c;
    i {
      margin-right: 4px;
    }
    .hw-grade {
      color: #1e6df2;
      font-weight: 600;
    }
  }
  .homework-desc {
    font-size: 14px;
    color: #4a668a;
    margin-bottom: 20px;
    padding-left: 6px;
    line-height: 1.5;
    flex: 1;
  }
  .card-footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding-left: 6px;
    .teacher-info {
      font-size: 14px;
      color: #5e7e9c;
      i {
        margin-right: 6px;
        color: #1e6df2;
      }
    }
    .btn-do {
      padding: 8px 24px;
      border-radius: 40px;
      font-weight: 600;
      font-size: 14px;
      border: none;
      color: white;
      display: flex;
      align-items: center;
      gap: 6px;
      cursor: pointer;
      transition: 0.2s;
      &.math {
        background: #1e6df2;
      }
      &.chinese {
        background: #2eb85c;
      }
      &.english {
        background: #ff9f1c;
      }
      &.science {
        background: #6f42c1;
      }
      &.art {
        background: #e83e8c;
      }
      &:hover {
        filter: brightness(0.9);
        transform: scale(1.02);
      }
    }
  }
}

/* ========== 弹窗样式 ========== */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
  backdrop-filter: blur(4px);
}
.modal-card {
  width: 520px;
  background: white;
  border-radius: 32px;
  padding: 28px;
  box-shadow: 0 30px 60px rgba(0, 0, 0, 0.2);
  animation: slideUp 0.3s ease;
}
.modal-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
  .modal-icon {
    width: 56px;
    height: 56px;
    border-radius: 18px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: white;
    font-size: 28px;
    &.math {
      background: #1e6df2;
    }
    &.chinese {
      background: #2eb85c;
    }
    &.english {
      background: #ff9f1c;
    }
    &.science {
      background: #6f42c1;
    }
    &.art {
      background: #e83e8c;
    }
  }
  h3 {
    flex: 1;
    font-size: 20px;
    font-weight: 700;
    color: #0b2b4a;
  }
  .close-btn {
    background: transparent;
    border: none;
    font-size: 24px;
    color: #5e7e9c;
    cursor: pointer;
    width: 44px;
    height: 44px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    &:hover {
      background: #f0f5fa;
    }
  }
}
.modal-body {
  .homework-detail {
    background: #f8fcff;
    border-radius: 20px;
    padding: 24px;
    margin-bottom: 28px;
    .detail-row {
      display: flex;
      margin-bottom: 16px;
      &:last-child {
        margin-bottom: 0;
      }
      .detail-label {
        width: 90px;
        font-weight: 600;
        color: #0b2b4a;
      }
      .detail-value {
        flex: 1;
        color: #2c4e6e;
        &.urgent {
          color: #cc3b3b;
          font-weight: 600;
        }
      }
    }
  }
  .modal-actions {
    display: flex;
    gap: 16px;
    justify-content: flex-end;
    .btn-start {
      padding: 14px 32px;
      border-radius: 40px;
      font-weight: 700;
      font-size: 16px;
      border: none;
      color: white;
      display: flex;
      align-items: center;
      gap: 10px;
      cursor: pointer;
      transition: 0.2s;
      &.math {
        background: #1e6df2;
      }
      &.chinese {
        background: #2eb85c;
      }
      &.english {
        background: #ff9f1c;
      }
      &.science {
        background: #6f42c1;
      }
      &.art {
        background: #e83e8c;
      }
      &:hover {
        filter: brightness(0.9);
        transform: scale(1.02);
      }
    }
    .btn-cancel {
      background: transparent;
      border: 2px solid #b8c9dd;
      color: #4a668a;
      padding: 14px 28px;
      border-radius: 40px;
      font-weight: 600;
      font-size: 16px;
      cursor: pointer;
      transition: 0.2s;
      &:hover {
        border-color: #ff4d4d;
        color: #ff4d4d;
      }
    }
  }
}

/* ========== Toast 反馈 ========== */
.toast-message {
  position: fixed;
  bottom: 40px;
  left: 50%;
  transform: translateX(-50%);
  padding: 14px 28px;
  border-radius: 60px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 12px;
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.2);
  z-index: 9999;
  animation: slideUpToast 0.3s;
  &.success {
    background: #2eb85c;
    color: white;
  }
  &.info {
    background: #1e6df2;
    color: white;
  }
  i {
    font-size: 20px;
  }
}

/* ========== 动画 ========== */
@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
@keyframes slideUpToast {
  from {
    opacity: 0;
    transform: translate(-50%, 20px);
  }
  to {
    opacity: 1;
    transform: translate(-50%, 0);
  }
}

/* ========== 响应式 ========== */
@media (max-width: 1000px) {
  .page-header {
    flex-direction: column;
    align-items: flex-start;
  }
  .stats-badge {
    width: 100%;
    justify-content: space-between;
  }
  .homework-cards {
    grid-template-columns: 1fr;
  }
  .modal-card {
    width: 90%;
    margin: 0 20px;
  }
}
</style>
