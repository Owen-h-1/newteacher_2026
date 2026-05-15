<template>
  <div class="ai-courseware-page">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="title-icon">
        <i class="fas fa-robot"></i>
      </div>
      <div class="title-text">
        <h1>AI 课件生成</h1>
        <p class="subtitle">输入教学主题，AI 自动生成完整的 PPT 课件</p>
      </div>
      <div class="ai-badge-large"><i class="fas fa-magic"></i> AI 生成 · 极速</div>
    </div>

    <!-- 主题输入卡片 -->
    <div class="input-card">
      <div class="input-label">
        <i class="fas fa-lightbulb"></i>
        <span>教学主题 / 关键词</span>
      </div>
      <div class="input-wrapper">
        <input
          v-model="topic"
          type="text"
          placeholder="例如：两位数乘法、英语问候语、端午节习俗……"
          class="topic-input"
          @keyup.enter="generatePPT"
        />
        <button
          class="btn-generate"
          :disabled="isGenerating || !topic.trim()"
          @click="generatePPT"
        >
          <i
            :class="[
              'fas',
              isGenerating ? 'fa-spinner fa-spin' : 'fa-wand-magic-sparkles',
            ]"
          ></i>
          {{ isGenerating ? "AI 生成中..." : "AI 生成课件" }}
        </button>
      </div>
      <p class="input-hint">
        <i class="fas fa-info-circle"></i>
        支持学科：数学、语文、英语、科学、美术等，AI 将自动生成 5~8 页课件大纲及内容
      </p>
      <p v-if="statusMsg" class="status-msg">{{ statusMsg }}</p>
    </div>

    <!-- 生成结果展示区（仅生成后显示） -->
    <transition name="fade">
      <div v-if="generated" class="result-section">
        <div class="result-header">
          <div class="result-title">
            <i class="fas fa-file-powerpoint"></i>
            <h2>《{{ generatedTopic }}》AI 课件预览</h2>
          </div>
          <div class="result-actions">
            <button class="btn-outline" @click="downloadPPT">
              <i class="fas fa-download"></i> 下载 PPT
            </button>
          </div>
        </div>

        <!-- 幻灯片缩略图网格 -->
        <div class="slides-grid">
          <div
            v-for="(slide, index) in generatedSlides"
            :key="index"
            class="slide-card"
            @click="previewSlide(slide)"
          >
            <div class="slide-thumb" :style="{ background: slide.bgColor }">
              <span class="slide-number">{{ index + 1 }}</span>
              <i :class="slide.icon" class="slide-icon"></i>
            </div>
            <div class="slide-info">
              <h4>{{ slide.title }}</h4>
              <p>{{ slide.desc }}</p>
            </div>
          </div>
        </div>

        <!-- AI 总结建议 -->
        <div class="ai-summary">
          <div class="summary-header">
            <i class="fas fa-robot" style="color: #1e6df2"></i>
            <span>AI 教学设计建议</span>
          </div>
          <p class="summary-text">
            {{ summaryText || "本课件已生成，建议根据班级学习节奏调整课堂互动与练习难度。" }}
          </p>
        </div>
      </div>
    </transition>

    <!-- 未生成时的占位提示 -->
    <div v-if="!generated" class="empty-state">
      <div class="empty-icon">
        <i class="fas fa-file-powerpoint"></i>
      </div>
      <h3>等待主题输入</h3>
      <p>输入教学主题，AI 将为你快速生成一套完整的 PPT 课件</p>
      <div class="example-topics">
        <span class="example-tag" @click="setExampleTopic('两位数乘法的计算方法')"
          >两位数乘法</span
        >
        <span class="example-tag" @click="setExampleTopic('英语日常问候语')">英语问候语</span>
        <span class="example-tag" @click="setExampleTopic('端午节的来历与习俗')"
          >端午节习俗</span
        >
        <span class="example-tag" @click="setExampleTopic('植物生长观察记录')">植物观察</span>
      </div>
    </div>

    <div v-if="previewingSlide" class="preview-mask" @click.self="closePreview">
      <div class="preview-panel">
        <div class="preview-head">
          <h3>{{ previewingSlide.title }}</h3>
          <button class="btn-close-preview" @click="closePreview">关闭</button>
        </div>
        <div class="preview-body">
          <div class="preview-card" :style="{ background: previewingSlide.bgColor }">
            <i :class="previewingSlide.icon"></i>
            <span>{{ previewingSlide.title }}</span>
          </div>
          <p>{{ previewingSlide.desc }}</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from "vue";
