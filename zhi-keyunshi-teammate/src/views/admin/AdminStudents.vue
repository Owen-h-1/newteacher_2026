<template>
  <div class="admin-page">
    <header class="page-header">
      <h1>学生管理</h1>
      <p>与老师端共用同一套学生数据，任一端修改都会实时同步。</p>
    </header>

    <section class="card">
      <h2>新增学生</h2>
      <div class="form-row">
        <select v-model="currentClassId">
          <option value="">全部班级</option>
          <option v-for="item in classes" :key="item.classId" :value="item.classId">{{ item.className }}</option>
        </select>
        <input v-model.trim="form.id" placeholder="学号" />
        <input v-model.trim="form.name" placeholder="姓名" />
        <button class="btn-primary" @click="handleCreate">新增学生</button>
      </div>
    </section>

    <section class="card">
      <div class="card-head">
        <h2>学生列表</h2>
        <button class="btn-ghost" @click="exportStudents">导出学生表</button>
      </div>
      <table>
        <thead>
          <tr>
            <th>学号</th>
            <th>姓名</th>
            <th>班级</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="s in students" :key="s.id">
            <td>{{ s.id }}</td>
            <td v-if="editingId === s.id"><input v-model.trim="editForm.name" /></td>
            <td v-else>{{ s.name }}</td>
            <td v-if="editingId === s.id">
              <select v-model="editForm.classId">
                <option v-for="item in classes" :key="item.classId" :value="item.classId">{{ item.className }}</option>
              </select>
            </td>
            <td v-else>{{ s.className }}</td>
            <td>{{ s.joinedAt ? "已入班" : "未入班" }}</td>
            <td>
              <template v-if="editingId === s.id">
                <button class="btn-ghost" @click="handleSave(s.id)">保存</button>
                <button class="btn-ghost" @click="editingId = ''">取消</button>
              </template>
              <template v-else>
                <button class="btn-ghost" @click="startEdit(s)">编辑</button>
                <button class="btn-danger" @click="handleDelete(s.id)">删除</button>
              </template>
            </td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from "vue";
import { createAdminStudent, deleteAdminStudent, fetchAdminClasses, fetchAdminStudents, updateAdminStudent } from "@/utils/api";

const classes = ref([]);
const students = ref([]);
const currentClassId = ref("");
const editingId = ref("");
const form = reactive({ id: "", name: "" });
const editForm = reactive({ name: "", classId: "" });

async function loadClasses() {
  const data = await fetchAdminClasses();
  classes.value = data.classes || [];
}

async function loadStudents() {
  const data = await fetchAdminStudents(currentClassId.value);
  students.value = data.list || [];
}

async function handleCreate() {
  if (!currentClassId.value || !form.id || !form.name) return;
  await createAdminStudent({ id: form.id, name: form.name, classId: currentClassId.value });
  form.id = "";
  form.name = "";
  await loadStudents();
}

function startEdit(row) {
  editingId.value = row.id;
  editForm.name = row.name;
  editForm.classId = row.classId;
}

async function handleSave(id) {
  await updateAdminStudent(id, { name: editForm.name, classId: editForm.classId });
  editingId.value = "";
  await loadStudents();
}

async function handleDelete(id) {
  await deleteAdminStudent(id);
  await loadStudents();
}

async function exportStudents() {
  const XLSX = await import("xlsx");
  const rows = students.value.map((s) => ({
    学号: s.id,
    姓名: s.name,
    班级: s.className,
    学生账号: s.accountUsername || "",
    入班状态: s.joinedAt ? "已入班" : "未入班",
  }));
  const ws = XLSX.utils.json_to_sheet(rows);
  ws["!cols"] = [
    { wch: 14 },
    { wch: 14 },
    { wch: 18 },
    { wch: 16 },
    { wch: 12 },
  ];
  const wb = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(wb, ws, "学生管理");
  const classPart = currentClassId.value ? "单班" : "全部班级";
  const stamp = new Date().toISOString().slice(0, 10);
  XLSX.writeFileXLSX(wb, `学生管理导出-${classPart}-${stamp}.xlsx`);
}

watch(currentClassId, loadStudents);
onMounted(async () => {
  await loadClasses();
  await loadStudents();
});
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
.form-row { display: grid; gap: 10px; grid-template-columns: 1.2fr 1fr 1fr auto; }
input, select, button { height: 38px; border-radius: 10px; }
input, select { border: 1px solid #d4e0ef; padding: 0 12px; }
table { width: 100%; border-collapse: collapse; }
th, td { padding: 10px; border-bottom: 1px solid #eef3fb; text-align: left; }
.btn-primary, .btn-ghost, .btn-danger { border: 0; cursor: pointer; padding: 0 12px; }
.btn-primary { background: #1e6df2; color: #fff; }
.btn-ghost { background: #eef5ff; color: #1e6df2; margin-right: 8px; }
.btn-danger { background: #ffe9ea; color: #b4232c; }
</style>
