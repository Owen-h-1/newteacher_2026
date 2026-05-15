<template>
  <div class="student-dashboard">
    <!-- 左侧：数字人 + 左下角退出登录 -->
    <aside class="digital-human-panel">
      <div class="dh-scroll">
        <AIDigitalHuman :compact="false" />
      </div>
      <div class="student-sidebar-footer">
        <div class="student-user-line">
          <i class="fas fa-user-circle"></i>
          <span>{{ studentName }}</span>
        </div>
        <button type="button" class="logout-btn" @click="logout">
          <i class="fas fa-sign-out-alt"></i>
          <span>退出登录</span>
        </button>
      </div>
    </aside>

    <!-- 右侧内容区 -->
    <main class="content-panel">
      <!-- 顶部导航标签 -->
      <nav class="student-nav">
        <router-link to="/student/learning" active-class="active">
          <i class="fas fa-home"></i> 学习台
        </router-link>
        <router-link to="/student/analysis" active-class="active">
          <i class="fas fa-comments"></i> AI问答
        </router-link>
        <router-link to="/student/selftest" active-class="active">
          <i class="fas fa-pencil-alt"></i> 自主练习
        </router-link>
        <router-link to="/student/homework" active-class="active">
          <i class="fas fa-tasks"></i> 作业任务
        </router-link>
        <router-link to="/student/join-class" active-class="active">
          <i class="fas fa-door-open"></i> 加入班级
        </router-link>
        <router-link to="/student/smart-ppt" active-class="active">
          <i class="fas fa-chalkboard-teacher"></i> 智讲PPT
        </router-link>
      </nav>

      <!-- 动态页面容器 -->
      <div class="page-container">
        <router-view />
      </div>
    </main>
  </div>
</template>

<script setup>
import { computed } from "vue";
import { useRouter } from "vue-router";
import AIDigitalHuman from "@/views/public/AIDigitalHuman.vue";
import { clearSession, getCurrentUser } from "@/utils/api";

const router = useRouter();
const studentName = computed(() => getCurrentUser()?.name || getCurrentUser()?.username || "学生");

const logout = () => {
  clearSession();
  router.push("/login");
};
</script>

<style scoped lang="scss">
.student-dashboard {
  display: flex;
  height: 100vh;
  background: #f6f9fc;
}

/* 左侧数字人面板 — 固定宽度，深色科技感 */
.digital-human-panel {
  width: 380px;
  min-height: 0;
  background: #0b1a28;
  display: flex;
  flex-direction: column;
  padding: 20px 16px 12px;
  box-shadow: 4px 0 20px rgba(0, 0, 0, 0.1);
  border-right: 1px solid #1e3a5f;
}

.dh-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 0 4px;
}

.student-sidebar-footer {
  flex-shrink: 0;
  margin-top: 12px;
  padding-top: 16px;
  border-top: 1px solid rgba(30, 109, 242, 0.25);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.student-user-line {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #b8cce8;
  font-size: 14px;
  font-weight: 500;
  min-width: 0;
  i {
    font-size: 22px;
    color: #7aa7e0;
    flex-shrink: 0;
  }
  span {
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
  padding: 10px 14px;
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.22);
  background: transparent;
  color: #c8daf5;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: 0.2s;
  &:hover {
    background: rgba(255, 77, 77, 0.18);
    color: #ffb4b4;
    border-color: rgba(255, 138, 138, 0.55);
  }
}

/* 右侧主内容区 */
.content-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 32px 40px;
  overflow-y: auto;
}

/* 导航标签 — 蓝白风格 */
.student-nav {
  display: flex;
  gap: 12px;
  border-bottom: 2px solid rgba(30, 109, 242, 0.2);
  padding-bottom: 12px;
  margin-bottom: 28px;

  a {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px 24px;
    border-radius: 40px;
    font-weight: 600;
    color: #4a668a;
    transition: all 0.2s;
    text-decoration: none;

    i {
      font-size: 16px;
    }

    &:hover {
      background: #e8f1ff;
      color: #1e6df2;
    }

    &.active {
      background: #1e6df2;
      color: white;
      box-shadow: 0 6px 14px rgba(30, 109, 242, 0.3);
    }
  }
}

/* 页面内容卡片区域 — 白色圆角卡片 */
.page-container {
  flex: 1;
  background: white;
  border-radius: 32px;
  padding: 32px;
  box-shadow: 0 12px 30px rgba(0, 20, 40, 0.06);
  border: 1px solid rgba(30, 109, 242, 0.1);
}
</style>
