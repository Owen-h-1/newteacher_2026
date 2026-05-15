<template>
  <div class="self-test-page">
    <!-- 页面标题区域 -->
    <div class="page-header">
      <div class="title-icon">
        <i class="fas fa-pencil-alt"></i>
      </div>
      <div class="title-text">
        <h1>小学自主练习</h1>
        <p class="subtitle">根据小学阶段薄弱知识点智能推荐，覆盖语数英与思想政治</p>
      </div>
      <div class="ai-badge"><i class="fas fa-robot"></i> 小学 AI 推荐</div>
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
      <div class="ai-practice-panel">
        <div class="panel-title">小学题目生成器（真实调用多智能体）</div>
        <p class="panel-hint">
          请用上方下拉框选择科目、年级、题型、难度与题量，点击「生成题目」。题目由出题智能体按 JSON 生成（含学科、年级 meta），提交后由批改智能体写评语。若某学科总不对，请在 Coze 中调整出题 Bot，使其严格服从用户消息里的学科与年级。
        </p>

        <div class="ai-gen-toolbar">
          <div class="ai-gen-field">
            <label>科目</label>
            <select v-model="aiGen.subject">
              <option value="数学">数学</option>
              <option value="语文">语文</option>
              <option value="英语">英语</option>
              <option value="思想政治">思想政治</option>
            </select>
          </div>
          <div class="ai-gen-field">
            <label>年级</label>
            <select v-model="aiGen.grade">
              <option value="一年级">一年级</option>
              <option value="二年级">二年级</option>
              <option value="三年级">三年级</option>
              <option value="四年级">四年级</option>
              <option value="五年级">五年级</option>
              <option value="六年级">六年级</option>
            </select>
          </div>
          <div class="ai-gen-field">
            <label>题型</label>
            <select v-model="aiGen.questionType">
              <option value="选择题">选择题</option>
              <option value="填空题">填空题</option>
              <option value="解答题">解答题</option>
            </select>
          </div>
          <div class="ai-gen-field">
            <label>知识点</label>
            <select v-model="aiGen.knowledgePoint">
              <option value="">不限（综合）</option>
              <option v-for="kp in knowledgePointOptions" :key="kp" :value="kp">
                {{ kp }}
              </option>
            </select>
            <input
              v-model.trim="aiGen.customKnowledgePoint"
              type="text"
              class="knowledge-input"
              placeholder="或手动输入知识点（如：分数加减法）"
            />
          </div>
          <div class="ai-gen-field">
            <label>难度</label>
            <select v-model="aiGen.difficulty">
              <option value="基础">基础</option>
              <option value="进阶">进阶</option>
              <option value="挑战">挑战</option>
            </select>
          </div>
          <div class="ai-gen-field">
            <label>题量</label>
            <select v-model.number="aiGen.count">
              <option :value="1">1道</option>
              <option :value="2">2道</option>
              <option :value="3">3道</option>
              <option :value="5">5道</option>
              <option :value="10">10道</option>
            </select>
          </div>
          <button class="btn-ai-generate" type="button" :disabled="generateLoading" @click="generatePracticeQuestions">
            <span class="emoji">🚀</span> {{ generateLoading ? "生成中..." : "生成题目" }}
          </button>
        </div>

        <div v-if="structuredQuestions.length" class="mcq-list">
          <div
            v-for="q in structuredQuestions"
            :key="q.no"
            class="mcq-card"
          >
            <div class="mcq-stem">{{ q.no }}. {{ q.stem }}</div>

            <template v-if="questionKindOf(q) === 'mcq'">
              <label
                v-for="opt in q.options"
                :key="`${q.no}-${opt.key}`"
                class="mcq-option"
                :class="{ 'mcq-option--picked': selections[q.no] === opt.key }"
              >
                <input
                  v-model="selections[q.no]"
                  type="radio"
                  class="mcq-radio"
                  :name="`mcq-${q.no}`"
                  :value="opt.key"
                />
                <span class="mcq-opt-label">{{ opt.key }}.</span>
                <span class="mcq-opt-text">{{ opt.text }}</span>
              </label>
            </template>

            <div v-else-if="questionKindOf(q) === 'fill'" class="text-answer-wrap">
              <label class="text-answer-label">作答</label>
              <input
                v-model.trim="textAnswers[q.no]"
                type="text"
                class="text-answer-input"
                :placeholder="`第${q.no}题答案`"
              />
            </div>

            <div v-else class="text-answer-wrap text-answer-wrap--essay">
              <label class="text-answer-label">作答</label>
              <textarea
                v-model.trim="textAnswers[q.no]"
                class="text-answer-textarea"
                rows="5"
                :placeholder="`第${q.no}题请写出解答过程或要点`"
              />
            </div>
          </div>
        </div>

        <button
          v-if="structuredQuestions.length && paperId"
          type="button"
          class="btn-ai-submit-grade"
          :disabled="gradeLoading || !allStructuredAnswered"
          @click="gradePracticeAnswer"
        >
          <span class="emoji">✍️</span> {{ gradeLoading ? "批改中..." : "提交答案并AI批改" }}
        </button>

        <div v-if="gradingResult && structuredQuestions.length" class="grade-result-card">
          <div class="grade-result-title">
            <template v-if="gradingResult.hasSubjective && gradingResult.total === 0">
              📊 批改结果：共 {{ structuredQuestions.length }} 道主观题（见下方评语）
            </template>
            <template v-else-if="gradingResult.hasSubjective">
              📊 批改结果：客观题答对 {{ gradingResult.correctCount }}/{{ gradingResult.total }}（含主观题见评语）
            </template>
            <template v-else>
              📊 批改结果：答对 {{ gradingResult.correctCount }}/{{ gradingResult.total }} 题
            </template>
          </div>
          <p v-if="gradingResult.gradeUsedFallback" class="grade-fallback-hint">
            当前为本地简评；若需 AI 逐题讲解，请检查后端 COZE_PAT、COZE_GRADE_BOT_ID 与网络。
          </p>
          <div
            v-for="row in gradingResult.items"
            :key="'g-' + row.no"
            class="grade-result-item"
          >
            <div class="grade-line">
              <template v-if="row.subjective">
                <span class="tag-neutral">📝 主观题</span>
                <span class="grade-meta">第{{ row.no }}题 · 已提交作答</span>
              </template>
              <template v-else>
                <span v-if="row.correct" class="tag-ok">✅ 正确</span>
                <span v-else class="tag-bad">❌ 需改进</span>
                <span class="grade-meta">第{{ row.no }}题 · 你的作答 {{ row.yourChoice || "—" }} · 参考 {{ row.correctKey }}</span>
              </template>
            </div>
          </div>
          <pre class="grade-ai-text">{{ gradingResult.gradeText || gradingResult.answer }}</pre>
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
          placeholder="例如：两位数乘法 / 阅读理解 / 单词拼写"
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
        <div class="history-actions">
          <button v-if="lastPlanRawText" class="btn-review btn-secondary" @click="openPlanRawModal">
            <i class="fas fa-file-lines"></i> 查看原始返回
          </button>
          <button class="btn-review" @click="previewPathPlan">
            <i class="fas fa-wand-magic-sparkles"></i> {{ pathPlanLoading ? "生成中..." : "生成路径" }}
          </button>
        </div>
      </div>
      <div class="path-plan-tip">
        将基于你的作业完成情况、薄弱知识点和课堂进度，自动生成 7 天学习路径。
      </div>
      <div class="path-list">
        <div v-for="(item, idx) in pathPlanItems" :key="`${item.day}-${idx}`" class="path-item">
          <span class="path-day">{{ item.day }}</span>
          <span class="path-text">{{ item.text }}</span>
        </div>
      </div>
      <div class="path-plan-foot">
        已接入个性化路径规划智能体，可根据当前学习情况动态生成。
      </div>
    </div>

    <!-- Toast 反馈 -->
    <div v-if="toast.show" class="toast-message" :class="toast.type">
      <i :class="toast.icon"></i> {{ toast.text }}
    </div>

    <div v-if="showPlanRawModal" class="plan-raw-mask" @click.self="closePlanRawModal">
      <div class="plan-raw-dialog">
        <div class="plan-raw-header">
          <h4>智能体原始返回</h4>
          <button class="btn-close-raw" @click="closePlanRawModal">
            <i class="fas fa-xmark"></i>
          </button>
        </div>
        <pre class="plan-raw-content">{{ lastPlanRawText }}</pre>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted } from "vue";
