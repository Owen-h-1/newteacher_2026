<template>
  <div class="analysis-feedback">
    <!-- 页面标题区域 -->
    <div class="page-header">
      <div class="title-icon">
        <i class="fas fa-chart-pie"></i>
      </div>
      <div class="title-text">
        <h1>学习分析与反馈</h1>
        <p class="subtitle">基于你的学习数据，AI 多学科智能诊断薄弱知识点</p>
      </div>
      <div class="ai-badge"><i class="fas fa-robot"></i> AI 实时分析</div>
    </div>

    <!-- 学习画像卡片（核心指标） -->
    <div class="profile-card">
      <div class="profile-header">
        <div class="avatar">
          <i class="fas fa-user-graduate"></i>
        </div>
        <div class="profile-info">
          <h2>张明远</h2>
          <p>高一(1)班 · 学号 20240101</p>
        </div>
      </div>
      <div class="profile-stats">
        <div class="stat-item">
          <span class="stat-label">本周学习时长</span>
          <span class="stat-value">12.5<span>h</span></span>
        </div>
        <div class="stat-item">
          <span class="stat-label">完成作业</span>
          <span class="stat-value">16<span>份</span></span>
        </div>
        <div class="stat-item">
          <span class="stat-label">平均正确率</span>
          <span class="stat-value">74<span>%</span></span>
        </div>
        <div class="stat-item">
          <span class="stat-label">班级排名</span>
          <span class="stat-value">8<span>/42</span></span>
        </div>
      </div>
    </div>

    <!-- 学科切换标签 -->
    <div class="subject-tabs">
      <button
        v-for="subject in subjectList"
        :key="subject.id"
        class="subject-tab"
        :class="{ active: currentSubject === subject.id, [subject.id]: true }"
        @click="currentSubject = subject.id"
      >
        <i :class="subject.icon"></i>
        {{ subject.name }}
      </button>
    </div>

    <!-- 两栏布局：左侧薄弱知识点，右侧掌握度雷达图（动态学科） -->
    <div class="dashboard-grid">
      <!-- 左侧：当前学科薄弱知识点 TOP5 -->
      <div class="card weak-card">
        <div class="card-header">
          <h3>
            <i class="fas fa-exclamation-triangle" :style="{ color: subjectColor }"></i>
            {{ currentSubjectName }} · 薄弱知识点 TOP{{ currentWeakPoints.length }}
          </h3>
          <span
            class="badge-warning"
            :style="{ background: subjectLightBg, color: subjectDarkColor }"
          >
            <i class="fas fa-lightbulb"></i> 需重点突破
          </span>
        </div>
        <div v-if="currentWeakPoints.length > 0" class="weak-list">
          <div v-for="(item, index) in currentWeakPoints" :key="index" class="weak-item">
            <div class="weak-rank" :class="`rank-${index + 1}`">{{ index + 1 }}</div>
            <div class="weak-info">
              <span class="weak-name">{{ item.name }}</span>
              <span class="weak-desc">{{ item.desc }}</span>
            </div>
            <div class="weak-stat">
              <span class="weak-rate">{{ item.errorRate }}%</span>
              <span class="weak-label">错误率</span>
            </div>
            <div class="weak-progress">
              <div class="progress-bar">
                <div
                  class="progress-fill"
                  :style="{ width: item.errorRate + '%', background: subjectColor }"
                ></div>
              </div>
            </div>
            <button class="btn-ai-explain" @click="askAI(item)">
              <i class="fas fa-robot"></i> AI讲解
            </button>
          </div>
        </div>
        <div v-else class="empty-weak">
          <i class="fas fa-check-circle"></i>
          <p>当前学科暂无显著薄弱点，继续保持！</p>
        </div>
        <div class="card-footer">
          <router-link
            :to="`/student/selftest?subject=${currentSubject}`"
            class="more-link"
          >
            前往 {{ currentSubjectName }} 专项练习 <i class="fas fa-arrow-right"></i>
          </router-link>
        </div>
      </div>

      <!-- 右侧：知识点掌握雷达图（学科自适应） -->
      <div class="card radar-card">
        <div class="card-header">
          <h3>
            <i class="fas fa-chart-simple" :style="{ color: subjectColor }"></i>
            {{ currentSubjectName }} · 知识点掌握度
          </h3>
          <span class="badge-sm">基于最近30次练习</span>
        </div>
        <div class="radar-container">
          <!-- 六边形网格（纯CSS绘制） -->
          <div class="radar-grid">
            <div
              class="radar-polygon"
              :style="{ transform: `rotate(0deg)`, borderColor: subjectColor + '40' }"
            ></div>
            <div
              class="radar-polygon"
              :style="{ transform: `rotate(60deg)`, borderColor: subjectColor + '40' }"
            ></div>
            <div
              class="radar-polygon"
              :style="{ transform: `rotate(120deg)`, borderColor: subjectColor + '40' }"
            ></div>
            <div class="radar-lines">
              <div
                class="radar-line"
                v-for="i in 5"
                :key="i"
                :style="{ borderColor: subjectColor + '20' }"
              ></div>
            </div>
            <!-- 数据填充区域（根据当前学科动态生成） -->
            <div
              class="radar-data"
              :style="{ background: subjectColor + '30', borderColor: subjectColor }"
            ></div>
          </div>
          <!-- 图例标签（根据学科变化） -->
          <div class="radar-labels">
            <div
              v-for="(label, idx) in currentRadarLabels"
              :key="idx"
              class="label-item"
              :style="label.style"
            >
              {{ label.text }}
            </div>
          </div>
        </div>
        <div
          class="radar-tip"
          :style="{ background: subjectLightBg, color: subjectDarkColor }"
        >
          <i class="fas fa-info-circle"></i>
          阴影面积越大，掌握度越高。建议优先突破错误率最高的知识点。
        </div>
      </div>
    </div>

    <!-- AI 个性化学习建议（多学科） -->
    <div class="card suggestion-card">
      <div class="card-header">
        <div class="suggestion-title">
          <i class="fas fa-lightbulb" style="color: #ff9f1c"></i>
          <h3>AI 个性化学习建议</h3>
        </div>
        <span class="update-time">更新于 {{ updateTime }}</span>
      </div>
      <div class="suggestion-content">
        <div
          v-for="(suggestion, index) in currentSuggestions"
          :key="index"
          class="suggestion-item"
        >
          <div class="suggestion-icon" :class="suggestion.subjectClass">
            <i :class="suggestion.icon"></i>
          </div>
          <div class="suggestion-text">
            <strong>{{ suggestion.subject }} · {{ suggestion.knowledge }}</strong>
            {{ suggestion.content }}
          </div>
          <button
            class="btn-action"
            :class="suggestion.subjectClass"
            @click="handleSuggestion(suggestion)"
          >
            <i :class="suggestion.actionIcon"></i> {{ suggestion.actionText }}
          </button>
        </div>
      </div>
    </div>

    <!-- AI 讲解弹窗（模拟） -->
    <div v-if="showAIPanel" class="modal-overlay" @click.self="showAIPanel = false">
      <div class="modal-card">
        <div class="modal-header">
          <div class="ai-icon" :style="{ background: subjectColor }">
            <i class="fas fa-robot"></i>
          </div>
          <h3>AI 数字人讲解 · {{ currentSubjectName }}</h3>
          <button class="close-btn" @click="showAIPanel = false">
            <i class="fas fa-times"></i>
          </button>
        </div>
        <div class="modal-body">
          <div class="ai-avatar-mini">
            <i class="fas fa-user-circle"></i>
          </div>
          <p class="ai-speech">
            “{{ currentWeakPoint?.name }}” 是 {{ currentSubjectName }}的核心考点。
            {{ getAISuggestion(currentWeakPoint) }}
          </p>
          <div class="ai-actions">
            <button
              class="btn-call"
              :style="{ background: subjectColor }"
              @click="startVideoCall"
            >
              <i class="fas fa-video"></i> 视频对话
            </button>
            <button
              class="btn-example"
              :style="{ color: subjectColor, borderColor: subjectColor }"
              @click="showExample"
            >
              <i class="fas fa-pencil"></i> 典型例题
            </button>
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
import { ref, reactive, computed } from "vue";

