<template>
  <div class="digital-human" :class="{ 'compact-mode': compact }">
    <!-- 视频区域（模拟数字人） -->
    <div class="video-container">
      <div class="ai-avatar">
        <span class="ai-glow"></span>
        <span class="floating-star star-a">⭐</span>
        <span class="floating-star star-b">✨</span>
        <span class="floating-star star-c">🌟</span>
        <div class="boy-avatar" aria-label="卡通小男孩挥手">
          <span class="boy-face">🙂</span>
          <span class="boy-hand">👋</span>
        </div>
      </div>
      <div class="live-badge" v-if="!compact">LIVE</div>
    </div>

    <!-- 信息与操作区 -->
    <div class="info-area">
      <div class="ai-title">
        <h3>AI 助教 · 小智</h3>
        <span class="status online">在线</span>
      </div>
      <p class="ai-subtitle" v-if="!compact">7×24h 实时解答，你的专属学习伙伴</p>

      <button class="btn-call" @click="startVideoCall">
        <i class="fas fa-video"></i>
        <span>{{ compact ? "通话" : "视频对话" }}</span>
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref } from "vue";
import { chatWithDigitalHuman } from "@/utils/api";

defineProps({
  compact: {
    type: Boolean,
    default: false,
  },
});

const calling = ref(false);

const startVideoCall = async () => {
  if (calling.value) return;
  calling.value = true;
  try {
    await chatWithDigitalHuman({
      message: "发起视频通话",
      sessionId: "",
      voiceId: "",
      avatarId: "",
    });
  } catch (e) {
    window.alert(e.message || "数字人服务调用失败");
  } finally {
    calling.value = false;
  }
};
</script>

<style scoped lang="scss">
.digital-human {
  background: linear-gradient(155deg, #1d4f7d 0%, #2a73b5 35%, #4aa1d8 70%, #80d38f 100%);
  border-radius: 32px;
  padding: 28px 24px;
  color: white;
  box-shadow: 0 20px 40px rgba(0, 20, 40, 0.3);
  border: 1px solid rgba(255, 255, 255, 0.32);
  transition: all 0.2s;

  &.compact-mode {
    padding: 20px 16px;
    .video-container {
      width: 100px;
      height: 100px;
      margin-bottom: 16px;
    }
    .ai-title h3 {
      font-size: 18px;
    }
    .btn-call {
      padding: 8px 16px;
    }
  }
}

.video-container {
  position: relative;
  width: 200px;
  height: 200px;
  margin: 0 auto 24px;
  background: linear-gradient(160deg, #10314f, #194d72);
  border-radius: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px solid #2563eb;
  box-shadow: 0 0 30px rgba(255, 255, 255, 0.22);
  overflow: hidden;

  .ai-avatar {
    position: relative;
    z-index: 2;
  }

  .boy-avatar {
    position: relative;
    width: 132px;
    height: 132px;
    border-radius: 26px;
    background: linear-gradient(160deg, #113a57, #0c2e47);
    border: 2px solid rgba(122, 167, 224, 0.55);
    display: flex;
    align-items: center;
    justify-content: center;
    box-shadow: inset 0 0 24px rgba(122, 167, 224, 0.15);
  }

  .boy-face {
    font-size: 72px;
    line-height: 1;
    filter: saturate(1.1);
    animation: bounce-face 2.2s ease-in-out infinite;
  }

  .boy-hand {
    position: absolute;
    right: 8px;
    top: 14px;
    font-size: 34px;
    animation: wave 1.4s ease-in-out infinite;
    transform-origin: 70% 70%;
  }

  .ai-glow {
    position: absolute;
    width: 150%;
    height: 150%;
    background: radial-gradient(circle at 30% 30%, rgba(255, 255, 255, 0.26), transparent 70%);
    animation: pulse 3s infinite;
  }

  .floating-star {
    position: absolute;
    z-index: 3;
    font-size: 18px;
    animation: star-float 2.8s ease-in-out infinite;
  }
  .star-a {
    left: 18px;
    top: 20px;
  }
  .star-b {
    right: 24px;
    bottom: 18px;
    animation-delay: 0.6s;
  }
  .star-c {
    left: 24px;
    bottom: 20px;
    animation-delay: 1.2s;
  }

  .live-badge {
    position: absolute;
    top: 12px;
    right: 12px;
    background: #ff4d4d;
    color: white;
    font-size: 12px;
    font-weight: 700;
    padding: 4px 10px;
    border-radius: 30px;
    letter-spacing: 1px;
    border: 1px solid rgba(255, 255, 255, 0.5);
  }
}

@keyframes wave {
  0%,
  100% {
    transform: rotate(0deg);
  }
  25% {
    transform: rotate(14deg);
  }
  75% {
    transform: rotate(-10deg);
  }
}

@keyframes pulse {
  0%,
  100% {
    opacity: 0.5;
    transform: scale(0.82);
  }
  50% {
    opacity: 0.86;
    transform: scale(1.18);
  }
}

@keyframes star-float {
  0%,
  100% {
    transform: translateY(0);
    opacity: 0.8;
  }
  50% {
    transform: translateY(-6px);
    opacity: 1;
  }
}

@keyframes bounce-face {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-4px);
  }
}

.info-area {
  text-align: center;

  .ai-title {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 10px;
    margin-bottom: 8px;
    h3 {
      font-size: 22px;
      font-weight: 700;
      margin: 0;
      background: linear-gradient(135deg, #fff, #d9ebff);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
    }
    .status {
      font-size: 12px;
      padding: 4px 10px;
      border-radius: 40px;
      background: #1e8b4c;
      color: white;
      font-weight: 600;
      &.online::before {
        content: "●";
        margin-right: 4px;
        font-size: 14px;
      }
    }
  }

  .ai-subtitle {
    color: #a0c6e9;
    font-size: 14px;
    margin-bottom: 20px;
  }
}

.btn-call {
  background: linear-gradient(145deg, #2563eb, #1a4bb0);
  border: none;
  color: white;
  font-weight: 700;
  padding: 14px 32px;
  border-radius: 50px;
  display: inline-flex;
  align-items: center;
  gap: 12px;
  font-size: 16px;
  cursor: pointer;
  transition: 0.2s;
  border: 1px solid rgba(255, 255, 255, 0.2);
  box-shadow: 0 8px 20px rgba(21, 94, 239, 0.3);

  i {
    font-size: 18px;
  }
  &:hover {
    background: #2563eb;
    transform: scale(1.02);
    box-shadow: 0 12px 28px rgba(37, 99, 235, 0.5);
  }
}
</style>
