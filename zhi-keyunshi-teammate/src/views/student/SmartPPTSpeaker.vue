<template>
  <div class="smart-ppt-page">
    <div class="page-header">
      <h1><i class="fas fa-chalkboard"></i> 智讲PPT生成器</h1>
      <p>选择教师上传的PPT，点击开始讲解后左侧数字人出现，右侧自动轮播PPT。</p>
    </div>

    <section class="control-card card">
      <div class="picker-row">
        <select v-model="selectedPptId" class="ppt-select">
          <option value="" disabled>请选择教师已上传PPT</option>
          <option v-for="item in pptList" :key="item.id" :value="item.id">
            {{ item.name }} · {{ formatTime(item.uploadedAt) }}
          </option>
        </select>
        <button class="btn-refresh" @click="loadPptList">刷新列表</button>
        <button class="btn-generate" :disabled="generating || !selectedPptId" @click="generateLectureScript">
          {{ generating ? "智能体生成中..." : "开始讲解" }}
        </button>
      </div>
      <div v-if="statusText" class="status-text">{{ statusText }}</div>
      <div v-if="!pptList.length" class="empty-tip">
        暂无可用课件，请先在教师端“PPT上传与AI讲解”上传课件。
      </div>
    </section>

    <div class="stage-grid">
      <section class="right-stage card">
        <div class="stage-title">
          <i class="fas fa-images"></i> PPT轮播
          <span class="talking-tag" :class="{ active: speaking }">{{ speaking ? "讲解中" : "待开始" }}</span>
        </div>
        <pre class="script-preview">{{ lectureScript || "等待智能体生成讲稿..." }}</pre>
        <div class="ppt-carousel">
          <div class="slide-card" v-if="activeSlides.length">
            <div class="slide-index">第 {{ currentSlideIndex + 1 }} / {{ activeSlides.length }} 页</div>
            <div class="slide-title">{{ activeSlides[currentSlideIndex] }}</div>
          </div>
          <div class="slide-empty" v-else>未检测到可轮播内容</div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { fetchStudentPptAssets, generateStudentPptLecture } from "@/utils/api";

const pptList = ref([]);
const selectedPptId = ref("");
const generating = ref(false);
const speaking = ref(false);
const lectureScript = ref("");
const statusText = ref("");
const currentSlideIndex = ref(0);
const generatedSlides = ref([]);
let timerId = null;

const activeSlides = computed(() => generatedSlides.value || []);

async function loadPptList() {
  try {
    const data = await fetchStudentPptAssets();
    const list = data?.list || [];
    pptList.value = Array.isArray(list) ? list : [];
    if (!selectedPptId.value && pptList.value.length) {
      selectedPptId.value = pptList.value[0].id;
    }
    statusText.value = "";
  } catch {
    pptList.value = [];
    statusText.value = "课件列表加载失败，请稍后重试";
  }
}

function formatTime(v) {
  const d = new Date(v);
  if (Number.isNaN(d.getTime())) return "--";
  const p = (n) => String(n).padStart(2, "0");
  return `${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

function startCarousel() {
  stopCarousel();
  if (activeSlides.value.length <= 1) return;
  timerId = setInterval(() => {
    currentSlideIndex.value = (currentSlideIndex.value + 1) % activeSlides.value.length;
  }, 2600);
}

function stopCarousel() {
  if (timerId) {
    clearInterval(timerId);
    timerId = null;
  }
}

async function generateLectureScript() {
  if (!selectedPptId.value || generating.value) return;
  generating.value = true;
  speaking.value = false;
  lectureScript.value = "";
  generatedSlides.value = [];
  statusText.value = "智能体正在生成讲解稿...";
  currentSlideIndex.value = 0;
  stopCarousel();
  try {
    const data = await generateStudentPptLecture({ pptId: selectedPptId.value });
    lectureScript.value = String(data?.script || "");
    generatedSlides.value = Array.isArray(data?.slides) ? data.slides : [];
    speaking.value = !!data?.speaking;
    statusText.value = speaking.value ? "讲解已开始" : "讲解未启动";
    startCarousel();
  } catch (e) {
    statusText.value = e.message || "讲解生成失败，请稍后重试";
  } finally {
    generating.value = false;
  }
}

onMounted(loadPptList);
onBeforeUnmount(stopCarousel);
</script>

<style scoped lang="scss">
.smart-ppt-page { padding: 4px 0; }
.page-header { margin-bottom: 18px; }
.page-header h1 { margin: 0 0 8px; color: #0b2b4a; font-size: 26px; }
.page-header p { margin: 0; color: #5d7d9b; }
.control-card { margin-bottom: 14px; }
.stage-grid { display: grid; grid-template-columns: 1fr; gap: 14px; }
.card {
  background: #fff;
  border: 1px solid rgba(30, 109, 242, 0.12);
  border-radius: 18px;
  padding: 16px;
}
.picker-row { display: flex; gap: 10px; margin: 12px 0; }
.ppt-select {
  flex: 1;
  border: 1px solid #d8e4f0;
  border-radius: 10px;
  padding: 8px 10px;
}
.btn-refresh {
  border: 1px solid #cfe0f7;
  background: #f3f8ff;
  color: #1e6df2;
  border-radius: 10px;
  padding: 8px 12px;
  cursor: pointer;
}
.status-text { color: #3f6488; font-size: 13px; }
.empty-tip { color: #7892ac; font-size: 13px; margin-bottom: 12px; }
.btn-generate {
  border: none;
  background: linear-gradient(135deg, #1e6df2, #4a8cf6);
  color: #fff;
  border-radius: 10px;
  padding: 10px 12px;
  font-weight: 700;
  cursor: pointer;
}
.btn-generate:disabled { background: #8db3f5; cursor: not-allowed; }
.stage-title {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #214b75;
  font-weight: 700;
  margin-bottom: 10px;
}
.script-preview {
  margin: 0;
  width: 100%;
  white-space: pre-wrap;
  line-height: 1.6;
  color: #2a4a69;
  font-size: 13px;
  border: 1px dashed #c9dcfb;
  border-radius: 10px;
  padding: 10px;
  min-height: 120px;
  margin-bottom: 10px;
}
.talking-tag {
  margin-left: auto;
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 20px;
  background: #e9f1ff;
  color: #4f6f92;
}
.talking-tag.active {
  background: #def3e5;
  color: #1f7f4c;
}
.ppt-carousel {
  border: 1px solid rgba(30, 109, 242, 0.2);
  border-radius: 12px;
  padding: 12px;
  background: linear-gradient(180deg, #edf4ff, #e8f2ff);
}
.slide-card {
  border-radius: 10px;
  background: #fff;
  min-height: 380px;
  padding: 14px;
}
.slide-index { color: #5e7ea1; font-size: 13px; margin-bottom: 8px; }
.slide-title { font-size: 28px; line-height: 1.4; font-weight: 700; color: #123e6b; }
.slide-empty {
  min-height: 380px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #6884a1;
}
@media (max-width: 1100px) {
  .picker-row { flex-wrap: wrap; }
  .stage-grid { grid-template-columns: 1fr; }
  .slide-card, .slide-empty { min-height: 260px; }
}
</style>
