<template>
  <div class="admin-page">
    <header class="page-header">
      <h1>老师管理</h1>
      <p>新增或删除老师，查看老师班级信息。管理员录入的老师ID号就是该老师的注册账号，必须完全一致才能注册。</p>
    </header>

    <section class="card">
      <h2>新增老师</h2>
      <div class="form-row">
        <input v-model.trim="form.username" placeholder="老师ID号（4-20位数字）" />
        <input v-model.trim="form.name" placeholder="教师姓名" />
        <select v-model="form.subject">
          <option value="">请选择学科</option>
          <option v-for="s in subjectOptions" :key="s" :value="s">{{ s }}</option>
        </select>
        <input v-model.trim="form.email" placeholder="邮箱（可选）" />
        <button class="btn-primary" @click="handleCreate">新增老师</button>
      </div>
    </section>

    <section class="card">
      <div class="card-head">
        <h2>老师列表</h2>
        <button class="btn-ghost" @click="exportTeachers">导出老师表</button>
      </div>
      <table>
        <thead>
          <tr>
            <th>老师ID号</th>
            <th>姓名</th>
            <th>学科</th>
            <th>已注册</th>
            <th>班级数</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in teachers" :key="item.username">
            <td>{{ item.username }}</td>
            <td>{{ item.name }}</td>
            <td>{{ item.subject }}</td>
            <td>{{ item.registered ? "是" : "否" }}</td>
            <td>{{ item.classCount }}</td>
            <td>
              <select v-model="subjectDraft[item.username]" class="subject-select">
                <option v-for="s in subjectOptions" :key="`${item.username}-${s}`" :value="s">{{ s }}</option>
              </select>
              <button class="btn-ghost" @click="saveSubject(item.username)">保存学科</button>
              <button class="btn-ghost" @click="showClasses(item.username)">查看班级</button>
              <button class="btn-danger" @click="handleDelete(item.username)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-if="classesText" class="class-tip">{{ classesText }}</p>
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import {
  createAdminTeacher,
  deleteAdminTeacher,
  fetchAdminTeacherClasses,
  fetchAdminTeachers,
  updateAdminTeacherSubject,
} from "@/utils/api";

const teachers = ref([]);
const classesText = ref("");
const subjectOptions = ["语文", "数学", "英语", "道德与科学"];
const subjectDraft = reactive({});
const form = reactive({ username: "", name: "", subject: "", email: "" });

async function loadTeachers() {
  const data = await fetchAdminTeachers();
  teachers.value = data.list || [];
  teachers.value.forEach((t) => {
    subjectDraft[t.username] = subjectOptions.includes(t.subject) ? t.subject : "数学";
  });
}

async function handleCreate() {
  await createAdminTeacher({ ...form });
  form.username = "";
  form.name = "";
  form.subject = "";
  form.email = "";
  await loadTeachers();
}

async function handleDelete(username) {
  if (!confirm(`确认删除老师 ${username} 吗？`)) return;
  await deleteAdminTeacher(username);
  classesText.value = "";
  await loadTeachers();
}

async function showClasses(username) {
  const data = await fetchAdminTeacherClasses(username);
  const list = data.list || [];
  classesText.value = list.length
    ? `老师 ${username} 的班级：${list.map((x) => x.className).join("、")}`
    : `老师 ${username} 当前没有班级`;
}

async function saveSubject(username) {
  const subject = subjectDraft[username] || "";
  if (!subject) return;
  await updateAdminTeacherSubject(username, subject);
  await loadTeachers();
}

async function exportTeachers() {
  const XLSX = await import("xlsx");
  const rows = teachers.value.map((t) => ({
    老师ID号: t.username,
    教师姓名: t.name,
    学科: t.subject || "未设置",
    邮箱: t.email || "",
    已注册: t.registered ? "是" : "否",
    班级数: t.classCount ?? 0,
  }));
  const ws = XLSX.utils.json_to_sheet(rows);
  ws["!cols"] = [
    { wch: 14 },
    { wch: 14 },
    { wch: 12 },
    { wch: 24 },
    { wch: 10 },
    { wch: 10 },
  ];
  const wb = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(wb, ws, "老师管理");
  const stamp = new Date().toISOString().slice(0, 10);
  XLSX.writeFileXLSX(wb, `老师管理导出-${stamp}.xlsx`);
}

onMounted(loadTeachers);
</script>

<style scoped>
.admin-page { display: grid; gap: 16px; }
.page-header h1 { margin: 0; color: #102a43; font-size: 28px; }
.page-header p { margin: 6px 0 0; color: #627d98; }
.card {
  background: #fff; border: 1px solid #d9e6f7; border-radius: 14px; padding: 16px;
  box-shadow: 0 8px 22px rgba(21, 50, 84, 0.06);
}
.card h2 { margin: 0 0 12px; color: #1f3b5b; font-size: 18px; }
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.form-row { display: grid; gap: 10px; grid-template-columns: repeat(5, minmax(120px, 1fr)); }
input, button, select { height: 38px; border-radius: 10px; }
input, select { border: 1px solid #d4e0ef; padding: 0 12px; }
table { width: 100%; border-collapse: collapse; }
th, td { padding: 10px; border-bottom: 1px solid #eef3fb; text-align: left; }
.btn-primary, .btn-ghost, .btn-danger { border: 0; cursor: pointer; padding: 0 12px; }
.btn-primary { background: #1e6df2; color: #fff; }
.btn-ghost { background: #eef5ff; color: #1e6df2; margin-right: 8px; }
.btn-danger { background: #ffe9ea; color: #b4232c; }
.class-tip { margin: 10px 0 0; color: #486581; }
.subject-select { margin-right: 8px; min-width: 120px; }
</style>
