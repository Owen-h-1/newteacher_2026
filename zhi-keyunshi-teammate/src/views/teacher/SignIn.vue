<template>
  <div class="sign-in-page">
    <div class="page-header">
      <div class="title-text">
        <h1>课堂签到</h1>
        <p class="subtitle">上传课堂照片，后续通过人脸识别模型自动判断学生签到情况</p>
      </div>
      <div class="class-selector-badge">
        <i class="fas fa-users"></i>
        <span>班级</span>
        <select v-model="currentClassId">
          <option v-for="c in classList" :key="c.classId" :value="c.classId">{{ c.className }}</option>
        </select>
      </div>
    </div>

    <div class="control-card">
      <div class="photo-section">
        <div class="photo-uploader" @click="triggerPhotoInput">
          <input
            ref="photoInputRef"
            type="file"
            accept="image/*"
            style="display: none"
            @change="onSelectPhoto"
          />
          <i class="fas fa-camera"></i>
          <h3>{{ selectedPhotoName || "点击上传课堂照片" }}</h3>
          <p>支持 jpg / png / webp，后续将接入人脸识别大模型自动识别签到</p>
        </div>
        <div class="photo-actions">
          <button class="btn-upload-photo" :disabled="photoUploading || !selectedPhotoData" @click="submitPhotoForRecognition">
            <i :class="['fas', photoUploading ? 'fa-spinner fa-spin' : 'fa-cloud-upload-alt']"></i>
            {{ photoUploading ? "上传中..." : "上传并识别（预留）" }}
          </button>
          <div v-if="photoStatusText" class="photo-status">
            <i class="fas fa-info-circle"></i> {{ photoStatusText }}
          </div>
        </div>
      </div>
      <div class="stats-summary">
        <div class="stat-item">
          <span class="stat-label">应到</span>
          <span class="stat-number">{{ totalStudents }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">已到</span>
          <span class="stat-number signed">{{ signedCount }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">未到</span>
          <span class="stat-number absent">{{ totalStudents - signedCount }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">签到率</span>
          <span class="stat-percent">{{ signRate }}%</span>
        </div>
      </div>
    </div>

    <div class="sign-records">
      <div class="records-header">
        <h3><i class="fas fa-list-check"></i> 今日签到记录</h3>
        <button class="btn-manual-sign" @click="openManualModal">
          <i class="fas fa-user-plus"></i> 手动签到
        </button>
      </div>
      <div class="record-table">
        <div class="table-header">
          <span>学号</span>
          <span>姓名</span>
          <span>签到时间</span>
          <span>签到方式</span>
          <span>状态</span>
          <span>操作</span>
        </div>
        <div class="table-body">
          <div v-for="(record, idx) in signRecords" :key="idx" class="table-row">
            <span>{{ record.studentId }}</span>
            <span><i class="fas fa-user-graduate"></i> {{ record.name }}</span>
            <span>{{ record.time || "--" }}</span>
            <span>
              <span :class="['sign-method', record.methodClass]">
                <i :class="record.methodIcon"></i> {{ record.method }}
              </span>
            </span>
            <span>
              <span :class="['status-badge', record.statusClass]">
                <i :class="record.statusIcon"></i> {{ record.status }}
              </span>
            </span>
            <span>
              <button v-if="!record.time" class="btn-mark" @click="markSign(record)">
                <i class="fas fa-check"></i> 标记签到
              </button>
              <button v-else class="btn-undo" @click="undoSign(record)">
                <i class="fas fa-undo-alt"></i> 撤销
              </button>
            </span>
          </div>
        </div>
      </div>
      <div class="table-footer">
        <span class="total-info"
          >共 {{ totalStudents }} 人，已签到 {{ signedCount }} 人</span
        >
        <button class="btn-export" @click="exportRecords">
          <i class="fas fa-download"></i> 导出签到表
        </button>
      </div>
    </div>

    <div
      v-if="showManualModal"
      class="modal-overlay"
      @click.self="showManualModal = false"
    >
      <div class="modal-card">
        <div class="modal-header">
          <h3><i class="fas fa-user-check"></i> 手动签到</h3>
          <button class="close-btn" @click="showManualModal = false">
            <i class="fas fa-times"></i>
          </button>
        </div>
        <div class="modal-body">
          <p>输入学生学号或姓名进行签到</p>
          <input
            v-model="manualSearch"
            type="text"
            placeholder="学号 / 姓名"
            class="modal-input"
          />
          <div class="search-results" v-if="manualSearch.length > 0">
            <div
              v-for="student in filteredStudents"
              :key="student.id"
              class="result-item"
            >
              <span>{{ student.id }} - {{ student.name }}</span>
              <button class="btn-sign" @click="manualSign(student)">签到</button>
            </div>
            <div v-if="filteredStudents.length === 0" class="no-result">
              未找到匹配学生
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="toast.show" class="toast-message" :class="toast.type">
      <i :class="toast.icon"></i> {{ toast.text }}
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted } from "vue";
import {
  fetchSigninClasses,
  fetchSigninRecords,
  markSignin,
  undoSignin,
  uploadSigninPhoto,
  downloadSigninExport,
} from "@/utils/api";

const classList = ref([]);
const currentClassId = ref("");

function enrichSignRecord(r) {
  const method = r.method && r.method !== "null" ? r.method : "--";
  let methodIcon = "";
  let methodClass = "";
  if (method === "扫码") {
    methodIcon = "fa-qrcode";
    methodClass = "qr";
  } else if (method === "数字人") {
    methodIcon = "fa-robot";
    methodClass = "ai";
  } else if (method === "手动") {
    methodIcon = "fa-pen";
    methodClass = "manual";
  }
  const signed = !!r.time;
  return {
    ...r,
    method,
    methodIcon,
    methodClass,
    status: signed ? "已签到" : "未签到",
    statusIcon: signed ? "fa-check-circle" : "fa-clock",
    statusClass: signed ? "signed" : "absent",
  };
}

const totalStudents = ref(0);
const signedCount = ref(0);
const signRecords = ref([]);

const loadRecords = async () => {
  if (!currentClassId.value) return;
  try {
    const data = await fetchSigninRecords(currentClassId.value);
    totalStudents.value = data.totalStudents ?? 0;
    signedCount.value = data.signedCount ?? 0;
    signRecords.value = (data.records || []).map(enrichSignRecord);
  } catch (e) {
    showToast("error", e.message || "加载签到数据失败", "fa-exclamation-circle");
  }
};

const loadClasses = async () => {
  try {
    const { classes } = await fetchSigninClasses();
    classList.value = Array.isArray(classes) ? classes : [];
    if (
      !currentClassId.value ||
      !classList.value.some((c) => c.classId === currentClassId.value)
    ) {
      currentClassId.value = classList.value[0]?.classId || "";
    } else {
      await loadRecords();
    }
  } catch (e) {
    showToast("error", e.message || "加载班级失败", "fa-exclamation-circle");
  }
};

watch(currentClassId, () => {
  loadRecords();
});

onMounted(loadClasses);

// 签到率计算
const signRate = computed(() => {
  if (totalStudents.value === 0) return 0;
  return Math.round((signedCount.value / totalStudents.value) * 100);
});

const photoInputRef = ref(null);
const selectedPhotoName = ref("");
const selectedPhotoData = ref("");
const photoUploading = ref(false);
const photoStatusText = ref("");

const triggerPhotoInput = () => {
  photoInputRef.value?.click();
};

const onSelectPhoto = async (e) => {
  const file = e.target.files?.[0];
  if (!file) return;
  selectedPhotoName.value = file.name;
  selectedPhotoData.value = await toBase64(file);
  photoStatusText.value = "照片已选择，可点击“上传并识别（预留）”";
};

const toBase64 = (file) =>
  new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result || ""));
    reader.onerror = reject;
    reader.readAsDataURL(file);
  });

