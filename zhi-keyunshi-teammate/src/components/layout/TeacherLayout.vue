<template>
  <div class="teacher-dashboard">
    <!-- 左侧导航侧边栏 -->
    <aside class="sidebar">
      <!-- 品牌标志区 -->
      <div class="sidebar-header">
        <div class="logo-mini">
          <i class="fas fa-robot"></i>
          <span>智课云师</span>
        </div>
        <div class="teacher-badge">教师端</div>
      </div>

      <!-- 主导航菜单 -->
      <nav class="nav-menu">
        <router-link to="/teacher/dashboard" active-class="active">
          <i class="fas fa-tachometer-alt"></i>
          <span>工作台</span>
        </router-link>

        <!-- AI 核心功能1：PPT上传讲解 -->
        <router-link to="/teacher/ppt-upload" active-class="active" class="ai-feature">
          <i class="fas fa-file-powerpoint"></i>
          <span>PPT 上传讲解</span>
          <span class="ai-badge">AI</span>
        </router-link>

        <!-- AI 核心功能2：AI 课件生成 -->
        <router-link to="/teacher/ai-courseware" active-class="active" class="ai-feature">
          <i class="fas fa-robot"></i>
          <span>AI 课件生成</span>
          <span class="ai-badge">AI</span>
        </router-link>

        <!-- AI 辅助：教学设计 -->
        <router-link to="/teacher/teaching-design" active-class="active">
          <i class="fas fa-chalkboard-teacher"></i>
          <span>教学设计辅助</span>
        </router-link>

        <!-- 课堂签到 -->
        <router-link to="/teacher/signin" active-class="active">
          <i class="fas fa-qrcode"></i>
          <span>课堂签到</span>
        </router-link>

        <!-- AI 核心功能3：学生数据看板 -->
        <router-link to="/teacher/student-data" active-class="active" class="ai-feature">
          <i class="fas fa-chart-line"></i>
          <span>学生数据看板</span>
          <span class="ai-badge">AI</span>
        </router-link>

        <router-link to="/teacher/student-manage" active-class="active">
          <i class="fas fa-user-plus"></i>
          <span>学生管理</span>
        </router-link>

        <!-- 题目发布 -->
        <router-link to="/teacher/publish" active-class="active">
          <i class="fas fa-tasks"></i>
          <span>题目发布</span>
        </router-link>
        <router-link to="/teacher/homework-manage" active-class="active">
          <i class="fas fa-clipboard-list"></i>
          <span>学生作业管理</span>
        </router-link>
      </nav>

      <!-- 左下角：当前用户 + 退出登录 -->
      <div class="sidebar-footer">
        <div class="teacher-info">
          <i class="fas fa-user-circle"></i>
          <span>{{ teacherName }}</span>
        </div>
        <button type="button" class="logout-btn" title="退出登录" @click="logout">
          <i class="fas fa-sign-out-alt"></i>
          <span>退出登录</span>
        </button>
      </div>
    </aside>

    <!-- 右侧主内容区：动态渲染教师页面 -->
    <main class="main-content">
      <router-view />
    </main>

    <div v-if="showOnboarding" class="onboarding-mask" @click.self="closeOnboarding">
      <div class="onboarding-card">
        <div class="onboarding-icon"><i class="fas fa-school"></i></div>
        <h3>欢迎使用教师端</h3>
        <p>你还没有创建班级。先创建班级并生成邀请码，学生才能加入并看到你的作业、签到和数据看板。</p>
        <div class="onboarding-actions">
          <button class="btn-skip" @click="closeOnboarding">稍后再说</button>
          <button class="btn-go" @click="goCreateClass">去创建班级</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { clearSession, fetchSigninClasses, getCurrentUser } from "@/utils/api";

const router = useRouter();
const teacherName = computed(() => getCurrentUser()?.name || "老师");
const showOnboarding = ref(false);

const logout = () => {
  clearSession();
  router.push("/login");
};

const onboardingKey = computed(() => {
  const uid = getCurrentUser()?.id || "unknown";
  return `zkys_teacher_onboarding_closed_${uid}`;
});

const closeOnboarding = () => {
  showOnboarding.value = false;
  sessionStorage.setItem(onboardingKey.value, "1");
};

const goCreateClass = () => {
  closeOnboarding();
  router.push("/teacher/student-manage");
};

onMounted(async () => {
  if (sessionStorage.getItem(onboardingKey.value) === "1") return;
  try {
    const data = await fetchSigninClasses();
    const classes = Array.isArray(data?.classes) ? data.classes : [];
    showOnboarding.value = classes.length === 0;
  } catch {
    showOnboarding.value = false;
  }
});
</script>

<style scoped lang="scss">
.teacher-dashboard {
  display: flex;
  height: 100vh;
  background: #f6f9fc;
  font-family: "Inter", sans-serif;
}