// ---------- 学科列表 ----------
const subjectList = [
  {
    id: "math",
    name: "数学",
    icon: "fas fa-calculator",
    color: "#1e6df2",
    lightBg: "#e8f1ff",
    darkColor: "#0a4bb0",
  },
  {
    id: "chinese",
    name: "语文",
    icon: "fas fa-book-open",
    color: "#2eb85c",
    lightBg: "#e3f2e9",
    darkColor: "#1e7b4c",
  },
  {
    id: "english",
    name: "英语",
    icon: "fas fa-language",
    color: "#ff9f1c",
    lightBg: "#fff2d6",
    darkColor: "#b9770e",
  },
  {
    id: "physics",
    name: "物理",
    icon: "fas fa-flask",
    color: "#6f42c1",
    lightBg: "#ede7f6",
    darkColor: "#4a2c7a",
  },
  {
    id: "chemistry",
    name: "化学",
    icon: "fas fa-vial",
    color: "#e83e8c",
    lightBg: "#fce4ec",
    darkColor: "#b01c5c",
  },
];

const currentSubject = ref("math");

// 当前学科对象
const currentSubjectObj = computed(() => {
  return subjectList.find((s) => s.id === currentSubject.value) || subjectList[0];
});
const currentSubjectName = computed(() => currentSubjectObj.value.name);
const subjectColor = computed(() => currentSubjectObj.value.color);
const subjectLightBg = computed(() => currentSubjectObj.value.lightBg);
const subjectDarkColor = computed(() => currentSubjectObj.value.darkColor);

