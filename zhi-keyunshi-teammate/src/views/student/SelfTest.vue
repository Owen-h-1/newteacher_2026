<template>
  <div class="self-test-page">
    <!-- 页面标题区域 -->
    <div class="page-header">
      <div class="title-text">
        <h1>小学自主练习</h1>
        <p class="subtitle">根据小学阶段薄弱知识点智能推荐，覆盖语数英科学美术</p>
      </div>
      <div class="ai-badge-large"><i class="fas fa-robot"></i> 小学 AI 推荐</div>
    </div>

    <!-- 筛选与推荐栏（多学科） -->
    <div class="filter-section">
      <div class="filter-row">
        <div class="filter-group">
          <label>学科</label>
          <select v-model="filters.subject">
            <option value="all">全部学科</option>
            <option value="math">数学</option>
            <option value="chinese">语文</option>
            <option value="english">英语</option>
            <option value="science">科学</option>
            <option value="art">美术</option>
          </select>
        </div>
        <div class="filter-group">
          <label>知识点</label>
          <select v-model="filters.knowledge">
            <option value="all">全部知识点</option>
            <!-- 数学 -->
            <optgroup
              v-if="filters.subject === 'all' || filters.subject === 'math'"
              label="数学"
            >
              <option value="function">两位数乘法</option>
              <option value="trigonometric">应用题</option>
              <option value="sequence">除法竖式</option>
            </optgroup>
            <!-- 语文 -->
            <optgroup
              v-if="filters.subject === 'all' || filters.subject === 'chinese'"
              label="语文"
            >
              <option value="classical">阅读理解</option>
              <option value="poetry">看图写话</option>
              <option value="writing">词语积累</option>
            </optgroup>
            <!-- 英语 -->
            <optgroup
              v-if="filters.subject === 'all' || filters.subject === 'english'"
              label="英语"
            >
              <option value="grammar">单词拼写</option>
              <option value="cloze">日常问候对话</option>
              <option value="reading">阅读理解</option>
            </optgroup>
            <!-- 科学 -->
            <optgroup
              v-if="filters.subject === 'all' || filters.subject === 'science'"
              label="科学"
            >
              <option value="newton">科学观察</option>
              <option value="circuit">天气现象</option>
              <option value="kinematics">实验步骤</option>
            </optgroup>
            <!-- 美术 -->
            <optgroup
              v-if="filters.subject === 'all' || filters.subject === 'art'"
              label="美术"
            >
              <option value="reaction">色彩搭配</option>
              <option value="mole">材料辨识</option>
              <option value="organic">作品构图</option>
            </optgroup>
          </select>
        </div>
        <div class="filter-group">
          <label>题型</label>
          <select v-model="filters.type">
            <option value="all">全部题型</option>
            <option value="choice">选择题</option>
            <option value="fill">填空题</option>
            <option value="essay">解答题</option>
          </select>
        </div>
        <div class="filter-group">
          <label>难度</label>
          <select v-model="filters.difficulty">
            <option value="all">全部难度</option>
            <option value="easy">简单</option>
            <option value="medium">进阶</option>
            <option value="hard">挑战</option>
          </select>
        </div>
        <button class="btn-filter" @click="applyFilter">
          <i class="fas fa-search"></i> 筛选
        </button>
      </div>
    </div>

    <!-- AI 智能推荐卡片（多学科） -->
    <div class="ai-recommend-card">
      <div class="ai-recommend-header">
        <div class="ai-icon">
          <i class="fas fa-robot"></i>
        </div>
        <div class="ai-text">
          <h3>AI 小学练习推荐</h3>
          <p>基于你的小学阶段薄弱知识，为你推荐以下练习</p>
        </div>
        <button class="btn-refresh" @click="refreshRecommend">
          <i class="fas fa-sync-alt"></i> 换一批
        </button>
      </div>
      <div class="recommend-list">
        <div
          v-for="(item, index) in recommendExercises"
          :key="index"
          class="recommend-item"
        >
          <div class="item-tag" :class="item.subjectClass">{{ item.subject }}</div>
          <div class="item-content">
            <div class="item-title">{{ item.title }}</div>
            <div class="item-meta">
              <span><i class="fas fa-tag"></i> {{ item.knowledge }}</span>
              <span><i class="fas fa-file-alt"></i> {{ item.type }}</span>
              <span><i class="fas fa-clock"></i> {{ formatDuration(item.time) }}</span>
            </div>
          </div>
          <span class="wait-llm-tag"><i class="fas fa-wand-magic-sparkles"></i> 待接入大模型</span>
        </div>
      </div>
    </div>

    <!-- 国家中小学智慧教育平台资源 -->
    <div class="platform-resource-card">
      <div class="platform-header">
        <div class="platform-title">
          <i class="fas fa-landmark"></i>
          <h3>国家中小学智慧教育平台资源</h3>
        </div>
        <div class="platform-actions">
          <button class="btn-go-platform" @click="goNationalPlatform()">
            <i class="fas fa-compass"></i> 前往国家平台首页
          </button>
        </div>
      </div>
      <div class="subject-quick-links">
        <button class="subject-link-btn math" @click="goNationalPlatformBySubject('数学')">
          数学
        </button>
        <button class="subject-link-btn chinese" @click="goNationalPlatformBySubject('语文')">
          语文
        </button>
        <button class="subject-link-btn english" @click="goNationalPlatformBySubject('英语')">
          英语
        </button>
        <button class="subject-link-btn science" @click="goNationalPlatformBySubject('科学')">
          科学
        </button>
        <button class="subject-link-btn art" @click="goNationalPlatformBySubject('美术')">
          美术
        </button>
      </div>
      <div class="platform-tip">
        仅保留跳转入口：可前往国家平台首页，或按学科快速进入。
      </div>
    </div>

    <div class="topic-nav-card">
      <div class="topic-nav-main">
        <h3><i class="fas fa-link"></i> 相关小学练习题</h3>
        <p>在这里输入你想练习的方向，我帮你搜索并直接跳转。</p>
      </div>
      <div class="topic-nav-actions">
        <input
          v-model.trim="topicInput"
          type="text"
          class="topic-input"
          placeholder="例如：两位数乘法 / 阅读理解 / 单词拼写 / 科学观察"
          @keyup.enter="jumpByTopic"
        />
        <button class="btn-topic-nav" @click="jumpByTopic">
          <i class="fas fa-search"></i> 搜索并跳转
        </button>
      </div>
    </div>

    <div class="history-card">
      <div class="history-header">
        <h3><i class="fas fa-route"></i> AI 学习路径规划</h3>
        <button class="btn-review" @click="previewPathPlan">
          <i class="fas fa-wand-magic-sparkles"></i> 生成路径（预览）
        </button>
      </div>
      <div class="path-plan-tip">
        将基于你的作业完成情况、薄弱知识点和课堂进度，自动生成 7 天学习路径。
      </div>
      <div class="path-list">
        <div class="path-item">
          <span class="path-day">第1-2天</span>
          <span class="path-text">夯实基础：两位数乘法与除法竖式</span>
        </div>
        <div class="path-item">
          <span class="path-day">第3-4天</span>
          <span class="path-text">能力提升：应用题审题与步骤表达</span>
        </div>
        <div class="path-item">
          <span class="path-day">第5-7天</span>
          <span class="path-text">综合巩固：错题回顾 + 小测查漏补缺</span>
        </div>
      </div>
      <div class="path-plan-foot">
        预留智能体接口：后续将接入个性化路径规划智能体并支持一键执行。
      </div>
    </div>

    <!-- Toast 反馈 -->
    <div v-if="toast.show" class="toast-message" :class="toast.type">
      <i :class="toast.icon"></i> {{ toast.text }}
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted } from "vue";
import { useRoute } from "vue-router";
import {
  fetchSelftestRecommendations,
} from "@/utils/api";