/* 左侧侧边栏 — 深蓝科技感，磨砂玻璃效果 */
.sidebar {
  width: 280px;
  min-height: 0;
  background: linear-gradient(180deg, #0b1f2e 0%, #0a1a28 100%);
  color: white;
  display: flex;
  flex-direction: column;
  box-shadow: 4px 0 20px rgba(0, 0, 0, 0.15);
  border-right: 1px solid #1e3a5f;
  backdrop-filter: blur(4px);
  padding: 28px 0 0;
}

.sidebar-header {
  padding: 0 24px 24px 24px;
  border-bottom: 1px solid rgba(30, 109, 242, 0.3);
  margin-bottom: 24px;

  .logo-mini {
    display: flex;
    align-items: center;
    gap: 12px;
    font-size: 22px;
    font-weight: 700;
    margin-bottom: 12px;
    i {
      color: #4b9fff;
      font-size: 28px;
    }
    span {
      background: linear-gradient(135deg, #fff, #b8d6ff);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
    }
  }

  .teacher-badge {
    display: inline-block;
    background: #1e6df2;
    padding: 6px 16px;
    border-radius: 40px;
    font-size: 13px;
    font-weight: 600;
    letter-spacing: 1px;
    border: 1px solid rgba(255, 255, 255, 0.2);
    box-shadow: 0 4px 10px rgba(30, 109, 242, 0.4);
  }
}

/* 导航菜单 */
.nav-menu {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 0 16px;

  a {
    display: flex;
    align-items: center;
    gap: 14px;
    padding: 14px 20px;
    border-radius: 18px;
    color: #c0d4f0;
    text-decoration: none;
    font-weight: 500;
    transition: all 0.2s;
    position: relative;
    border: 1px solid transparent;

    i {
      width: 24px;
      text-align: center;
      font-size: 18px;
    }

    span {
      flex: 1;
      font-size: 15px;
    }

    &:hover {
      background: rgba(30, 109, 242, 0.2);
      color: white;
      border-color: rgba(30, 109, 242, 0.5);
    }

    &.active {
      background: #1e6df2;
      color: white;
      box-shadow: 0 8px 16px rgba(30, 109, 242, 0.3);
    }
  }

  /* AI 功能条目右侧徽章 */
  .ai-feature {
    .ai-badge {
      background: #ffb700;
      color: #0a2e4a;
      font-size: 12px;
      font-weight: 700;
      padding: 4px 10px;
      border-radius: 40px;
      margin-left: auto;
      letter-spacing: 0.5px;
      box-shadow: 0 2px 6px rgba(255, 183, 0, 0.4);
    }

    &:hover .ai-badge {
      background: #ffcc00;
    }

    &.active .ai-badge {
      background: white;
      color: #1e6df2;
    }
  }
}

/* 底部教师信息 */
.sidebar-footer {
  padding: 20px 20px 12px;
  margin-top: auto;
  border-top: 1px solid rgba(30, 109, 242, 0.2);
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 12px;

  .teacher-info {
    display: flex;
    align-items: center;
    gap: 12px;
    color: #c0d4f0;
    min-width: 0;
    i {
      font-size: 24px;
      color: #7aa7e0;
      flex-shrink: 0;
    }
    span {
      font-size: 15px;
      font-weight: 500;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }

  .logout-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    width: 100%;
    background: transparent;
    border: 1px solid rgba(255, 255, 255, 0.22);
    color: #c0d4f0;
    padding: 10px 14px;
    border-radius: 12px;
    cursor: pointer;
    font-size: 14px;
    font-weight: 600;
    transition: 0.2s;
    &:hover {
      background: rgba(255, 77, 77, 0.18);
      color: #ffb4b4;
      border-color: rgba(255, 138, 138, 0.6);
    }
  }
}

/* 右侧主内容区 — 白色卡片背景，与全局统一 */
.main-content {
  flex: 1;
  padding: 32px 40px;
  overflow-y: auto;
  background: #f6f9fc;
}

.onboarding-mask {
  position: fixed;
  inset: 0;
  background: rgba(9, 24, 40, 0.5);
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}
.onboarding-card {
  width: min(520px, 100%);
  background: #fff;
  border-radius: 20px;
  border: 1px solid #dce8f7;
  box-shadow: 0 24px 48px rgba(0, 0, 0, 0.2);
  padding: 24px 22px;
  text-align: center;
}
.onboarding-icon {
  width: 56px;
  height: 56px;
  margin: 0 auto 12px;
  border-radius: 14px;
  background: linear-gradient(145deg, #1e6df2, #0a4bb0);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
}
.onboarding-card h3 {
  margin: 0 0 8px;
  color: #0b2b4a;
}
.onboarding-card p {
  margin: 0;
  color: #5d7c9b;
  line-height: 1.6;
}
.onboarding-actions {
  margin-top: 16px;
  display: flex;
  justify-content: center;
  gap: 10px;
}
.btn-skip,
.btn-go {
  height: 40px;
  border-radius: 12px;
  padding: 0 14px;
  border: 1px solid #c8d9ef;
  background: #fff;
  color: #335a84;
  font-weight: 600;
  cursor: pointer;
}
.btn-go {
  border-color: #1e6df2;
  background: #1e6df2;
  color: #fff;
}

/* 确保 router-view 渲染的页面默认有卡片样式（可选，但建议每个页面自己控制） */
:deep(.page) {
  background: white;
  border-radius: 28px;
  padding: 28px;
  box-shadow: 0 12px 30px rgba(0, 20, 40, 0.06);
  border: 1px solid rgba(30, 109, 242, 0.1);
}
</style>
