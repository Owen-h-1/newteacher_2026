const API_BASE = "/api";
const TOKEN_KEY = "zkys_token";
const USER_KEY = "zkys_user";

export function getToken() {
  const s = sessionStorage.getItem(TOKEN_KEY);
  if (s) return s;
  // One-time migration for old sessions stored in localStorage.
  const legacy = localStorage.getItem(TOKEN_KEY) || "";
  if (legacy) {
    sessionStorage.setItem(TOKEN_KEY, legacy);
  }
  return legacy;
}

export function setSession({ token, user }) {
  if (token) {
    sessionStorage.setItem(TOKEN_KEY, token);
  }
  if (user) {
    sessionStorage.setItem(USER_KEY, JSON.stringify(user));
  }
}

export function getCurrentUser() {
  let raw = sessionStorage.getItem(USER_KEY);
  if (!raw) {
    const legacy = localStorage.getItem(USER_KEY);
    if (legacy) {
      raw = legacy;
      sessionStorage.setItem(USER_KEY, legacy);
    }
  }
  if (!raw) return null;
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

export function clearSession() {
  sessionStorage.removeItem(TOKEN_KEY);
  sessionStorage.removeItem(USER_KEY);
  // Keep legacy keys untouched to avoid wiping other tab identities.
}

async function request(path, options = {}) {
  const headers = {
    "Content-Type": "application/json",
    ...(options.headers || {}),
  };
  const token = getToken();
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  const res = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers,
  });

  const text = await res.text();
  let data = {};
  try {
    data = text ? JSON.parse(text) : {};
  } catch {
    data = {};
  }
  if (!res.ok) {
    throw new Error(data.message || text || `请求失败(${res.status})`);
  }
  return data;
}