// ---------- 各学科薄弱知识点数据 ----------
const weakPointsData = {
  math: [
    { name: "函数单调性", desc: "高一 · 函数", errorRate: 68 },
    { name: "三角函数图像变换", desc: "高一 · 三角函数", errorRate: 57 },
    { name: "数列通项公式", desc: "高一 · 数列", errorRate: 45 },
    { name: "立体几何体积", desc: "高一 · 立体几何", errorRate: 38 },
    { name: "概率计算", desc: "高一 · 概率", errorRate: 32 },
  ],
  chinese: [
    { name: "文言文虚词", desc: "高一 · 文言文", errorRate: 62 },
    { name: "诗歌鉴赏手法", desc: "高一 · 诗歌", errorRate: 54 },
    { name: "病句辨析", desc: "高一 · 语言文字运用", errorRate: 41 },
    { name: "作文立意", desc: "高一 · 写作", errorRate: 35 },
    { name: "名句默写", desc: "高一 · 古诗文", errorRate: 28 },
  ],
  english: [
    { name: "虚拟语气", desc: "高一 · 语法", errorRate: 57 },
    { name: "非谓语动词", desc: "高一 · 语法", errorRate: 52 },
    { name: "完形填空上下文推理", desc: "高一 · 完形", errorRate: 48 },
    { name: "阅读理解主旨大意", desc: "高一 · 阅读", errorRate: 40 },
    { name: "书面表达句式", desc: "高一 · 写作", errorRate: 33 },
  ],
  physics: [
    { name: "牛顿第二定律应用", desc: "高一 · 运动学", errorRate: 61 },
    { name: "受力分析", desc: "高一 · 力学", errorRate: 55 },
    { name: "动能定理", desc: "高一 · 能量", errorRate: 47 },
    { name: "电路动态分析", desc: "高一 · 恒定电流", errorRate: 39 },
    { name: "平抛运动", desc: "高一 · 曲线运动", errorRate: 31 },
  ],
  chemistry: [
    { name: "氧化还原反应配平", desc: "高一 · 氧化还原", errorRate: 64 },
    { name: "离子方程式书写", desc: "高一 · 离子反应", errorRate: 58 },
    { name: "物质的量浓度计算", desc: "高一 · 化学计量", errorRate: 49 },
    { name: "元素周期律", desc: "高一 · 元素周期表", errorRate: 42 },
    { name: "有机反应类型", desc: "高一 · 有机化学", errorRate: 36 },
  ],
};

const currentWeakPoints = computed(() => {
  return weakPointsData[currentSubject.value] || [];
});