const route = useRoute();

// ---------- 筛选条件 ----------
const filters = reactive({
  subject: "all",
  knowledge: "all",
  type: "all",
  difficulty: "all",
});

const recommendExercises = ref([]);
const topicInput = ref("");
const blockedKeywords = /(氧化还原|牛顿定律|虚拟语气|化学|物理|chemistry|physics)/i;
const fallbackRecommend = [
  {
    id: 9001,
    subject: "数学",
    subjectClass: "math",
    title: "乘法应用题闯关",
    knowledge: "乘法应用题",
    type: "填空题",
    time: "6分钟",
    difficultyClass: "medium",
    difficultyText: "进阶",
  },
  {
    id: 9002,
    subject: "科学",
    subjectClass: "science",
    title: "日常现象科学观察",
    knowledge: "科学观察",
    type: "解答题",
    time: "8分钟",
    difficultyClass: "hard",
    difficultyText: "挑战",
  },
  {
    id: 9003,
    subject: "英语",
    subjectClass: "english",
    title: "日常问候对话训练",
    knowledge: "日常对话",
    type: "选择题",
    time: "4分钟",
    difficultyClass: "medium",
    difficultyText: "进阶",
  },
];

const normalizeRecommendList = (list) => {
  const safe = (Array.isArray(list) ? list : []).filter((x) => {
    const text = `${x?.subject || ""} ${x?.title || ""} ${x?.knowledge || ""}`;
    return !blockedKeywords.test(text);
  });
  if (safe.length >= 3) return safe.slice(0, 3);
  const keys = new Set(safe.map((x) => `${x.subject}|${x.title}`));
  for (const item of fallbackRecommend) {
    const key = `${item.subject}|${item.title}`;
    if (!keys.has(key)) safe.push(item);
    if (safe.length >= 3) break;
  }
  return safe.slice(0, 3);
};