export function register(payload) {
  return request("/auth/register", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function login(payload) {
  return request("/auth/login", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function fetchAuthMe() {
  return request("/auth/me");
}

export function fetchHomeworkList(status) {
  const query = status ? `?status=${encodeURIComponent(status)}` : "";
  return request(`/homework${query}`);
}

export function publishHomework(payload) {
  return request("/homework", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function submitHomework(id) {
  return request(`/homework/${id}/submit`, {
    method: "POST",
  });
}

export function fetchHomeworkDetail(id) {
  return request(`/homework/${encodeURIComponent(id)}`);
}

export function submitHomeworkAnswers(id, answers) {
  return request(`/homework/${encodeURIComponent(id)}/submit`, {
    method: "POST",
    body: JSON.stringify({ answers }),
  });
}

export function fetchSigninClasses() {
  return request("/signin/classes");
}

export function fetchSigninRecords(classId) {
  const q = classId ? `?classId=${encodeURIComponent(classId)}` : "";
  return request(`/signin/records${q}`);
}

export function refreshSignCode(payload) {
  return request("/signin/refresh", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function markSignin(payload) {
  return request("/signin/mark", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function undoSignin(payload) {
  return request("/signin/undo", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function uploadSigninPhoto(payload) {
  return request("/signin/photo-recognition", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export async function downloadSigninExport(classId) {
  const token = getToken();
  const q = classId ? `?classId=${encodeURIComponent(classId)}` : "";
  const res = await fetch(`${API_BASE}/signin/export${q}`, {
    method: "GET",
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
  });
  if (!res.ok) {
    let msg = `请求失败(${res.status})`;
    try {
      const data = await res.json();
      msg = data.message || msg;
    } catch {
      // ignore
    }
    throw new Error(msg);
  }
  const blob = await res.blob();
  const disposition = res.headers.get("content-disposition") || "";
  const encoded = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1] || "";
  const filename = encoded ? decodeURIComponent(encoded) : "签到表.xlsx";
  return { blob, filename };
}

export function fetchTeacherStudentBoard(classId) {
  const q = classId ? `?classId=${encodeURIComponent(classId)}` : "";
  return request(`/teacher/student-board${q}`);
}

export function fetchTeacherOverview() {
  return request("/teacher/overview");
}

export function generateTeacherCourseware(payload) {
  return request("/teacher/ai-courseware/generate", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function generateTeacherTeachingDesign(payload) {
  return request("/teacher/ai-teaching-design/generate", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export async function downloadTeacherCoursewarePpt(payload) {
  const token = getToken();
  const res = await fetch(`${API_BASE}/teacher/ai-courseware/download`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(payload),
  });
  if (!res.ok) {
    let msg = `请求失败(${res.status})`;
    try {
      const data = await res.json();
      msg = data.message || msg;
    } catch {
      // ignore
    }
    throw new Error(msg);
  }
  const blob = await res.blob();
  const disposition = res.headers.get("content-disposition") || "";
  const encoded = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1] || "";
  const filename = encoded ? decodeURIComponent(encoded) : "ai-courseware.pptx";
  return { blob, filename };
}

export async function downloadTeacherTeachingDesignDoc(payload) {
  const token = getToken();
  const res = await fetch(`${API_BASE}/teacher/ai-teaching-design/download`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(payload),
  });
  if (!res.ok) {
    let msg = `请求失败(${res.status})`;
    try {
      const data = await res.json();
      msg = data.message || msg;
    } catch {
      // ignore
    }
    throw new Error(msg);
  }
  const blob = await res.blob();
  const disposition = res.headers.get("content-disposition") || "";
  const encoded = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1] || "";
  const filename = encoded ? decodeURIComponent(encoded) : "teaching-design.docx";
  return { blob, filename };
}

export function fetchTeacherTodos() {
  return request("/teacher/todos");
}

export function createTeacherTodo(payload) {
  return request("/teacher/todos", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function updateTeacherTodo(id, payload) {
  return request(`/teacher/todos/${encodeURIComponent(id)}`, {
    method: "PUT",
    body: JSON.stringify(payload),
  });
}

export function deleteTeacherTodo(id) {
  return request(`/teacher/todos/${encodeURIComponent(id)}`, {
    method: "DELETE",
  });
}

export function sendTeacherMessage(payload) {
  return request("/teacher/messages", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function fetchTeacherHomeworkSubmissions(id) {
  return request(`/teacher/homework/${encodeURIComponent(id)}/submissions`);
}

export function fetchTeacherHomeworkSubmissionDetail(id, studentNo) {
  const q = new URLSearchParams({ studentNo: String(studentNo || "") }).toString();
  return request(`/teacher/homework/${encodeURIComponent(id)}/submission-detail?${q}`);
}

export function setTeacherHomeworkGrade(payload) {
  return request("/teacher/homework/grade", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function fetchTeacherHomeworkManageList() {
  return request("/teacher/homework");
}

export function deleteTeacherHomework(id) {
  return request(`/teacher/homework/${encodeURIComponent(id)}`, {
    method: "DELETE",
  });
}

export function fetchTeacherStudents(classId) {
  const q = classId ? `?classId=${encodeURIComponent(classId)}` : "";
  return request(`/teacher/students${q}`);
}

export function fetchTeacherClassInvites() {
  return request("/teacher/class-invites");
}

export function createTeacherClass(payload) {
  return request("/teacher/classes", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function resetTeacherClassInvite(classId) {
  return request("/teacher/class-invites/reset", {
    method: "POST",
    body: JSON.stringify({ classId }),
  });
}

export function createTeacherStudent(payload) {
  return request("/teacher/students", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function deleteTeacherStudent(id) {
  return request(`/teacher/students/${encodeURIComponent(id)}`, {
    method: "DELETE",
  });
}

export function updateTeacherStudent(id, payload) {
  return request(`/teacher/students/${encodeURIComponent(id)}`, {
    method: "PUT",
    body: JSON.stringify(payload),
  });
}

export function batchImportTeacherStudents(items) {
  return request("/teacher/students/batch", {
    method: "POST",
    body: JSON.stringify({ items }),
  });
}

export function joinClassByInviteCode(inviteCode) {
  return request("/student/join-class", {
    method: "POST",
    body: JSON.stringify({ inviteCode }),
  });
}

export function fetchStudentOverview() {
  return request("/student/overview");
}

export function markStudentMessageRead(id) {
  return request(`/student/messages/${encodeURIComponent(id)}/read`, {
    method: "POST",
  });
}

export function chatWithLLM(message) {
  return request("/llm/chat", {
    method: "POST",
    body: JSON.stringify({ message }),
  });
}

export function fetchLLMStatus() {
  return request("/llm/status");
}

// 数字人占位接口：后续数字人接入时前端直接调用这里。
export function chatWithDigitalHuman(payload) {
  return request("/digital-human/chat", {
    method: "POST",
    body: JSON.stringify({
      message: String(payload?.message || ""),
      sessionId: payload?.sessionId || "",
      voiceId: payload?.voiceId || "",
      avatarId: payload?.avatarId || "",
    }),
  });
}

export function fetchSelftestExercises() {
  return request("/selftest/exercises");
}

export function toggleSelftestCollect(id) {
  return request(`/selftest/exercises/${id}/collect`, {
    method: "POST",
  });
}

export function fetchSelftestRecommendations() {
  return request("/selftest/recommendations");
}

export function fetchSelftestHistory() {
  return request("/selftest/history");
}

export function fetchNationalPlatformResources(params = {}) {
  const query = new URLSearchParams();
  if (params.keyword) query.set("keyword", params.keyword);
  if (params.type) query.set("type", params.type);
  if (params.grade) query.set("grade", params.grade);
  const q = query.toString();
  return request(`/platform/resources${q ? `?${q}` : ""}`);
}

export function uploadTeacherPptAsset(payload) {
  return request("/teacher/ppt-assets", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function fetchTeacherPptAssets() {
  return request("/teacher/ppt-assets");
}

export function deleteTeacherPptAsset(id) {
  return request(`/teacher/ppt-assets/${encodeURIComponent(id)}`, {
    method: "DELETE",
  });
}

export function fetchStudentPptAssets() {
  return request("/student/ppt-assets");
}

export function generateStudentPptLecture(payload) {
  return request("/student/ppt-lecture/generate", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function generateTeacherWarningAgentPlan(payload) {
  return request("/teacher/warnings/agent-plan", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function generateTeacherTeachingDesignAgentPlan(payload) {
  return request("/teacher/ai-teaching-design/agent-plan", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}