// ---------- 各学科雷达图标签（知识点）----------
const radarLabelsData = {
  math: [
    { text: "函数", style: "top: 0%; left: 50%; transform: translateX(-50%);" },
    { text: "三角函数", style: "top: 20%; right: 10%;" },
    { text: "数列", style: "bottom: 20%; right: 10%;" },
    { text: "立体几何", style: "bottom: 0%; left: 50%; transform: translateX(-50%);" },
    { text: "概率", style: "bottom: 20%; left: 10%;" },
    { text: "向量", style: "top: 20%; left: 10%;" },
  ],
  chinese: [
    { text: "文言文", style: "top: 0%; left: 50%; transform: translateX(-50%);" },
    { text: "诗歌", style: "top: 20%; right: 10%;" },
    { text: "现代文", style: "bottom: 20%; right: 10%;" },
    { text: "作文", style: "bottom: 0%; left: 50%; transform: translateX(-50%);" },
    { text: "语言文字", style: "bottom: 20%; left: 10%;" },
    { text: "名著", style: "top: 20%; left: 10%;" },
  ],
  english: [
    { text: "语法", style: "top: 0%; left: 50%; transform: translateX(-50%);" },
    { text: "完形", style: "top: 20%; right: 10%;" },
    { text: "阅读", style: "bottom: 20%; right: 10%;" },
    { text: "写作", style: "bottom: 0%; left: 50%; transform: translateX(-50%);" },
    { text: "听力", style: "bottom: 20%; left: 10%;" },
    { text: "词汇", style: "top: 20%; left: 10%;" },
  ],
  physics: [
    { text: "运动学", style: "top: 0%; left: 50%; transform: translateX(-50%);" },
    { text: "力学", style: "top: 20%; right: 10%;" },
    { text: "电磁学", style: "bottom: 20%; right: 10%;" },
    { text: "能量", style: "bottom: 0%; left: 50%; transform: translateX(-50%);" },
    { text: "电路", style: "bottom: 20%; left: 10%;" },
    { text: "光学", style: "top: 20%; left: 10%;" },
  ],
  chemistry: [
    { text: "氧化还原", style: "top: 0%; left: 50%; transform: translateX(-50%);" },
    { text: "离子反应", style: "top: 20%; right: 10%;" },
    { text: "物质的量", style: "bottom: 20%; right: 10%;" },
    { text: "元素周期律", style: "bottom: 0%; left: 50%; transform: translateX(-50%);" },
    { text: "有机化学", style: "bottom: 20%; left: 10%;" },
    { text: "实验", style: "top: 20%; left: 10%;" },
  ],
};

const currentRadarLabels = computed(() => {
  return radarLabelsData[currentSubject.value] || radarLabelsData.math;
});

// ---------- AI 学习建议（多学科）----------
const suggestionsData = {
  math: [
    {
      subject: "数学",
      subjectClass: "math",
      icon: "fas fa-calculator",
      knowledge: "函数单调性",
      content: "错误率68%，建议从定义法、导数法两个角度理解，完成3道专项练习。",
      actionText: "开始练习",
      actionIcon: "fas fa-play",
      actionColor: "#1e6df2",
    },
    {
      subject: "数学",
      subjectClass: "math",
      icon: "fas fa-calculator",
      knowledge: "三角函数图像变换",
      content: "错误率57%，掌握“五点作图法”和平移伸缩规律，推荐观看AI讲解视频。",
      actionText: "观看视频",
      actionIcon: "fas fa-video",
      actionColor: "#1e6df2",
    },
  ],
  chinese: [
    {
      subject: "语文",
      subjectClass: "chinese",
      icon: "fas fa-book-open",
      knowledge: "文言文虚词",
      content: "错误率62%，建议整理常见虚词（之、其、而、以）的用法，每日2句翻译练习。",
      actionText: "开始练习",
      actionIcon: "fas fa-play",
      actionColor: "#2eb85c",
    },
  ],
  english: [
    {
      subject: "英语",
      subjectClass: "english",
      icon: "fas fa-language",
      knowledge: "虚拟语气",
      content: "错误率57%，区分 if 条件句与 wish/as if 从句的谓语形式，做5道改错题。",
      actionText: "开始练习",
      actionIcon: "fas fa-play",
      actionColor: "#ff9f1c",
    },
  ],
  physics: [
    {
      subject: "物理",
      subjectClass: "physics",
      icon: "fas fa-flask",
      knowledge: "牛顿第二定律",
      content: "错误率61%，重点训练连接体问题、传送带模型，建议先复习受力分析。",
      actionText: "复习基础",
      actionIcon: "fas fa-book",
      actionColor: "#6f42c1",
    },
  ],
  chemistry: [
    {
      subject: "化学",
      subjectClass: "chemistry",
      icon: "fas fa-vial",
      knowledge: "氧化还原反应",
      content: "错误率64%，掌握化合价升降法配平，练习陌生方程式书写。",
      actionText: "开始练习",
      actionIcon: "fas fa-play",
      actionColor: "#e83e8c",
    },
  ],
};

