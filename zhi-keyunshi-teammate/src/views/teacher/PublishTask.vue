<template>
  <div class="publish-task-page">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="title-text">
        <h1>题目发布</h1>
        <p class="subtitle">发布作业、测验题，支持小学课堂题型</p>
      </div>
      <div class="ai-badge-large"><i class="fas fa-magic"></i> AI 智能组题</div>
    </div>

    <!-- 发布表单卡片 -->
    <div class="form-card">
      <div class="form-section">
        <div class="section-header">
          <i class="fas fa-info-circle"></i>
          <h3>基本信息</h3>
        </div>
        <div class="form-row">
          <div class="form-group">
            <label>选择班级</label>
            <select v-model="formData.classId">
              <option v-for="c in classOptions" :key="c.classId" :value="c.classId">{{ c.className }}</option>
            </select>
          </div>
          <div class="form-group">
            <label>截止时间</label>
            <input type="datetime-local" v-model="formData.deadline" />
          </div>
        </div>
        <div class="form-row">
          <div class="form-group full">
            <label>任务标题</label>
            <input
              v-model="formData.title"
              type="text"
              placeholder="例如：两位数乘法课后练习"
            />
          </div>
        </div>
        <div class="form-row">
          <div class="form-group full">
            <label>任务描述</label>
            <textarea
              v-model="formData.description"
              rows="3"
              placeholder="输入详细要求或说明……"
            ></textarea>
          </div>
        </div>
      </div>

      <div class="form-section">
        <div class="section-header">
          <i class="fas fa-plus-circle"></i>
          <h3>添加题目</h3>
          <span class="section-badge">小学课堂</span>
        </div>
        <div class="question-type-tabs">
          <button
            v-for="type in questionTypes"
            :key="type.value"
            class="type-btn"
            :class="{ active: currentQuestionType === type.value }"
            @click="currentQuestionType = type.value"
          >
            <i :class="type.icon"></i> {{ type.label }}
          </button>
        </div>

        <div class="question-editor">
          <div class="form-group full">
            <label>题目内容</label>
            <input v-model="newQuestion.content" type="text" placeholder="输入题目……" />
          </div>

          <!-- 选择题选项 -->
          <div v-if="currentQuestionType === 'choice'" class="options-group">
            <label>选项 (每行一个)</label>
            <textarea
              v-model="newQuestion.options"
              rows="3"
              placeholder="A. 3 支&#10;B. 4 支&#10;C. 5 支&#10;D. 6 支"
            ></textarea>
            <div class="form-group">
              <label>正确答案</label>
              <input v-model="newQuestion.answer" type="text" placeholder="如：A" />
            </div>
          </div>

          <!-- 填空题 -->
          <div v-if="currentQuestionType === 'fill'" class="form-group">
            <label>参考答案（可用“/”分隔多个答案）</label>
            <input v-model="newQuestion.answer" type="text" placeholder="如：4" />
          </div>

          <!-- 解答题 -->
          <div v-if="currentQuestionType === 'essay'" class="form-group">
            <label>参考答案或提示（可选）</label>
            <input
              v-model="newQuestion.answer"
              type="text"
              placeholder="关键步骤或结果"
            />
          </div>

          <div class="form-row">
            <div class="form-group">
              <label>分值</label>
              <input v-model="newQuestion.score" type="number" min="1" value="5" />
            </div>
            <button class="btn-add-question" @click="addQuestion">
              <i class="fas fa-plus"></i> 添加到试卷
            </button>
          </div>
        </div>
      </div>

      <!-- AI 智能组题（小学课堂专版） -->
      <div class="ai-quick-entry">
        <i class="fas fa-robot"></i>
        <span>AI 智能组题：根据知识点自动生成小学课堂题</span>
        <button class="btn-ai-generate" @click="aiGenerate">
          <i class="fas fa-wand-magic-sparkles"></i> 一键生成
        </button>
      </div>
    </div>

    <!-- 题目列表预览 -->
    <div class="question-list-card">
      <div class="list-header">
        <h3><i class="fas fa-list-ul"></i> 试卷题目（{{ questionList.length }}）</h3>
        <div class="list-actions">
          <span class="total-score">总分：{{ totalScore }} 分</span>
          <button class="btn-clear" @click="clearAll" v-if="questionList.length">
            <i class="fas fa-trash-alt"></i> 清空
          </button>
        </div>
      </div>

      <div v-if="questionList.length === 0" class="empty-questions">
        <i class="fas fa-file-alt"></i>
        <p>尚未添加题目，请通过上方表单添加</p>
      </div>

      <div v-else class="question-items">
        <div v-for="(q, index) in questionList" :key="index" class="question-item">
          <div class="question-index">{{ index + 1 }}</div>
          <div class="question-main">
            <div class="question-header">
              <span class="question-type-badge" :class="q.type">
                <i :class="getTypeIcon(q.type)"></i> {{ getTypeLabel(q.type) }}
              </span>
              <span class="question-score">{{ q.score }}分</span>
            </div>
            <div class="question-content" v-html="formatMath(q.content)"></div>
            <div
              v-if="q.options"
              class="question-options"
              v-html="formatOptions(q.options)"
            ></div>
            <div v-if="q.answer" class="question-answer">
              <span class="answer-label">答案：</span>
              <span v-html="formatMath(q.answer)"></span>
            </div>
          </div>
          <button class="btn-remove" @click="removeQuestion(index)" title="删除">
            <i class="fas fa-times"></i>
          </button>
        </div>
      </div>

      <div class="publish-actions">
        <button class="btn-preview" @click="previewPaper">
          <i class="fas fa-eye"></i> 预览试卷
        </button>
        <button class="btn-publish" @click="publishTask">
          <i class="fas fa-paper-plane"></i> 发布任务
        </button>
      </div>

      <div v-if="submissionBoard.homeworkId" class="submission-board">
        <div class="list-header">
          <h3><i class="fas fa-clipboard-check"></i> 学生作答情况</h3>
          <button class="btn-preview" @click="loadSubmissionBoard">刷新</button>
        </div>
        <div v-if="submissionBoard.loading">加载中...</div>
        <div v-else-if="!submissionBoard.list.length" class="empty-questions">暂无作答记录</div>
        <div v-else class="question-items">
          <div v-for="s in submissionBoard.list" :key="submissionBoardRowKey(s)" class="question-item">
            <div class="question-main">
              <div class="question-header">
                <span class="question-type-badge">{{ s.studentName }}（{{ s.studentNo }}）</span>
                <span class="question-score">{{ s.submitted ? "已提交" : "未提交" }}</span>
              </div>
              <div class="question-content">{{ formatSubmitTimeForDisplay(s.submitTime) || "--" }}</div>
              <div v-if="s.submitted" class="question-content">
                个人正确率：{{ s.accuracyPercent != null ? `${s.accuracyPercent}%` : "—" }}
              </div>
              <div v-if="s.submitted" class="submission-grade-row">
                <span class="submission-grade-label">评级</span>
                <button
                  v-for="g in gradeLetters"
                  :key="g"
                  type="button"
                  class="submission-grade-pill"
                  :class="{ active: s.teacherGrade === g, disabled: gradeSavingKey === submissionBoardRowKey(s) }"
                  :disabled="gradeSavingKey === submissionBoardRowKey(s)"
                  @click.stop="saveSubmissionGrade(s, g)"
                >
                  {{ g }}
                </button>
                <button
                  type="button"
                  class="submission-grade-clear"
                  :disabled="gradeSavingKey === submissionBoardRowKey(s) || !s.teacherGrade"
                  @click.stop="saveSubmissionGrade(s, null)"
                >
                  清除
                </button>
              </div>
            </div>
            <button class="btn-preview" @click="openSubmissionDetail(s)">查看详情</button>
          </div>
        </div>
        <div v-if="submissionBoard.detailLoading" class="empty-questions">正在加载作答详情...</div>
        <div v-else-if="submissionBoard.detail" class="submission-detail">
          <h4>
            {{ submissionBoard.detail.student.studentName }}（{{ submissionBoard.detail.student.studentNo }}）
            · 逐题对照
            <span v-if="submissionBoard.detail.student.submitted">
              · 个人正确率：{{
                submissionBoard.detail.student.accuracyPercent != null
                  ? `${submissionBoard.detail.student.accuracyPercent}%`
                  : "—"
              }}
            </span>
            <span v-if="submissionBoard.detail.student.submitted">
              · 评级：{{ submissionBoard.detail.student.teacherGrade || "—" }}
            </span>
          </h4>
          <div
            v-if="submissionBoard.detail.student.submitted"
            class="submission-detail-grade"
          >
            <span class="submission-grade-label">调整评级</span>
            <button
              v-for="g in gradeLetters"
              :key="`dg_${g}`"
              type="button"
              class="submission-grade-pill"
              :class="{
                active: submissionBoard.detail.student.teacherGrade === g,
                disabled: submissionDetailGradeSaving,
              }"
              :disabled="submissionDetailGradeSaving"
              @click="saveSubmissionDetailGrade(g)"
            >
              {{ g }}
            </button>
            <button
              type="button"
              class="submission-grade-clear"
              :disabled="submissionDetailGradeSaving || !submissionBoard.detail.student.teacherGrade"
              @click="saveSubmissionDetailGrade(null)"
            >
              清除
            </button>
          </div>
          <div
            v-for="(q, idx) in submissionBoard.detail.questions"
            :key="`d_${idx}`"
            class="question-item"
          >
            <div class="question-main">
              <div class="question-header">
                <span class="question-type-badge">第 {{ idx + 1 }} 题</span>
                <span class="question-score">{{ q.type || "题目" }}</span>
              </div>
              <div class="question-content">{{ q.content }}</div>
              <div class="question-answer">学生答案：{{ findStudentAnswer(idx) || "（未作答）" }}</div>
              <div class="question-answer">参考答案：{{ q.answer || "（无）" }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 发布成功 Toast -->
    <div v-if="toast.show" class="toast-message" :class="toast.type">
      <i :class="toast.icon"></i> {{ toast.text }}
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from "vue";
import {
  getCurrentUser,
  publishHomework,
  fetchSigninClasses,
  fetchTeacherHomeworkSubmissions,
  fetchTeacherHomeworkSubmissionDetail,
  setTeacherHomeworkGrade,
} from "@/utils/api";
import { formatSubmitTimeForDisplay } from "@/utils/formatSubmitTime";

// ---------- 题型定义（小学课堂）----------
const questionTypes = [
  { value: "choice", label: "选择题", icon: "fa-regular fa-circle-check" },
  { value: "fill", label: "填空题", icon: "fa-regular fa-square" },
  { value: "essay", label: "解答题", icon: "fa-regular fa-pen-to-square" },
];

const getTypeLabel = (type) => {
  const map = { choice: "选择题", fill: "填空题", essay: "解答题" };
  return map[type] || type;
};
const getTypeIcon = (type) => {
  const map = {
    choice: "fa-regular fa-circle-check",
    fill: "fa-regular fa-square",
    essay: "fa-regular fa-pen-to-square",
  };
  return map[type] || "fa-regular fa-question";
};

// ---------- 简单的 LaTeX 渲染模拟（将 $...$ 替换为斜体，实际项目中可集成 KaTeX）----------
const formatMath = (text) => {
  if (!text) return "";
  return text.replace(
    /\$(.*?)\$/g,
    '<span style="font-style:italic;font-family: serif;">$1</span>'
  );
};

// ---------- 表单数据 ----------
const classOptions = ref([]);

const formData = reactive({
  classId: "",
  deadline: new Date(Date.now() + 86400000 * 3).toISOString().slice(0, 16),
  title: "",
  description: "",
});

onMounted(async () => {
  try {
    const { classes } = await fetchSigninClasses();
    classOptions.value = Array.isArray(classes) ? classes : [];
    if (classOptions.value.length && !formData.classId) {
      formData.classId = classOptions.value[0].classId;
    }
  } catch {
    classOptions.value = [];
  }
});

// ---------- 当前正在编辑的题目 ----------
const currentQuestionType = ref("choice");
const newQuestion = reactive({
  content: "",
  options: "",
  answer: "",
  score: 5,
});

// ---------- 已添加题目列表 ----------
const questionList = ref([]);
const submissionBoard = reactive({
  homeworkId: "",
  loading: false,
  list: [],
  detailLoading: false,
  detail: null,
});
const gradeLetters = ["A", "B", "C", "D"];
const gradeSavingKey = ref("");
const submissionDetailGradeSaving = ref(false);

const submissionBoardRowKey = (s) => `${s.studentNo}__${s.submitAccount || ""}`;

// 添加题目
const addQuestion = () => {
  if (!newQuestion.content.trim()) {
    showToast("error", "题目内容不能为空", "fa-circle-exclamation");
    return;
  }
  const question = {
    type: currentQuestionType.value,
    content: newQuestion.content,
    score: parseInt(newQuestion.score) || 5,
    answer: newQuestion.answer || "",
  };
  if (currentQuestionType.value === "choice") {
    if (!newQuestion.options.trim()) {
      showToast("error", "请填写选项", "fa-circle-exclamation");
      return;
    }
    question.options = newQuestion.options;
  }
  questionList.value.push(question);
  // 清空输入
  newQuestion.content = "";
  newQuestion.options = "";
  newQuestion.answer = "";
  newQuestion.score = 5;
  showToast("success", "题目已添加", "fa-check-circle");
};

// 删除题目
const removeQuestion = (index) => {
  questionList.value.splice(index, 1);
  showToast("info", "题目已移除", "fa-trash-can");
};

// 清空所有题目
const clearAll = () => {
  questionList.value = [];
  showToast("info", "已清空试卷", "fa-broom");
};

// 计算总分
const totalScore = computed(() => {
  return questionList.value.reduce((sum, q) => sum + (q.score || 0), 0);
});

// 格式化选项显示（换行转 html）
const formatOptions = (options) => {
  if (!options) return "";
  return options
    .split("\n")
    .map((line) => line.trim())
    .filter((line) => line)
    .join("<br>");
};

// ---------- AI 智能组题（小学数学专用）----------
const aiGenerate = () => {
  questionList.value.push({
    type: "choice",
    content: "小明有 24 支铅笔，平均分给 6 位同学，每位同学分到多少支？",
    options:
      "A. 3 支\nB. 4 支\nC. 5 支\nD. 6 支",
    answer: "B",
    score: 5,
  });
  questionList.value.push({
    type: "fill",
    content: "计算：36 ÷ 9 = ____。",
    answer: "4",
    score: 4,
  });
  questionList.value.push({
    type: "essay",
    content: "请用自己的话写出“爱护校园环境”可以做到的三件事。",
    answer: "示例：不乱扔垃圾、节约用水、爱护花草。",
    score: 8,
  });
  showToast("success", "AI 已生成3道小学题", "fa-robot");
};

// ---------- 预览试卷（模拟）----------
const previewPaper = () => {
  if (questionList.value.length === 0) {
    showToast("error", "试卷为空，无法预览", "fa-circle-exclamation");
    return;
  }
  alert(
    `模拟预览：当前试卷共 ${questionList.value.length} 题，总分 ${totalScore.value} 分`
  );
};

// ---------- 发布任务（真实接口）----------
const publishTask = async () => {
  if (!formData.title.trim()) {
    showToast("error", "请填写任务标题", "fa-circle-exclamation");
    return;
  }
  if (!formData.classId) {
    showToast("error", "请先创建或选择班级", "fa-circle-exclamation");
    return;
  }
  if (questionList.value.length === 0) {
    showToast("error", "请至少添加一道题目", "fa-circle-exclamation");
    return;
  }
  const user = getCurrentUser();
  if (!user || user.role !== "teacher") {
    showToast("error", "请先以教师身份登录", "fa-circle-exclamation");
    return;
  }

  try {
    const ret = await publishHomework({
      classId: formData.classId,
      deadline: formData.deadline,
      title: formData.title,
      description: formData.description,
      questions: questionList.value,
    });
    submissionBoard.homeworkId = ret?.item?.id || "";
    if (submissionBoard.homeworkId) {
      await loadSubmissionBoard();
    }
    const clsLabel =
      classOptions.value.find((c) => c.classId === formData.classId)?.className || "班级";
    showToast("success", `任务已发布至 ${clsLabel}！`, "fa-paper-plane");
    formData.title = "";
    formData.description = "";
    questionList.value = [];
  } catch (err) {
    showToast("error", err.message || "发布失败", "fa-circle-exclamation");
  }
};

const loadSubmissionBoard = async () => {
  if (!submissionBoard.homeworkId) return;
  submissionBoard.loading = true;
  submissionBoard.detail = null;
  try {
    const data = await fetchTeacherHomeworkSubmissions(submissionBoard.homeworkId);
    submissionBoard.list = data.list || [];
  } catch (err) {
    showToast("error", err.message || "获取学生作答情况失败", "fa-circle-exclamation");
  } finally {
    submissionBoard.loading = false;
  }
};

const openSubmissionDetail = async (student) => {
  if (!submissionBoard.homeworkId || !student?.studentNo) return;
  submissionBoard.detailLoading = true;
  try {
    const data = await fetchTeacherHomeworkSubmissionDetail(
      submissionBoard.homeworkId,
      student.studentNo
    );
    submissionBoard.detail = data;
  } catch (err) {
    showToast("error", err.message || "获取作答详情失败", "fa-circle-exclamation");
  } finally {
    submissionBoard.detailLoading = false;
  }
};

const patchSubmissionBoardGrade = (studentNo, teacherGrade) => {
  const row = submissionBoard.list.find((x) => String(x.studentNo) === String(studentNo));
  if (row) row.teacherGrade = teacherGrade;
};

const saveSubmissionGrade = async (s, letter) => {
  if (!submissionBoard.homeworkId) return;
  const key = submissionBoardRowKey(s);
  gradeSavingKey.value = key;
  try {
    const { teacherGrade } = await setTeacherHomeworkGrade({
      homeworkId: submissionBoard.homeworkId,
      studentNo: s.studentNo,
      grade: letter == null ? "" : letter,
    });
    s.teacherGrade = teacherGrade ?? null;
    patchSubmissionBoardGrade(s.studentNo, teacherGrade ?? null);
    if (
      submissionBoard.detail?.student &&
      String(submissionBoard.detail.student.studentNo) === String(s.studentNo)
    ) {
      submissionBoard.detail.student.teacherGrade = teacherGrade ?? null;
    }
    showToast("success", teacherGrade ? `已设为 ${teacherGrade}` : "已清除评级", "fa-check");
  } catch (err) {
    showToast("error", err.message || "保存评级失败", "fa-circle-exclamation");
  } finally {
    gradeSavingKey.value = "";
  }
};

const saveSubmissionDetailGrade = async (letter) => {
  if (!submissionBoard.homeworkId || !submissionBoard.detail?.student) return;
  const sn = submissionBoard.detail.student.studentNo;
  submissionDetailGradeSaving.value = true;
  try {
    const { teacherGrade } = await setTeacherHomeworkGrade({
      homeworkId: submissionBoard.homeworkId,
      studentNo: sn,
      grade: letter == null ? "" : letter,
    });
    submissionBoard.detail.student.teacherGrade = teacherGrade ?? null;
    patchSubmissionBoardGrade(sn, teacherGrade ?? null);
    showToast("success", teacherGrade ? `已设为 ${teacherGrade}` : "已清除评级", "fa-check");
  } catch (err) {
    showToast("error", err.message || "保存评级失败", "fa-circle-exclamation");
  } finally {
    submissionDetailGradeSaving.value = false;
  }
};

const findStudentAnswer = (idx) => {
  const arr = submissionBoard.detail?.answers || [];
  const hit = arr.find((x) => Number(x?.index) === idx);
  return String(hit?.value || "").trim();
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
.publish-task-page {
  padding: 8px 0;
}

/* 页面头部（与教师端其他页面统一） */
.page-header {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 32px;
  flex-wrap: wrap;

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
}

/* 卡片通用样式 */
.form-card,
.question-list-card {
  background: white;
  border-radius: 32px;
  padding: 32px;
  box-shadow: 0 12px 30px rgba(0, 20, 40, 0.04);
  border: 1px solid rgba(30, 109, 242, 0.1);
  margin-bottom: 32px;
}

.submission-detail {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px dashed #d7e3f3;
}

.submission-grade-row,
.submission-detail-grade {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
}
.submission-detail-grade {
  margin-bottom: 12px;
  padding: 8px 10px;
  background: #f5f9ff;
  border-radius: 10px;
  border: 1px solid #dce8f7;
}
.submission-grade-label {
  font-size: 12px;
  color: #5d7c9b;
  margin-right: 4px;
}
.submission-grade-pill {
  min-width: 30px;
  height: 28px;
  padding: 0 8px;
  border-radius: 8px;
  border: 1px solid #b9d2f5;
  background: #fff;
  color: #0b2b4a;
  font-weight: 600;
  cursor: pointer;
  font-size: 12px;
}
.submission-grade-pill:hover:not(:disabled) {
  border-color: #1e6df2;
  color: #1e6df2;
}
.submission-grade-pill.active {
  background: linear-gradient(145deg, #1e6df2, #0a4bb0);
  border-color: #1e6df2;
  color: #fff;
}
.submission-grade-pill.disabled,
.submission-grade-pill:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.submission-grade-clear {
  font-size: 11px;
  padding: 4px 8px;
  border-radius: 8px;
  border: 1px dashed #c5d4e8;
  background: #fff;
  color: #5d7c9b;
  cursor: pointer;
}
.submission-grade-clear:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

/* 表单分区 */
.form-section {
  margin-bottom: 32px;
  &:last-child {
    margin-bottom: 0;
  }
}
.section-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
  i {
    color: #1e6df2;
    font-size: 20px;
  }
  h3 {
    font-size: 18px;
    font-weight: 700;
    color: #0b2b4a;
    margin: 0;
  }
  .section-badge {
    background: #e8f1ff;
    color: #1e6df2;
    padding: 4px 14px;
    border-radius: 40px;
    font-size: 12px;
    font-weight: 600;
  }
}
.form-row {
  display: flex;
  gap: 20px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}
.form-group {
  flex: 1;
  min-width: 200px;
  label {
    display: block;
    font-size: 14px;
    font-weight: 600;
    color: #0b2b4a;
    margin-bottom: 8px;
  }
  select,
  input,
  textarea {
    width: 100%;
    padding: 12px 16px;
    border: 2px solid #e2eaf2;
    border-radius: 16px;
    font-size: 15px;
    transition: 0.2s;
    background: #f8fcff;
    &:focus {
      border-color: #1e6df2;
      outline: none;
      box-shadow: 0 0 0 4px rgba(30, 109, 242, 0.1);
      background: white;
    }
  }
  &.full {
    flex: 0 0 100%;
  }
}

/* 题型切换标签 */
.question-type-tabs {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
  flex-wrap: wrap;
}
.type-btn {
  background: white;
  border: 2px solid #e2eaf2;
  color: #4a668a;
  padding: 10px 24px;
  border-radius: 40px;
  font-weight: 600;
  font-size: 15px;
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  transition: 0.2s;
  i {
    font-size: 16px;
  }
  &:hover {
    border-color: #1e6df2;
    color: #1e6df2;
  }
  &.active {
    background: #1e6df2;
    border-color: #1e6df2;
    color: white;
  }
}

/* 题目编辑器 */
.question-editor {
  background: #f8fcff;
  border-radius: 24px;
  padding: 24px;
  margin-bottom: 20px;
}
.options-group {
  margin-top: 16px;
  textarea {
    margin-bottom: 16px;
  }
}
.btn-add-question {
  background: #1e6df2;
  border: none;
  color: white;
  padding: 12px 32px;
  border-radius: 40px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  transition: 0.2s;
  margin-top: 8px;
  &:hover {
    background: #0a4bb0;
  }
}

/* AI 智能组题快捷入口 */
.ai-quick-entry {
  margin-top: 32px;
  padding: 20px;
  background: #e8f1ff;
  border-radius: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  i {
    color: #1e6df2;
    font-size: 22px;
  }
  span {
    color: #0b2b4a;
    font-weight: 600;
    flex: 1;
  }
  .btn-ai-generate {
    background: white;
    border: 2px solid #1e6df2;
    color: #1e6df2;
    padding: 10px 24px;
    border-radius: 40px;
    font-weight: 700;
    display: flex;
    align-items: center;
    gap: 8px;
    cursor: pointer;
    transition: 0.2s;
    &:hover {
      background: #1e6df2;
      color: white;
    }
  }
}

/* 题目列表卡片 */
.question-list-card {
  .list-header {
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
      gap: 10px;
      i {
        color: #1e6df2;
      }
    }
    .list-actions {
      display: flex;
      align-items: center;
      gap: 20px;
      .total-score {
        font-weight: 700;
        color: #1e6df2;
        background: #e8f1ff;
        padding: 6px 18px;
        border-radius: 40px;
      }
      .btn-clear {
        background: transparent;
        border: 2px solid #ff8a8a;
        color: #ff4d4d;
        padding: 8px 20px;
        border-radius: 40px;
        font-weight: 600;
        display: flex;
        align-items: center;
        gap: 8px;
        cursor: pointer;
        transition: 0.2s;
        &:hover {
          background: #ffe0e0;
        }
      }
    }
  }
}

.empty-questions {
  text-align: center;
  padding: 60px 20px;
  color: #5e7e9c;
  i {
    font-size: 48px;
    margin-bottom: 16px;
    color: #c0d4f0;
  }
  p {
    font-size: 16px;
  }
}

.question-items {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-bottom: 28px;
}

.question-item {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 20px;
  background: #f8fcff;
  border-radius: 20px;
  border: 1px solid rgba(30, 109, 242, 0.1);
  transition: 0.2s;
  &:hover {
    background: white;
    border-color: #1e6df2;
  }
  .question-index {
    width: 36px;
    height: 36px;
    background: #1e6df2;
    color: white;
    border-radius: 12px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-weight: 700;
    font-size: 16px;
    flex-shrink: 0;
  }
  .question-main {
    flex: 1;
  }
  .question-header {
    display: flex;
    align-items: center;
    gap: 16px;
    margin-bottom: 8px;
    .question-type-badge {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 4px 14px;
      border-radius: 40px;
      font-size: 13px;
      font-weight: 600;
      &.choice {
        background: #e8f1ff;
        color: #1e6df2;
      }
      &.fill {
        background: #fff2d6;
        color: #b9770e;
      }
      &.essay {
        background: #e6f7e6;
        color: #2eb85c;
      }
    }
    .question-score {
      font-weight: 700;
      color: #0b2b4a;
    }
  }
  .question-content {
    font-size: 16px;
    font-weight: 600;
    color: #0b2b4a;
    margin-bottom: 8px;
    line-height: 1.5;
    :deep(span) {
      font-style: italic;
      font-family: "Times New Roman", serif;
    }
  }
  .question-options {
    font-size: 14px;
    color: #4a668a;
    margin-bottom: 8px;
    line-height: 1.6;
  }
  .question-answer {
    font-size: 14px;
    color: #1e6df2;
    background: #e8f1ff;
    padding: 6px 16px;
    border-radius: 30px;
    display: inline-block;
    .answer-label {
      font-weight: 600;
    }
  }
  .btn-remove {
    background: transparent;
    border: none;
    color: #b8c9dd;
    font-size: 20px;
    padding: 8px;
    cursor: pointer;
    transition: 0.2s;
    border-radius: 50%;
    &:hover {
      color: #ff4d4d;
      background: #ffe0e0;
    }
  }
}

.publish-actions {
  display: flex;
  justify-content: flex-end;
  gap: 20px;
  margin-top: 20px;
  .btn-preview {
    background: transparent;
    border: 2px solid #1e6df2;
    color: #1e6df2;
    padding: 14px 32px;
    border-radius: 40px;
    font-weight: 700;
    display: flex;
    align-items: center;
    gap: 10px;
    cursor: pointer;
    transition: 0.2s;
    &:hover {
      background: #e8f1ff;
    }
  }
  .btn-publish {
    background: #1e6df2;
    border: none;
    color: white;
    padding: 14px 40px;
    border-radius: 40px;
    font-weight: 700;
    font-size: 16px;
    display: flex;
    align-items: center;
    gap: 12px;
    cursor: pointer;
    transition: 0.2s;
    box-shadow: 0 8px 20px rgba(30, 109, 242, 0.3);
    &:hover {
      background: #0a4bb0;
      transform: scale(1.02);
    }
  }
}

/* Toast 反馈（与签到页一致） */
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
  &.error {
    background: #ff4d4d;
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

/* 响应式 */
@media (max-width: 768px) {
  .form-row {
    flex-direction: column;
    gap: 16px;
  }
  .question-type-tabs {
    flex-direction: column;
    .type-btn {
      justify-content: center;
    }
  }
  .publish-actions {
    flex-direction: column;
  }
}
</style>