import { useRoute } from "vue-router";
import {
  fetchSelftestRecommendations,
  chatWithLLM,
  generateSelftestByAgent,
  gradeSelftestByAgent,
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
    subject: "语文",
    subjectClass: "chinese",
    title: "看图写话小练笔",
    knowledge: "看图写话",
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
const defaultPathPlan = [
  { day: "第1-2天", text: "夯实基础：两位数乘法与除法竖式" },
  { day: "第3-4天", text: "能力提升：应用题审题与步骤表达" },
  { day: "第5-7天", text: "综合巩固：错题回顾 + 小测查漏补缺" },
];
const pathPlanItems = ref(defaultPathPlan);
const pathPlanLoading = ref(false);
const lastPlanRawText = ref("");
const showPlanRawModal = ref(false);
const generateLoading = ref(false);
const gradeLoading = ref(false);

/** 参考「小学题目生成器」：筛选栏 + 选择题卡片 */
const aiGen = reactive({
  subject: "数学",
  grade: "一年级",
  questionType: "选择题",
  knowledgePoint: "",
  customKnowledgePoint: "",
  difficulty: "基础",
  count: 2,
});
const paperId = ref("");
const structuredQuestions = ref([]);
const selections = reactive({});
const textAnswers = reactive({});
const gradingResult = ref(null);

const knowledgePointBySubject = {
  数学: ["两位数乘法", "应用题", "除法竖式", "口算与估算"],
  语文: ["阅读理解", "看图写话", "词语积累", "古诗文背诵"],
  英语: ["单词拼写", "日常问候对话", "阅读理解", "基础语法"],
  思想政治: ["文明礼仪", "规则意识", "爱国教育", "社会责任"],
};

const knowledgePointOptions = computed(() => {
  return knowledgePointBySubject[aiGen.subject] || [];
});

const selectedKnowledgePoint = computed(() => {
  return String(aiGen.customKnowledgePoint || aiGen.knowledgePoint || "").trim();
});

watch(
  () => aiGen.subject,
  (subject) => {
    const options = knowledgePointBySubject[subject] || [];
    if (!options.includes(aiGen.knowledgePoint)) {
      aiGen.knowledgePoint = "";
    }
  },
);

const questionKindOf = (q) => {
  const k = String(q?.kind || "").trim();
  if (k === "fill" || k === "填空题") return "fill";
  if (k === "essay" || k === "解答题") return "essay";
  return "mcq";
};

const allStructuredAnswered = computed(() => {
  if (!structuredQuestions.value.length) return false;
  return structuredQuestions.value.every((q) => {
    const kind = questionKindOf(q);
    if (kind === "mcq") return Boolean(selections[q.no]);
    const t = String(textAnswers[q.no] ?? "").trim();
    return t.length > 0;
  });
});

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

const generatePracticeQuestions = async () => {
  if (generateLoading.value) return;
  generateLoading.value = true;
  try {
    const rec = await generateSelftestByAgent({
      subject: aiGen.subject,
      grade: aiGen.grade,
      questionType: aiGen.questionType,
      knowledgePoint: selectedKnowledgePoint.value,
      difficulty: aiGen.difficulty,
      count: Number(aiGen.count) > 0 ? Number(aiGen.count) : 2,
    });
    if (rec?.mode === "structured" && Array.isArray(rec.questions)) {
      paperId.value = rec.paperId || "";
      structuredQuestions.value = rec.questions;
      Object.keys(selections).forEach((k) => delete selections[k]);
      Object.keys(textAnswers).forEach((k) => delete textAnswers[k]);
      gradingResult.value = null;
      showToast("success", "题目已生成", "fa-wand-magic-sparkles");
    } else {
      throw new Error("未返回结构化题目，请重试");
    }
  } catch (e) {
    showToast("error", e.message || "题目生成失败", "fa-exclamation-circle");
  } finally {
    generateLoading.value = false;
  }
};

const gradePracticeAnswer = async () => {
  if (gradeLoading.value) return;
  if (!structuredQuestions.value.length || !paperId.value) {
    showToast("error", "请先生成题目", "fa-exclamation-circle");
    return;
  }
  if (!allStructuredAnswered.value) {
    showToast("error", "请先完成每道题的作答", "fa-exclamation-circle");
    return;
  }
  gradeLoading.value = true;
  try {
    const selPayload = {};
    const ansPayload = {};
    structuredQuestions.value.forEach((q) => {
      const kind = questionKindOf(q);
      if (kind === "mcq") {
        const v = selections[q.no];
        if (v) selPayload[String(q.no)] = v;
      } else {
        const t = String(textAnswers[q.no] ?? "").trim();
        if (t) ansPayload[String(q.no)] = t;
      }
    });
    const rec = await gradeSelftestByAgent({
      paperId: paperId.value,
      selections: selPayload,
      answers: ansPayload,
    });
    gradingResult.value = rec;
    showToast("success", "批改完成", "fa-check-double");
  } catch (e) {
    showToast("error", e.message || "批改失败", "fa-exclamation-circle");
  } finally {
    gradeLoading.value = false;
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

const extractPathPlanItems = (answer) => {
  const text = String(answer || "").trim();
  if (!text) return [];

  const jsonBlock = text.match(/```(?:json)?\s*([\s\S]*?)```/i)?.[1] || "";
  const candidate = jsonBlock || text;
  try {
    const parsed = JSON.parse(candidate);
    if (Array.isArray(parsed?.plan)) {
      return parsed.plan
        .map((x) => ({
          day: String(x?.day || "").trim(),
          text: String(x?.text || x?.task || "").trim(),
        }))
        .filter((x) => x.day && x.text);
    }
    if (Array.isArray(parsed)) {
      return parsed
        .map((x) => ({
          day: String(x?.day || "").trim(),
          text: String(x?.text || x?.task || "").trim(),
        }))
        .filter((x) => x.day && x.text);
    }
  } catch {
    // ignore parse failure and fallback to line extraction
  }

  return text
    .split(/\r?\n/)
    .map((line) => line.trim().replace(/^[-*]\s*/, ""))
    .map((line) => {
      const match = line.match(/^(第?\d+(?:-\d+)?天?)\s*[:：]\s*(.+)$/);
      return match ? { day: match[1], text: match[2] } : null;
    })
    .filter(Boolean)
    .slice(0, 7);
};

const previewPathPlan = async () => {
  if (pathPlanLoading.value) return;
  pathPlanLoading.value = true;
  try {
    const subject = filters.subject === "all" ? "综合" : getSubjectName(filters.subject);
    const weaknesses = recommendExercises.value.map((x) => x.knowledge).filter(Boolean).join("、");
    const prompt = [
      "你是一名小学学习规划助手。",
      `学科：${subject}`,
      `薄弱知识点：${weaknesses || "两位数乘法、应用题审题、单词拼写"}`,
      "课堂进度：小学阶段同步课程",
      "目标：7天内提升基础能力和作业正确率。",
      "请输出7天学习路径，格式为“第X天: 任务内容”，每行一条，不要输出其他说明。",
    ].join("\n");
    const rec = await chatWithLLM(prompt);
    lastPlanRawText.value = String(rec?.answer || "").trim();
    const plan = extractPathPlanItems(rec?.answer);
    if (!plan.length) {
      throw new Error("智能体返回格式异常，请稍后重试");
    }
    pathPlanItems.value = plan;
    showToast("success", "学习路径已生成", "fa-route");
  } catch (e) {
    showToast("error", e.message || "学习路径生成失败", "fa-exclamation-circle");
  } finally {
    pathPlanLoading.value = false;
  }
};

const openPlanRawModal = () => {
  if (!lastPlanRawText.value) return;
  showPlanRawModal.value = true;
};

const closePlanRawModal = () => {
  showPlanRawModal.value = false;
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

/* ========== 筛选栏 ========== */
.filter-section {
  background: white;
  border-radius: 28px;
  padding: 24px;
  margin-bottom: 28px;
  box-shadow: 0 12px 30px rgba(0, 20, 40, 0.04);
  border: 1px solid rgba(30, 109, 242, 0.1);
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
  background: white;
  border-radius: 28px;
  padding: 28px;
  margin-bottom: 28px;
  box-shadow: 0 12px 30px rgba(0, 20, 40, 0.04);
  border: 1px solid rgba(30, 109, 242, 0.1);
  border-left: 8px solid #1e6df2;
}
.ai-recommend-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
  flex-wrap: wrap;
  .ai-icon {
    width: 56px;
    height: 56px;
    background: #e8f1ff;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    i {
      font-size: 32px;
      color: #1e6df2;
    }
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
  background: #f8fcff;
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
  .btn-use-demand {
    border: none;
    color: #1e6df2;
    background: #e8f1ff;
    border-radius: 999px;
    padding: 8px 12px;
    font-size: 12px;
    font-weight: 700;
    white-space: nowrap;
    cursor: pointer;
  }
}

.ai-practice-panel {
  margin-top: 18px;
  border: 1px solid #d9e7ff;
  border-radius: 16px;
  background: #f8fbff;
  padding: 14px;
}
.panel-title {
  font-size: 15px;
  font-weight: 700;
  color: #15457e;
  margin-bottom: 6px;
}
.panel-hint {
  margin: 0 0 14px;
  font-size: 13px;
  color: #5e7e9c;
  line-height: 1.5;
}
.panel-block {
  margin-bottom: 16px;
  &:last-child {
    margin-bottom: 0;
  }
}
.panel-block--paper {
  scroll-margin-top: 12px;
}
.section-label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 700;
  color: #0b2b4a;
  margin-bottom: 8px;
}
.section-num {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #1e6df2;
  color: #fff;
  font-size: 12px;
  font-weight: 800;
}
.practice-input {
  width: 100%;
  box-sizing: border-box;
  resize: vertical;
  border: 1px solid #d6e4fb;
  border-radius: 12px;
  padding: 12px;
  font-size: 14px;
  color: #23415f;
}
.practice-input--demand {
  min-height: 88px;
}
.practice-input--answer {
  min-height: 160px;
}
.btn-panel-main {
  border: none;
  border-radius: 12px;
  background: #1e6df2;
  color: #fff;
  font-weight: 700;
  cursor: pointer;
  padding: 12px 16px;
  margin-top: 10px;
}
.btn-panel-main--full {
  width: 100%;
  margin-top: 10px;
}
.btn-panel-main--secondary {
  background: #0a4bb0;
}
.btn-panel-main:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}
.panel-answer {
  border: 1px solid #dce8fa;
  border-radius: 12px;
  background: #fff;
  padding: 12px;
  pre {
    margin: 0;
    white-space: pre-wrap;
    word-break: break-word;
    font-size: 13px;
    color: #2f4d6d;
    line-height: 1.6;
  }
}
.panel-answer--question {
  max-height: 360px;
  overflow: auto;
}
.panel-answer.feedback {
  border-color: #cde5d6;
  background: #f8fff9;
}

.ai-gen-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 14px;
  align-items: flex-end;
  margin-bottom: 18px;
  padding: 14px;
  background: #fff;
  border-radius: 14px;
  border: 1px solid #e2eaf2;
}
.ai-gen-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 100px;
  label {
    font-size: 12px;
    font-weight: 600;
    color: #5e7e9c;
  }
  select {
    padding: 8px 10px;
    border-radius: 10px;
    border: 2px solid #e2eaf2;
    background: #f8fcff;
    font-size: 14px;
    color: #0b2b4a;
  }
}
.knowledge-input {
  margin-top: 6px;
  padding: 8px 10px;
  border-radius: 10px;
  border: 2px solid #e2eaf2;
  background: #fff;
  font-size: 13px;
  color: #23415f;
}
.btn-ai-generate {
  border: none;
  border-radius: 12px;
  background: linear-gradient(145deg, #1e6df2, #0a4bb0);
  color: #fff;
  font-weight: 700;
  padding: 10px 18px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-left: auto;
  .emoji {
    font-style: normal;
  }
}
.btn-ai-generate:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.mcq-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-bottom: 16px;
}
.mcq-card {
  background: #fff;
  border: 1px solid #dce8fa;
  border-radius: 14px;
  padding: 14px 16px;
}
.mcq-stem {
  font-size: 16px;
  font-weight: 600;
  color: #0b2b4a;
  margin-bottom: 12px;
}
.mcq-option {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  margin-bottom: 8px;
  border-radius: 10px;
  border: 1px solid #eef2f7;
  cursor: pointer;
  transition: background 0.15s;
  &:last-child {
    margin-bottom: 0;
  }
}
.mcq-option--picked {
  background: #e8f8ec;
  border-color: #b8e6c5;
}
.mcq-radio {
  accent-color: #2eb85c;
}
.mcq-opt-label {
  font-weight: 700;
  color: #1e6df2;
  min-width: 28px;
}
.mcq-opt-text {
  color: #23415f;
}