import { downloadTeacherCoursewarePpt, generateTeacherCourseware } from "@/utils/api";

// 主题输入
const topic = ref("");
const isGenerating = ref(false);
const generated = ref(false);
const generatedTopic = ref("");
const generatedSlides = ref([]);
const summaryText = ref("");
const statusMsg = ref("");
const previewingSlide = ref(null);

// 示例快捷填充
const setExampleTopic = (example) => {
  topic.value = example;
};

// 调用后端接口生成 PPT
const generatePPT = async () => {
  if (!topic.value.trim()) return;
  isGenerating.value = true;
  generated.value = false;
  statusMsg.value = "";
  try {
    const data = await generateTeacherCourseware({ topic: topic.value.trim() });
    generatedTopic.value = data?.topic || topic.value.trim();
    generatedSlides.value = Array.isArray(data?.slides) ? data.slides : [];
    summaryText.value = String(data?.summary || "");
    isGenerating.value = false;
    generated.value = true;
  } catch (e) {
    generated.value = false;
    isGenerating.value = false;
    statusMsg.value = e.message || "AI 课件生成失败";
  }
};

const downloadPPT = async () => {
  if (!generatedSlides.value.length) return;
  try {
    const { blob, filename } = await downloadTeacherCoursewarePpt({
      topic: generatedTopic.value,
      slides: generatedSlides.value,
    });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = filename || `${generatedTopic.value || "ai-courseware"}.pptx`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
  } catch (e) {
    statusMsg.value = e.message || "下载失败，请稍后重试";
  }
};

const previewSlide = (slide) => {
  previewingSlide.value = slide;
};

const closePreview = () => {
  previewingSlide.value = null;
};
</script>

<style scoped lang="scss">
.ai-courseware-page {
  padding: 8px 0;
}

/* 页面头部（复用数据看板风格） */
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
}

