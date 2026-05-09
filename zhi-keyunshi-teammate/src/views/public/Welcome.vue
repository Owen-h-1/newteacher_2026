<template>
  <div class="welcome-page">
    <header class="top-nav">
      <div class="brand">
        <div class="brand-text">
          <span class="brand-name">智课云师</span>
          <span class="brand-subtitle">AI 智慧课堂平台</span>
        </div>
      </div>
      <div class="nav-actions">
        <router-link to="/login" class="btn ghost">登录</router-link>
        <router-link to="/register" class="btn solid">注册</router-link>
      </div>
    </header>

    <main class="hero">
      <section class="hero-panel hero-copy">
        <span class="badge">AI 智慧课堂平台</span>
        <h1>让教学更高效，让学习更有趣</h1>
        <p>融合 AI 助教、学习分析与互动内容，服务教师与学生的日常课堂与课后学习。</p>
        <div class="hero-stats" aria-label="产品亮点">
          <div class="stat-item">
            <span class="stat-icon"><i class="fas fa-layer-group"></i></span>
            <div>
              <strong>3+</strong>
              <span>核心场景</span>
            </div>
          </div>
          <div class="stat-item">
            <span class="stat-icon"><i class="fas fa-sparkles"></i></span>
            <div>
              <strong>AI</strong>
              <span>智能辅助</span>
            </div>
          </div>
          <div class="stat-item">
            <span class="stat-icon"><i class="fas fa-clock"></i></span>
            <div>
              <strong>24/7</strong>
              <span>持续陪伴</span>
            </div>
          </div>
        </div>
        <div class="hero-cta">
          <router-link to="/register" class="btn solid">免费开始</router-link>
          <router-link to="/login" class="btn ghost dark">已有账号，去登录</router-link>
        </div>
      </section>

      <section class="hero-panel hero-carousel" aria-label="首页轮播图" @mouseenter="pauseCarousel" @mouseleave="resumeCarousel">
        <div class="carousel-track" :style="{ transform: `translateX(-${currentSlide * 100}%)` }">
          <article v-for="slide in slides" :key="slide.title" class="carousel-slide">
            <img :src="slide.image" :alt="slide.title" loading="eager" />
            <div class="carousel-overlay">
              <span>{{ slide.kicker }}</span>
              <h3>{{ slide.title }}</h3>
              <p>{{ slide.desc }}</p>
            </div>
          </article>
        </div>

        <button class="carousel-arrow arrow-left" type="button" aria-label="上一张" @click="prevSlide">
          &lt;
        </button>
        <button class="carousel-arrow arrow-right" type="button" aria-label="下一张" @click="nextSlide">
          &gt;
        </button>

        <div class="carousel-dots">
          <button
            v-for="(slide, index) in slides"
            :key="slide.title"
            class="dot"
            :class="{ active: index === currentSlide }"
            @click="goToSlide(index)"
            :aria-label="`切换到第 ${index + 1} 张`"
          />
        </div>
      </section>
    </main>

    <section class="intro-grid">
      <article class="intro-card intro-card-lesson">
        <div class="intro-media">
          <img class="intro-image" :src="smartPrepImage" alt="智能备课" />
        </div>
        <div class="intro-copy">
          <h3>智能备课</h3>
          <p>支持教师快速生成课件草稿、教学设计建议与课堂活动方案。</p>
        </div>
      </article>
      <article class="intro-card intro-card-tracking">
        <div class="intro-media">
          <img class="intro-image" :src="trackingImage" alt="学情追踪" />
        </div>
        <div class="intro-copy">
          <h3>学情追踪</h3>
          <p>作业与课堂数据同步汇总，帮助老师识别薄弱知识点并及时干预。</p>
        </div>
      </article>
      <article class="intro-card intro-card-companion">
        <div class="intro-media">
          <img class="intro-image" :src="companionImage" alt="学习陪伴" />
        </div>
        <div class="intro-copy">
          <h3>学习陪伴</h3>
          <p>学生可在学习中心进行提问、练习和复盘，形成持续学习闭环。</p>
        </div>
      </article>
    </section>

    <PublicFooter />
  </div>
