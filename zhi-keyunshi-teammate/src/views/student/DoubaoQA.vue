<template>
  <div class="doubao-qa">
    <div class="page-header">
      <div class="title-text">
        <h1>AI 智能问答</h1>
        <p class="subtitle">输入学习问题，获取实时答疑建议</p>
      </div>
      <div class="ai-badge-large connection-pill">
        <i
          class="fas"
          :class="llmStatus.ollama.available ? 'fa-link' : 'fa-unlink'"
        ></i>
        <div class="connection-pill-body">
          <span class="connection-line">当前：{{ currentProviderLabel }}</span>
          <span class="status-chip" :class="statusClass(llmStatus.ollama)">
            Ollama（{{ ollamaModel }}）
          </span>
        </div>
      </div>
    </div>

    <div class="chat-card">
      <div class="chat-list">
        <div v-for="item in messages" :key="item.id" class="chat-item" :class="item.role">
          {{ item.text }}
        </div>
      </div>

      <div class="chat-input-row">
        <input
          v-model.trim="inputText"
          class="chat-input"
          type="text"
          maxlength="500"
          placeholder="例如：三年级乘法应用题总是审题错，怎么练？"
          @keyup.enter="sendMessage"
        />
        <button class="btn-send" :disabled="loading || !inputText" @click="sendMessage">
          {{ loading ? "思考中..." : "发送" }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from "vue";
import { chatWithLLM, fetchLLMStatus, getCurrentUser } from "@/utils/api";

const CHAT_STORAGE_PREFIX = "zkys_student_ai_chat";

function chatStorageKey() {
  const u = getCurrentUser();
  const id = u?.id ?? u?.username ?? "local";
  return `${CHAT_STORAGE_PREFIX}_${id}`;
}

function defaultWelcomeMessage() {
  return {
    id: 1,
    role: "assistant",
    text: "你好呀！我是你的学习小助手，有不会的题目或想聊聊怎么学习，都可以跟我说～",
  };
}

function loadPersistedMessages() {
  try {
    const raw = localStorage.getItem(chatStorageKey());
    if (!raw) return [defaultWelcomeMessage()];
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed) || !parsed.length) return [defaultWelcomeMessage()];
    const cleaned = parsed.filter(
      (m) =>
        m &&
        typeof m.id === "number" &&
        (m.role === "user" || m.role === "assistant") &&
        typeof m.text === "string"
    );
    return cleaned.length ? cleaned : [defaultWelcomeMessage()];
  } catch {
    return [defaultWelcomeMessage()];
  }
}

function persistMessages(list) {
  try {
    localStorage.setItem(chatStorageKey(), JSON.stringify(list));
  } catch {
    /* quota or private mode */
  }
}

const loading = ref(false);
const inputText = ref("");
const lastProvider = ref("");
const ollamaModel = ref("qwen2.5:1.5b");
const llmStatus = ref({
  ollama: { configured: true, available: false },
});
const messages = ref(loadPersistedMessages());

watch(
  messages,
  (v) => {
    persistMessages(v);
  },
  { deep: true }
);

const currentProviderLabel = computed(() => {
  if (lastProvider.value) return lastProvider.value;
  if (llmStatus.value.ollama.available) return "ollama";
  return "未连接";
});

const statusClass = (obj) => {
  if (!obj?.configured) return "off";
  return obj?.available ? "on" : "warn";
};

const loadLLMStatus = async () => {
  try {
    const res = await fetchLLMStatus();
    if (res?.status) llmStatus.value = { ...llmStatus.value, ...res.status };
    if (res?.ollamaModel) ollamaModel.value = res.ollamaModel;
  } catch {
    // keep default status
  }
};

const sendMessage = async () => {
  const text = String(inputText.value || "").trim();
  if (!text || loading.value) return;
  messages.value.push({ id: Date.now(), role: "user", text });
  inputText.value = "";
  loading.value = true;
  try {
    const res = await chatWithLLM(text);
    if (res?.provider) lastProvider.value = res.provider;
    messages.value.push({
      id: Date.now() + 1,
      role: "assistant",
      text: res?.answer || "嗯……我这边暂时没有想好怎么说，你再问一次好不好？",
    });
  } catch (e) {
    messages.value.push({
      id: Date.now() + 1,
      role: "assistant",
      text: e.message || "模型服务暂时不可用，请稍后重试。",
    });
  } finally {
    loading.value = false;
  }
};

