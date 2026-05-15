<template>
  <div class="student-data-board">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="title-icon">
        <i class="fas fa-chart-line"></i>
      </div>
      <div class="title-text">
        <h1>学生数据看板</h1>
        <p class="subtitle">班级整体学情与薄弱知识点智能分析</p>
      </div>
      <div class="class-filter">
        <i class="fas fa-filter"></i>
        <select v-model="selectedClassId">
          <option value="">全部班级</option>
          <option v-for="c in classOptions" :key="c.classId" :value="c.classId">{{ c.className }}</option>
        </select>
      </div>
      <div class="ai-badge-large"><i class="fas fa-brain"></i> AI 学情分析</div>
    </div>
    <div v-if="statusMsg" class="status-msg">{{ statusMsg }}</div>

    <!-- 核心数据卡片（班级概览 - 小学课堂） -->
    <div class="stats-grid">
      <div class="stat-card">
        <div
          class="stat-icon"
          style="background: rgba(30, 109, 242, 0.12); color: #1e6df2"
        >
          <i class="fas fa-users"></i>
        </div>
        <div class="stat-info">
          <span class="stat-label">总学生数</span>
          <span class="stat-value">{{ stats.totalStudents }}</span>
          <span class="stat-trend">{{ classWeakness.length }} 个班级</span>
        </div>
      </div>
      <div class="stat-card">
        <div
          class="stat-icon"
          style="background: rgba(46, 184, 92, 0.12); color: #2eb85c"
        >
          <i class="fas fa-check-circle"></i>
        </div>
        <div class="stat-info">
          <span class="stat-label">作业完成率</span>
          <span class="stat-value">{{ stats.completionRate }}%</span>
          <span class="stat-trend">教学班整体</span>
        </div>
      </div>
    </div>

    <!-- 两栏布局：左侧薄弱知识点，右侧趋势图 -->
    <div class="analytics-row">
      <!-- 薄弱知识点 TOP5（小学阶段） -->
      <div class="weak-points-card">
        <div class="card-header">
          <h3>
            <i class="fas fa-exclamation-triangle" style="color: #ff9f1c"></i>
            当前薄弱知识点 TOP5
          </h3>
          <button class="btn-ai-suggestion" @click="askAIAnalysis">
            <i class="fas fa-robot"></i> AI 教学建议
          </button>
        </div>
        <div class="weak-meta">
          <span>数据来源：{{ weakPointSourceText }}</span>
          <span>条数：{{ weakPoints.length }}</span>
        </div>
        <div class="weak-list">
          <div v-for="(item, index) in weakPoints" :key="index" class="weak-item">
            <div class="weak-rank" :class="`rank-${index + 1}`">{{ index + 1 }}</div>
            <div class="weak-info">
              <span class="weak-name">{{ item.name }}</span>
              <span class="weak-sub">{{ item.sub }}</span>
            </div>
            <div class="weak-stat">
              <span class="weak-percent">{{ item.errorRate }}%</span>
              <span class="weak-label">错误率</span>
            </div>
            <div class="weak-bar">
              <div class="progress-bar">
                <div
                  class="progress-fill"
                  :style="{ width: item.errorRate + '%', background: item.color }"
                ></div>
              </div>
            </div>
          </div>
          <div v-if="!weakPoints.length" class="weak-empty">
            暂无可展示的薄弱点统计，请先让学生提交自测或作业后再刷新。
          </div>
        </div>
        <div class="card-footer">
          <a href="#" class="more-link" @click.prevent="viewAllWeakPoints"
            >查看全部知识点 <i class="fas fa-arrow-right"></i
          ></a>
        </div>
      </div>

      <!-- 学习趋势图（小学班级活跃度） -->
      <div class="trend-card">
        <div class="card-header">
          <h3>
            <i class="fas fa-chart-simple" style="color: #1e6df2"></i> 最近7天学习活跃度
          </h3>
          <span class="trend-badge">日均 {{ avgWeekParticipants }} 人参与</span>
        </div>
        <div class="chart-container">
          <div class="bar-chart">
            <div v-for="(day, index) in weekData" :key="index" class="bar-item">
              <div class="bar-label">{{ day.day }}</div>
              <div class="bar-wrapper">
                <div
                  class="bar-fill"
                  :style="{
                    height: day.value * 0.7 + 'px',
                    background: 'linear-gradient(to top, #1e6df2, #4b9fff)',
                  }"
                ></div>
              </div>
              <div class="bar-value">{{ day.value }}人</div>
            </div>
          </div>
        </div>
        <div class="trend-insight">
          <i class="fas fa-lightbulb"></i>
          <span>周三活跃度最高，建议安排应用题专题答疑</span>
        </div>
      </div>
    </div>

    <!-- 各班级薄弱指数（小学班级） -->
    <div class="subject-weakness">
      <div class="card-header">
        <h3><i class="fas fa-book-open" style="color: #6f42c1"></i> 各班级薄弱指数</h3>
        <div class="header-actions">
          <span class="badge-info">基于最近3次周测分析</span>
          <button class="btn-refresh" @click="refreshData">
            <i class="fas fa-sync-alt"></i>
          </button>
        </div>
      </div>
      <div class="subject-grid">
        <div v-for="(item, index) in classWeakness" :key="index" class="subject-item">
          <div class="subject-name">
            <span>{{ item.className }}</span>
            <span class="weak-level" :class="`level-${item.level}`"
              >{{ item.weakness }}%</span
            >
          </div>
          <div class="subject-bar">
            <div class="progress-bar">
              <div
                class="progress-fill"
                :style="{ width: item.weakness + '%', background: item.color }"
              ></div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="student-activity-card">
      <div class="card-header">
        <h3><i class="fas fa-user-clock" style="color: #1e6df2"></i> 学生学习行为明细</h3>
        <span class="badge-info">展示全部学生最近行为</span>
      </div>
      <div class="activity-table">
        <div class="activity-head">
          <span>学号</span>
          <span>姓名</span>
          <span>班级</span>
          <span>作业进度</span>
          <span>签到状态</span>
        </div>
        <div class="activity-body">
          <div v-for="item in studentActivities" :key="`${item.className}-${item.studentNo}`" class="activity-row">
            <span>{{ item.studentNo }}</span>
            <span>{{ item.studentName }}</span>
            <span>{{ item.className }}</span>
            <span>{{ item.submittedCount }}/{{ item.totalHomework }}</span>
            <span>{{ item.signStatus }}</span>
          </div>
          <div v-if="!studentActivities.length" class="activity-empty">暂无学生行为数据</div>
        </div>
      </div>
    </div>

    <!-- AI 深度分析建议弹窗（小学教学建议） -->
    <div v-if="showAIPanel" class="ai-panel-overlay">
      <div class="ai-panel">
        <div class="ai-panel-header">
          <div class="ai-icon"><i class="fas fa-robot"></i></div>
          <h3>AI 教学策略建议</h3>
          <button class="close-btn" @click="showAIPanel = false">
            <i class="fas fa-times"></i>
          </button>
        </div>
        <div class="ai-panel-content">
          <p>基于当前真实学情数据分析，建议：</p>
          <ul>
            <li v-for="(line, idx) in aiSuggestionLines" :key="idx">
              <i class="fas fa-check-circle" style="color: #2eb85c"></i>
              {{ line }}
            </li>
          </ul>
          <button class="btn-apply" @click="applySuggestion">应用建议</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from "vue";