</template>

<script setup>
import { onMounted, onBeforeUnmount, ref } from "vue";
import PublicFooter from "@/components/common/PublicFooter.vue";

const smartPrepImage = new URL("@/assets/智能备课.jpg", import.meta.url).href;
const trackingImage = new URL("@/assets/学情追踪.jpg", import.meta.url).href;
const companionImage = new URL("@/assets/学习陪伴.jpg", import.meta.url).href;

const teacherSlide = new URL("@/assets/home-carousel-teacher.jpg", import.meta.url).href;
const studentSlide = new URL("@/assets/home-carousel-student.jpg", import.meta.url).href;
const overallSlide = new URL("@/assets/home-carousel-overall.jpg", import.meta.url).href;

const slides = [
  {
    image: teacherSlide,
    kicker: "教师端",
    title: "AI 备课·高效课堂",
    desc: "把课件、教学设计和学情数据放在一起，帮助老师更快准备课堂。",
  },
  {
    image: studentSlide,
    kicker: "学生端",
    title: "成长陪伴·自主学习",
    desc: "作业、目标、消息和 AI 助手组合成完整的学习闭环。",
  },
  {
    image: overallSlide,
    kicker: "整体方案",
    title: "全场景协同·教学闭环",
    desc: "覆盖讲解、练习、反馈与统计，让教学流程更顺畅。",
  },
];

const currentSlide = ref(0);
let timer;

const nextSlide = () => {
  currentSlide.value = (currentSlide.value + 1) % slides.length;
};

const prevSlide = () => {
  currentSlide.value = (currentSlide.value - 1 + slides.length) % slides.length;
};

const goToSlide = (index) => {
  currentSlide.value = index;
};

const pauseCarousel = () => {
  if (timer) {
    clearInterval(timer);
    timer = null;
  }
};

const resumeCarousel = () => {
  if (!timer) {
    timer = setInterval(nextSlide, 3500);
  }
};

onMounted(() => {
  timer = setInterval(nextSlide, 3500);
});

onBeforeUnmount(() => {
  pauseCarousel();
});
</script>