onMounted(loadLLMStatus);
</script>

<style scoped lang="scss">
.doubao-qa {
  padding: 10px 0;
  position: relative;
  overflow: hidden;
}

.doubao-qa::before {
  content: "🍃 ✨ 🍃";
  position: absolute;
  top: 6px;
  right: 10px;
  opacity: 0.26;
  font-size: 16px;
}

.doubao-qa::after {
  content: "🦋 🍬 🌈";
  position: absolute;
  bottom: 8px;
  left: 10px;
  opacity: 0.24;
  font-size: 16px;
}
.page-header {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 22px;
  flex-wrap: wrap;
  background: transparent;
  border: none;
  border-radius: 0;
  padding: 0 0 4px;
  justify-content: space-between;
}

.title-text {
  flex: 1;
  min-width: 200px;
}

.title-text h1 {
  margin: 0 0 6px;
  font-size: 26px;
  font-weight: 700;
  color: #0b2b4a;
}

.title-text .subtitle {
  margin: 0;
  font-size: 15px;
  color: #5e7e9c;
}

.ai-badge-large.connection-pill {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 12px;
  padding: 12px 20px;
  border-radius: 999px;
  font-weight: 600;
  font-size: 14px;
  color: #fff;
  background: linear-gradient(145deg, #1e5e86, #2f8f83);
  box-shadow: 0 8px 20px rgba(16, 47, 82, 0.22);
  border: 1px solid rgba(255, 255, 255, 0.2);
}

.connection-pill-body {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
}

.connection-line {
  font-size: 12px;
  font-weight: 600;
  opacity: 0.95;
  letter-spacing: 0.02em;
}

.status-chip {
  font-size: 11px;
  padding: 4px 10px;
  border-radius: 999px;
  border: 1px solid transparent;
  font-weight: 600;
}

.status-chip.on {
  background: rgba(80, 220, 140, 0.25);
  color: #c8ffd8;
  border-color: rgba(120, 240, 180, 0.45);
}

.status-chip.warn {
  background: rgba(255, 200, 120, 0.25);
  color: #ffe8c8;
  border-color: rgba(255, 200, 140, 0.45);
}

.status-chip.off {
  background: rgba(255, 255, 255, 0.12);
  color: rgba(255, 255, 255, 0.85);
  border-color: rgba(255, 255, 255, 0.22);
}
.chat-card {
  border: 1px solid rgba(87, 147, 94, 0.22);
  border-radius: 18px;
  background: linear-gradient(160deg, #f4ffed 0%, #ecf8ff 100%);
  box-shadow: 0 12px 24px rgba(31, 84, 43, 0.1);
  padding: 16px;
  position: relative;
}

.chat-card::after {
  content: "🌳 问答森林";
  position: absolute;
  top: 10px;
  right: 14px;
  font-size: 12px;
  color: #3f7f49;
  font-weight: 700;
}
.chat-list {
  min-height: 320px;
  max-height: 56vh;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 12px;
  padding-right: 4px;
}
.chat-item {
  max-width: 88%;
  padding: 10px 12px;
  border-radius: 12px;
  line-height: 1.45;
  font-size: 14px;
}
.chat-item.user {
  margin-left: auto;
  background: linear-gradient(135deg, #4b8f52, #73ba7f);
  color: #fff;
}
.chat-item.assistant {
  background: #fffdf4;
  border: 1px solid #e4dabd;
  color: #22415f;
}
.chat-input-row {
  display: flex;
  gap: 8px;
}
.chat-input {
  flex: 1;
  border: 1px solid #d8e4f0;
  border-radius: 10px;
  padding: 9px 10px;
  font-size: 14px;
}
.chat-input:focus {
  outline: none;
  border-color: #79a9f7;
  box-shadow: 0 0 0 3px rgba(30, 109, 242, 0.14);
}
.btn-send {
  border: none;
  border-radius: 10px;
  background: linear-gradient(135deg, #6f42c1, #8e60df);
  color: #fff;
  font-weight: 600;
  padding: 0 16px;
  cursor: pointer;
}
.btn-send:disabled {
  opacity: 0.65;
  cursor: default;
}
</style>
