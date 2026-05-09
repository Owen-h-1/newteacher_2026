<template>
  <div class="join-class-page">
    <div class="page-header">
      <div class="title-text">
        <h1>加入班级</h1>
        <p class="subtitle">输入老师提供的邀请码，完成班级绑定</p>
      </div>
    </div>

    <div class="card">
      <label>邀请码</label>
      <div class="row">
        <input v-model.trim="inviteCode" type="text" placeholder="例如：ZKYS-123456" />
        <button class="btn" :disabled="loading" @click="handleJoin">
          {{ loading ? "加入中..." : "加入班级" }}
        </button>
      </div>
    </div>

    <div v-if="joinedClass" class="result success">已加入：{{ joinedClass }}</div>
    <div v-if="errorMsg" class="result error">{{ errorMsg }}</div>
  </div>
</template>

<script setup>
import { ref } from "vue";
import { joinClassByInviteCode } from "@/utils/api";

const inviteCode = ref("");
const loading = ref(false);
const joinedClass = ref("");
const errorMsg = ref("");

async function handleJoin() {
  if (!inviteCode.value) {
    errorMsg.value = "请输入邀请码";
    return;
  }
  loading.value = true;
  errorMsg.value = "";
  joinedClass.value = "";
  try {
    const data = await joinClassByInviteCode(inviteCode.value);
    joinedClass.value = data.className || "";
  } catch (e) {
    errorMsg.value = e.message || "加入失败";
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped lang="scss">
.join-class-page {
  .page-header {
    margin-bottom: 16px;
  }
  .title-text h1 {
    margin: 0 0 6px;
    color: #0b2b4a;
    font-size: 24px;
    font-weight: 700;
  }
  .subtitle {
    margin: 0;
    color: #5e7e9c;
    font-size: 15px;
  }
}
.card {
  background: #fff;
  border-radius: 16px;
  padding: 16px;
  border: 1px solid #dce8f6;
}
.row {
  display: flex;
  gap: 10px;
  margin-top: 8px;
}
input {
  flex: 1;
  height: 40px;
  border: 1px solid #d8e3ef;
  border-radius: 10px;
  padding: 0 12px;
}
.btn {
  height: 40px;
  border: none;
  border-radius: 10px;
  padding: 0 16px;
  background: #1e6df2;
  color: #fff;
  cursor: pointer;
}
.result {
  margin-top: 14px;
  padding: 10px 12px;
  border-radius: 10px;
}
.success {
  background: #e8f7ee;
  color: #1e7b4c;
}
.error {
  background: #ffe8e8;
  color: #cc3b3b;
}
</style>
