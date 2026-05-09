<template>
  <div class="practice-page">
    <div class="practice-panel">
      <div class="practice-main">
        <div class="account-bar" :class="{ warn: serverUser.role !== 'student' }">
          当前提交身份：{{ serverUser.username || "--" }}（{{ serverUser.role || "--" }}）
        </div>
        <div class="practice-header">
          <h3>{{ practiceHomework?.title || "作业作答" }}</h3>
          <button class="btn-cancel" @click="router.push('/student/homework')">返回作业列表</button>
        </div>
        <div v-if="isSubmitted && practiceHomework?.teacherGrade" class="teacher-grade-bar">
          教师评级：<strong>{{ practiceHomework.teacherGrade }}</strong>
        </div>
        <div v-if="loading" class="empty-state">
          <i class="fas fa-spinner fa-spin"></i>
          <p>正在加载题目...</p>
        </div>
        <div v-else-if="!practiceHomework?.questions?.length" class="empty-state">
          <i class="fas fa-file-circle-exclamation"></i>
          <p>该作业暂无题目数据（通常是旧作业）。请联系老师重新发布。</p>
        </div>
        <div v-else class="question-sheet">
          <div v-for="(q, idx) in practiceHomework.questions" :key="idx" class="question-row">
            <div class="question-title">{{ idx + 1 }}. {{ q.content }}</div>
            <div v-if="showCorrectAnswer" class="judge-chip" :class="judgeClass(q, idx)">
              {{ judgeLabel(q, idx) }}
            </div>
            <div v-if="q.type === 'choice'" class="question-options choice-options">
              <label v-for="opt in parseChoiceOptions(q.options)" :key="opt.key" class="choice-item">
                <input
                  type="radio"
                  :name="`choice_${idx}`"
                  :value="opt.key"
                  :checked="getAnswerValue(idx) === opt.key"
                  @change="setAnswerValue(idx, opt.key)"
                />
                <span>{{ opt.text }}</span>
              </label>
            </div>
            <input
              v-else-if="q.type === 'fill'"
              class="answer-input input-line"
              :value="getAnswerValue(idx)"
              placeholder="填写答案"
              @input="setAnswerValue(idx, $event.target.value)"
            />
            <textarea
              v-else
              class="answer-input"
              :value="getAnswerValue(idx)"
              placeholder="在这里输入你的答案"
              @input="setAnswerValue(idx, $event.target.value)"
            ></textarea>
            <div v-if="showCorrectAnswer && q.answer" class="answer-ref">参考答案：{{ q.answer }}</div>
          </div>
          <div class="practice-actions">
            <button class="btn-do math" @click="confirmVisible = true">
              <i class="fas fa-paper-plane"></i> 提交作答
            </button>
            <button class="btn-review" :disabled="!isSubmitted" @click="showCorrectAnswer = true">
              <i class="fas fa-eye"></i> 查看答案
            </button>
          </div>
        </div>
      </div>
    </div>
    <div v-if="toast.show" class="toast-message" :class="toast.type">
      <i :class="toast.icon"></i> {{ toast.text }}
    </div>
    <div v-if="confirmVisible" class="modal-overlay" @click.self="confirmVisible = false">
      <div class="confirm-card">
        <h4>确认提交作答？</h4>
        <p>提交后老师可以查看你的作答内容，你仍可查看答案。</p>
        <div class="confirm-actions">
          <button class="btn-cancel" @click="confirmVisible = false">再检查一下</button>
          <button class="btn-do math" @click="doSubmit">确认提交</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { fetchHomeworkDetail, submitHomeworkAnswers, getCurrentUser, fetchAuthMe } from "@/utils/api";

const route = useRoute();
const router = useRouter();
const loading = ref(false);
const practiceHomework = ref(null);
const practiceAnswers = ref([]);
const showCorrectAnswer = ref(false);
const isSubmitted = ref(false);
const confirmVisible = ref(false);
const serverUser = reactive({ username: "", role: "" });

const loadDetail = async () => {
  loading.value = true;
  try {
    const data = await fetchHomeworkDetail(route.params.id);
    const detail = data.item || {};
    practiceHomework.value = {
      ...detail,
      questions: Array.isArray(detail.questions) ? detail.questions : [],
    };
    const existingAnswers = Array.isArray(detail.answers) ? detail.answers : [];
    practiceAnswers.value = practiceHomework.value.questions.map((_, idx) => ({
      index: idx,
      value: String(existingAnswers[idx]?.value || ""),
    }));
    isSubmitted.value = detail.status === "completed";
    showCorrectAnswer.value = detail.status === "completed";
  } catch (err) {
    showToast("info", err.message || "打开作业失败", "fa-circle-exclamation");
  } finally {
    loading.value = false;
  }
};

const loadAuthIdentity = async () => {
  try {
    const data = await fetchAuthMe();
    serverUser.username = data?.user?.username || "";
    serverUser.role = data?.user?.role || "";
  } catch {
    serverUser.username = "";
    serverUser.role = "";
  }
};

const getAnswerValue = (idx) => {
  const hit = practiceAnswers.value.find((x) => x.index === idx);
  return hit ? hit.value : "";
};
const setAnswerValue = (idx, val) => {
  const hit = practiceAnswers.value.find((x) => x.index === idx);
  if (hit) hit.value = String(val || "");
  else practiceAnswers.value.push({ index: idx, value: String(val || "") });
};
const normalizeText = (v) => String(v || "").trim().replace(/\s+/g, "").toLowerCase();
const parseChoiceOptions = (options) =>
  String(options || "")
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line, idx) => {
      const m = line.match(/^([A-Za-z])[.、\s]*(.*)$/);
      if (m) return { key: m[1].toUpperCase(), text: line };
      return { key: String.fromCharCode(65 + idx), text: line };
    });