<style scoped lang="scss">
.welcome-page {
  min-height: 100vh;
  background:
    radial-gradient(circle at 20% 10%, rgba(255, 255, 255, 0.72), transparent 22%),
    radial-gradient(circle at 85% 12%, rgba(255, 208, 140, 0.18), transparent 18%),
    linear-gradient(180deg, #eef5ff 0%, #f7fbff 48%, #ffffff 100%);
  padding: 22px clamp(16px, 4vw, 64px) 0;
}

.top-nav,
.hero,
.intro-grid {
  max-width: 1280px;
  margin-left: auto;
  margin-right: auto;
}

.top-nav {
  margin-bottom: 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 18px;
  border: 1px solid rgba(30, 109, 242, 0.08);
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(12px);
  box-shadow: 0 12px 28px rgba(24, 77, 146, 0.06);
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* brand-mark removed */

.brand-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.brand-name {
  color: #0b2b4a;
  font-size: 22px;
  font-weight: 800;
  line-height: 1.1;
}

.brand-subtitle {
  font-size: 12px;
  color: #6a84a0;
}

.nav-actions {
  display: flex;
  gap: 10px;
}

.btn {
  text-decoration: none;
  border-radius: 999px;
  font-size: 14px;
  font-weight: 600;
  padding: 10px 18px;
  transition: 0.2s ease;
}

.btn.solid {
  color: #fff;
  background: linear-gradient(135deg, #1e6df2, #0a4bb0);
  box-shadow: 0 10px 20px rgba(30, 109, 242, 0.2);
}

.btn.ghost {
  color: #1e6df2;
  border: 1px solid #c7dbfb;
  background: #fff;
}

.btn.ghost.dark {
  color: #0b2b4a;
  border-color: #c8d8eb;
}

.btn:hover {
  transform: translateY(-1px);
}

.hero {
  display: grid;
  grid-template-columns: minmax(0, 0.92fr) minmax(0, 1.08fr);
  gap: 18px;
  align-items: stretch;
}

.hero-panel {
  position: relative;
  overflow: hidden;
  border-radius: 28px;
  border: 1px solid rgba(30, 109, 242, 0.14);
  box-shadow: 0 14px 32px rgba(24, 77, 146, 0.08);
  background: #fff;
}

.hero-copy {
  padding: 36px;
  min-height: 440px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  background:
    radial-gradient(circle at 88% 16%, rgba(255, 225, 154, 0.25) 0, transparent 18%),
    linear-gradient(180deg, #fff 0%, #f7fbff 100%);
}

.badge {
  display: inline-flex;
  align-items: center;
  width: fit-content;
  margin-bottom: 14px;
  font-size: 13px;
  color: #1e6df2;
  background: #e8f1ff;
  border-radius: 999px;
  padding: 6px 12px;
}

.hero-copy h1 {
  margin: 0 0 14px;
  color: #0b2b4a;
  font-size: clamp(32px, 5vw, 48px);
  line-height: 1.12;
}

.hero-copy p {
  margin: 0;
  line-height: 1.78;
  color: #4e6d8b;
  font-size: 16px;
}

.hero-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 24px;
}

.stat-item {
  padding: 14px 14px 13px;
  border-radius: 18px;
  background: #fff;
  border: 1px solid rgba(30, 109, 242, 0.1);
  box-shadow: 0 10px 18px rgba(24, 77, 146, 0.05);
}

.stat-item strong {
  display: block;
  margin-bottom: 4px;
  color: #0b2b4a;
  font-size: 20px;
}

.stat-item span {
  color: #6a84a0;
  font-size: 13px;
}

.hero-cta {
  margin-top: 24px;
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.hero-carousel {
  position: relative;
  min-height: 500px;
  background:
    radial-gradient(circle at 18% 18%, rgba(255, 255, 255, 0.28) 0, transparent 20%),
    linear-gradient(180deg, #0d1735 0%, #173b86 100%);
}

.carousel-track {
  display: flex;
  height: 100%;
  transition: transform 0.75s cubic-bezier(0.22, 1, 0.36, 1);
}

.carousel-slide {
  min-width: 100%;
  position: relative;
}

.carousel-slide img {
  width: 100%;
  height: 100%;
  min-height: 500px;
  object-fit: cover;
  display: block;
  transform: scale(1.01);
  background: #eef6ff;
}

.carousel-overlay {
  position: absolute;
  left: 22px;
  right: 22px;
  bottom: 18px;
  max-width: 620px;
  color: #fff;
  text-shadow: 0 2px 10px rgba(0, 0, 0, 0.24);
  background: linear-gradient(180deg, rgba(7, 11, 25, 0.02), rgba(7, 11, 25, 0.68));
  padding: 18px 20px 16px;
  border-radius: 18px;
  backdrop-filter: blur(8px);
}

.carousel-overlay span {
  display: inline-flex;
  align-items: center;
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 700;
  padding: 5px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.22);
  backdrop-filter: blur(8px);
}

.carousel-overlay h3 {
  margin: 0 0 6px;
  font-size: 30px;
  line-height: 1.2;
}

.carousel-overlay p {
  margin: 0;
  font-size: 14px;
  line-height: 1.6;
  opacity: 0.94;
}

.carousel-arrow {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: auto;
  height: auto;
  padding: 0;
  border: none;
  border-radius: 0;
  cursor: pointer;
  color: #fff;
  background: transparent;
  box-shadow: none;
  backdrop-filter: none;
  transition: 0.2s ease;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  font-weight: 700;
  line-height: 1;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
}

.carousel-arrow:hover {
  color: #fff;
  transform: translateY(-50%) scale(1.08);
}

.arrow-left { left: 16px; }
.arrow-right { right: 16px; }

.carousel-dots {
  position: absolute;
  left: 22px;
  right: 22px;
  bottom: 16px;
  display: flex;
  justify-content: center;
  gap: 8px;
}

.dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  border: none;
  background: rgba(255, 255, 255, 0.5);
  cursor: pointer;
  transition: 0.2s ease;
}

.dot.active {
  width: 28px;
  border-radius: 999px;
  background: #fff;
}

.intro-grid {
  margin-top: 8px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.intro-card {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  background: transparent;
  border-radius: 14px;
  padding: 10px;
  border: 1px solid rgba(30, 109, 242, 0.08);
  box-shadow: 0 8px 18px rgba(24, 77, 146, 0.04);
  min-height: 138px;
}

.intro-media {
  flex: 0 0 44px;
  width: 44px;
  height: 44px;
  border-radius: 12px;
  overflow: hidden;
  background: #f7fbff;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 2px;
}

.intro-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.intro-copy {
  flex: 1;
  min-width: 0;
}

.intro-card h3 {
  margin: 0 0 4px;
  color: #0b2b4a;
  font-size: 21px;
  line-height: 1.08;
}

.intro-card p {
  margin: 0;
  color: #5e7e9c;
  line-height: 1.28;
  font-size: 15px;
}

.intro-card::after {
  content: "";
  position: absolute;
  inset: auto -20px -20px auto;
  width: 46px;
  height: 46px;
  border-radius: 16px;
  background: radial-gradient(circle at 30% 30%, rgba(30, 109, 242, 0.12), transparent 60%);
}

@media (max-width: 980px) {
  .hero,
  .intro-grid {
    grid-template-columns: 1fr;
  }

  .hero-copy,
  .hero-carousel,
  .carousel-slide img {
    min-height: 320px;
  }

  .carousel-arrow {
    font-size: 24px;
  }

  .intro-card {
    min-height: 120px;
  }

  .intro-media {
    flex-basis: 38px;
    width: 38px;
    height: 38px;
  }

  .intro-card h3 {
    font-size: 18px;
  }

  .intro-card p {
    font-size: 13px;
  }
}

@media (max-width: 640px) {
  .top-nav {
    flex-direction: column;
    align-items: flex-start;
    gap: 14px;
  }

  .hero-copy {
    padding: 26px 20px;
  }

  .hero-stats {
    grid-template-columns: 1fr;
  }

  .hero-cta {
    flex-direction: column;
  }

  .btn {
    width: 100%;
    text-align: center;
  }

  .carousel-arrow {
    font-size: 22px;
    left: 12px;
    right: 12px;
  }

  .arrow-left {
    left: 12px;
  }

  .arrow-right {
    right: 12px;
  }

  .intro-card {
    min-height: 110px;
  }

  .intro-media {
    flex-basis: 34px;
    width: 34px;
    height: 34px;
  }

  .intro-card h3 {
    font-size: 17px;
  }

  .intro-card p {
    font-size: 12px;
  }
}

@media (max-width: 640px) {
  .top-nav {
    flex-direction: column;
    align-items: flex-start;
    gap: 14px;
  }

  .hero-copy {
    padding: 26px 20px;
  }

  .hero-stats {
    grid-template-columns: 1fr;
  }

  .hero-cta {
    flex-direction: column;
  }

  .btn {
    width: 100%;
    text-align: center;
  }

  .carousel-arrow {
    font-size: 22px;
    left: 12px;
    right: 12px;
  }

  .arrow-left {
    left: 12px;
  }

  .arrow-right {
    right: 12px;
  }

  .intro-image {
    transform: scale(0.4);
  }

  .intro-card h3 {
    font-size: 12px;
  }

  .intro-card p {
    font-size: 9px;
  }
}
</style>
