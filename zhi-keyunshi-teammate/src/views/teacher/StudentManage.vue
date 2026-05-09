<template>
  <div class="student-manage-page">
    <div class="page-header">
      <div>
        <h1>学生管理</h1>
        <p>
          在此录入的学号、姓名会立即写入本班花名册；学生注册时须与花名册一致方可绑定。可选填学生登录账号。支持邀请码入班。
        </p>
      </div>
      <div class="class-filter-badge">
        <i class="fas fa-users"></i>
        <span>班级</span>
        <select v-model="currentClassId">
          <option v-for="c in classList" :key="c.classId" :value="c.classId">{{ c.className }}</option>
        </select>
      </div>
    </div>

    <div class="card add-class-card">
      <h3>添加班级</h3>
      <p class="hint">新建班级后会自动生成邀请码，可在下方「班级邀请码」中查看或复制。</p>
      <div class="form-row add-class-row">
        <input
          v-model.trim="newClassName"
          type="text"
          maxlength="50"
          placeholder="例如：四年级(1)班"
          @keyup.enter="handleCreateClass"
        />
        <button class="btn-primary" :disabled="creatingClass || !newClassName" @click="handleCreateClass">
          {{ creatingClass ? "创建中..." : "创建班级" }}
        </button>
      </div>
    </div>

    <div class="card add-card">
      <h3>花名册录入</h3>
      <div class="form-row">
        <input v-model.trim="form.id" type="text" placeholder="学号（数字）" />
        <input v-model.trim="form.name" type="text" placeholder="姓名" />
        <button class="btn-primary" :disabled="submitting" @click="handleCreate">
          {{ submitting ? "录入中..." : "录入花名册" }}
        </button>
      </div>
    </div>

    <div class="card invite-card">
      <h3>班级邀请码</h3>
      <div class="invite-list">
        <div v-for="row in classInvites" :key="row.classId" class="invite-item">
          <span class="cls">{{ row.className }}</span>
          <code>{{ row.inviteCode }}</code>
          <button class="btn-ghost" @click="copyInvite(row.inviteCode)">复制</button>
          <button class="btn-warn" @click="resetInvite(row)">重置</button>
        </div>
      </div>
    </div>

    <div class="card import-card">
      <h3>批量导入花名册（Excel/CSV）</h3>
      <p class="hint">
        导入行将写入本班花名册。支持 `.xlsx` / `.xls` / `.csv`，列名可用：`学号/姓名/班级` 或 `学号/姓名/账号/班级`（账号可选）或
        `id/name/className` / `id/name/accountUsername/className`
      </p>
      <div class="import-row">
        <input ref="fileInputRef" type="file" accept=".xlsx,.xls,.csv" @change="handleFileChange" />
        <button class="btn-primary" :disabled="!selectedFile || importing" @click="handleImport">
          {{ importing ? "导入中..." : "开始导入" }}
        </button>
      </div>
      <p v-if="selectedFile" class="hint">已选择：{{ selectedFile.name }}</p>
    </div>

    <div class="card table-card">
      <div class="table-head">
        <h3>本班花名册（{{ students.length }}人）</h3>
        <button class="btn-ghost" @click="loadStudents">刷新</button>
      </div>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>学号</th>
              <th>姓名</th>
              <th>学生账号</th>
              <th>班级</th>
              <th>入班状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="s in students" :key="s.id">
              <td>{{ s.id }}</td>
              <td v-if="editingId === s.id">
                <input v-model.trim="editForm.name" type="text" />
              </td>
              <td v-else>{{ s.name }}</td>
              <td v-if="editingId === s.id">
                <input v-model.trim="editForm.accountUsername" type="text" />
              </td>
              <td v-else>{{ s.accountUsername || "-" }}</td>
              <td v-if="editingId === s.id">
                <select v-model="editForm.classId">
                  <option v-for="c in classList" :key="c.classId" :value="c.classId">{{ c.className }}</option>
                </select>
              </td>
              <td v-else>{{ s.className }}</td>
              <td>{{ s.joinedAt ? "已入班" : "未入班" }}</td>
              <td>
                <template v-if="editingId === s.id">
                  <button class="btn-save" @click="handleSaveEdit(s)">保存</button>
                  <button class="btn-ghost" @click="cancelEdit">取消</button>
                </template>
                <template v-else>
                  <button class="btn-edit" @click="startEdit(s)">编辑</button>
                  <button class="btn-danger" @click="handleDelete(s)">删除</button>
                </template>
              </td>
            </tr>
            <tr v-if="!students.length">
              <td colspan="6" class="empty">当前班级暂无学生</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div v-if="toast.show" class="toast" :class="toast.type">{{ toast.text }}</div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from "vue";
