<template>
  <div class="admin-page">
    <header class="page-header">
      <h1>学科管理</h1>
      <p>查看每个学科下的老师分布情况。</p>
    </header>

    <div class="subject-grid">
      <section v-for="item in subjects" :key="item.subject" class="subject-card">
        <h2>{{ item.subject }}</h2>
        <div v-if="item.teachers.length" class="teacher-list">
          <div v-for="t in item.teachers" :key="t.username" class="teacher-item">
            <div>
              <strong>{{ t.name }}</strong>
              <span class="meta">（{{ t.username }}）</span>
            </div>
            <span class="tag" :class="t.registered ? 'ok' : 'pending'">{{ t.registered ? "已注册" : "未注册" }}</span>
          </div>
        </div>
        <p v-else class="empty">暂无老师</p>
      </section>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { fetchAdminSubjects } from "@/utils/api";

const subjects = ref([]);
async function loadData() {
  const data = await fetchAdminSubjects();
  subjects.value = data.list || [];
}
onMounted(loadData);
</script>

<style scoped>
.admin-page { display: grid; gap: 16px; }
.page-header h1 { margin: 0; color: #102a43; font-size: 28px; }
.page-header p { margin: 6px 0 0; color: #627d98; }
.subject-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 14px;
}
.subject-card {
  background: #fff;
  border: 1px solid #d9e6f7;
  border-radius: 14px;
  padding: 16px;
  box-shadow: 0 8px 22px rgba(21, 50, 84, 0.06);
}
.subject-card h2 {
  margin: 0 0 12px;
  font-size: 18px;
  color: #1f3b5b;
}
.teacher-list { display: grid; gap: 8px; }
.teacher-item {
  border: 1px solid #e8eef8;
  border-radius: 10px;
  padding: 10px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.meta { color: #829ab1; font-weight: 400; }
.tag {
  font-size: 12px;
  border-radius: 999px;
  padding: 3px 9px;
}
.tag.ok { background: #dcfce7; color: #16794f; }
.tag.pending { background: #fff4ce; color: #8a5a00; }
.empty { color: #829ab1; margin: 0; }
</style>