const formatDuration = (value) => {
  const text = String(value ?? "").trim();
  if (!text) return "5分钟";
  return /分钟$/.test(text) ? text : `${text}分钟`;
};

const loadExerciseData = async () => {
  try {
    const rec = await fetchSelftestRecommendations();
    recommendExercises.value = normalizeRecommendList(rec.list);
  } catch (e) {
    showToast("error", e.message || "加载练习题失败", "fa-exclamation-circle");
  }
};

onMounted(() => {
  loadExerciseData();
  const q = route.query.subject;
  if (q && typeof q === "string") {
    filters.subject = q;
  }
});

// 辅助函数
const getSubjectName = (code) => {
  const map = {
    math: "数学",
    chinese: "语文",
    english: "英语",
    science: "科学",
    art: "美术",
  };
  return map[code] || code;
};

// 监听筛选条件变化，重置页码
// ---------- 交互方法 ----------
const applyFilter = () => {
  const text = [
    getSubjectName(filters.subject),
    filters.knowledge !== "all" ? filters.knowledge : "",
    filters.type !== "all" ? filters.type : "",
  ]
    .filter((x) => x && x !== "all")
    .join(" ");
  topicInput.value = text;
  jumpByTopic();
};

const refreshRecommend = async () => {
  try {
    const rec = await fetchSelftestRecommendations();
    recommendExercises.value = normalizeRecommendList(rec.list);
    showToast("success", "AI推荐已更新", "fa-sync-alt");
  } catch (e) {
    showToast("error", e.message || "刷新失败", "fa-exclamation-circle");
  }
};

const goNationalPlatform = () => {
  window.open("https://www.zxx.edu.cn/", "_blank", "noopener,noreferrer");
};

const goNationalPlatformBySubject = (subject) => {
  const keyword = `小学 ${subject} 课程`;
  const url = `https://www.zxx.edu.cn/search?${new URLSearchParams({ keyword }).toString()}`;
  window.open(url, "_blank", "noopener,noreferrer");
};

