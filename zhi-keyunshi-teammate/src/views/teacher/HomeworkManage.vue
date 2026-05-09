<template>
  <div class="homework-manage-page">
    <div class="page-header">
      <div class="title-text">
        <h1>学生作业管理</h1>
        <p class="subtitle">查看已布置作业、提交进度与学生作答详情</p>
      </div>
      <div class="ai-badge-large"><i class="fas fa-clipboard-check"></i> 学生作业管理</div>
    </div>

    <div class="card">
      <div class="card-header">
        <h3>已布置作业</h3>
        <button class="btn" @click="loadHomeworks">刷新</button>
      </div>
      <div v-if="loading">加载中...</div>
      <div v-else-if="!homeworks.length" class="empty">暂无作业</div>
      <div v-else class="rows">
        <div
          v-for="hw in homeworks"
          :key="hw.id"
          class="row"
          :class="{ active: selectedHomeworkId === hw.id }"
          @click="selectHomework(hw)"
        >
          <div class="main">
            <div class="title">{{ hw.title }}</div>
            <div class="meta">{{ hw.className }} · 截止 {{ hw.deadline || "--" }}</div>
          </div>
          <div class="actions">
            <div class="stat">已交 {{ hw.submittedStudents }}/{{ hw.totalStudents }}（{{ hw.submitRate }}%）</div>
            <button class="btn btn-danger" @click.stop="removeHomework(hw)">删除作业</button>
          </div>
        </div>
      </div>
    </div>

    <div v-if="selectedHomeworkId" class="card">
      <div class="card-header">
        <h3>学生提交情况</h3>
      </div>
      <div v-if="submissionLoading">加载中...</div>
      <div v-else-if="!submissions.length" class="empty">暂无学生数据</div>
      <div v-else class="rows">
        <div v-for="s in submissions" :key="submissionRowKey(s)" class="row">
          <div class="main">
            <div class="title">{{ s.studentName }}（{{ s.studentNo }}）</div>
            <div class="meta">{{ formatSubmitTimeForDisplay(s.submitTime) || "未提交" }}</div>
            <div v-if="s.submitted" class="meta">
              个人正确率：{{ s.accuracyPercent != null ? `${s.accuracyPercent}%` : "—" }}
            </div>
            <div v-if="s.submitted" class="grade-row">
              <span class="grade-label">作业评级</span>
              <button
                v-for="g in gradeLetters"
                :key="g"
                type="button"
                class="grade-pill"
                :class="{ active: s.teacherGrade === g, disabled: gradeSavingKey === submissionRowKey(s) }"
                :disabled="gradeSavingKey === submissionRowKey(s)"
                @click.stop="saveGrade(s, g)"
              >
                {{ g }}
              </button>
              <button
                type="button"
                class="grade-clear"
                :disabled="gradeSavingKey === submissionRowKey(s) || !s.teacherGrade"
                @click.stop="saveGrade(s, null)"
              >
                清除
              </button>
            </div>
            <div v-if="s.submitAccount" class="meta">提交账号：{{ s.submitAccount }}</div>
          </div>
          <button class="btn" @click="openDetail(s)">
            {{ s.submitted ? "查看作答" : "查看题目" }}
          </button>
        </div>
      </div>
    </div>

    <div v-if="detail" class="card">
      <div class="card-header">
        <h3>
          作答详情：{{ detail.student.studentName }}（{{ detail.student.studentNo }}）
          <span v-if="detail.student.submitted" class="detail-accuracy">
            · 个人正确率：{{
              detail.student.accuracyPercent != null ? `${detail.student.accuracyPercent}%` : "—"
            }}
          </span>
          <span v-if="detail.student.submitted" class="detail-accuracy">
            · 评级：{{ detail.student.teacherGrade || "—" }}
          </span>
        </h3>
      </div>
      <div v-if="detail.student.submitted" class="detail-grade-bar">
        <span class="grade-label">调整评级</span>
        <button
          v-for="g in gradeLetters"
          :key="`d_${g}`"
          type="button"
          class="grade-pill"
          :class="{ active: detail.student.teacherGrade === g, disabled: detailGradeSaving }"
          :disabled="detailGradeSaving"
          @click="saveDetailGrade(g)"
        >
          {{ g }}
        </button>
        <button
          type="button"
          class="grade-clear"
          :disabled="detailGradeSaving || !detail.student.teacherGrade"
          @click="saveDetailGrade(null)"
        >
          清除
        </button>
      </div>
      <div class="rows">
        <div v-for="(q, idx) in detail.questions" :key="idx" class="row detail-row">
          <div class="main">
            <div class="title">第 {{ idx + 1 }} 题：{{ q.content }}</div>
            <div class="meta">学生答案：{{ studentAnswer(idx) || "（未作答）" }}</div>
            <div class="meta">参考答案：{{ q.answer || "（无）" }}</div>
          </div>
        </div>
      </div>
    </div>
    <div v-if="toast.show" class="toast">{{ toast.text }}</div>
  </div>
