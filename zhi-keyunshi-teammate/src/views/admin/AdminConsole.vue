<template>
  <div class="page">
    <h1>管理员控制台</h1>

    <section class="card">
      <h2>老师管理</h2>
      <div class="row">
        <input v-model.trim="teacherForm.username" placeholder="教师账号（6-20位字母数字）" />
        <input v-model.trim="teacherForm.name" placeholder="教师姓名" />
        <input v-model.trim="teacherForm.subject" placeholder="学科（如：数学）" />
        <input v-model.trim="teacherForm.email" placeholder="邮箱（可选）" />
        <button @click="handleCreateTeacher">新增老师</button>
      </div>
      <table>
        <thead>
          <tr>
            <th>账号</th>
            <th>姓名</th>
            <th>学科</th>
            <th>已注册</th>
            <th>班级数</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="t in teachers" :key="t.username">
            <td>{{ t.username }}</td>
            <td>{{ t.name }}</td>
            <td>{{ t.subject }}</td>
            <td>{{ t.registered ? "是" : "否" }}</td>
            <td>{{ t.classCount }}</td>
            <td>
              <button class="ghost" @click="handleViewClasses(t.username)">查看班级</button>
              <button class="danger" @click="handleDeleteTeacher(t.username)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-if="teacherClasses.length">该老师班级：{{ teacherClasses.map((x) => x.className).join("、") }}</p>
    </section>

    <section class="card">
      <h2>学生管理（与老师端统一）</h2>
      <div class="row">
        <select v-model="currentClassId">
          <option value="">请选择班级</option>
          <option v-for="c in classes" :key="c.classId" :value="c.classId">{{ c.className }}</option>
        </select>
        <input v-model.trim="studentForm.id" placeholder="学号" />
        <input v-model.trim="studentForm.name" placeholder="姓名" />
        <button @click="handleCreateStudent">新增学生</button>
      </div>
      <table>
        <thead>
          <tr>
            <th>学号</th>
            <th>姓名</th>
            <th>班级</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="s in students" :key="s.id">
            <td>{{ s.id }}</td>
            <td v-if="editingId === s.id"><input v-model.trim="editStudent.name" /></td>
            <td v-else>{{ s.name }}</td>
            <td v-if="editingId === s.id">
              <select v-model="editStudent.classId">
                <option v-for="c in classes" :key="c.classId" :value="c.classId">{{ c.className }}</option>
              </select>
            </td>
            <td v-else>{{ s.className }}</td>
            <td>
              <template v-if="editingId === s.id">
                <button class="ghost" @click="handleSaveStudent(s.id)">保存</button>
                <button @click="editingId = ''">取消</button>
              </template>
              <template v-else>
                <button class="ghost" @click="startEditStudent(s)">编辑</button>
                <button class="danger" @click="handleDeleteStudent(s.id)">删除</button>
              </template>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <section class="card">
      <h2>学科管理</h2>
      <div v-for="item in subjects" :key="item.subject" class="subject-item">
        <strong>{{ item.subject }}</strong>：{{ item.teachers.map((t) => t.name).join("、") || "暂无老师" }}
      </div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from "vue";
import {
  createAdminStudent,
  createAdminTeacher,
  deleteAdminStudent,
  deleteAdminTeacher,
  fetchAdminStudents,
  fetchAdminSubjects,
  fetchAdminTeacherClasses,
  fetchAdminTeachers,
  fetchAdminClasses,
  updateAdminStudent,
} from "@/utils/api";

const teachers = ref([]);
const teacherClasses = ref([]);
const subjects = ref([]);
const classes = ref([]);
const currentClassId = ref("");
const students = ref([]);
const editingId = ref("");
const editStudent = reactive({ name: "", classId: "" });

const teacherForm = reactive({ username: "", name: "", subject: "", email: "" });
const studentForm = reactive({ id: "", name: "" });

async function loadAll() {
  const [teacherRes, subjectRes, classRes] = await Promise.all([
    fetchAdminTeachers(),
    fetchAdminSubjects(),
    fetchAdminClasses(),
  ]);
  teachers.value = teacherRes.list || [];
  subjects.value = subjectRes.list || [];
  classes.value = classRes.classes || [];
}

async function loadStudents() {
  if (!currentClassId.value) return;
  const data = await fetchAdminStudents(currentClassId.value);
  students.value = data.list || [];
}

async function handleCreateTeacher() {
  await createAdminTeacher({ ...teacherForm });
  teacherForm.username = "";
  teacherForm.name = "";
  teacherForm.subject = "";
  teacherForm.email = "";
  await loadAll();
}

async function handleDeleteTeacher(username) {
  if (!confirm(`确认删除老师 ${username} 吗？`)) return;
  await deleteAdminTeacher(username);
  await loadAll();
}

async function handleViewClasses(username) {
  const data = await fetchAdminTeacherClasses(username);
  teacherClasses.value = data.list || [];
}

async function handleCreateStudent() {
  if (!currentClassId.value) return;
  await createAdminStudent({
    id: studentForm.id,
    name: studentForm.name,
    classId: currentClassId.value,
  });
  studentForm.id = "";
  studentForm.name = "";
  await loadStudents();
}

async function handleDeleteStudent(id) {
  await deleteAdminStudent(id);
  await loadStudents();
}

function startEditStudent(row) {
  editingId.value = row.id;
  editStudent.name = row.name;
  editStudent.classId = row.classId;
}

async function handleSaveStudent(id) {
  await updateAdminStudent(id, {
    name: editStudent.name,
    classId: editStudent.classId,
  });
  editingId.value = "";
  await loadStudents();
}

watch(currentClassId, loadStudents);
onMounted(loadAll);
</script>

<style scoped>
.page {
  display: grid;
  gap: 16px;
}
.card {
  background: #fff;
  border: 1px solid #d9e6f7;
  border-radius: 12px;
  padding: 14px;
}
.row {
  display: grid;
  gap: 8px;
  grid-template-columns: repeat(5, minmax(120px, 1fr));
  margin-bottom: 10px;
}
input,
select,
button {
  height: 36px;
}
table {
  width: 100%;
  border-collapse: collapse;
}
th,
td {
  border-bottom: 1px solid #eef3fb;
  padding: 8px;
  text-align: left;
}
.ghost {
  margin-right: 6px;
}
.danger {
  color: #b00020;
}
.subject-item {
  margin: 6px 0;
}
</style>