const normalizeTopic = (raw) => {
  const text = String(raw || "").trim();
  if (!text) return "小学 练习题";
  const rules = [
    { match: /乘法|应用题|除法|口算/, keyword: "小学 数学 乘法 应用题 练习题" },
    { match: /阅读|写话|词语|古诗|语文/, keyword: "小学 语文 阅读理解 看图写话 练习题" },
    { match: /英语|单词|听力|对话|拼写/, keyword: "小学 英语 单词拼写 听力 练习题" },
    { match: /科学|实验|观察|天气/, keyword: "小学 科学 观察 实验 练习题" },
    { match: /美术|色彩|构图|绘画/, keyword: "小学 美术 色彩搭配 构图 练习题" },
  ];
  const hit = rules.find((r) => r.match.test(text));
  if (hit) return hit.keyword;
  return `小学 ${text} 练习题`;
};

const jumpByTopic = () => {
  const t = String(topicInput.value || "").trim();
  const keyword = normalizeTopic(t);
  const url = `https://www.zxx.edu.cn/search?${new URLSearchParams({ keyword }).toString()}`;
  window.open(url, "_blank", "noopener,noreferrer");
};

const previewPathPlan = () => {
  showToast("success", "学习路径已生成（预览版），智能体接入后将支持自动下发", "fa-route");
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
.self-test-page {
  padding: 8px 0;
  position: relative;
  overflow: hidden;
}

.self-test-page::before {
  content: "🍃 ✨ 🍂 ✨";
  position: absolute;
  top: 6px;
  right: 12px;
  font-size: 16px;
  opacity: 0.24;
}

.self-test-page::after {
  content: "🦋 ⭐ 🍬 🌈";
  position: absolute;
  bottom: 8px;
  left: 10px;
  font-size: 16px;
  opacity: 0.22;
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
  .ai-badge-large {
    background: linear-gradient(145deg, #4b8f52, #2d6f40);
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

/* ========== 筛选栏 ========== */
.filter-section {
  background: linear-gradient(150deg, #f2ffe9 0%, #ebf8ff 100%);
  border-radius: 28px;
  padding: 24px;
  margin-bottom: 28px;
  box-shadow: 0 12px 30px rgba(0, 20, 40, 0.04);
  border: 1px solid rgba(74, 137, 80, 0.2);
}
.filter-row {
  display: flex;
  gap: 20px;
  flex-wrap: wrap;
  align-items: flex-end;
}
.filter-group {
  flex: 1;
  min-width: 160px;
  label {
    display: block;
    font-size: 13px;
    font-weight: 600;
    color: #0b2b4a;
    margin-bottom: 6px;
  }
  select {
    width: 100%;
    padding: 12px 16px;
    border: 2px solid #e2eaf2;
    border-radius: 18px;
    font-size: 14px;
    color: #0b2b4a;
    background: #f8fcff;
    transition: 0.2s;
    cursor: pointer;
    &:hover {
      border-color: #b8d6ff;
    }
    &:focus {
      border-color: #1e6df2;
      outline: none;
      box-shadow: 0 0 0 4px rgba(30, 109, 242, 0.1);
      background: white;
    }
  }
}
.btn-filter {
  background: #1e6df2;
  border: none;
  color: white;
  padding: 12px 32px;
  border-radius: 40px;
  font-weight: 600;
  font-size: 15px;
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  transition: 0.2s;
  border: 1px solid transparent;
  white-space: nowrap;
  &:hover {
    background: #0a4bb0;
    transform: scale(1.02);
  }
}

/* ========== AI 推荐卡片 ========== */
.ai-recommend-card {
  background: linear-gradient(150deg, #f5f0ff 0%, #edf7ff 100%);
  border-radius: 28px;
  padding: 28px;
  margin-bottom: 28px;
  box-shadow: 0 12px 30px rgba(0, 20, 40, 0.04);
  border: 1px solid rgba(123, 97, 186, 0.2);
  border-left: 8px solid #6f42c1;
}
.ai-recommend-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
  flex-wrap: wrap;
  .ai-icon {
    display: none;
  }
  .ai-text {
    flex: 1;
    h3 {
      font-size: 20px;
      font-weight: 700;
      color: #0b2b4a;
      margin-bottom: 4px;
    }
    p {
      color: #5e7e9c;
      font-size: 14px;
    }
  }
  .btn-refresh {
    background: transparent;
    border: 2px solid #1e6df2;
    color: #1e6df2;
    padding: 10px 24px;
    border-radius: 40px;
    font-weight: 600;
    display: flex;
    align-items: center;
    gap: 8px;
    cursor: pointer;
    transition: 0.2s;
    white-space: nowrap;
    &:hover {
      background: #1e6df2;
      color: white;
    }
  }
}
.recommend-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.recommend-item {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 16px 20px;
  background: #fffdf5;
  border: 1px solid #efe4c3;
  border-radius: 24px;
  transition: 0.2s;
  &:hover {
    background: white;
    box-shadow: 0 8px 16px rgba(0, 0, 0, 0.02);
  }
  .item-tag {
    padding: 6px 16px;
    border-radius: 40px;
    font-size: 14px;
    font-weight: 700;
    color: white;
    white-space: nowrap;
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
  .item-content {
    flex: 1;
    .item-title {
      font-size: 16px;
      font-weight: 600;
      color: #0b2b4a;
      margin-bottom: 6px;
    }
    .item-meta {
      display: flex;
      gap: 20px;
      font-size: 13px;
      color: #5e7e9c;
      i {
        margin-right: 4px;
      }
    }
  }
  .wait-llm-tag {
    color: #1e6df2;
    background: #e8f1ff;
    border-radius: 999px;
    padding: 8px 12px;
    font-size: 12px;
    font-weight: 700;
    white-space: nowrap;
  }
}

/* 跳转到输入页 */
.topic-nav-card {
  background: linear-gradient(150deg, #eefde6 0%, #f6fcff 100%);
  border-radius: 28px;
  padding: 22px 24px;
  margin-bottom: 28px;
  box-shadow: 0 12px 30px rgba(0, 20, 40, 0.04);
  border: 1px solid rgba(85, 138, 90, 0.22);
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  .topic-nav-main h3 {
    font-size: 20px;
    font-weight: 700;
    color: #0b2b4a;
    display: flex;
    align-items: center;
    gap: 10px;
    margin: 0 0 6px;
    i {
      color: #1e6df2;
    }
  }
  .topic-nav-main p {
    margin: 0;
    color: #5e7e9c;
    font-size: 14px;
  }
  .btn-topic-nav {
    border: none;
    border-radius: 999px;
    background: #1e6df2;
    color: white;
    font-weight: 700;
    padding: 10px 16px;
    cursor: pointer;
  }
}
.topic-nav-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.topic-input {
  min-width: 340px;
  border: 2px solid #e2eaf2;
  border-radius: 999px;
  padding: 10px 14px;
  font-size: 14px;
}

/* ========== 练习历史卡片 ========== */
.history-card {
  background: linear-gradient(150deg, #f4ffec 0%, #f1ebff 100%);
  border-radius: 28px;
  padding: 28px;
  box-shadow: 0 12px 30px rgba(0, 20, 40, 0.04);
  border: 1px solid rgba(87, 147, 94, 0.2);
}

.platform-resource-card {
  background: linear-gradient(150deg, #eefde8 0%, #f2f9ff 100%);
  border-radius: 28px;
  padding: 28px;
  margin-bottom: 28px;
  box-shadow: 0 12px 30px rgba(0, 20, 40, 0.04);
  border: 1px solid rgba(84, 135, 89, 0.22);
}
.platform-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
  flex-wrap: wrap;
  .platform-title {
    display: flex;
    align-items: center;
    gap: 10px;
    h3 {
      margin: 0;
      color: #0b2b4a;
      font-size: 20px;
      font-weight: 700;
    }
    i {
      color: #1e6df2;
      font-size: 20px;
    }
  }
  .platform-actions {
    display: flex;
    gap: 10px;
    select {
      border: 2px solid #e2eaf2;
      border-radius: 12px;
      padding: 8px 12px;
      background: #f8fcff;
    }
  }
}
.btn-go-platform {
  border: none;
  background: #1e6df2;
  color: white;
  border-radius: 30px;
  padding: 9px 14px;
  cursor: pointer;
  font-weight: 700;
}
.subject-quick-links {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}
.subject-link-btn {
  border: none;
  border-radius: 999px;
  padding: 6px 14px;
  font-size: 13px;
  font-weight: 700;
  color: white;
  cursor: pointer;
}
.subject-link-btn.math {
  background: #1e6df2;
}
.subject-link-btn.chinese {
  background: #2eb85c;
}
.subject-link-btn.english {
  background: #ff9f1c;
}
.subject-link-btn.science {
  background: #6f42c1;
}
.subject-link-btn.art {
  background: #e83e8c;
}
.platform-tip {
  color: #5e7e9c;
  font-size: 13px;
}
.history-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
  h3 {
    font-size: 20px;
    font-weight: 700;
    color: #0b2b4a;
    display: flex;
    align-items: center;
    gap: 12px;
    i {
      color: #1e6df2;
    }
  }
  .more-link {
    color: #1e6df2;
    font-size: 15px;
    font-weight: 600;
    text-decoration: none;
    display: flex;
    align-items: center;
    gap: 6px;
    &:hover {
      text-decoration: underline;
    }
  }
}
.path-plan-tip {
  color: #4f6e8d;
  font-size: 14px;
  margin-bottom: 12px;
}
.path-list {
  display: grid;
  gap: 10px;
}
.path-item {
  display: flex;
  gap: 12px;
  align-items: center;
  border: 1px solid #e7eef9;
  border-radius: 12px;
  padding: 10px 12px;
  background: #f9fcff;
}
.path-day {
  min-width: 72px;
  text-align: center;
  border-radius: 999px;
  padding: 3px 8px;
  background: #e7f0ff;
  color: #1e6df2;
  font-size: 12px;
  font-weight: 700;
}
.path-text {
  color: #23415f;
  font-size: 14px;
}
.path-plan-foot {
  margin-top: 12px;
  color: #6c86a0;
  font-size: 13px;
}
.history-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.history-item {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 16px;
  background: #f8fcff;
  border-radius: 20px;
  transition: 0.2s;
  &:hover {
    background: white;
    border: 1px solid rgba(30, 109, 242, 0.2);
  }
  .history-icon {
    width: 48px;
    height: 48px;
    border-radius: 16px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 24px;
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
  .history-info {
    flex: 1;
    display: flex;
    flex-direction: column;
    .history-title {
      font-size: 16px;
      font-weight: 600;
      color: #0b2b4a;
      margin-bottom: 4px;
    }
    .history-meta {
      font-size: 13px;
      color: #5e7e9c;
    }
  }
  .btn-review {
    background: transparent;
    border: 2px solid #1e6df2;
    color: #1e6df2;
    padding: 8px 20px;
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
  .filter-row {
    flex-direction: column;
    align-items: stretch;
  }
  .btn-filter {
    width: 100%;
    justify-content: center;
  }
  .ai-recommend-header {
    flex-direction: column;
    align-items: flex-start;
  }
  .btn-refresh {
    width: 100%;
    justify-content: center;
  }
  .recommend-item {
    flex-direction: column;
    align-items: flex-start;
    .wait-llm-tag {
      width: 100%;
      text-align: center;
    }
  }
  .history-item {
    flex-direction: column;
    align-items: flex-start;
    .btn-review {
      width: 100%;
      justify-content: center;
    }
  }
}
</style>