</template>

<script setup>
import { reactive, ref } from "vue";
import {
  fetchTeacherHomeworkManageList,
  fetchTeacherHomeworkSubmissions,
  fetchTeacherHomeworkSubmissionDetail,
  deleteTeacherHomework,
  setTeacherHomeworkGrade,
} from "@/utils/api";
import { formatSubmitTimeForDisplay } from "@/utils/formatSubmitTime";

const loading = ref(false);
const homeworks = ref([]);
const selectedHomeworkId = ref("");
const submissionLoading = ref(false);
const submissions = ref([]);
const detail = ref(null);
const toast = reactive({ show: false, text: "" });
const gradeLetters = ["A", "B", "C", "D"];
const gradeSavingKey = ref("");
const detailGradeSaving = ref(false);

const submissionRowKey = (s) => `${s.studentNo}__${s.submitAccount || ""}`;

const loadHomeworks = async () => {
  loading.value = true;
  try {
    const data = await fetchTeacherHomeworkManageList();
    homeworks.value = data.list || [];
    if (!selectedHomeworkId.value && homeworks.value.length) {
      await selectHomework(homeworks.value[0]);
    }
  } catch (e) {
    toast.show = true;
    toast.text = e.message || "加载作业失败";
  } finally {
    loading.value = false;
  }
};

const selectHomework = async (hw) => {
  selectedHomeworkId.value = hw.id;
  detail.value = null;
  submissionLoading.value = true;
  try {
    const data = await fetchTeacherHomeworkSubmissions(hw.id);
    submissions.value = data.list || [];
  } catch (e) {
    submissions.value = [];
    toast.show = true;
    toast.text = e.message || "加载提交情况失败";
  } finally {
    submissionLoading.value = false;
  }
};

const openDetail = async (s) => {
  try {
    const data = await fetchTeacherHomeworkSubmissionDetail(selectedHomeworkId.value, s.studentNo);
    detail.value = data;
  } catch (e) {
    toast.show = true;
    toast.text = e.message || "加载作答详情失败";
  }
};

const patchSubmissionGrade = (studentNo, teacherGrade) => {
  const row = submissions.value.find((x) => String(x.studentNo) === String(studentNo));
  if (row) row.teacherGrade = teacherGrade;
};

const saveGrade = async (s, letter) => {
  if (!selectedHomeworkId.value) return;
  const key = submissionRowKey(s);
  gradeSavingKey.value = key;
  try {
    const { teacherGrade } = await setTeacherHomeworkGrade({
      homeworkId: selectedHomeworkId.value,
      studentNo: s.studentNo,
      grade: letter == null ? "" : letter,
    });
    s.teacherGrade = teacherGrade;
    patchSubmissionGrade(s.studentNo, teacherGrade);
    if (detail.value?.student && String(detail.value.student.studentNo) === String(s.studentNo)) {
      detail.value.student.teacherGrade = teacherGrade;
    }
    toast.show = true;
    toast.text = teacherGrade ? `已设为 ${teacherGrade}` : "已清除评级";
  } catch (e) {
    toast.show = true;
    toast.text = e.message || "保存评级失败";
  } finally {
    gradeSavingKey.value = "";
  }
};