import {
  fetchSigninClasses,
  fetchTeacherClassInvites,
  createTeacherClass,
  resetTeacherClassInvite,
  fetchTeacherStudents,
  createTeacherStudent,
  deleteTeacherStudent,
  updateTeacherStudent,
  batchImportTeacherStudents,
} from "@/utils/api";

const classList = ref([]);
const currentClassId = ref("");
const students = ref([]);
const classInvites = ref([]);
const submitting = ref(false);
const creatingClass = ref(false);
const newClassName = ref("");
const importing = ref(false);
const selectedFile = ref(null);
const fileInputRef = ref(null);
const editingId = ref("");
const form = reactive({
  id: "",
  name: "",
});
const editForm = reactive({
  name: "",
  accountUsername: "",
  classId: "",
});

const toast = reactive({
  show: false,
  text: "",
  type: "info",
});
let toastTimer = null;
function showToast(text, type = "info") {
  if (toastTimer) clearTimeout(toastTimer);
  toast.show = true;
  toast.text = text;
  toast.type = type;
  toastTimer = setTimeout(() => {
    toast.show = false;
  }, 2500);
}

async function loadClasses() {
  const { classes } = await fetchSigninClasses();
  classList.value = Array.isArray(classes) ? classes : [];
  if (!currentClassId.value && classList.value.length) {
    currentClassId.value = classList.value[0].classId;
  }
  const inviteData = await fetchTeacherClassInvites();
  classInvites.value = inviteData.list || [];
}

async function handleCreateClass() {
  const name = newClassName.value.trim();
  if (!name) {
    showToast("请输入班级名称", "error");
    return;
  }
  creatingClass.value = true;
  try {
    const data = await createTeacherClass({ className: name });
    newClassName.value = "";
    await loadClasses();
    currentClassId.value = data.classId || currentClassId.value;
    await loadStudents();
    showToast(data.message || `班级「${data.className}」已创建`, "success");
  } catch (e) {
    showToast(e.message || "创建失败", "error");
  } finally {
    creatingClass.value = false;
  }
}

async function loadStudents() {
  if (!currentClassId.value) return;
  const { list } = await fetchTeacherStudents(currentClassId.value);
  students.value = list || [];
}

async function handleCreate() {
  if (!form.id || !form.name || !currentClassId.value) {
    showToast("请填写学号、姓名并选择班级", "error");
    return;
  }
  submitting.value = true;
  try {
    await createTeacherStudent({
      id: form.id,
      name: form.name,
      classId: currentClassId.value,
    });
    form.id = "";
    form.name = "";
    await loadStudents();
    showToast("已写入本班花名册", "success");
  } catch (e) {
    showToast(e.message || "添加失败", "error");
  } finally {
    submitting.value = false;
  }
}

async function handleDelete(s) {
  if (!confirm(`确认删除学生 ${s.name}（${s.id}）吗？`)) return;
  try {
    await deleteTeacherStudent(s.id);
    await loadStudents();
    showToast("删除成功", "success");
  } catch (e) {
    showToast(e.message || "删除失败", "error");
  }
}

function startEdit(s) {
  editingId.value = s.id;
  editForm.name = s.name;
  editForm.accountUsername = s.accountUsername || "";
  editForm.classId = s.classId || currentClassId.value;
}