import { fetchSigninClasses, fetchTeacherStudentBoard } from "@/utils/api";

const stats = ref({
  totalStudents: 0,
  completionRate: 0,
  avgStudyHours: 0,
  avgAccuracy: 0,
});

const weakPoints = ref([]);
const weekData = ref([]);
const classWeakness = ref([]);
const studentActivities = ref([]);
const classOptions = ref([]);
const selectedClassId = ref("");
const weakPointSource = ref("");

const avgWeekParticipants = computed(() => {
  if (!weekData.value.length) return 0;
  const sum = weekData.value.reduce((a, d) => a + d.value, 0);
  return Math.round(sum / weekData.value.length);
});

const showAIPanel = ref(false);
const statusMsg = ref("");
const weakPointSourceText = computed(() => {
  const src = String(weakPointSource.value || "");
  if (src === "wrong-question-records") return "错题库";
  if (src === "wrong-question-records-global") return "错题库（全库）";
  if (src === "submitted-homework-answers") return "作业提交统计";
  if (weakPoints.value.some((x) => String(x?.sub || "").includes("错题库"))) return "错题库（推断）";
  if (weakPoints.value.length) return "作业提交统计（推断）";
  return "未知";
});
const aiSuggestionLines = computed(() => {
  if (!weakPoints.value.length) {
    return ["当前暂无足够数据，建议先完成作业与测验后再查看学情建议。"];
  }
  const top = weakPoints.value.slice(0, 3).map((x) => x.name).join("、");
  const worstClass = classWeakness.value[0]?.className || "当前班级";
  return [
    `建议优先针对「${top}」安排分层讲解与专项练习。`,
    `建议在 ${worstClass} 增加一次专题答疑课，并布置基础+进阶两套训练。`,
    "建议在下次周测后复盘错因，追踪错误率变化并调整教学节奏。",
  ];
});