const saveDetailGrade = async (letter) => {
  if (!selectedHomeworkId.value || !detail.value?.student) return;
  const sn = detail.value.student.studentNo;
  detailGradeSaving.value = true;
  try {
    const { teacherGrade } = await setTeacherHomeworkGrade({
      homeworkId: selectedHomeworkId.value,
      studentNo: sn,
      grade: letter == null ? "" : letter,
    });
    detail.value.student.teacherGrade = teacherGrade;
    patchSubmissionGrade(sn, teacherGrade);
    toast.show = true;
    toast.text = teacherGrade ? `已设为 ${teacherGrade}` : "已清除评级";
  } catch (e) {
    toast.show = true;
    toast.text = e.message || "保存评级失败";
  } finally {
    detailGradeSaving.value = false;
  }
};

const removeHomework = async (hw) => {
  const ok = window.confirm(`确认删除作业「${hw.title}」吗？删除后学生端会同步移除。`);
  if (!ok) return;
  try {
    await deleteTeacherHomework(hw.id);
    toast.show = true;
    toast.text = "作业已删除";
    if (selectedHomeworkId.value === hw.id) {
      selectedHomeworkId.value = "";
      submissions.value = [];
      detail.value = null;
    }
    await loadHomeworks();
  } catch (e) {
    toast.show = true;
    toast.text = e.message || "删除作业失败";
  }
};

const studentAnswer = (idx) => {
  const arr = detail.value?.answers || [];
  const hit = arr.find((x) => Number(x?.index) === idx);
  return String(hit?.value || "");
};

loadHomeworks();
</script>

<style scoped>
.homework-manage-page { display: grid; gap: 14px; }
.page-header { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.page-header .title-text { flex: 1; min-width: 0; }
.page-header .ai-badge-large {
  background: linear-gradient(145deg, #1e6df2, #0a4bb0);
  color: #fff;
  padding: 12px 24px;
  border-radius: 40px;
  font-weight: 700;
  font-size: 16px;
  display: flex;
  align-items: center;
  gap: 10px;
  box-shadow: 0 8px 20px rgba(30, 109, 242, 0.3);
}
.card { background:#fff; border:1px solid #dce8f7; border-radius: 14px; padding: 12px; }
.card-header { display:flex; justify-content:space-between; align-items:center; margin-bottom: 8px; }
.rows { display:flex; flex-direction:column; gap:8px; }
.row { border:1px solid #e7eef9; border-radius: 10px; padding:10px; display:flex; justify-content:space-between; align-items:center; }
.row.active { border-color:#8db8ff; background:#f5f9ff; }
.title { font-weight: 600; color:#0b2b4a; }
.meta { color:#5d7c9b; font-size: 13px; margin-top:2px; }
.detail-accuracy { font-weight: 500; font-size: 0.85em; color: #5d7c9b; }
.detail-grade-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin: 0 0 12px;
  padding: 10px 12px;
  background: #f5f9ff;
  border-radius: 10px;
  border: 1px solid #dce8f7;
}
.grade-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
}
.grade-label {
  font-size: 12px;
  color: #5d7c9b;
  margin-right: 4px;
}
.grade-pill {
  min-width: 32px;
  height: 30px;
  padding: 0 8px;
  border-radius: 8px;
  border: 1px solid #b9d2f5;
  background: #fff;
  color: #0b2b4a;
  font-weight: 600;
  cursor: pointer;
  font-size: 13px;
}
.grade-pill:hover:not(:disabled) {
  border-color: #1e6df2;
  color: #1e6df2;
}
.grade-pill.active {
  background: linear-gradient(145deg, #1e6df2, #0a4bb0);
  border-color: #1e6df2;
  color: #fff;
}
.grade-pill.disabled,
.grade-pill:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.grade-clear {
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 8px;
  border: 1px dashed #c5d4e8;
  background: #fff;
  color: #5d7c9b;
  cursor: pointer;
}
.grade-clear:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.btn { border:1px solid #b9d2f5; background:#fff; border-radius: 8px; padding:6px 10px; cursor:pointer; }
.btn:disabled { opacity:.5; cursor:not-allowed; }
.actions { display:flex; align-items:center; gap:8px; }
.btn-danger { border-color:#f2b8b5; color:#b42318; }
.empty { color:#7b95ad; padding: 8px; }
.toast { position: fixed; right: 24px; bottom: 24px; background: #0b2b4a; color: #fff; padding: 8px 12px; border-radius: 8px; }
</style>
