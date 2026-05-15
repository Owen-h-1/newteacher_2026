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

  let res;
  try {
    res = await fetch(`${API_BASE}${path}`, {
      ...options,
      headers,
    });
  } catch (e) {
    const msg = e?.message || "";
    throw new Error(
      msg.includes("Failed to fetch") || msg.includes("NetworkError")
        ? "无法连接后端：请确认 Spring Boot 已在 http://127.0.0.1:8080 启动，并刷新页面。若仍失败，请重启 npm run dev:all。"
        : msg || "网络请求失败",
    );
  }

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

export async function importTeacherHomeworkQuestions(file) {
  const token = getToken();
  const form = new FormData();
  form.append("file", file);
  const res = await fetch(`${API_BASE}/teacher/homework/import-questions`, {
    method: "POST",
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: form,
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

const FACE_RECOGNITION_API_BASE = "http://localhost:8000/api";

export async function recognizeAttendancePhoto(payload) {
  const res = await fetch(`${FACE_RECOGNITION_API_BASE}/attendance/photo/upload`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(payload),
  });
  const text = await res.text();
  let data = {};
  try {
    data = text ? JSON.parse(text) : {};
  } catch {
    data = {};
  }
  if (!res.ok) {
    throw new Error(data.message || text || `人脸识别请求失败(${res.status})`);
  }
  return data;
}

export async function fetchFaceRecognitionStudents(className) {
  const q = className ? `?class_name=${encodeURIComponent(className)}` : "";
  const res = await fetch(`${FACE_RECOGNITION_API_BASE}/attendance/students${q}`, {
    method: "GET",
    headers: {
      "Content-Type": "application/json",
    },
  });
  const text = await res.text();
  let data = {};
  try {
    data = text ? JSON.parse(text) : {};
  } catch {
    data = {};
  }
  if (!res.ok) {
    throw new Error(data.message || text || `获取学生列表失败(${res.status})`);
  }
  return data;
}

export async function registerFaceStudent(payload) {
  const res = await fetch(`${FACE_RECOGNITION_API_BASE}/attendance/student/register`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(payload),
  });
  const text = await res.text();
  let data = {};
  try {
    data = text ? JSON.parse(text) : {};
  } catch {
    data = {};
  }
  if (!res.ok) {
    throw new Error(data.message || text || `学生注册失败(${res.status})`);
  }
  return data;
}

export async function fetchAttendanceStatistics(className, date) {
  const params = new URLSearchParams();
  if (className) params.set("class_name", className);
  if (date) params.set("date", date);
  const q = params.toString();
  const res = await fetch(`${FACE_RECOGNITION_API_BASE}/attendance/statistics${q ? `?${q}` : ""}`, {
    method: "GET",
    headers: {
      "Content-Type": "application/json",
    },
  });
  const text = await res.text();
  let data = {};
  try {
    data = text ? JSON.parse(text) : {};
  } catch {
    data = {};
  }
  if (!res.ok) {
    throw new Error(data.message || text || `获取考勤统计失败(${res.status})`);
  }
  return data;
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

export function fetchTeacherHomeworkManageList(classId) {
  const q = classId ? `?classId=${encodeURIComponent(classId)}` : "";
  return request(`/teacher/homework${q}`);
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

export function fetchAdminTeachers() {
  return request("/admin/teachers");
}

export function createAdminTeacher(payload) {
  return request("/admin/teachers", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function deleteAdminTeacher(username) {
  return request(`/admin/teachers/${encodeURIComponent(username)}`, {
    method: "DELETE",
  });
}

export function updateAdminTeacherSubject(username, subject) {
  return request(`/admin/teachers/${encodeURIComponent(username)}/subject`, {
    method: "PUT",
    body: JSON.stringify({ subject }),
  });
}

export function fetchAdminTeacherClasses(username) {
  const q = username ? `?username=${encodeURIComponent(username)}` : "";
  return request(`/admin/teacher-classes${q}`);
}

export function fetchAdminSubjects() {
  return request("/admin/subjects");
}

export function fetchAdminStudents(classId) {
  const q = classId ? `?classId=${encodeURIComponent(classId)}` : "";
  return request(`/admin/students${q}`);
}

export function fetchAdminClasses() {
  return request("/admin/classes");
}

export function createAdminStudent(payload) {
  return request("/admin/students", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function updateAdminStudent(id, payload) {
  return request(`/admin/students/${encodeURIComponent(id)}`, {
    method: "PUT",
    body: JSON.stringify(payload),
  });
}

export function deleteAdminStudent(id) {
  return request(`/admin/students/${encodeURIComponent(id)}`, {
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

export function generateSelftestByAgent(payload) {
  const p = payload || {};
  const body = {
    demand: String(p.demand || ""),
    subject: String(p.subject ?? ""),
    grade: String(p.grade ?? ""),
    questionType: String(p.questionType ?? ""),
    knowledgePoint: String(p.knowledgePoint ?? p.knowledge ?? ""),
    difficulty: String(p.difficulty ?? ""),
  };
  const raw = p.count;
  if (raw != null && raw !== "") {
    const n = Number(raw);
    if (!Number.isNaN(n) && n > 0) {
      body.count = n;
    }
  }
  return request("/selftest/ai/generate", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export function gradeSelftestByAgent(payload) {
  const selections =
    payload?.selections && typeof payload.selections === "object" ? payload.selections : {};
  const answers =
    payload?.answers && typeof payload.answers === "object" ? payload.answers : {};
  return request("/selftest/ai/grade", {
    method: "POST",
    body: JSON.stringify({
      question: String(payload?.question || ""),
      studentAnswer: String(payload?.studentAnswer || ""),
      paperId: String(payload?.paperId || ""),
      selections,
      answers,
    }),
  });
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

export async function generateStudentStudyPathPlan(payload = {}) {
  const baseUrl = String(import.meta.env.VITE_DIFY_BASE_URL || "https://api.dify.ai").replace(/\/+$/, "");
  const appKey = String(import.meta.env.VITE_STUDENT_STUDY_PLAN_APP_KEY || "").trim();
  const routeMode = String(import.meta.env.VITE_STUDENT_STUDY_PLAN_ROUTE || "auto").trim().toLowerCase();
  if (!appKey) {
    throw new Error("学业规划智能体未配置：请设置 VITE_STUDENT_STUDY_PLAN_APP_KEY");
  }

  const query =
    String(payload.query || "").trim() ||
    "请根据以下信息，生成小学阶段7天学业规划，每天给出学习重点和可执行任务。";
  const studentId = String(payload.studentId || payload.student_id || payload.user || "student-self-test");
  const grade = String(payload.grade || "小学");
  const answersJson =
    typeof payload.answers_json === "string"
      ? payload.answers_json
      : JSON.stringify(payload.answers || []);
  const sessionDuration = String(payload.session_duration || payload.sessionDuration || "30");
  const requestBody = {
    inputs: {
      student_id: studentId,
      grade,
      answers_json: answersJson,
      session_duration: sessionDuration,
      subject: payload.subject || "综合",
      weaknesses: payload.weaknesses || "两位数乘法、应用题审题、单词拼写",
      classProgress: payload.classProgress || "小学阶段同步课程",
      learningGoal: payload.learningGoal || "7天内提升基础能力和作业正确率",
    },
    query,
    response_mode: "blocking",
    user: studentId,
  };
  const postDify = async (path, body) => {
    const res = await fetch(`${baseUrl}${path}`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${appKey}`,
      },
      body: JSON.stringify(body),
    });
    const text = await res.text();
    let data = {};
    try {
      data = text ? JSON.parse(text) : {};
    } catch {
      data = {};
    }
    return { ok: res.ok, status: res.status, data, text, path };
  };

  const pickWorkflowAnswer = (outputs) => {
    if (!outputs || typeof outputs !== "object") return "";
    if (typeof outputs.answer === "string") return outputs.answer;
    if (typeof outputs.text === "string") return outputs.text;
    const firstString = Object.values(outputs).find((x) => typeof x === "string");
    return typeof firstString === "string" ? firstString : JSON.stringify(outputs);
  };

  const routesByMode = {
    chat: [{ path: "/v1/chat-messages", body: requestBody }],
    completion: [{ path: "/v1/completion-messages", body: requestBody }],
    workflow: [{
      path: "/v1/workflows/run",
      body: {
        inputs: requestBody.inputs,
        response_mode: "blocking",
        user: requestBody.user,
      },
    }],
  };
  const defaultRoutes = [
    ...routesByMode.chat,
    ...routesByMode.completion,
    ...routesByMode.workflow,
  ];
  const routes = routesByMode[routeMode] || defaultRoutes;

  let lastError = null;
  for (const route of routes) {
    const result = await postDify(route.path, route.body);
    if (!result.ok) {
      lastError = result;
      continue;
    }
    const answer =
      typeof result.data?.answer === "string"
        ? result.data.answer
        : pickWorkflowAnswer(result.data?.data?.outputs);
    return {
      ...result.data,
      answer: String(answer || "").trim(),
      _route: route.path,
    };
  }

  const tip =
    routeMode === "auto"
      ? "请检查 Dify 应用类型与路由：chat-messages / completion-messages / workflows/run"
      : `请检查 Dify 应用类型与路由，当前配置为 ${routeMode}`;
  throw new Error(
    lastError?.data?.message ||
      lastError?.data?.code ||
      lastError?.text ||
      `${tip}，请求失败(${lastError?.status || "unknown"})`,
  );
}