const currentSuggestions = computed(() => {
  return suggestionsData[currentSubject.value] || [];
});

// ---------- 更新时间 ----------
const updateTime = computed(() => {
  const now = new Date();
  return `${now.getHours()}:${String(now.getMinutes()).padStart(2, "0")}`;
});

// ---------- AI 讲解弹窗 ----------
const showAIPanel = ref(false);
const currentWeakPoint = ref(null);

const askAI = (item) => {
  currentWeakPoint.value = item;
  showAIPanel.value = true;
};

const getAISuggestion = (point) => {
  if (!point) return "";
  const map = {
    函数单调性: "建议从定义法、导数法两个角度理解，推荐观看专题讲解视频。",
    三角函数图像变换: "掌握“五点作图法”和平移伸缩规律，多做对比练习。",
    数列通项公式: "先判断数列类型（等差/等比），再套用公式，注意验证n=1的情况。",
    文言文虚词: "整理“之、其、而、以、于”等高频虚词的用法，结合语境记忆。",
    虚拟语气: "牢记 if 条件句主从句时态规则，以及 wish/as if 的虚拟用法。",
    牛顿第二定律: "先做受力分析，再正交分解，最后列方程求解。",
    氧化还原反应配平: "标化合价 → 找升降 → 求最小公倍数 → 配系数 → 检查原子电荷。",
  };
  return map[point?.name] || "点击视频通话，与AI助教一对一探讨。";
};

// ---------- 模拟交互 ----------
const handleSuggestion = (suggestion) => {
  showToast(
    "info",
    `已跳转至 ${suggestion.subject} · ${suggestion.knowledge}`,
    "fa-arrow-right"
  );
};

const startVideoCall = () => {
  showToast("success", "正在连接AI数字人...", "fa-video");
  setTimeout(() => {
    showToast("success", "已建立视频通话", "fa-video");
  }, 1500);
};