/* 输入卡片 */
.input-card {
  background: white;
  border-radius: 32px;
  padding: 32px;
  box-shadow: 0 12px 30px rgba(0, 20, 40, 0.04);
  border: 1px solid rgba(30, 109, 242, 0.1);
  margin-bottom: 32px;

  .input-label {
    display: flex;
    align-items: center;
    gap: 10px;
    color: #0b2b4a;
    font-weight: 600;
    margin-bottom: 20px;
    i {
      color: #1e6df2;
      font-size: 20px;
    }
  }

  .input-wrapper {
    display: flex;
    gap: 16px;
    align-items: center;
    flex-wrap: wrap;
  }

  .topic-input {
    flex: 1;
    min-width: 300px;
    padding: 16px 24px;
    border: 2px solid #e2eaf2;
    border-radius: 60px;
    font-size: 16px;
    transition: 0.2s;
    background: #f8fcff;
    &:focus {
      border-color: #1e6df2;
      outline: none;
      box-shadow: 0 0 0 4px rgba(30, 109, 242, 0.1);
      background: white;
    }
    &::placeholder {
      color: #a0bedb;
    }
  }

  .btn-generate {
    background: linear-gradient(145deg, #1e6df2, #0a4bb0);
    border: none;
    color: white;
    font-weight: 700;
    padding: 16px 36px;
    border-radius: 60px;
    font-size: 16px;
    display: inline-flex;
    align-items: center;
    gap: 12px;
    cursor: pointer;
    transition: 0.2s;
    box-shadow: 0 8px 20px rgba(30, 109, 242, 0.3);
    border: 1px solid rgba(255, 255, 255, 0.2);
    white-space: nowrap;

    &:hover:not(:disabled) {
      background: #0a4bb0;
      transform: scale(1.02);
    }
    &:disabled {
      opacity: 0.6;
      cursor: not-allowed;
    }
    i {
      font-size: 18px;
    }
  }

  .input-hint {
    margin-top: 20px;
    color: #5e7e9c;
    font-size: 14px;
    display: flex;
    align-items: center;
    gap: 8px;
    i {
      color: #1e6df2;
    }
  }
  .status-msg {
    margin-top: 12px;
    color: #b42318;
    font-size: 13px;
  }
}

/* 结果区域 */
.result-section {
  animation: fadeIn 0.5s;
}

.result-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 28px;
  flex-wrap: wrap;
  gap: 16px;

  .result-title {
    display: flex;
    align-items: center;
    gap: 12px;
    i {
      font-size: 28px;
      color: #ff9f1c;
    }
    h2 {
      font-size: 22px;
      font-weight: 700;
      color: #0b2b4a;
      margin: 0;
    }
  }

  .result-actions {
    display: flex;
    gap: 12px;
    .btn-outline {
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
    .btn-primary {
      background: #1e6df2;
      border: none;
      color: white;
      padding: 10px 24px;
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
}

/* 幻灯片网格 */
.slides-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 24px;
  margin-bottom: 32px;
}

.slide-card {
  background: white;
  border-radius: 20px;
  overflow: hidden;
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.02);
  border: 1px solid rgba(30, 109, 242, 0.1);
  transition: 0.25s;
  cursor: pointer;

  &:hover {
    transform: translateY(-4px);
    box-shadow: 0 16px 30px rgba(30, 109, 242, 0.1);
    border-color: #1e6df2;
  }

  .slide-thumb {
    height: 120px;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    color: white;
    position: relative;

    .slide-number {
      position: absolute;
      top: 12px;
      left: 12px;
      background: rgba(0, 0, 0, 0.2);
      padding: 2px 10px;
      border-radius: 30px;
      font-size: 12px;
      font-weight: 600;
      backdrop-filter: blur(2px);
    }
    .slide-icon {
      font-size: 42px;
      margin-bottom: 8px;
    }
  }

  .slide-info {
    padding: 16px;
    h4 {
      font-size: 16px;
      font-weight: 700;
      color: #0b2b4a;
      margin-bottom: 6px;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    p {
      font-size: 13px;
      color: #5e7e9c;
      line-height: 1.4;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }
  }
}

/* AI 总结建议 */
.ai-summary {
  background: #f0f7ff;
  border-radius: 24px;
  padding: 24px;
  border-left: 8px solid #1e6df2;
  margin-top: 16px;

  .summary-header {
    display: flex;
    align-items: center;
    gap: 12px;
    font-weight: 700;
    color: #0b2b4a;
    margin-bottom: 12px;
    i {
      font-size: 22px;
    }
  }
  .summary-text {
    color: #2c4e6e;
    line-height: 1.6;
    margin: 0;
  }
}

/* 空状态（未生成时） */
.empty-state {
  background: white;
  border-radius: 32px;
  padding: 60px 40px;
  text-align: center;
  box-shadow: 0 12px 30px rgba(0, 20, 40, 0.02);
  border: 1px dashed rgba(30, 109, 242, 0.3);

  .empty-icon {
    width: 100px;
    height: 100px;
    background: #e8f1ff;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    margin: 0 auto 24px;
    i {
      font-size: 48px;
      color: #1e6df2;
    }
  }
  h3 {
    font-size: 22px;
    font-weight: 700;
    color: #0b2b4a;
    margin-bottom: 12px;
  }
  p {
    color: #5e7e9c;
    margin-bottom: 28px;
  }

  .example-topics {
    display: flex;
    flex-wrap: wrap;
    gap: 12px;
    justify-content: center;
    .example-tag {
      background: #f0f5fa;
      padding: 10px 22px;
      border-radius: 40px;
      font-size: 14px;
      color: #1e6df2;
      font-weight: 500;
      cursor: pointer;
      transition: 0.2s;
      border: 1px solid transparent;
      &:hover {
        background: #1e6df2;
        color: white;
        border-color: white;
      }
    }
  }
}

/* 过渡动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 响应式调整 */
@media (max-width: 768px) {
  .input-wrapper {
    flex-direction: column;
    align-items: stretch;
  }
  .btn-generate {
    justify-content: center;
  }
  .result-header {
    flex-direction: column;
    align-items: flex-start;
  }
}

.preview-mask {
  position: fixed;
  inset: 0;
  background: rgba(8, 20, 36, 0.52);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}
.preview-panel {
  width: min(760px, 92vw);
  background: #fff;
  border-radius: 20px;
  border: 1px solid #d8e4f5;
  overflow: hidden;
}
.preview-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid #e8eef8;
}
.preview-head h3 {
  margin: 0;
  color: #0b2b4a;
}
.btn-close-preview {
  border: 1px solid #c9d9ef;
  background: #fff;
  border-radius: 8px;
  padding: 6px 10px;
  cursor: pointer;
}
.preview-body {
  padding: 16px;
}
.preview-card {
  min-height: 180px;
  border-radius: 14px;
  color: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
}
.preview-card i {
  font-size: 48px;
}
.preview-card span {
  font-size: 22px;
  font-weight: 700;
}
.preview-body p {
  margin: 14px 0 0;
  color: #2a4a68;
  line-height: 1.6;
}
</style>