const isCorrect = (q, idx) => {
  const ans = normalizeText(getAnswerValue(idx));
  const ref = String(q?.answer || "").trim();
  if (!ans || !ref) return false;
  if (q?.type === "essay") return false;
  if (q?.type === "choice") return ans === normalizeText(ref);
  const refs = ref.split("/").map((x) => normalizeText(x)).filter(Boolean);
  return refs.includes(ans);
};
const judgeLabel = (q, idx) => {
  const ans = normalizeText(getAnswerValue(idx));
  if (!ans) return "未作答";
  if (q?.type === "essay") return "已作答";
  return isCorrect(q, idx) ? "正确" : "需改进";
};
const judgeClass = (q, idx) => {
  const label = judgeLabel(q, idx);
  if (label === "正确") return "ok";
  if (label === "需改进") return "bad";
  return "empty";
};

const submitCurrentHomework = async () => {
  const item = practiceHomework.value;
  if (!item) return;
  const user = getCurrentUser();
  if (!user || user.role !== "student" || serverUser.role !== "student") {
    showToast("info", "请使用学生账号提交作业", "fa-circle-exclamation");
    return;
  }
  try {
    await submitHomeworkAnswers(item.id, practiceAnswers.value);
    isSubmitted.value = true;
    showCorrectAnswer.value = true;
    const q = new URLSearchParams({
      tab: "completed",
      completedId: String(item.id || ""),
    }).toString();
    const base = String(import.meta.env.BASE_URL || "/");
    const normalizedBase = base.endsWith("/") ? base : `${base}/`;
    const target = `${normalizedBase}student/homework?${q}`;
    window.location.assign(target);
  } catch (err) {
    showToast("info", err.message || "提交失败", "fa-circle-exclamation");
  }
};

const doSubmit = async () => {
  confirmVisible.value = false;
  await submitCurrentHomework();
};

const toast = reactive({ show: false, text: "", icon: "", type: "" });
let toastTimer = null;
const showToast = (type, text, icon) => {
  if (toastTimer) clearTimeout(toastTimer);
  toast.show = true;
  toast.text = text;
  toast.icon = icon;
  toast.type = type;
  toastTimer = setTimeout(() => (toast.show = false), 3000);
};

onMounted(loadDetail);
onMounted(loadAuthIdentity);
onMounted(() => {
  const user = getCurrentUser();
  if (!user || user.role !== "student") {
    showToast("info", "当前不是学生账号，无法提交作业", "fa-circle-exclamation");
  }
  if (String(route.query.showAnswers || "") === "1") {
    showCorrectAnswer.value = true;
  }
});
</script>

<style scoped lang="scss">
.practice-page { padding: 8px 0; }
.practice-panel { display: block; }
.practice-main { background: #fff; border: 1px solid rgba(30,109,242,.14); border-radius: 14px; padding: 14px; }
.account-bar { margin-bottom: 10px; padding: 8px 10px; border-radius: 8px; background: #edf5ff; color: #24517f; font-size: 13px; }
.account-bar.warn { background: #fff1e8; color: #9a4f00; }
.practice-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.teacher-grade-bar {
  margin-bottom: 10px;
  padding: 8px 12px;
  border-radius: 10px;
  background: linear-gradient(90deg, #e8f1ff, #f5f9ff);
  border: 1px solid #c5d8f5;
  color: #24517f;
  font-size: 14px;
}
.teacher-grade-bar strong {
  color: #1e6df2;
  font-size: 18px;
  margin-left: 4px;
}
.question-sheet { display: flex; flex-direction: column; gap: 10px; }
.question-row { border: 1px solid #e8eef7; border-radius: 10px; padding: 10px; }
.question-title { font-weight: 600; color: #0b2b4a; margin-bottom: 6px; }
.question-options { white-space: pre-wrap; color: #577695; margin-bottom: 6px; }
.choice-options { display: flex; flex-direction: column; gap: 6px; }
.choice-item { display: flex; gap: 8px; align-items: center; background: #f7faff; border: 1px solid #e2ebf8; border-radius: 8px; padding: 6px 8px; }
.answer-input { width: 100%; min-height: 72px; border: 1px solid #d7e3f3; border-radius: 8px; padding: 8px 10px; }
.answer-input.input-line { min-height: 0; height: 38px; }
.answer-ref { margin-top: 8px; color: #2e9d58; background: #ebfaef; border-radius: 8px; padding: 6px 8px; }
.practice-actions { display: flex; gap: 10px; margin-top: 8px; }
.btn-review:disabled { opacity: .55; cursor: not-allowed; }
.judge-chip { display: inline-block; font-size: 12px; border-radius: 999px; padding: 2px 8px; margin-bottom: 6px; }
.judge-chip.ok { color: #1e8e4d; background: #e9f8ef; }
.judge-chip.bad { color: #b2600b; background: #fff5e7; }
.judge-chip.empty { color: #6c829b; background: #eef3f9; }
.toast-message { position: fixed; right: 24px; bottom: 24px; z-index: 1000; }
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(8, 25, 45, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1200;
}
.confirm-card {
  width: min(420px, 92vw);
  background: #fff;
  border-radius: 14px;
  padding: 16px;
  border: 1px solid #d9e6f7;
}
.confirm-card h4 {
  margin: 0 0 8px;
  color: #0b2b4a;
}
.confirm-card p {
  margin: 0 0 12px;
  color: #587796;
}
.confirm-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
