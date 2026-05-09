<template>
  <div class="ppt-upload-page">
    <div class="page-header">
      <div class="title-text">
        <h1><i class="fas fa-file-powerpoint"></i> PPT上传</h1>
        <p class="subtitle">仅保留课件上传与管理功能</p>
      </div>
      <div class="ai-badge-large"><i class="fas fa-file-powerpoint"></i> PPT上传管理</div>
    </div>

    <div class="upload-section card">
      <div
        class="upload-area"
        :class="{ dragover: isDragover }"
        @dragenter.prevent="isDragover = true"
        @dragleave.prevent="isDragover = false"
        @dragover.prevent
        @drop.prevent="handleDrop"
        @click="triggerFileInput"
      >
        <input
          ref="fileInput"
          type="file"
          accept=".ppt,.pptx"
          style="display: none"
          @change="handleFileSelect"
        />
        <i class="fas fa-cloud-upload-alt"></i>
        <h3>拖拽PPT文件至此，或点击上传</h3>
        <p>支持 .ppt, .pptx 格式，单文件不超过50MB</p>
        <button class="btn-primary" @click.stop="triggerFileInput">
          <i class="fas fa-upload"></i> 选择文件
        </button>
      </div>

      <div v-if="uploadedFile" class="uploaded-info">
        <i class="fas fa-check-circle"></i>
        <span>{{ uploadedFile.name }} 上传成功</span>
        <button class="btn-text" @click="clearFile">重新上传</button>
      </div>
    </div>

    <div class="history-section card">
      <div class="history-header">
        <h2><i class="fas fa-clock-rotate-left"></i> PPT历史列表</h2>
        <button class="btn-refresh" @click="loadHistory">刷新</button>
      </div>
      <div v-if="!historyList.length" class="history-empty">暂无上传记录</div>
      <div v-else class="history-list">
        <div v-for="item in historyList" :key="item.id" class="history-item">
          <div class="item-main">
            <div class="item-name">{{ item.name }}</div>
            <div class="item-meta">上传时间：{{ formatTime(item.uploadedAt) }}</div>
          </div>
          <button class="btn-delete" @click="removeHistory(item.id)">删除</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { uploadTeacherPptAsset, fetchTeacherPptAssets, deleteTeacherPptAsset } from "@/utils/api";

const isDragover = ref(false);
const fileInput = ref(null);
const uploadedFile = ref(null);
const historyList = ref([]);

const triggerFileInput = () => {
  fileInput.value?.click();
};

const handleFileSelect = (e) => {
  const file = e.target.files[0];
  if (file) {
    processFile(file);
  }
};

const handleDrop = (e) => {
  isDragover.value = false;
  const file = e.dataTransfer.files[0];
  if (file) {
    processFile(file);
  }
};

const processFile = async (file) => {
  if (!file.name.match(/\.(ppt|pptx)$/i)) {
    alert("请上传PPT文件（.ppt 或 .pptx）");
    return;
  }
  if (file.size > 50 * 1024 * 1024) {
    alert("文件大小不能超过50MB");
    return;
  }

  try {
    await savePptAsset(file);
    uploadedFile.value = file;
    await loadHistory();
  } catch (e) {
    uploadedFile.value = null;
    alert(e.message || "上传失败，请稍后重试");
  }
};

const savePptAsset = async (file) => {
  const mockSlides = [
    `${file.name} · 课程导入`,
    `${file.name} · 核心知识点讲解`,
    `${file.name} · 例题演示`,
    `${file.name} · 课堂练习`,
    `${file.name} · 总结与作业`,
  ];
  await uploadTeacherPptAsset({
    name: String(file.name || "未命名课件"),
    slides: mockSlides,
  });
};

const clearFile = () => {
  uploadedFile.value = null;
  if (fileInput.value) {
    fileInput.value.value = "";
  }
};

const loadHistory = async () => {
  try {
    const data = await fetchTeacherPptAssets();
    historyList.value = Array.isArray(data?.list) ? data.list : [];
  } catch {
    historyList.value = [];
  }
};