const showExample = () => {
  showToast("info", "加载典型例题中", "fa-pencil");
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
</script>

<style scoped lang="scss">
.analysis-feedback {
  padding: 8px 0;
}

/* ========== 页面头部 ========== */
.page-header {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 28px;
  flex-wrap: wrap;
  background: white;
  border-radius: 28px;
  padding: 20px 28px;
  box-shadow: 0 8px 20px rgba(0, 20, 40, 0.04);
  border: 1px solid rgba(30, 109, 242, 0.1);

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
  .ai-badge {
    background: linear-gradient(145deg, #1e6df2, #0a4bb0);
    color: white;
    padding: 10px 22px;
    border-radius: 40px;
    font-weight: 600;
    font-size: 15px;
    display: flex;
    align-items: center;
    gap: 10px;
    box-shadow: 0 6px 16px rgba(30, 109, 242, 0.3);
    i {
      font-size: 18px;
    }
  }
}

/* ========== 学习画像卡片 ========== */
.profile-card {
  background: white;
  border-radius: 28px;
  padding: 28px;
  margin-bottom: 28px;
  box-shadow: 0 12px 30px rgba(0, 20, 40, 0.04);
  border: 1px solid rgba(30, 109, 242, 0.1);
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;

  .profile-header {
    display: flex;
    align-items: center;
    gap: 20px;
    .avatar {
      width: 64px;
      height: 64px;
      background: #e8f1ff;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      i {
        font-size: 36px;
        color: #1e6df2;
      }
    }
    .profile-info {
      h2 {
        font-size: 22px;
        font-weight: 700;
        color: #0b2b4a;
        margin-bottom: 6px;
      }
      p {
        color: #5e7e9c;
        font-size: 14px;
      }
    }
  }

  .profile-stats {
    display: flex;
    gap: 32px;
    .stat-item {
      display: flex;
      flex-direction: column;
      .stat-label {
        font-size: 13px;
        color: #5e7e9c;
        margin-bottom: 6px;
      }
      .stat-value {
        font-size: 26px;
        font-weight: 700;
        color: #0b2b4a;
        span {
          font-size: 16px;
          font-weight: 400;
          color: #5e7e9c;
          margin-left: 2px;
        }
      }
    }
  }
}

/* ========== 学科切换标签 ========== */
.subject-tabs {
  display: flex;
  gap: 12px;
  margin-bottom: 28px;
  flex-wrap: wrap;
}
.subject-tab {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 24px;
  border-radius: 40px;
  font-weight: 600;
  font-size: 15px;
  border: 2px solid transparent;
  background: white;
  color: #4a668a;
  cursor: pointer;
  transition: 0.2s;
  i {
    font-size: 16px;
  }
  &:hover {
    background: #f8fcff;
    border-color: #b8d6ff;
  }
  &.active {
    background: #1e6df2;
    color: white;
    i {
      color: white;
    }
  }
  /* 学科激活色 */
  &.math.active {
    background: #1e6df2;
  }
  &.chinese.active {
    background: #2eb85c;
  }
  &.english.active {
    background: #ff9f1c;
  }
  &.physics.active {
    background: #6f42c1;
  }
  &.chemistry.active {
    background: #e83e8c;
  }
}

/* ========== 两栏布局 ========== */
.dashboard-grid {
  display: grid;
  grid-template-columns: 1.2fr 0.8fr;
  gap: 28px;
  margin-bottom: 28px;
}

/* ========== 通用卡片样式 ========== */
.card {
  background: white;
  border-radius: 28px;
  padding: 24px;
  box-shadow: 0 12px 30px rgba(0, 20, 40, 0.04);
  border: 1px solid rgba(30, 109, 242, 0.1);
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
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
  .badge-warning {
    padding: 6px 16px;
    border-radius: 40px;
    font-size: 13px;
    font-weight: 600;
    display: flex;
    align-items: center;
    gap: 6px;
  }
  .badge-sm {
    background: #e8f1ff;
    color: #1e6df2;
    padding: 4px 14px;
    border-radius: 40px;
    font-size: 12px;
    font-weight: 600;
  }
}
.card-footer {
  margin-top: 20px;
  text-align: right;
  .more-link {
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

/* ========== 薄弱知识点列表 ========== */
.weak-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.weak-item {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
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
    min-width: 140px;
    .weak-name {
      font-weight: 700;
      color: #0b2b4a;
      display: block;
      margin-bottom: 4px;
    }
    .weak-desc {
      font-size: 12px;
      color: #5e7e9c;
    }
  }
  .weak-stat {
    display: flex;
    flex-direction: column;
    align-items: center;
    min-width: 50px;
    .weak-rate {
      font-weight: 700;
      color: #ff4d4d;
      font-size: 18px;
    }
    .weak-label {
      font-size: 11px;
      color: #5e7e9c;
    }
  }
  .weak-progress {
    width: 100px;
  }
  .btn-ai-explain {
    background: #e8f1ff;
    border: none;
    color: #1e6df2;
    padding: 6px 16px;
    border-radius: 40px;
    font-weight: 600;
    font-size: 13px;
    display: flex;
    align-items: center;
    gap: 6px;
    cursor: pointer;
    transition: 0.2s;
    white-space: nowrap;
    &:hover {
      background: #1e6df2;
      color: white;
    }
  }
}
.empty-weak {
  text-align: center;
  padding: 40px 20px;
  i {
    font-size: 48px;
    color: #2eb85c;
    margin-bottom: 16px;
  }
  p {
    color: #5e7e9c;
    font-size: 15px;
  }
}

/* ========== 雷达图 ========== */
.radar-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  position: relative;
  padding: 20px 0;
}
.radar-grid {
  position: relative;
  width: 240px;
  height: 240px;
  margin: 0 auto;
}
.radar-polygon {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  border: 1px solid;
  border-radius: 50%;
  transform-origin: center;
}
.radar-lines {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  .radar-line {
    position: absolute;
    border: 1px solid;
    border-radius: 50%;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);
    width: 40%;
    height: 40%;
    &:nth-child(2) {
      width: 60%;
      height: 60%;
    }
    &:nth-child(3) {
      width: 80%;
      height: 80%;
    }
    &:nth-child(4) {
      width: 100%;
      height: 100%;
    }
  }
}
.radar-data {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(30, 109, 242, 0.15);
  border: 2px solid #1e6df2;
  border-radius: 50%;
  clip-path: polygon(50% 0%, 80% 25%, 70% 65%, 50% 85%, 30% 65%, 20% 25%);
}
.radar-labels {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  .label-item {
    position: absolute;
    font-size: 13px;
    font-weight: 600;
    color: #0b2b4a;
    white-space: nowrap;
  }
}
.radar-tip {
  margin-top: 20px;
  padding: 12px 16px;
  border-radius: 16px;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 8px;
  i {
    font-size: 16px;
  }
}