.text-answer-wrap {
  margin-top: 4px;
}
.text-answer-wrap--essay {
  margin-top: 8px;
}
.text-answer-label {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: #5e7e9c;
  margin-bottom: 8px;
}
.text-answer-input {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid #dce8fa;
  border-radius: 10px;
  padding: 10px 12px;
  font-size: 15px;
  color: #23415f;
  background: #fbfdff;
}
.text-answer-textarea {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid #dce8fa;
  border-radius: 10px;
  padding: 10px 12px;
  font-size: 15px;
  line-height: 1.55;
  color: #23415f;
  background: #fbfdff;
  resize: vertical;
  min-height: 120px;
  font-family: inherit;
}

.btn-ai-submit-grade {
  width: 100%;
  border: none;
  border-radius: 14px;
  background: #2eb85c;
  color: #fff;
  font-weight: 700;
  font-size: 15px;
  padding: 14px;
  cursor: pointer;
  margin-bottom: 18px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  .emoji {
    font-style: normal;
  }
}
.btn-ai-submit-grade:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.grade-result-card {
  background: #fff;
  border: 1px solid #dce8fa;
  border-radius: 14px;
  padding: 16px;
  margin-top: 8px;
}
.grade-result-title {
  font-size: 16px;
  font-weight: 700;
  color: #0b2b4a;
  margin-bottom: 12px;
}
.grade-fallback-hint {
  font-size: 13px;
  color: #8a5a00;
  background: #fff8e6;
  border: 1px solid #f0d9a8;
  border-radius: 10px;
  padding: 8px 12px;
  margin: 0 0 12px;
  line-height: 1.45;
}
.grade-result-item {
  margin-bottom: 10px;
}
.grade-line {
  font-size: 14px;
  color: #23415f;
  line-height: 1.5;
}
.tag-ok {
  color: #2eb85c;
  font-weight: 700;
  margin-right: 8px;
}
.tag-bad {
  color: #e55353;
  font-weight: 700;
  margin-right: 8px;
}
.tag-neutral {
  color: #5e7e9c;
  font-weight: 700;
  margin-right: 8px;
}
.grade-meta {
  color: #5e7e9c;
  font-size: 13px;
}
.grade-ai-text {
  margin: 14px 0 0;
  padding: 12px;
  background: #f8fbff;
  border-radius: 10px;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 14px;
  line-height: 1.65;
  color: #2f4d6d;
}