const submitPhotoForRecognition = async () => {
  if (!selectedPhotoData.value) return;
  photoUploading.value = true;
  try {
    const data = await uploadSigninPhoto({
      classId: currentClassId.value,
      imageName: selectedPhotoName.value,
      imageData: selectedPhotoData.value,
    });
    photoStatusText.value = data?.message || "照片已上传";
    showToast("success", "课堂照片上传成功，待人脸识别模型接入", "fa-check-circle");
  } catch (e) {
    photoStatusText.value = e.message || "上传失败";
    showToast("error", e.message || "上传失败", "fa-exclamation-circle");
  } finally {
    photoUploading.value = false;
  }
};

// ---------- 手动签到相关 ----------
const showManualModal = ref(false);
const manualSearch = ref("");

// 所有学生列表（用于搜索）
const allStudents = computed(() => {
  return signRecords.value.map((r) => ({
    id: r.studentId,
    name: r.name,
    signed: !!r.time,
  }));
});

const filteredStudents = computed(() => {
  if (!manualSearch.value) return [];
  return allStudents.value.filter(
    (s) =>
      (s.id.includes(manualSearch.value) || s.name.includes(manualSearch.value)) &&
      !s.signed
  );
});

const openManualModal = () => {
  manualSearch.value = "";
  showManualModal.value = true;
};

