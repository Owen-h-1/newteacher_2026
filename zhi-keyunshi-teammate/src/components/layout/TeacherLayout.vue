<template>
  <div class="teacher-dashboard teacher-app-dark">
    <div class="teacher-floating-sky" aria-hidden="true">
      <span class="cloud cloud-1">☁</span>
      <span class="cloud cloud-2">☁</span>
      <span class="star star-1">✦</span>
      <span class="star star-2">✧</span>
      <span class="star star-3">✦</span>
    </div>

    <header class="teacher-shell-header">
      <div class="teacher-brand">
        <div class="logo-mini">
          <i class="fas fa-robot"></i>
          <span>智课云师</span>
        </div>
        <span class="teacher-badge">教师端</span>
      </div>

      <div class="teacher-fantasy-banner" aria-hidden="true">
        <img class="banner-art" :src="teacherBannerArt" alt="" />
        <div class="banner-caption">
          <span>魔法课堂</span>
          <small>教案 · 课件 · 数据 · 签到</small>
        </div>
      </div>

      <nav class="teacher-top-nav" aria-label="教师端主导航">
        <router-link to="/teacher/dashboard" active-class="active">
          <i class="fas fa-tachometer-alt"></i>
          <span>工作台</span>
        </router-link>

        <router-link to="/teacher/ppt-upload" active-class="active" class="ai-feature">
          <i class="fas fa-file-powerpoint"></i>
          <span>PPT 上传讲解</span>
          <span class="ai-badge">AI</span>
        </router-link>

        <router-link to="/teacher/ai-courseware" active-class="active" class="ai-feature">
          <i class="fas fa-robot"></i>
          <span>AI 课件生成</span>
          <span class="ai-badge">AI</span>
        </router-link>

        <router-link to="/teacher/teaching-design" active-class="active">
          <i class="fas fa-chalkboard-teacher"></i>
          <span>教学设计辅助</span>
        </router-link>

        <router-link to="/teacher/signin" active-class="active">
          <i class="fas fa-qrcode"></i>
          <span>课堂签到</span>
        </router-link>

        <router-link to="/teacher/student-data" active-class="active" class="ai-feature">
          <i class="fas fa-chart-line"></i>
          <span>学生数据看板</span>
          <span class="ai-badge">AI</span>
        </router-link>

        <router-link to="/teacher/student-manage" active-class="active">
          <i class="fas fa-user-plus"></i>
          <span>学生管理</span>
        </router-link>

        <router-link to="/teacher/publish" active-class="active">
          <i class="fas fa-tasks"></i>
          <span>题目发布</span>
        </router-link>

        <router-link to="/teacher/homework-manage" active-class="active">
          <i class="fas fa-clipboard-list"></i>
          <span>学生作业管理</span>
        </router-link>
      </nav>

      <div class="teacher-header-actions">
        <div class="teacher-info">
          <i class="fas fa-user-circle"></i>
          <span>{{ teacherName }}</span>
        </div>
        <button type="button" class="logout-btn" title="退出登录" @click="logout">
          <i class="fas fa-sign-out-alt"></i>
          <span>退出登录</span>
        </button>
      </div>
    </header>

    <main class="main-content">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { computed } from "vue";
import { useRouter } from "vue-router";
import { clearSession, getCurrentUser } from "@/utils/api";
import teacherBannerArt from "@/assets/fantasy-academy.svg";

const router = useRouter();
const teacherName = computed(() => getCurrentUser()?.name || "老师");

const logout = () => {
  clearSession();
  router.push("/login");
};
</script>

