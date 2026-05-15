<template>
  <div class="doubao-qa">
    <div class="page-header">
      <div class="title-icon"><i class="fas fa-comments"></i></div>
      <div class="title-text">
        <h1>AI 智能问答</h1>
        <p>输入学习问题，获取实时答疑建议</p>
      </div>
      <div class="status-panel">
        <div class="status-current">当前：{{ currentProviderLabel }}</div>
        <div class="status-list">
          <span class="status-chip" :class="statusClass(llmStatus.ollama)">
            Ollama（{{ ollamaModel }}）
          </span>
        </div>
      </div>
    </div>

    <div class="chat-card">
      <div ref="chatListRef" class="chat-list">
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
import { ref, computed, onMounted, watch, nextTick } from "vue";
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
const chatListRef = ref(null);

watch(
  messages,
  (v) => {
    persistMessages(v);
  },
  { deep: true }
);

watch(
  () => messages.value.length,
  async () => {
    await nextTick();
    scrollToBottom();
  }
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

const scrollToBottom = () => {
  const el = chatListRef.value;
  if (!el) return;
  el.scrollTop = el.scrollHeight;
};

onMounted(async () => {
  await loadLLMStatus();
  await nextTick();
  scrollToBottom();
});
</script>

<style scoped lang="scss">
.doubao-qa {
  padding: 10px 0;
}
.page-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 18px;
  background: linear-gradient(135deg, #ffffff 0%, #eef5ff 100%);
  border: 1px solid rgba(30, 109, 242, 0.14);
  border-radius: 18px;
  padding: 18px 20px;
  justify-content: space-between;
}
.status-panel {
  margin-left: auto;
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: flex-end;
}
.status-current {
  font-size: 12px;
  color: #4a668a;
  font-weight: 600;
}
.status-list {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  justify-content: flex-end;
}
.status-chip {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 20px;
  border: 1px solid transparent;
}
.status-chip.on {
  background: #eaf8ef;
  color: #2e9d58;
  border-color: #c9efd6;
}
.status-chip.warn {
  background: #fff5e8;
  color: #b9770e;
  border-color: #ffdca8;
}
.status-chip.off {
  background: #edf2f7;
  color: #8aa0b5;
  border-color: #d9e2ec;
}
.title-icon {
  width: 48px;
  height: 48px;
  border-radius: 14px;
  background: linear-gradient(135deg, #1e6df2, #4b8cf6);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
}
.title-text h1 {
  margin: 0 0 4px;
  font-size: 22px;
  color: #0b2b4a;
}
.title-text p {
  margin: 0;
  color: #5e7e9c;
}
.chat-card {
  border: 1px solid rgba(30, 109, 242, 0.14);
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 10px 22px rgba(29, 74, 138, 0.07);
  padding: 16px;
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
  background: linear-gradient(135deg, #1e6df2, #4a8cf6);
  color: #fff;
}
.chat-item.assistant {
  background: #eff5ff;
  border: 1px solid #dbeaff;
  color: #0b2b4a;
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
  background: linear-gradient(135deg, #1e6df2, #4a8cf6);
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