const manualSign = async (student) => {
  try {
    await markSignin({
      classId: currentClassId.value,
      studentId: student.id,
      method: "手动",
    });
    await loadRecords();
    showManualModal.value = false;
    manualSearch.value = "";
    showToast("success", `已为 ${student.name} 签到`, "fa-check-circle");
  } catch (e) {
    showToast("error", e.message || "签到失败", "fa-exclamation-circle");
  }
};

const markSign = async (record) => {
  try {
    await markSignin({
      classId: currentClassId.value,
      studentId: record.studentId,
      method: "手动",
    });
    await loadRecords();
    showToast("success", `已标记 ${record.name} 签到`, "fa-check-circle");
  } catch (e) {
    showToast("error", e.message || "操作失败", "fa-exclamation-circle");
  }
};

const undoSign = async (record) => {
  try {
    await undoSignin({
      classId: currentClassId.value,
      studentId: record.studentId,
    });
    await loadRecords();
    showToast("info", `已撤销 ${record.name} 的签到`, "fa-undo-alt");
  } catch (e) {
    showToast("error", e.message || "撤销失败", "fa-exclamation-circle");
  }
};

// ---------- 导出 ----------
const exportRecords = async () => {
  try {
    const { blob, filename } = await downloadSigninExport(currentClassId.value);
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = filename || `${currentClassId.value || "班级"}-签到表.xlsx`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
    showToast("success", "签到表导出成功", "fa-download");
  } catch (e) {
    showToast("error", e.message || "导出失败", "fa-exclamation-circle");
  }
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
/* 样式与之前完全相同，无需修改 */
.sign-in-page {
  padding: 8px 0;
}
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
  .class-selector-badge {
    display: flex;
    align-items: center;
    gap: 10px;
    background: linear-gradient(145deg, #1e6df2, #0a4bb0);
    color: #fff;
    padding: 10px 18px;
    border-radius: 40px;
    box-shadow: 0 8px 20px rgba(30, 109, 242, 0.3);
    i {
      font-size: 14px;
    }
    span {
      font-weight: 700;
    }
    select {
      border: none;
      background: rgba(255, 255, 255, 0.18);
      font-size: 15px;
      font-weight: 600;
      color: #fff;
      padding: 0 12px;
      border-radius: 999px;
      min-width: 128px;
      outline: none;
      cursor: pointer;
      option {
        color: #0b2b4a;
      }
    }
  }
}
.control-card {
  background: white;
  border-radius: 32px;
  padding: 28px 32px;
  box-shadow: 0 12px 30px rgba(0, 20, 40, 0.04);
  border: 1px solid rgba(30, 109, 242, 0.1);
  margin-bottom: 32px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 32px;
}
.photo-section {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 14px;
  flex: 2;
  min-width: 300px;
}
.photo-uploader {
  width: 100%;
  border: 2px dashed #b9d2f4;
  border-radius: 18px;
  padding: 18px;
  background: #f8fbff;
  cursor: pointer;
  i {
    color: #1e6df2;
    font-size: 28px;
    margin-bottom: 8px;
  }
  h3 {
    margin: 0 0 6px;
    color: #0b2b4a;
    font-size: 18px;
  }
  p {
    margin: 0;
    color: #5e7e9c;
    font-size: 13px;
  }
}
.photo-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.btn-upload-photo {
  padding: 10px 18px;
  border-radius: 40px;
  border: none;
  background: #1e6df2;
  color: #fff;
  font-weight: 600;
  cursor: pointer;
}
.btn-upload-photo:disabled {
  background: #92b6f5;
  cursor: not-allowed;
}
.photo-status {
  color: #5e7e9c;
  font-size: 13px;
}
.stats-summary {
  flex: 1;
  min-width: 200px;
  background: #f8fcff;
  border-radius: 24px;
  padding: 20px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  .stat-item {
    display: flex;
    flex-direction: column;
    .stat-label {
      font-size: 13px;
      color: #5e7e9c;
      margin-bottom: 6px;
    }
    .stat-number {
      font-size: 30px;
      font-weight: 700;
      color: #0b2b4a;
      line-height: 1;
      &.signed {
        color: #2eb85c;
      }
      &.absent {
        color: #ff4d4d;
      }
    }
    .stat-percent {
      font-size: 30px;
      font-weight: 700;
      color: #1e6df2;
    }
  }
}
.sign-records {
  background: white;
  border-radius: 28px;
  padding: 28px;
  box-shadow: 0 12px 30px rgba(0, 20, 40, 0.04);
  border: 1px solid rgba(30, 109, 242, 0.1);
}
.records-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
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
  .btn-manual-sign {
    background: #1e6df2;
    border: none;
    color: white;
    padding: 10px 22px;
    border-radius: 40px;
    font-weight: 600;
    display: flex;
    align-items: center;
    gap: 8px;
    cursor: pointer;
    transition: 0.2s;
    &:hover {
      background: #0a4bb0;
    }
  }
}
.record-table {
  width: 100%;
  border-radius: 20px;
  overflow: hidden;
  border: 1px solid #edf2f7;
  margin-bottom: 20px;
}
.table-header {
  display: grid;
  grid-template-columns: 80px 1.2fr 1fr 1.2fr 1fr 1.2fr;
  background: #f0f7ff;
  padding: 16px 20px;
  font-weight: 700;
  color: #0b2b4a;
  font-size: 14px;
}
.table-row {
  display: grid;
  grid-template-columns: 80px 1.2fr 1fr 1.2fr 1fr 1.2fr;
  padding: 16px 20px;
  border-bottom: 1px solid #edf2f7;
  align-items: center;
  font-size: 14px;
  &:last-child {
    border-bottom: none;
  }
  &:hover {
    background: #fafcff;
  }
}
.sign-method {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 14px;
  border-radius: 40px;
  font-size: 13px;
  font-weight: 600;
  &.qr {
    background: #e8f1ff;
    color: #1e6df2;
  }
  &.ai {
    background: #e6f7e6;
    color: #2eb85c;
  }
  &.manual {
    background: #fff2d6;
    color: #b9770e;
  }
}
.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 14px;
  border-radius: 40px;
  font-size: 13px;
  font-weight: 600;
  &.signed {
    background: #e3f2e9;
    color: #1e7b4c;
  }
  &.absent {
    background: #ffe0e0;
    color: #cc3b3b;
  }
}
.btn-mark,
.btn-undo {
  padding: 6px 16px;
  border-radius: 40px;
  font-size: 13px;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  border: none;
  transition: 0.2s;
}
.btn-mark {
  background: #1e6df2;
  color: white;
  &:hover {
    background: #0a4bb0;
  }
}
.btn-undo {
  background: #f0f5fa;
  color: #4a668a;
  &:hover {
    background: #e2eaf2;
  }
}
.table-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  .total-info {
    color: #5e7e9c;
    font-size: 14px;
  }
  .btn-export {
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
    &:hover {
      background: #e8f1ff;
    }
  }
}
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
  width: 480px;
  background: white;
  border-radius: 32px;
  padding: 28px;
  box-shadow: 0 30px 60px rgba(0, 0, 0, 0.2);
}
.modal-header {
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
.modal-body {
  p {
    color: #4a668a;
    margin-bottom: 16px;
  }
  .modal-input {
    width: 100%;
    padding: 16px 20px;
    border: 2px solid #e2eaf2;
    border-radius: 16px;
    font-size: 15px;
    margin-bottom: 20px;
    &:focus {
      border-color: #1e6df2;
      outline: none;
      box-shadow: 0 0 0 4px rgba(30, 109, 242, 0.1);
    }
  }
  .search-results {
    max-height: 240px;
    overflow-y: auto;
    border-top: 1px solid #edf2f7;
    padding-top: 16px;
  }
  .result-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px 0;
    border-bottom: 1px solid #edf2f7;
    &:last-child {
      border-bottom: none;
    }
    .btn-sign {
      background: #1e6df2;
      color: white;
      border: none;
      padding: 6px 18px;
      border-radius: 40px;
      font-weight: 600;
      font-size: 13px;
      cursor: pointer;
      &:hover {
        background: #0a4bb0;
      }
    }
  }
  .no-result {
    text-align: center;
    color: #5e7e9c;
    padding: 20px 0;
  }
}
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
@media (max-width: 1000px) {
  .control-card {
    flex-direction: column;
    align-items: stretch;
  }
  .table-header,
  .table-row {
    grid-template-columns: 60px 1fr 0.8fr 1fr 0.8fr 1.2fr;
    font-size: 12px;
  }
}
</style>