/* ========== AI 学习建议 ========== */
.suggestion-card {
  margin-top: 28px;
}
.suggestion-title {
  display: flex;
  align-items: center;
  gap: 10px;
}
.update-time {
  color: #5e7e9c;
  font-size: 13px;
}
.suggestion-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.suggestion-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 0;
  border-bottom: 1px solid #edf2f7;
  &:last-child {
    border-bottom: none;
  }
  .suggestion-icon {
    width: 44px;
    height: 44px;
    border-radius: 14px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: white;
    font-size: 22px;
    flex-shrink: 0;
    &.math {
      background: #1e6df2;
    }
    &.chinese {
      background: #2eb85c;
    }
    &.english {
      background: #ff9f1c;
    }
    &.physics {
      background: #6f42c1;
    }
    &.chemistry {
      background: #e83e8c;
    }
  }
  .suggestion-text {
    flex: 1;
    font-size: 14px;
    color: #2c4e6e;
    strong {
      color: #0b2b4a;
      margin-right: 6px;
    }
  }
  .btn-action {
    padding: 8px 20px;
    border-radius: 40px;
    font-size: 13px;
    font-weight: 600;
    border: none;
    cursor: pointer;
    transition: 0.2s;
    white-space: nowrap;
    display: flex;
    align-items: center;
    gap: 6px;
    &.math {
      background: #1e6df2;
      color: white;
    }
    &.chinese {
      background: #2eb85c;
      color: white;
    }
    &.english {
      background: #ff9f1c;
      color: white;
    }
    &.physics {
      background: #6f42c1;
      color: white;
    }
    &.chemistry {
      background: #e83e8c;
      color: white;
    }
    &:hover {
      filter: brightness(0.9);
      transform: scale(1.02);
    }
  }
}

/* ========== AI讲解弹窗 ========== */
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
  .ai-icon {
    width: 56px;
    height: 56px;
    border-radius: 18px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: white;
    font-size: 28px;
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
  display: flex;
  flex-direction: column;
  align-items: center;
  .ai-avatar-mini {
    width: 80px;
    height: 80px;
    background: #0b1f2e;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    margin-bottom: 20px;
    i {
      font-size: 60px;
      color: #7aa7e0;
    }
  }
  .ai-speech {
    font-size: 16px;
    line-height: 1.6;
    color: #0b2b4a;
    background: #f8fcff;
    padding: 20px;
    border-radius: 20px;
    margin-bottom: 24px;
    position: relative;
    &::before {
      content: "";
      position: absolute;
      top: -10px;
      left: 30px;
      border-left: 10px solid transparent;
      border-right: 10px solid transparent;
      border-bottom: 10px solid #f8fcff;
    }
  }
  .ai-actions {
    display: flex;
    gap: 20px;
    width: 100%;
    .btn-call,
    .btn-example {
      flex: 1;
      padding: 14px;
      border-radius: 40px;
      font-weight: 600;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      cursor: pointer;
      transition: 0.2s;
      border: none;
    }
    .btn-call {
      background: #1e6df2;
      color: white;
      &:hover {
        filter: brightness(0.9);
      }
    }
    .btn-example {
      background: transparent;
      border: 2px solid #1e6df2;
      color: #1e6df2;
      &:hover {
        background: #e8f1ff;
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

/* ========== 进度条通用 ========== */
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

/* ========== 响应式 ========== */
@media (max-width: 1000px) {
  .dashboard-grid {
    grid-template-columns: 1fr;
  }
  .profile-card {
    flex-direction: column;
    align-items: flex-start;
    .profile-stats {
      width: 100%;
      justify-content: space-between;
    }
  }
  .subject-tabs {
    justify-content: center;
  }
  .suggestion-item {
    flex-wrap: wrap;
    .btn-action {
      width: 100%;
      justify-content: center;
    }
  }
}
</style>
