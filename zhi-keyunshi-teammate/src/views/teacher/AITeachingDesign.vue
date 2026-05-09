<template>
  <div class="ai-teaching-design">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="title-text">
        <h1>AI 教学设计辅助</h1>
        <p class="subtitle">输入课题，AI 自动生成完整的教学设计初稿</p>
      </div>
      <div class="ai-badge-large"><i class="fas fa-magic"></i> AI 辅助设计</div>
    </div>

    <!-- 输入卡片 -->
    <div class="input-card">
      <div class="input-label">
        <i class="fas fa-graduation-cap"></i>
        <span>教学课题 / 章节</span>
      </div>
      <div class="input-wrapper">
        <input
          v-model="topic"
          type="text"
          placeholder="例如：两位数乘法、燕子课文导读、植物的生长……"
          class="topic-input"
          @keyup.enter="generateDesign"
        />
        <select v-model="grade" class="grade-select">
          <option value="小学">小学</option>
          <option value="初中">初中</option>
        </select>
        <select v-model="subject" class="subject-select">
          <option value="数学">数学</option>
          <option value="语文">语文</option>
          <option value="英语">英语</option>
          <option value="科学">科学</option>
          <option value="美术">美术</option>
        </select>
        <button
          class="btn-generate"
          :disabled="isGenerating || !topic.trim()"
          @click="generateDesign"
        >
          <i :class="['fas', isGenerating ? 'fa-spinner fa-spin' : 'fa-robot']"></i>
          {{ isGenerating ? "AI 生成中..." : "生成教学设计" }}
        </button>
      </div>
      <p class="input-hint">
        <i class="fas fa-info-circle"></i>
        支持中小学各学科，AI 将根据课题自动生成教学目标、重难点、教学过程等完整设计。
      </p>
      <p v-if="statusMsg" class="status-msg">{{ statusMsg }}</p>
    </div>

    <!-- 生成结果展示区 -->
    <transition name="fade">
      <div v-if="generated" class="result-section">
        <div class="result-header">
          <div class="result-title">
            <i class="fas fa-file-alt"></i>
            <h2>《{{ generatedTopic }}》教学设计（{{ grade }}·{{ subject }}）</h2>
          </div>
          <div class="result-actions">
            <button class="btn-outline" @click="exportDesign">
              <i class="fas fa-download"></i> 导出文档
            </button>
          </div>
        </div>

        <!-- 教学设计内容卡片 -->
        <div class="design-content">
          <!-- 教学目标 -->
          <div class="design-section">
            <div class="section-header">
              <i class="fas fa-bullseye" style="color: #1e6df2"></i>
              <h3>教学目标</h3>
              <span class="section-badge">核心</span>
            </div>
            <ul class="section-list">
              <li v-for="(item, idx) in designData.objectives" :key="idx">
                <i class="fas fa-check-circle"></i>
                <span>{{ item }}</span>
              </li>
            </ul>
          </div>

          <!-- 教学重难点 -->
          <div class="design-section">
            <div class="section-header">
              <i class="fas fa-exclamation-triangle" style="color: #ff9f1c"></i>
              <h3>教学重难点</h3>
            </div>
            <div class="split-row">
              <div class="split-item">
                <span class="label">重点：</span>
                <span>{{ designData.keyPoints }}</span>
              </div>
              <div class="split-item">
                <span class="label">难点：</span>
                <span>{{ designData.difficultPoints }}</span>
              </div>
            </div>
          </div>

          <!-- 教学方法与准备 -->
          <div class="design-row">
            <div class="design-section half">
              <div class="section-header">
                <i class="fas fa-tools" style="color: #2eb85c"></i>
                <h3>教学方法</h3>
              </div>
              <div class="tags">
                <span
                  v-for="(method, idx) in designData.methods"
                  :key="idx"
                  class="tag"
                  >{{ method }}</span
                >
              </div>
            </div>
            <div class="design-section half">
              <div class="section-header">
                <i class="fas fa-school" style="color: #6f42c1"></i>
                <h3>教学准备</h3>
              </div>
              <div class="tags">
                <span
                  v-for="(item, idx) in designData.preparations"
                  :key="idx"
                  class="tag"
                  >{{ item }}</span
                >
              </div>
            </div>
          </div>

          <!-- 教学过程（时间线） -->
          <div class="design-section">
            <div class="section-header">
              <i class="fas fa-clock" style="color: #e83e8c"></i>
              <h3>教学过程</h3>
              <span class="section-badge">40分钟</span>
            </div>
            <div class="process-steps">
              <div
                v-for="(step, idx) in designData.process"
                :key="idx"
                class="process-step"
              >
                <div class="step-index">{{ idx + 1 }}</div>
                <div class="step-content">
                  <div class="step-title">
                    {{ step.title }} <span class="step-time">{{ step.time }}</span>
                  </div>
                  <p class="step-desc">{{ step.desc }}</p>
                </div>
              </div>
            </div>
          </div>

          <!-- 作业布置 -->
          <div class="design-section">
            <div class="section-header">
              <i class="fas fa-pencil-alt" style="color: #20c997"></i>
              <h3>作业布置</h3>
            </div>
            <ul class="section-list">
              <li v-for="(item, idx) in designData.homework" :key="idx">
                <i class="fas fa-check-circle"></i>
                <span>{{ item }}</span>
              </li>
            </ul>
          </div>

          <!-- AI 优化建议 -->
          <div class="ai-optimization">
            <div class="optimization-header">
              <i class="fas fa-robot"></i>
              <span>AI 优化建议</span>
            </div>
            <p>{{ designData.aiSuggestion }}</p>
            <button class="btn-apply" @click="applySuggestion">
              <i class="fas fa-check"></i> 应用建议
            </button>
          </div>
        </div>
      </div>
    </transition>

    <!-- 未生成时的占位提示 -->
    <div v-if="!generated" class="empty-state">
      <h3>开始设计您的课堂</h3>
      <p>输入课题并选择学段学科，AI 将为您生成完整的教学设计方案</p>
      <div class="example-topics">
        <span
          class="example-tag"
          @click="setExample('两位数乘法的计算方法', '小学', '数学')"
          >两位数乘法</span
        >
        <span class="example-tag" @click="setExample('燕子课文导读', '小学', '语文')"
          >燕子导读</span
        >
        <span class="example-tag" @click="setExample('植物的生长变化', '小学', '科学')"
          >植物生长</span
        >
        <span class="example-tag" @click="setExample('英语日常问候语', '小学', '英语')"
          >英语问候语</span
        >
      </div>
    </div>

    <!-- 成功提示弹窗（应用建议） -->
    <div v-if="showToast" class="toast-message">
      <i class="fas fa-check-circle"></i> AI 优化建议已应用
    </div>
  </div>
