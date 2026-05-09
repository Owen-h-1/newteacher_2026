<template>
  <div class="course-select-container">
    <!-- 页面头部（保持不变） -->
    <header class="page-header">
      <div class="brand">
        <div class="logo-icon">
          <i class="fas fa-robot"></i>
        </div>
        <h1>智课云师</h1>
        <span class="badge-ai">AI 赋能</span>
      </div>
      <div class="user-actions">
        <router-link to="/login" class="btn-outline">登录</router-link>
        <router-link to="/register" class="btn-outline">注册</router-link>
      </div>
    </header>

    <!-- 主内容：课程列表（与之前一致） -->
    <main class="main-content">
      <h2 class="section-title"><i class="fas fa-book-open"></i> 我的课程</h2>

      <div class="course-grid">
        <!-- 课程卡片 1 -->
        <div class="course-card">
          <div
            class="course-cover"
            style="background: linear-gradient(145deg, #1e6df2, #0a4bb0)"
          >
            <i class="fas fa-code"></i>
          </div>
          <div class="course-info">
            <h3>前端开发进阶</h3>
            <p class="instructor">张老师 · 8课时</p>
            <div class="progress">
              <span>进度 45%</span>
              <div class="progress-bar"><div style="width: 45%"></div></div>
            </div>
            <button class="btn-continue">继续学习</button>
          </div>
        </div>
        <!-- 课程卡片 2 -->
        <div class="course-card">
          <div
            class="course-cover"
            style="background: linear-gradient(145deg, #0f4c81, #0a3a62)"
          >
            <i class="fas fa-chart-line"></i>
          </div>
          <div class="course-info">
            <h3>数据分析实战</h3>
            <p class="instructor">李老师 · 12课时</p>
            <div class="progress">
              <span>进度 12%</span>
              <div class="progress-bar"><div style="width: 12%"></div></div>
            </div>
            <button class="btn-continue">继续学习</button>
          </div>
        </div>
        <!-- 课程卡片 3 -->
        <div class="course-card">
          <div
            class="course-cover"
            style="background: linear-gradient(145deg, #2c3e50, #1e2b38)"
          >
            <i class="fas fa-robot"></i>
          </div>
          <div class="course-info">
            <h3>AI 基础入门</h3>
            <p class="instructor">王老师 · 6课时</p>
            <div class="progress">
              <span>未开始</span>
              <div class="progress-bar"><div style="width: 0%"></div></div>
            </div>
            <button class="btn-start">开始学习</button>
          </div>
        </div>
      </div>

      <!-- 教师专属后台入口（保持不变） -->
      <div class="teacher-entry">
        <router-link to="/teacher/dashboard" class="btn-teacher">
          <i class="fas fa-chalkboard-teacher"></i> 教师专属后台
          <span class="badge">教师</span>
        </router-link>
        <p class="entry-hint">如果你是教师，点击进入管理后台</p>
      </div>
    </main>

    <!-- 🚀 右侧悬浮 AI 数字人小窗（新增） -->
    <div class="floating-digital-human">
      <AIDigitalHuman :compact="true" />
    </div>
  </div>
</template>

<script setup>
import AIDigitalHuman from "./AIDigitalHuman.vue"; // 注意：当前文件在 public 目录，直接引用同目录下的组件
</script>

<style scoped lang="scss">
.course-select-container {
  min-height: 100vh;
  background: #f6f9fc;
  padding: 32px 40px;
  position: relative; /* 为悬浮定位提供参考 */
}

/* 页面头部（保持不变） */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 48px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 16px;
  .logo-icon {
    width: 48px;
    height: 48px;
    background: linear-gradient(145deg, #1e6df2, #0a4bb0);
    border-radius: 16px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: white;
    font-size: 24px;
    box-shadow: 0 8px 16px rgba(21, 94, 239, 0.2);
  }
  h1 {
    font-size: 28px;
    font-weight: 700;
    color: #0b2b4a;
    margin: 0;
  }
  .badge-ai {
    background: #e8f1ff;
    color: #1e6df2;
    padding: 6px 14px;
    border-radius: 40px;
    font-size: 14px;
    font-weight: 600;
    border: 1px solid #b8d6ff;
  }
}

