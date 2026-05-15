<template>
  <div class="digital-human" :class="{ 'compact-mode': compact }">
    <!-- 视频区域（模拟数字人） -->
    <div class="video-container">
      <div class="ai-avatar">
        <span class="ai-glow"></span>
        <i class="fas fa-user-robot"></i>
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
        <span>{{ compact ? "通话" : (embedUrl ? "进入数字人" : "视频对话") }}</span>
      </button>
    </div>

    <Teleport to="body">
      <div v-if="showEmbed" class="embed-mask" @click.self="showEmbed = false">
        <div class="embed-card">
          <div class="embed-head">
            <h3>数字人交互</h3>
            <button type="button" class="btn-close" @click="showEmbed = false">关闭</button>
          </div>
          <iframe
            :src="embedUrl"
            title="数字人页面"
            allow="microphone; camera; autoplay; clipboard-read; clipboard-write"
            referrerpolicy="strict-origin-when-cross-origin"
          />
        </div>
      </div>
    </Teleport>
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
const showEmbed = ref(false);
const embedUrl = String(import.meta.env.VITE_DIGITAL_HUMAN_EMBED_URL || "").trim();

const startVideoCall = async () => {
  if (calling.value) return;
  if (embedUrl) {
    showEmbed.value = true;
    return;
  }
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
  background: linear-gradient(145deg, #0b1f2e, #0a1a28);
  border-radius: 32px;
  padding: 28px 24px;
  color: white;
  box-shadow: 0 20px 40px rgba(0, 20, 40, 0.4);
  border: 1px solid rgba(30, 109, 242, 0.3);
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
  background: #0e2a3b;
  border-radius: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px solid #2563eb;
  box-shadow: 0 0 30px rgba(37, 99, 235, 0.3);
  overflow: hidden;

  .ai-avatar {
    font-size: 90px;
    color: #7aa7e0;
    position: relative;
    z-index: 2;
  }

  .ai-glow {
    position: absolute;
    width: 150%;
    height: 150%;
    background: radial-gradient(
      circle at 30% 30%,
      rgba(37, 99, 235, 0.4),
      transparent 70%
    );
    animation: pulse 3s infinite;
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

@keyframes pulse {
  0% {
    opacity: 0.5;
    transform: scale(0.8);
  }
  50% {
    opacity: 0.8;
    transform: scale(1.2);
  }
  100% {
    opacity: 0.5;
    transform: scale(0.8);
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

.embed-mask {
  position: fixed;
  inset: 0;
  background: rgba(8, 26, 42, 0.55);
  z-index: 10000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.embed-card {
  width: min(1100px, 100%);
  height: min(760px, 92vh);
  background: #fff;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 20px 50px rgba(0, 0, 0, 0.35);
  display: flex;
  flex-direction: column;
}

.embed-head {
  height: 52px;
  padding: 0 14px;
  border-bottom: 1px solid #e6edf6;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.embed-head h3 {
  margin: 0;
  color: #173a5e;
  font-size: 16px;
}

.btn-close {
  border: 0;
  background: #eef5ff;
  color: #215ea5;
  border-radius: 8px;
  height: 34px;
  padding: 0 10px;
  cursor: pointer;
}

iframe {
  border: 0;
  width: 100%;
  flex: 1;
}
</style>