</template>

<script setup>
import { ref } from "vue";
import {
  downloadTeacherTeachingDesignDoc,
  generateTeacherTeachingDesign,
} from "@/utils/api";

// 表单数据
const topic = ref("");
const grade = ref("小学");
const subject = ref("数学");
const isGenerating = ref(false);
const generated = ref(false);
const generatedTopic = ref("");
const designData = ref({});
const showToast = ref(false);
const statusMsg = ref("");

// 快捷示例填充
const setExample = (exampleTopic, exampleGrade, exampleSubject) => {
  topic.value = exampleTopic;
  grade.value = exampleGrade;
  subject.value = exampleSubject;
};

const generateDesign = async () => {
  if (!topic.value.trim()) return;
  isGenerating.value = true;
  generated.value = false;
  statusMsg.value = "";
  try {
    const data = await generateTeacherTeachingDesign({
      topic: topic.value.trim(),
      grade: grade.value,
      subject: subject.value,
    });
    generatedTopic.value = data?.topic || topic.value.trim();
    designData.value = data?.design || {};
    isGenerating.value = false;
    generated.value = true;
  } catch (e) {
    isGenerating.value = false;
    generated.value = false;
    statusMsg.value = e.message || "生成失败，请稍后重试";
  }
};
const exportDesign = async () => {
  if (!generatedTopic.value) return;
  try {
    const { blob, filename } = await downloadTeacherTeachingDesignDoc({
      topic: generatedTopic.value,
      grade: grade.value,
      subject: subject.value,
      design: designData.value,
    });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = filename || `${generatedTopic.value}-教学设计.docx`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
  } catch (e) {
    statusMsg.value = e.message || "导出失败，请稍后重试";
  }
};

// 应用AI优化建议
const applySuggestion = () => {
  // 模拟应用成功提示
  showToast.value = true;
  setTimeout(() => {
    showToast.value = false;
  }, 3000);
  // 可以在此处实际修改 designData 中的内容，为简化演示，仅做提示
};
</script>

<style scoped lang="scss">
.ai-teaching-design {
  padding: 8px 0;
}