function cancelEdit() {
  editingId.value = "";
  editForm.name = "";
  editForm.accountUsername = "";
  editForm.classId = "";
}

async function handleSaveEdit(s) {
  if (!editForm.name || !editForm.classId) {
    showToast("姓名和班级不能为空", "error");
    return;
  }
  try {
    await updateTeacherStudent(s.id, {
      name: editForm.name,
      accountUsername: (editForm.accountUsername || "").trim(),
      classId: editForm.classId,
    });
    cancelEdit();
    await loadStudents();
    showToast("更新成功", "success");
  } catch (e) {
    showToast(e.message || "更新失败", "error");
  }
}

function handleFileChange(event) {
  selectedFile.value = event.target?.files?.[0] || null;
}

async function copyInvite(code) {
  try {
    await navigator.clipboard.writeText(code);
    showToast("邀请码已复制", "success");
  } catch {
    showToast(`邀请码：${code}`, "info");
  }
}

async function resetInvite(row) {
  if (!confirm(`确认重置 ${row.className} 的邀请码吗？旧邀请码将失效。`)) return;
  try {
    const data = await resetTeacherClassInvite(row.classId);
    classInvites.value = classInvites.value.map((x) =>
      x.classId === row.classId ? { ...x, inviteCode: data.inviteCode } : x
    );
    showToast("邀请码已重置", "success");
  } catch (e) {
    showToast(e.message || "重置失败", "error");
  }
}

function parseImportRows(file) {
  return file.arrayBuffer().then((buffer) => {
    // Lazy-load xlsx only when teacher performs import.
    return import("xlsx").then((XLSX) => ({ buffer, XLSX }));
  }).then(({ buffer, XLSX }) => {
    const wb = XLSX.read(buffer, { type: "array" });
    const sheet = wb.Sheets[wb.SheetNames[0]];
    const rows = XLSX.utils.sheet_to_json(sheet, { header: 1, defval: "" });
    if (!rows.length) return [];
    const first = rows[0].map((x) => String(x).trim());
    const hasHeader = first.some((x) =>
      ["学号", "姓名", "账号", "班级", "id", "name", "accountUsername", "className"].includes(x)
    );
    const dataRows = hasHeader ? rows.slice(1) : rows;
    const idx = hasHeader
      ? {
          id: first.findIndex((x) => x === "学号" || x === "id"),
          name: first.findIndex((x) => x === "姓名" || x === "name"),
          accountUsername: first.findIndex((x) => x === "账号" || x === "accountUsername"),
          className: first.findIndex((x) => x === "班级" || x === "className"),
        }
      : { id: 0, name: 1, accountUsername: 2, className: 3 };
    if (idx.id < 0 || idx.name < 0) {
      throw new Error("模板不正确，请至少包含学号、姓名列（账号列可选）");
    }
    return dataRows
      .map((r) => ({
        id: String(r[idx.id] ?? "").trim(),
        name: String(r[idx.name] ?? "").trim(),
        accountUsername:
          idx.accountUsername >= 0 ? String(r[idx.accountUsername] ?? "").trim() : "",
        className:
          idx.className >= 0
            ? String(r[idx.className] ?? "").trim() ||
              classList.value.find((x) => x.classId === currentClassId.value)?.className ||
              ""
            : classList.value.find((x) => x.classId === currentClassId.value)?.className || "",
      }))
      .filter((r) => r.id || r.name || r.className);
  });
}

async function handleImport() {
  if (!selectedFile.value) {
    showToast("请先选择文件", "error");
    return;
  }
  importing.value = true;
  try {
    const items = await parseImportRows(selectedFile.value);
    if (!items.length) {
      showToast("文件无有效数据", "error");
      return;
    }
    const result = await batchImportTeacherStudents(items);
    await loadStudents();
    showToast(`导入完成：新增${result.created} 更新${result.updated} 跳过${result.skipped}`, "success");
    selectedFile.value = null;
    if (fileInputRef.value) fileInputRef.value.value = "";
  } catch (e) {
    showToast(e.message || "导入失败", "error");
  } finally {
    importing.value = false;
  }
}