const loadBoard = async () => {
  try {
    const data = await fetchTeacherStudentBoard(selectedClassId.value);
    stats.value = data.stats || stats.value;
    weakPoints.value = data.weakPoints || [];
    weakPointSource.value = data.weakPointSource || "";
    weekData.value = data.weekData || [];
    classWeakness.value = data.classWeakness || [];
    studentActivities.value = data.studentActivities || [];
    statusMsg.value = "";
  } catch (e) {
    statusMsg.value = e.message || "加载看板失败";
  }
};

const loadClassOptions = async () => {
  try {
    const data = await fetchSigninClasses();
    const classes = Array.isArray(data?.classes) ? data.classes : [];
    classOptions.value = classes;
  } catch {
    classOptions.value = [];
  }
};

watch(selectedClassId, () => {
  loadBoard();
});

onMounted(async () => {
  await loadClassOptions();
  await loadBoard();
});

const askAIAnalysis = () => {
  showAIPanel.value = true;
};

const viewAllWeakPoints = () => {
  statusMsg.value = "已展示当前数据范围内错误率最高的TOP5知识点";
};

const refreshData = () => {
  loadBoard();
};

const applySuggestion = () => {
  showAIPanel.value = false;
  statusMsg.value = "AI 教学建议已记录，可按建议安排后续课堂与作业";
};
</script>

<style scoped lang="scss">
.student-data-board {
  padding: 8px 0;
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

/* 页面头部 */
.page-header {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 32px;
  flex-wrap: wrap;

  .title-icon {
    width: 56px;
    height: 56px;
    background: linear-gradient(145deg, #1e6df2, #0a4bb0);
    border-radius: 18px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: white;
    font-size: 28px;
    box-shadow: 0 8px 16px rgba(30, 109, 242, 0.2);
  }

  .title-text {
    flex: 1;
    h1 {
      font-size: 28px;
      font-weight: 700;
      color: #0b2b4a;
      margin-bottom: 6px;
    }
    .subtitle {
      color: #5e7e9c;
      font-size: 15px;
    }
  }

  .ai-badge-large {
    background: linear-gradient(145deg, #1e6df2, #0a4bb0);
    color: white;
    padding: 12px 24px;
    border-radius: 40px;
    font-weight: 700;
    font-size: 16px;
    display: flex;
    align-items: center;
    gap: 10px;
    box-shadow: 0 8px 20px rgba(30, 109, 242, 0.3);
    i {
      font-size: 20px;
    }
  }
  .class-filter {
    display: flex;
    align-items: center;
    gap: 8px;
    background: #fff;
    border: 1px solid rgba(30, 109, 242, 0.16);
    border-radius: 40px;
    padding: 8px 14px;
    i {
      color: #1e6df2;
    }
    select {
      border: none;
      outline: none;
      background: transparent;
      color: #0b2b4a;
      font-size: 14px;
      font-weight: 600;
      cursor: pointer;
    }
  }
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
    .stat-value {
      font-size: 32px;
      font-weight: 700;
      color: #0b2b4a;
      line-height: 1;
      margin-bottom: 6px;
    }
    .stat-trend {
      font-size: 13px;
      color: #5e7e9c;
      &.positive {
        color: #2eb85c;
      }
      &.negative {
        color: #ff4d4d;
      }
    }
  }
}

/* 两栏布局 */
.analytics-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
  margin-bottom: 32px;
}

/* 公共卡片样式 */
.weak-points-card,
.trend-card,
.subject-weakness {
  background: white;
  border-radius: 28px;
  padding: 28px;
  box-shadow: 0 12px 30px rgba(0, 20, 40, 0.04);
  border: 1px solid rgba(30, 109, 242, 0.1);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
  flex-wrap: wrap;

  h3 {
    font-size: 18px;
    font-weight: 700;
    color: #0b2b4a;
    display: flex;
    align-items: center;
    gap: 10px;
    i {
      font-size: 20px;
    }
  }
}

/* 薄弱知识点列表 */
.weak-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
  margin-bottom: 20px;
}