/* 页面头部（复用风格） */
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
    flex: 2;
    min-width: 260px;
    padding: 14px 20px;
    border: 2px solid #e2eaf2;
    border-radius: 40px;
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

  .grade-select,
  .subject-select {
    padding: 14px 24px;
    border: 2px solid #e2eaf2;
    border-radius: 40px;
    font-size: 15px;
    background: white;
    color: #0b2b4a;
    cursor: pointer;
    transition: 0.2s;
    &:hover {
      border-color: #1e6df2;
    }
    &:focus {
      border-color: #1e6df2;
      outline: none;
      box-shadow: 0 0 0 4px rgba(30, 109, 242, 0.1);
    }
  }

  .btn-generate {
    background: linear-gradient(145deg, #1e6df2, #0a4bb0);
    border: none;
    color: white;
    font-weight: 700;
    padding: 14px 32px;
    border-radius: 40px;
    font-size: 16px;
    display: inline-flex;
    align-items: center;
    gap: 12px;
    cursor: pointer;
    transition: 0.2s;
    box-shadow: 0 8px 20px rgba(30, 109, 242, 0.3);
    white-space: nowrap;

    &:hover:not(:disabled) {
      background: #0a4bb0;
      transform: scale(1.02);
    }
    &:disabled {
      opacity: 0.6;
      cursor: not-allowed;
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
    margin-top: 10px;
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
      color: #1e6df2;
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
  }
}

/* 教学设计内容卡片 */
.design-content {
  background: white;
  border-radius: 28px;
  padding: 32px;
  box-shadow: 0 12px 30px rgba(0, 20, 40, 0.04);
  border: 1px solid rgba(30, 109, 242, 0.1);
}

.design-section {
  margin-bottom: 32px;
  &:last-child {
    margin-bottom: 0;
  }

  .section-header {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 18px;
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
}

.section-list {
  list-style: none;
  padding: 0;
  margin: 0;
  li {
    display: flex;
    align-items: baseline;
    gap: 12px;
    margin-bottom: 12px;
    color: #2c4e6e;
    i {
      color: #2eb85c;
      font-size: 16px;
      margin-top: 2px;
    }
  }
}

.split-row {
  display: flex;
  gap: 32px;
  background: #f8fcff;
  padding: 20px;
  border-radius: 20px;

  .split-item {
    flex: 1;
    .label {
      font-weight: 700;
      color: #0b2b4a;
      margin-right: 8px;
    }
  }
}

.design-row {
  display: flex;
  gap: 24px;
  margin-bottom: 32px;
  .half {
    flex: 1;
    margin-bottom: 0;
  }
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  .tag {
    background: #e8f1ff;
    color: #1e6df2;
    padding: 8px 18px;
    border-radius: 40px;
    font-size: 14px;
    font-weight: 500;
  }
}

.process-steps {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.process-step {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  .step-index {
    width: 32px;
    height: 32px;
    background: #1e6df2;
    color: white;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    font-weight: 700;
    font-size: 15px;
    flex-shrink: 0;
  }
  .step-content {
    flex: 1;
    .step-title {
      font-weight: 700;
      color: #0b2b4a;
      margin-bottom: 4px;
      .step-time {
        font-weight: 400;
        color: #5e7e9c;
        font-size: 13px;
        margin-left: 12px;
      }
    }
    .step-desc {
      color: #4a668a;
      font-size: 14px;
      margin: 0;
    }
  }
}

.ai-optimization {
  margin-top: 32px;
  background: #f0f7ff;
  border-radius: 20px;
  padding: 24px;
  border-left: 8px solid #1e6df2;
  display: flex;
  flex-direction: column;
  gap: 16px;

  .optimization-header {
    display: flex;
    align-items: center;
    gap: 12px;
    font-weight: 700;
    color: #0b2b4a;
    i {
      font-size: 22px;
      color: #1e6df2;
    }
  }
  p {
    color: #2c4e6e;
    margin: 0;
    line-height: 1.6;
  }
  .btn-apply {
    align-self: flex-end;
    background: #1e6df2;
    color: white;
    border: none;
    padding: 12px 28px;
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

/* 空状态 */
.empty-state {
  background: white;
  border-radius: 32px;
  padding: 60px 40px;
  text-align: center;
  box-shadow: 0 12px 30px rgba(0, 20, 40, 0.02);
  border: 1px dashed rgba(30, 109, 242, 0.3);

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

/* 提示 Toast */
.toast-message {
  position: fixed;
  bottom: 40px;
  left: 50%;
  transform: translateX(-50%);
  background: #1e6df2;
  color: white;
  padding: 16px 32px;
  border-radius: 60px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 12px;
  box-shadow: 0 12px 30px rgba(30, 109, 242, 0.4);
  z-index: 9999;
  animation: slideUpToast 0.3s;
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

/* 响应式 */
@media (max-width: 768px) {
  .input-wrapper {
    flex-direction: column;
    align-items: stretch;
  }
  .grade-select,
  .subject-select,
  .btn-generate {
    width: 100%;
  }
  .result-header {
    flex-direction: column;
    align-items: flex-start;
  }
  .split-row {
    flex-direction: column;
    gap: 16px;
  }
  .design-row {
    flex-direction: column;
  }
}
</style>