const removeHistory = async (id) => {
  try {
    await deleteTeacherPptAsset(id);
    await loadHistory();
  } catch (e) {
    alert(e.message || "删除失败");
  }
};

const formatTime = (v) => {
  const d = new Date(v);
  if (Number.isNaN(d.getTime())) return "--";
  const p = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(
    d.getMinutes()
  )}`;
};

onMounted(loadHistory);
</script>

<style scoped lang="scss">
.ppt-upload-page {
  max-width: 1000px;
  margin: 0 auto;

  .page-header {
    display: flex;
    align-items: center;
    gap: 20px;
    flex-wrap: wrap;
    margin-bottom: 32px;
    .title-text {
      flex: 1;
      h1 {
        display: flex;
        align-items: center;
        gap: 12px;
        font-size: 28px;
        font-weight: 700;
        color: #0a3e6d;
        i {
          color: #d35230; // PPT 橙色系
        }
      }
      .subtitle {
        color: #5e7e9c;
        font-size: 16px;
        margin-top: 8px;
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

  .card {
    background: white;
    border-radius: 28px;
    padding: 32px;
    box-shadow: 0 12px 30px rgba(0, 20, 40, 0.06);
    border: 1px solid rgba(30, 109, 242, 0.1);
    margin-bottom: 28px;
  }

  .upload-section {
    .upload-area {
      border: 2px dashed #b8d6ff;
      border-radius: 24px;
      padding: 48px 20px;
      text-align: center;
      background: #f6fbff;
      transition: all 0.2s;
      cursor: pointer;

      i {
        font-size: 64px;
        color: #1e6df2;
        margin-bottom: 16px;
      }

      h3 {
        font-size: 20px;
        font-weight: 600;
        color: #0b2b4a;
        margin-bottom: 8px;
      }

      p {
        color: #6b8faa;
        margin-bottom: 24px;
      }

      &.dragover {
        border-color: #1e6df2;
        background: #e8f1ff;
        box-shadow: 0 0 0 4px rgba(30, 109, 242, 0.1);
      }
    }

    .uploaded-info {
      display: flex;
      align-items: center;
      gap: 12px;
      margin-top: 20px;
      padding: 16px;
      background: #e8f5e9;
      border-radius: 20px;
      color: #2e7d32;
      i {
        font-size: 24px;
      }
      .btn-text {
        background: transparent;
        border: none;
        color: #1e6df2;
        font-weight: 600;
        cursor: pointer;
        margin-left: auto;
        &:hover {
          text-decoration: underline;
        }
      }
    }
  }

  .history-section {
    .history-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 16px;
      h2 {
        margin: 0;
        font-size: 22px;
        color: #0a3e6d;
        display: flex;
        align-items: center;
        gap: 10px;
      }
      .btn-refresh {
        border: 1px solid #1e6df2;
        background: #fff;
        color: #1e6df2;
        border-radius: 10px;
        padding: 8px 14px;
        font-weight: 600;
        cursor: pointer;
        &:hover {
          background: #edf5ff;
        }
      }
    }
    .history-empty {
      padding: 20px 8px;
      color: #6f8aa6;
    }
    .history-list {
      display: flex;
      flex-direction: column;
      gap: 10px;
    }
    .history-item {
      display: flex;
      align-items: center;
      justify-content: space-between;
      border: 1px solid #e4edf8;
      border-radius: 12px;
      padding: 10px 12px;
      background: #f9fcff;
      .item-main {
        display: flex;
        flex-direction: column;
        gap: 4px;
      }
      .item-name {
        color: #0b2b4a;
        font-weight: 700;
      }
      .item-meta {
        color: #6c88a3;
        font-size: 13px;
      }
      .btn-delete {
        border: 1px solid #f1c3c3;
        background: #fff4f4;
        color: #b42318;
        border-radius: 10px;
        padding: 6px 12px;
        cursor: pointer;
        font-weight: 600;
      }
    }
  }
}
</style>