watch(currentClassId, loadStudents);

onMounted(async () => {
  try {
    await loadClasses();
    await loadStudents();
  } catch (e) {
    showToast(e.message || "加载失败", "error");
  }
});
</script>

<style scoped lang="scss">
.student-manage-page {
  .page-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 18px;
    h1 {
      margin: 0 0 6px;
      font-size: 26px;
      color: #0b2b4a;
    }
    p {
      margin: 0;
      color: #5e7e9c;
    }
  }
}
.class-filter-badge {
  display: flex;
  align-items: center;
  gap: 10px;
  background: linear-gradient(145deg, #1e6df2, #0a4bb0);
  color: #fff;
  padding: 10px 18px;
  border-radius: 40px;
  box-shadow: 0 8px 20px rgba(30, 109, 242, 0.3);
  i {
    font-size: 14px;
  }
  span {
    font-weight: 700;
  }
  select {
    border: none;
    background: rgba(255, 255, 255, 0.18);
    color: #fff;
    padding: 0 12px;
    border-radius: 999px;
    min-width: 128px;
    font-weight: 600;
    outline: none;
    cursor: pointer;
    option {
      color: #0b2b4a;
    }
  }
}
.card {
  background: #fff;
  border-radius: 20px;
  padding: 18px;
  border: 1px solid rgba(30, 109, 242, 0.12);
  margin-bottom: 16px;
}
.form-row {
  display: grid;
  grid-template-columns: 180px 180px 220px 120px;
  gap: 10px;
}
.add-class-card .add-class-row {
  grid-template-columns: 1fr auto;
  max-width: 560px;
}
.add-class-card h3 {
  margin: 0 0 8px;
  font-size: 17px;
  color: #0b2b4a;
}
.invite-list {
  display: grid;
  gap: 10px;
}
.invite-item {
  display: flex;
  align-items: center;
  gap: 10px;
  .cls {
    min-width: 90px;
    color: #0b2b4a;
    font-weight: 600;
  }
  code {
    background: #f3f7ff;
    border: 1px solid #d7e4ff;
    padding: 6px 10px;
    border-radius: 8px;
    min-width: 130px;
  }
}
.import-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.hint {
  color: #5e7e9c;
  font-size: 13px;
  margin: 6px 0 0;
}
input,
select {
  height: 40px;
  border: 1px solid #d8e3ef;
  border-radius: 10px;
  padding: 0 12px;
}
.table-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
table {
  width: 100%;
  border-collapse: collapse;
}
th,
td {
  padding: 12px;
  border-bottom: 1px solid #edf2f7;
  text-align: left;
}
.btn-primary,
.btn-ghost,
.btn-danger,
.btn-edit,
.btn-save {
  height: 36px;
  border-radius: 8px;
  border: none;
  cursor: pointer;
  padding: 0 12px;
}
.btn-primary {
  background: #1e6df2;
  color: #fff;
}
.btn-ghost {
  background: #edf4ff;
  color: #1e6df2;
}
.btn-danger {
  background: #ffe8e8;
  color: #cc3b3b;
}
.btn-edit {
  background: #eef7ff;
  color: #1e6df2;
  margin-right: 8px;
}
.btn-save {
  background: #2eb85c;
  color: #fff;
  margin-right: 8px;
}
.btn-warn {
  height: 36px;
  border-radius: 8px;
  border: none;
  cursor: pointer;
  padding: 0 12px;
  background: #fff3d9;
  color: #b9770e;
}
.empty {
  color: #8aa0b8;
  text-align: center;
}
.toast {
  position: fixed;
  right: 24px;
  bottom: 24px;
  padding: 10px 14px;
  border-radius: 8px;
  color: #fff;
  &.success {
    background: #2eb85c;
  }
  &.error {
    background: #d9534f;
  }
  &.info {
    background: #1e6df2;
  }
}
</style>