/* 跳转到输入页 */
.topic-nav-card {
  background: white;
  border-radius: 28px;
  padding: 22px 24px;
  margin-bottom: 28px;
  box-shadow: 0 12px 30px rgba(0, 20, 40, 0.04);
  border: 1px solid rgba(30, 109, 242, 0.1);
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
  background: white;
  border-radius: 28px;
  padding: 28px;
  box-shadow: 0 12px 30px rgba(0, 20, 40, 0.04);
  border: 1px solid rgba(30, 109, 242, 0.1);
}

.platform-resource-card {
  background: white;
  border-radius: 28px;
  padding: 28px;
  margin-bottom: 28px;
  box-shadow: 0 12px 30px rgba(0, 20, 40, 0.04);
  border: 1px solid rgba(30, 109, 242, 0.1);
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
.history-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.btn-secondary {
  border-color: #d5e4ff !important;
  color: #345f9e !important;
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

.plan-raw-mask {
  position: fixed;
  inset: 0;
  background: rgba(8, 20, 35, 0.45);
  z-index: 10000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}
.plan-raw-dialog {
  width: min(880px, 100%);
  max-height: min(80vh, 760px);
  background: #fff;
  border-radius: 18px;
  border: 1px solid #dce8fa;
  box-shadow: 0 20px 40px rgba(0, 20, 40, 0.2);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.plan-raw-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid #ecf2fb;
  h4 {
    margin: 0;
    color: #12365d;
    font-size: 16px;
    font-weight: 700;
  }
}
.btn-close-raw {
  border: none;
  background: transparent;
  color: #6a829d;
  cursor: pointer;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  &:hover {
    background: #eef4ff;
    color: #1e6df2;
  }
}
.plan-raw-content {
  margin: 0;
  padding: 16px;
  overflow: auto;
  color: #2e4761;
  font-size: 13px;
  line-height: 1.6;
  background: #fbfdff;
  white-space: pre-wrap;
  word-break: break-word;
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
    .btn-use-demand {
      width: 100%;
      text-align: center;
    }
  }
  .btn-panel-main {
    min-height: 44px;
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
