<template>
  <div class="student-dashboard student-app-dark">
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

    <main class="content-panel">
      <nav class="student-nav">
        <router-link to="/student/learning" active-class="active">
          <i class="fas fa-home"></i>
          <span>学习台</span>
        </router-link>
        <router-link to="/student/analysis" active-class="active">
          <i class="fas fa-comments"></i>
          <span>AI问答</span>
        </router-link>
        <router-link to="/student/selftest" active-class="active">
          <i class="fas fa-pencil-alt"></i>
          <span>自主练习</span>
        </router-link>
        <router-link to="/student/homework" active-class="active">
          <i class="fas fa-tasks"></i>
          <span>作业任务</span>
        </router-link>
        <router-link to="/student/join-class" active-class="active">
          <i class="fas fa-door-open"></i>
          <span>加入班级</span>
        </router-link>
        <router-link to="/student/smart-ppt" active-class="active">
          <i class="fas fa-chalkboard-teacher"></i>
          <span>智讲PPT</span>
        </router-link>
      </nav>

      <div class="edu-main-surface">
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
  min-height: 0;
  background: linear-gradient(180deg, #12345d 0%, #1b5c74 42%, #2c7f7c 100%);
  font-family: "Inter", -apple-system, sans-serif;
}

.digital-human-panel {
  width: 380px;
  min-height: 0;
  background: linear-gradient(180deg, #15365f 0%, #1b5c74 48%, #2c7f7c 100%);
  display: flex;
  flex-direction: column;
  padding: 20px 16px 12px;
  box-shadow: 4px 0 24px rgba(16, 47, 82, 0.12);
  border-right: 1px solid rgba(255, 255, 255, 0.1);
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
  border-top: 1px solid rgba(255, 255, 255, 0.16);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.student-user-line {
  display: flex;
  align-items: center;
  gap: 10px;
  color: rgba(255, 255, 255, 0.84);
  font-size: 14px;
  font-weight: 600;
  min-width: 0;

  i {
    font-size: 22px;
    color: rgba(255, 255, 255, 0.58);
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
  border-radius: var(--edu-radius-control, 10px);
  border: 1px solid rgba(255, 255, 255, 0.2);
  background: transparent;
  color: rgba(255, 255, 255, 0.9);
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  transition: 0.2s;

  &:hover {
    background: rgba(255, 77, 77, 0.18);
    color: #ffcbcb;
    border-color: rgba(255, 138, 138, 0.55);
  }
}

.content-panel {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: 18px 22px 24px;
  overflow-y: auto;
  background: transparent;
  position: relative;
}

.content-panel::before {
  content: "✦  ✧  ✦  ✧";
  position: absolute;
  top: 10px;
  right: 18px;
  font-size: 18px;
  color: rgba(255, 255, 255, 0.38);
  pointer-events: none;
}

.student-nav {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  background: linear-gradient(180deg, rgba(22, 48, 89, 0.94) 0%, rgba(15, 33, 68, 0.96) 100%);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 18px;
  padding: 12px 14px;
  margin-bottom: 16px;
  box-shadow: 0 10px 24px rgba(4, 12, 24, 0.15);
  flex-shrink: 0;

  a {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    padding: 10px 14px;
    border-radius: 12px;
    font-weight: 700;
    font-size: 13px;
    color: rgba(255, 255, 255, 0.84);
    transition: all 0.2s ease;
    text-decoration: none;
    border: 1px solid rgba(255, 255, 255, 0.1);
    background: rgba(255, 255, 255, 0.04);

    i {
      font-size: 14px;
    }

    &:hover {
      color: #fff;
      border-color: rgba(255, 154, 68, 0.35);
      background: rgba(255, 154, 68, 0.1);
      transform: translateY(-1px);
    }

    &.active {
      background: linear-gradient(135deg, #ff9a44, #e86f12);
      color: #fff;
      border-color: rgba(255, 200, 140, 0.48);
      box-shadow: 0 10px 18px rgba(255, 120, 40, 0.24);
    }
  }
}

.edu-main-surface {
  flex: 1;
  min-height: 0;
  background: transparent;
  border: none;
  box-shadow: none;
  border-radius: 0;
  padding: 4px 0 8px;
}
</style>