.weak-meta {
  display: flex;
  gap: 12px;
  margin: -8px 0 14px;
  font-size: 12px;
  color: #5e7e9c;
}

.weak-empty {
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px dashed #d2deed;
  background: #f8fbff;
  color: #5e7e9c;
  font-size: 13px;
}

.weak-item {
  display: flex;
  align-items: center;
  gap: 16px;

  .weak-rank {
    width: 28px;
    height: 28px;
    border-radius: 10px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-weight: 700;
    font-size: 14px;
    background: #f0f5fa;
    color: #4a668a;

    &.rank-1 {
      background: #ffd966;
      color: #7a5c00;
    }
    &.rank-2 {
      background: #e2eaf2;
      color: #3a5e7e;
    }
    &.rank-3 {
      background: #e8c1a0;
      color: #7b4a2c;
    }
  }

  .weak-info {
    flex: 1;
    display: flex;
    flex-direction: column;
    .weak-name {
      font-weight: 600;
      color: #0b2b4a;
    }
    .weak-sub {
      font-size: 12px;
      color: #5e7e9c;
    }
  }

  .weak-stat {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    min-width: 50px;
    .weak-percent {
      font-weight: 700;
      color: #0b2b4a;
    }
    .weak-label {
      font-size: 11px;
      color: #5e7e9c;
    }
  }

  .weak-bar {
    width: 120px;
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
    border-radius: 20px;
    transition: width 0.3s;
  }
}

.btn-ai-suggestion {
  background: #e8f1ff;
  border: none;
  color: #1e6df2;
  padding: 10px 20px;
  border-radius: 40px;
  font-weight: 600;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  transition: 0.2s;
  border: 1px solid transparent;

  &:hover {
    background: #1e6df2;
    color: white;
    border-color: white;
  }
}

.card-footer {
  text-align: right;
  .more-link {
    color: #1e6df2;
    text-decoration: none;
    font-weight: 600;
    font-size: 14px;
    display: inline-flex;
    align-items: center;
    gap: 6px;
    &:hover {
      text-decoration: underline;
    }
  }
}

/* 图表区域（纯CSS柱状图） */
.trend-card {
  .chart-container {
    height: 200px;
    display: flex;
    align-items: flex-end;
    margin-bottom: 16px;
  }

  .bar-chart {
    display: flex;
    align-items: flex-end;
    justify-content: space-around;
    width: 100%;
    height: 180px;
  }

  .bar-item {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8px;
    width: 12%;
  }

  .bar-label {
    font-size: 12px;
    color: #5e7e9c;
  }

  .bar-wrapper {
    width: 100%;
    height: 120px;
    display: flex;
    flex-direction: column-reverse;
    align-items: center;
  }

  .bar-fill {
    width: 24px;
    border-radius: 12px 12px 4px 4px;
    transition: height 0.3s;
  }

  .bar-value {
    font-size: 12px;
    font-weight: 600;
    color: #0b2b4a;
  }

  .trend-badge {
    background: #e8f1ff;
    color: #1e6df2;
    padding: 6px 16px;
    border-radius: 40px;
    font-size: 13px;
    font-weight: 600;
  }

  .trend-insight {
    margin-top: 16px;
    padding: 16px;
    background: #f0f7ff;
    border-radius: 16px;
    display: flex;
    align-items: center;
    gap: 12px;
    color: #1e6df2;
    i {
      font-size: 18px;
    }
  }
}

/* 学科薄弱分布 */
.subject-weakness {
  margin-bottom: 0;
}