.user-actions {
  display: flex;
  gap: 12px;
  .btn-outline {
    padding: 10px 24px;
    border-radius: 40px;
    font-weight: 600;
    color: #1e6df2;
    border: 2px solid #1e6df2;
    background: transparent;
    text-decoration: none;
    transition: 0.2s;
    &:hover {
      background: #1e6df2;
      color: white;
    }
  }
}

.section-title {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 24px;
  font-weight: 600;
  color: #0a3e6d;
  margin-bottom: 28px;
  i {
    color: #2563eb;
  }
}

.course-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 28px;
  margin-bottom: 60px;
}

.course-card {
  background: white;
  border-radius: 28px;
  overflow: hidden;
  box-shadow: 0 12px 30px rgba(0, 20, 40, 0.06);
  border: 1px solid rgba(30, 109, 242, 0.12);
  transition: all 0.25s;
  display: flex;
  flex-direction: column;

  &:hover {
    transform: translateY(-4px);
    box-shadow: 0 20px 40px rgba(30, 109, 242, 0.12);
    border-color: rgba(30, 109, 242, 0.3);
  }

  .course-cover {
    height: 140px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 56px;
    color: white;
    position: relative;
    &::after {
      content: "";
      position: absolute;
      bottom: 0;
      left: 0;
      right: 0;
      height: 4px;
      background: rgba(255, 255, 255, 0.3);
    }
  }

  .course-info {
    padding: 24px;
    h3 {
      font-size: 20px;
      font-weight: 700;
      color: #0b2b4a;
      margin-bottom: 6px;
    }
    .instructor {
      color: #5e7e9c;
      font-size: 14px;
      margin-bottom: 16px;
    }
    .progress {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 20px;
      span {
        font-size: 14px;
        font-weight: 600;
        color: #2563eb;
      }
      .progress-bar {
        width: 60%;
        height: 6px;
        background: #e2eaf2;
        border-radius: 10px;
        overflow: hidden;
        div {
          height: 100%;
          background: linear-gradient(90deg, #1e6df2, #0a4bb0);
          border-radius: 10px;
        }
      }
    }
    .btn-continue,
    .btn-start {
      width: 100%;
      padding: 12px;
      border: none;
      border-radius: 40px;
      font-weight: 600;
      font-size: 16px;
      cursor: pointer;
      transition: 0.2s;
    }
    .btn-continue {
      background: #1e6df2;
      color: white;
      &:hover {
        background: #0a4bb0;
      }
    }
    .btn-start {
      background: #e8f1ff;
      color: #1e6df2;
      &:hover {
        background: #d1e0ff;
      }
    }
  }
}

.teacher-entry {
  margin-top: 20px;
  padding: 32px;
  background: linear-gradient(105deg, #e8f1ff, #ffffff);
  border-radius: 32px;
  border: 2px solid #1e6df2;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8px 0 #0a4bb0;

  .btn-teacher {
    display: inline-flex;
    align-items: center;
    gap: 16px;
    background: #1e6df2;
    color: white;
    font-size: 20px;
    font-weight: 700;
    padding: 18px 48px;
    border-radius: 60px;
    text-decoration: none;
    box-shadow: 0 12px 24px rgba(30, 109, 242, 0.4);
    transition: 0.2s;
    i {
      font-size: 24px;
    }
    .badge {
      background: #ffb700;
      color: #0a2e4a;
      padding: 4px 14px;
      border-radius: 40px;
      font-size: 14px;
      font-weight: 700;
      margin-left: 8px;
    }
    &:hover {
      background: #0a4bb0;
      transform: scale(1.02);
    }
  }

  .entry-hint {
    margin-top: 16px;
    color: #2563eb;
    font-size: 15px;
    font-weight: 500;
  }
}

/* 🚀 右侧悬浮数字人样式 */
.floating-digital-human {
  position: fixed;
  bottom: 40px;
  right: 40px;
  width: 280px; /* 紧凑模式下的宽度 */
  z-index: 1000;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.2);
  border-radius: 28px;
  transition: transform 0.25s;
  animation: slideIn 0.5s ease;

  &:hover {
    transform: scale(1.02);
  }
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateX(30px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

/* 小屏幕适配：隐藏或缩小 */
@media (max-width: 768px) {
  .floating-digital-human {
    display: none; /* 移动端可隐藏或缩小 */
  }
}
</style>