<style scoped lang="scss">
.teacher-dashboard {
  display: flex;
  flex-direction: column;
  height: 100vh;
  min-height: 0;
  background: linear-gradient(180deg, #0b1d3a 0%, #14335f 42%, #1e5778 100%);
  font-family: "Inter", -apple-system, sans-serif;
  position: relative;
  overflow: hidden;
}

.teacher-floating-sky {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.teacher-floating-sky .cloud,
.teacher-floating-sky .star {
  position: absolute;
  opacity: 0.18;
  animation: float-slow 12s ease-in-out infinite;
}

.teacher-floating-sky .cloud {
  color: #fff;
  font-size: 28px;
}

.teacher-floating-sky .star {
  color: #ffcf72;
  font-size: 18px;
}

.teacher-floating-sky .cloud-1 { top: 48px; left: 4%; animation-delay: 0s; }
.teacher-floating-sky .cloud-2 { top: 172px; right: 12%; animation-delay: 3s; }
.teacher-floating-sky .star-1 { top: 94px; left: 20%; animation-delay: 1s; }
.teacher-floating-sky .star-2 { top: 230px; right: 20%; animation-delay: 4s; }
.teacher-floating-sky .star-3 { top: 340px; left: 8%; animation-delay: 5s; }

.teacher-shell-header {
  display: grid;
  grid-template-columns: auto minmax(280px, 420px) 1fr auto;
  gap: 14px 18px;
  align-items: center;
  padding: 14px 20px 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.12);
  flex-shrink: 0;
  position: relative;
  z-index: 1;
  background: linear-gradient(180deg, rgba(5, 18, 36, 0.18), rgba(5, 18, 36, 0));
}

.teacher-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.logo-mini {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 18px;
  font-weight: 800;
  color: #fff;

  i { font-size: 22px; color: #ff9a44; }
}

.teacher-badge {
  display: inline-block;
  background: rgba(255, 255, 255, 0.14);
  padding: 5px 12px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: #fff;
  border: 1px solid rgba(255, 255, 255, 0.22);
}

.teacher-fantasy-banner {
  position: relative;
  min-height: 118px;
  border-radius: 20px;
  overflow: hidden;
  background: linear-gradient(135deg, rgba(255,255,255,0.14), rgba(255,255,255,0.05));
  border: 1px solid rgba(255, 255, 255, 0.12);
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.08);
}

.banner-art {
  display: block;
  width: 100%;
  height: 100%;
  max-height: 150px;
  object-fit: cover;
}

.banner-caption {
  position: absolute;
  left: 14px;
  bottom: 12px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  color: #fff;
  text-shadow: 0 2px 8px rgba(0,0,0,0.28);

  span { font-size: 18px; font-weight: 800; }
  small { font-size: 12px; opacity: 0.92; }
}

.teacher-top-nav {
  flex: 1;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  min-width: 0;

  a {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    padding: 8px 14px;
    border-radius: 12px;
    font-size: 13px;
    font-weight: 700;
    color: rgba(255, 255, 255, 0.82);
    text-decoration: none;
    border: 1px solid transparent;
    transition: background 0.2s ease, color 0.2s ease, border-color 0.2s ease, transform 0.2s ease;

    &:hover {
      background: rgba(255, 255, 255, 0.1);
      color: #fff;
      border-color: rgba(255, 255, 255, 0.12);
      transform: translateY(-1px);
    }

    &.active {
      background: linear-gradient(135deg, #ff9a44, #e86f12);
      color: #fff;
      border-color: rgba(255, 200, 140, 0.48);
      box-shadow: 0 10px 18px rgba(255, 120, 40, 0.26);
    }
  }

  .ai-badge {
    margin-left: 4px;
    font-size: 10px;
    font-weight: 800;
    padding: 2px 7px;
    border-radius: 999px;
    background: rgba(255, 255, 255, 0.18);
    color: #fff;
    border: 1px solid rgba(255, 255, 255, 0.2);
  }
}

.teacher-header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
  margin-left: auto;
}

.teacher-info {
  display: flex;
  align-items: center;
  gap: 8px;
  color: rgba(255, 255, 255, 0.88);
  font-size: 13px;
  font-weight: 600;
  max-width: 160px;
}

.logout-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  background: transparent;
  color: rgba(255, 255, 255, 0.92);
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: 0.2s;

  &:hover {
    background: rgba(255, 77, 77, 0.18);
    color: #ffcbcb;
    border-color: rgba(255, 138, 138, 0.55);
  }
}

@keyframes float-slow {
  0%, 100% { transform: translateY(0) translateX(0); }
  50% { transform: translateY(-12px) translateX(8px); }
}

@media (max-width: 1320px) {
  .teacher-shell-header {
    grid-template-columns: auto 1fr auto;
  }

  .teacher-fantasy-banner {
    grid-column: 1 / -1;
    order: 3;
  }
}

@media (max-width: 1100px) {
  .teacher-header-actions {
    width: 100%;
    margin-left: 0;
    justify-content: flex-end;
  }
}
</style>