.student-activity-card {
  background: white;
  border-radius: 28px;
  padding: 28px;
  box-shadow: 0 12px 30px rgba(0, 20, 40, 0.04);
  border: 1px solid rgba(30, 109, 242, 0.1);
  margin-top: 24px;
}
.activity-table {
  border: 1px solid #e8eef6;
  border-radius: 14px;
  overflow: hidden;
}
.activity-head,
.activity-row {
  display: grid;
  grid-template-columns: 120px 120px 140px 120px 100px;
  gap: 10px;
  padding: 12px 14px;
  align-items: center;
}
.activity-head {
  background: #f3f8ff;
  font-weight: 700;
  color: #0b2b4a;
  font-size: 13px;
}
.activity-row {
  border-top: 1px solid #edf2f7;
  font-size: 13px;
  color: #2c4e6e;
}
.activity-empty {
  padding: 18px 14px;
  color: #6d89a5;
  font-size: 13px;
}

.subject-grid {
  display: flex;
  flex-direction: column;
  gap: 18px;
  margin-top: 8px;
}

.subject-item {
  display: flex;
  align-items: center;
  gap: 20px;
}

.subject-name {
  width: 80px;
  display: flex;
  justify-content: space-between;
  font-weight: 600;
  color: #0b2b4a;
  .weak-level {
    font-size: 13px;
    font-weight: 600;
    padding: 2px 10px;
    border-radius: 20px;
    &.level-high {
      background: #ffe0e0;
      color: #cc3b3b;
    }
    &.level-medium {
      background: #fff2d6;
      color: #b9770e;
    }
    &.level-low {
      background: #e3f2e9;
      color: #1e7b4c;
    }
  }
}

.subject-bar {
  flex: 1;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 16px;
  .badge-info {
    background: #f0f5fa;
    color: #4a668a;
    padding: 6px 14px;
    border-radius: 40px;
    font-size: 13px;
  }
  .btn-refresh {
    background: transparent;
    border: 1px solid #d0e0f0;
    color: #1e6df2;
    width: 36px;
    height: 36px;
    border-radius: 50%;
    cursor: pointer;
    transition: 0.2s;
    &:hover {
      background: #e8f1ff;
      border-color: #1e6df2;
    }
  }
}

/* AI 建议弹窗 */
.ai-panel-overlay {
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

.ai-panel {
  width: 480px;
  background: white;
  border-radius: 32px;
  padding: 28px;
  box-shadow: 0 30px 60px rgba(0, 0, 0, 0.2);
  animation: slideUp 0.3s ease;

  .ai-panel-header {
    display: flex;
    align-items: center;
    gap: 16px;
    margin-bottom: 24px;
    .ai-icon {
      width: 48px;
      height: 48px;
      background: linear-gradient(145deg, #1e6df2, #0a4bb0);
      border-radius: 16px;
      display: flex;
      align-items: center;
      justify-content: center;
      color: white;
      font-size: 24px;
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
      font-size: 20px;
      color: #5e7e9c;
      cursor: pointer;
      width: 36px;
      height: 36px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      &:hover {
        background: #f0f5fa;
      }
    }
  }

  .ai-panel-content {
    p {
      color: #0b2b4a;
      font-weight: 500;
      margin-bottom: 20px;
    }
    ul {
      list-style: none;
      padding: 0;
      margin-bottom: 28px;
      li {
        display: flex;
        align-items: center;
        gap: 12px;
        padding: 12px 0;
        border-bottom: 1px solid #edf2f7;
        color: #2c4e6e;
        i {
          width: 20px;
        }
      }
    }
    .btn-apply {
      width: 100%;
      padding: 14px;
      background: linear-gradient(145deg, #1e6df2, #0a4bb0);
      border: none;
      color: white;
      font-weight: 700;
      border-radius: 40px;
      font-size: 16px;
      cursor: pointer;
      &:hover {
        background: #1e6df2;
      }
    }
  }
}

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

/* 响应式 */
@media (max-width: 1200px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .analytics-row {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .stats-grid {
    grid-template-columns: 1fr;
  }
  .page-header {
    flex-direction: column;
    align-items: flex-start;
  }
  .activity-head,
  .activity-row {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
