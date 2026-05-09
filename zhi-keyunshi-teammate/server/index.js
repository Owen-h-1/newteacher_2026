import dotenv from "dotenv";
dotenv.config({ override: true });
import express from "express";
import cors from "cors";
import bcrypt from "bcryptjs";
import jwt from "jsonwebtoken";
import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import pptxgen from "pptxgenjs";
import { Document, Packer, Paragraph, HeadingLevel, TextRun } from "docx";
import XLSX from "xlsx";
import {
  initDatabase,
  seedIfEmpty,
  reconcileClassInviteCodes,
  findUserByUsername,
  findUserByEmail,
  findUserById,
  createUser,
  deleteUserById,
  linkStudentAccountToClassFromRegistration,
  bindRegisteredStudentToRoster,
  listHomeworkWithStatus,
  createHomework,
  homeworkExists,
  hasSubmitted,
  createSubmission,
  createSubmissionWithAnswers,
  getHomeworkDetailForStudent,
  listHomeworkSubmissionsForTeacher,
  getHomeworkSubmissionDetailForTeacher,
  setHomeworkSubmissionGrade,
  listHomeworkManageForTeacher,
  deleteHomeworkForTeacher,
  listTeacherClasses,
  getClassInviteCodesForTeacher,
  createTeacherClass,
  resetClassInviteCode,
  teacherOwnsClass,
  getClassDisplayById,
  findClassByInviteCode,
  listStudents,
  addStudent,
  removeStudent,
  updateStudent,
  batchUpsertStudents,
  getSignData,
  refreshSignCode,
  markSignRecord,
  undoSignRecord,
  getExercises,
  toggleExerciseCollection,
  getTodayClassSchedules,
  listTeacherTodos,
  createTeacherTodo,
  updateTeacherTodo,
  deleteTeacherTodo,
  createTeacherMessage,
  getStudentClassContextByAccount,
  listStudentMessages,
  markStudentMessageRead,
  getTeacherWeakWarningsFromHomework,
  getTeacherStudentBoardData,
  insertAiGenerationRecord,
} from "./db.js";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const DATA_FILE = path.join(__dirname, "data.json");
const PPT_ASSETS_FILE = path.join(__dirname, "ppt-assets.json");
const PORT = Number(process.env.PORT || 3000);
const JWT_SECRET = process.env.JWT_SECRET || "zhi-keyunshi-dev-secret";
const LLM_PROVIDER = String(process.env.LLM_PROVIDER || "auto").toLowerCase();
const OPENROUTER_API_BASE = process.env.OPENROUTER_API_BASE || "https://openrouter.ai/api/v1";
const OPENROUTER_MODEL = process.env.OPENROUTER_MODEL || "qwen/qwen3-8b:free";
const OPENROUTER_API_KEY = process.env.OPENROUTER_API_KEY || "";
const OLLAMA_API_BASE = process.env.OLLAMA_API_BASE || "http://127.0.0.1:11434";
const OLLAMA_MODEL = process.env.OLLAMA_MODEL || "qwen2.5:7b";
const DOUBAO_API_BASE = process.env.DOUBAO_API_BASE || "https://ark.cn-beijing.volces.com/api/v3";
const DOUBAO_MODEL = process.env.DOUBAO_MODEL || "doubao-1-5-pro-32k-250115";
const DOUBAO_API_KEY = process.env.DOUBAO_API_KEY || "";
const EXTERNAL_SIGNIN_FACE_API = process.env.EXTERNAL_SIGNIN_FACE_API || "";
const EXTERNAL_WARNINGS_AGENT_API = process.env.EXTERNAL_WARNINGS_AGENT_API || "";
const EXTERNAL_TEACHING_AGENT_API = process.env.EXTERNAL_TEACHING_AGENT_API || "";
const EXTERNAL_PPT_LECTURE_API = process.env.EXTERNAL_PPT_LECTURE_API || "";
const EXTERNAL_DIGITAL_HUMAN_API = process.env.EXTERNAL_DIGITAL_HUMAN_API || "";
const EXTERNAL_COURSEWARE_GEN_API = process.env.EXTERNAL_COURSEWARE_GEN_API || "";
const EXTERNAL_TEACHING_GEN_API = process.env.EXTERNAL_TEACHING_GEN_API || "";
const EXTERNAL_SERVICE_API_KEY = process.env.EXTERNAL_SERVICE_API_KEY || "";

async function readPptAssets() {
  try {
    const text = await fs.readFile(PPT_ASSETS_FILE, "utf8");
    const list = JSON.parse(text);
    return Array.isArray(list) ? list : [];
  } catch {
    return [];
  }
}

async function writePptAssets(list) {
  await fs.writeFile(PPT_ASSETS_FILE, JSON.stringify(list, null, 2), "utf8");
}

const app = express();
app.use(cors());
app.use(express.json());

function signToken(user) {
  return jwt.sign({ id: user.id, role: user.role }, JWT_SECRET, { expiresIn: "7d" });
}

function safeUser(user) {
  return {
    id: user.id,
    username: user.username,
    role: user.role,
    email: user.email,
    name: user.name || user.username,
  };
}

async function bootstrapSeedData() {
  await initDatabase();
  try {
    const text = await fs.readFile(DATA_FILE, "utf8");
    const json = JSON.parse(text);
    if (Array.isArray(json.users)) {
      for (const u of json.users) {
        if (!u.passwordHash && u.password) {
          u.passwordHash = await bcrypt.hash(u.password, 10);
          delete u.password;
        }
      }
    }
    await seedIfEmpty(json);
  } catch {
    await seedIfEmpty({});
  }
  try {
    await reconcileClassInviteCodes();
  } catch (e) {
    console.error("reconcileClassInviteCodes", e);
  }
}

async function auth(req, res, next) {
  const token = req.headers.authorization?.replace("Bearer ", "");
  if (!token) return res.status(401).json({ message: "未登录或登录已过期" });
  try {
    const payload = jwt.verify(token, JWT_SECRET);
    const user = await findUserById(payload.id);
    if (!user) return res.status(401).json({ message: "用户不存在" });
    req.user = safeUser(user);
    next();
  } catch {
    res.status(401).json({ message: "身份验证失败" });
  }
}

function requireRole(role) {
  return (req, res, next) => {
    if (!req.user || req.user.role !== role) {
      return res.status(403).json({ message: "无权限访问该接口" });
    }
    next();
  };
}

async function chatWithDoubaoModel(message) {
  if (!DOUBAO_API_KEY) {
    throw new Error("豆包 API Key 未配置，请先在 .env 中设置 DOUBAO_API_KEY");
  }
  const endpoint = `${DOUBAO_API_BASE.replace(/\/$/, "")}/chat/completions`;
  const res = await fetch(endpoint, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${DOUBAO_API_KEY}`,
    },
    body: JSON.stringify({
      model: DOUBAO_MODEL,
      temperature: 0.6,
      messages: [
        {
          role: "system",
          content: "你是小学学习助手，请用简短、清晰、鼓励的中文回答。",
        },
        { role: "user", content: String(message || "") },
      ],
    }),
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    const msg =
      data?.error?.message ||
      data?.message ||
      `豆包请求失败(${res.status})`;
    throw new Error(msg);
  }
  const answer = data?.choices?.[0]?.message?.content;
  if (!answer) throw new Error("豆包返回内容为空");
  return answer;
}

async function chatWithOpenRouterModel(message) {
  if (!OPENROUTER_API_KEY) {
    throw new Error("云端模型未配置，请在 .env 中设置 OPENROUTER_API_KEY");
  }
  const endpoint = `${OPENROUTER_API_BASE.replace(/\/$/, "")}/chat/completions`;
  const res = await fetch(endpoint, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${OPENROUTER_API_KEY}`,
    },
    body: JSON.stringify({
      model: OPENROUTER_MODEL,
      temperature: 0.6,
      messages: [
        {
          role: "system",
          content: "你是小学学习助手，请用简短、清晰、鼓励的中文回答。",
        },
        { role: "user", content: String(message || "") },
      ],
    }),
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    const msg = data?.error?.message || data?.message || `云端模型请求失败(${res.status})`;
    throw new Error(msg);
  }
  const answer = data?.choices?.[0]?.message?.content;
  if (!answer) throw new Error("云端模型返回内容为空");
  return answer;
}

async function chatWithOllamaModel(message) {
  const endpoint = `${OLLAMA_API_BASE.replace(/\/$/, "")}/api/chat`;
  const res = await fetch(endpoint, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      model: OLLAMA_MODEL,
      stream: false,
      messages: [
        {
          role: "system",
          content:
            "你是面向小学生（含低年级）的学习小助手。语气要像温柔的大朋友：句子短、好懂，多用「你」「我们」，少用难词和长句；适当鼓励，必要时举一个小例子。不要使用网络梗或成人化表达。严禁在回答里写「我是某模型」「作为人工智能」等自我介绍，严禁在句末或括号里标注模型名、厂商名或技术说明；只输出对孩子有用的正文。",
        },
        { role: "user", content: String(message || "") },
      ],
    }),
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    const msg = data?.error || data?.message || `Ollama 请求失败(${res.status})`;
    throw new Error(msg);
  }
  const answer = data?.message?.content;
  if (!answer) throw new Error("Ollama 返回内容为空");
  return answer;
}

async function checkOllamaAvailable() {
  try {
    const endpoint = `${OLLAMA_API_BASE.replace(/\/$/, "")}/api/tags`;
    const res = await fetch(endpoint);
    return res.ok;
  } catch {
    return false;
  }
}

function checkOpenRouterConfigured() {
  return !!OPENROUTER_API_KEY;
}

function checkDoubaoConfigured() {
  return !!DOUBAO_API_KEY;
}

function requireExternalEndpoint(res, endpoint, name) {
  if (endpoint) return true;
  res.status(503).json({ message: `${name} 未配置，请在 .env 设置对应 EXTERNAL_*_API` });
  return false;
}

async function callExternalJson(endpoint, payload, timeoutMs = 30000) {
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), timeoutMs);
  try {
    const res = await fetch(endpoint, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...(EXTERNAL_SERVICE_API_KEY ? { Authorization: `Bearer ${EXTERNAL_SERVICE_API_KEY}` } : {}),
      },
      body: JSON.stringify(payload),
      signal: ctrl.signal,
    });
    const text = await res.text();
    let data = {};
    try {
      data = text ? JSON.parse(text) : {};
    } catch {
      data = { message: text || `外部服务返回非JSON(${res.status})` };
    }
    if (!res.ok) {
      throw new Error(data?.message || `外部服务调用失败(${res.status})`);
    }
    return data;
  } finally {
    clearTimeout(timer);
  }
}

const AI_AUDIT_JSON_MAX = 200000;

function jsonForAiAudit(value) {
  const clip = (s) => {
    const t = String(s);
    return t.length <= AI_AUDIT_JSON_MAX ? t : `${t.slice(0, AI_AUDIT_JSON_MAX)}\n...[truncated]`;
  };
  try {
    return clip(
      JSON.stringify(value, (k, v) => {
        if ((k === "imageData" || k === "password" || k === "password_hash") && typeof v === "string") {
          return v.length > 400 ? `[omitted ${v.length} chars]` : v;
        }
        return v;
      })
    );
  } catch {
    return clip(String(value));
  }
}

async function auditAiCall(featureType, requestObj, success, message, responseObj) {
  await insertAiGenerationRecord({
    featureType,
    message: message != null ? String(message).slice(0, 500) : null,
    requestPayload: requestObj === undefined ? null : jsonForAiAudit(requestObj),
    responsePayload: responseObj === undefined ? null : jsonForAiAudit(responseObj),
    success,
  });
}

function getProviderOrder() {
  return ["ollama"];
}

async function chatWithLLM(message) {
  const order = getProviderOrder();
  const errors = [];
  for (const p of order) {
    try {
      if (p === "openrouter") {
        const answer = await chatWithOpenRouterModel(message);
        return { answer, providerUsed: "openrouter" };
      }
      if (p === "ollama") {
        const answer = await chatWithOllamaModel(message);
        return { answer, providerUsed: "ollama" };
      }
      if (p === "doubao") {
        const answer = await chatWithDoubaoModel(message);
        return { answer, providerUsed: "doubao" };
      }
    } catch (e) {
      errors.push(`${p}: ${e.message || "调用失败"}`);
    }
  }
  throw new Error(
    `当前 Ollama 不可用。请确认 Ollama 服务已启动，且模型 ${OLLAMA_MODEL} 已下载完成。详情：${errors.join(" | ")}`
  );
}

function buildNationalPlatformFallback({ keyword = "", type = "all", grade = "小学" } = {}) {
  const base = "https://www.zxx.edu.cn/";
  const buildJumpUrl = (subject, resourceType) => {
    const q = [grade, subject, resourceType === "exercise" ? "习题" : "课程", keyword]
      .filter((x) => String(x || "").trim())
      .join(" ");
    return `${base}search?${new URLSearchParams({ keyword: q }).toString()}`;
  };
  const keywordPart = String(keyword || "").trim();
  const withKeyword = (label) => (keywordPart ? `${label}（关键词：${keywordPart}）` : label);
  const list = [
    {
      id: "np-course-1",
      title: withKeyword(`国家平台 · ${grade}数学《两位数乘法》微课`),
      type: "course",
      source: "国家中小学智慧教育平台",
      subject: "数学",
      grade,
      duration: "15分钟",
      url: buildJumpUrl("数学", "course"),
      cover: "",
    },
    {
      id: "np-course-2",
      title: withKeyword(`国家平台 · ${grade}语文《阅读理解策略》课堂资源`),
      type: "course",
      source: "国家中小学智慧教育平台",
      subject: "语文",
      grade,
      duration: "18分钟",
      url: buildJumpUrl("语文", "course"),
      cover: "",
    },
    {
      id: "np-ex-1",
      title: withKeyword(`国家平台 · ${grade}数学应用题在线练习`),
      type: "exercise",
      source: "国家中小学智慧教育平台",
      subject: "数学",
      grade,
      duration: "10题",
      url: buildJumpUrl("数学", "exercise"),
      cover: "",
    },
    {
      id: "np-ex-2",
      title: withKeyword(`国家平台 · ${grade}英语单词拼写训练`),
      type: "exercise",
      source: "国家中小学智慧教育平台",
      subject: "英语",
      grade,
      duration: "12题",
      url: buildJumpUrl("英语", "exercise"),
      cover: "",
    },
  ];
  const normalizedKeyword = String(keyword).trim();
  return list.filter((item) => {
    if (type !== "all" && item.type !== type) return false;
    if (!normalizedKeyword) return true;
    return item.title.includes(normalizedKeyword) || item.subject.includes(normalizedKeyword);
  });
}

app.get("/api/health", (req, res) => {
  res.json({ ok: true, service: "zhi-keyunshi-server", database: "mysql" });
});

app.post("/api/auth/register", async (req, res) => {
  let pendingRollbackUserId = null;
  try {
    const { username, password, email, role, name, inviteCode, studentNo, studentName } = req.body || {};
    if (!username || !password || !email) {
      return res.status(400).json({ message: "账号、密码、邮箱不能为空" });
    }
    if (!["student", "teacher"].includes(role)) {
      return res.status(400).json({ message: "角色不合法" });
    }
    if (!/^[a-zA-Z0-9]{6,20}$/.test(username)) {
      return res.status(400).json({ message: "账号需为6-20位字母数字" });
    }
    if (String(password).length < 6) {
      return res.status(400).json({ message: "密码长度至少6位" });
    }
    const invite = String(inviteCode || "").trim();
    let classNameForStudent = null;
    let classIdForStudent = null;
    let studentNoTrim = "";
    let studentNameTrim = "";
    if (role === "student") {
      if (!invite) {
        return res.status(400).json({ message: "学生注册需填写班级邀请码" });
      }
      const cls = await findClassByInviteCode(invite);
      if (!cls?.classId || !cls?.className) {
        return res.status(400).json({ message: "班级邀请码无效，请核对后重试" });
      }
      classIdForStudent = cls.classId;
      classNameForStudent = cls.className;
      studentNoTrim = String(studentNo || "").trim();
      studentNameTrim = String(studentName || name || "").trim();
      if (!studentNoTrim || !studentNameTrim) {
        return res.status(400).json({ message: "学生注册需填写学号与真实姓名（须与老师登记一致）" });
      }
      if (!/^\d{6,20}$/.test(studentNoTrim)) {
        return res.status(400).json({ message: "学号须为6-20位数字" });
      }
    }
    if (await findUserByUsername(username)) {
      return res.status(409).json({ message: "账号已存在" });
    }
    if (await findUserByEmail(email)) {
      return res.status(409).json({ message: "邮箱已被使用" });
    }
    const passwordHash = await bcrypt.hash(password, 10);
    const user = await createUser({
      username,
      email,
      role,
      name:
        role === "student"
          ? studentNameTrim
          : String(name || username || "")
              .trim() || username,
      passwordHash,
    });
    pendingRollbackUserId = user?.id || null;
    if (role === "student" && classIdForStudent) {
      const bind = await bindRegisteredStudentToRoster(
        username,
        studentNameTrim,
        studentNoTrim,
        classIdForStudent
      );
      if (!bind.ok) {
        await deleteUserById(user.id);
        pendingRollbackUserId = null;
        return res.status(409).json({ message: bind.message || "无法绑定花名册" });
      }
    }
    pendingRollbackUserId = null;
    res.status(201).json({
      message: "注册成功",
      className: role === "student" ? classNameForStudent : undefined,
      classId: role === "student" ? classIdForStudent : undefined,
    });
  } catch (e) {
    if (pendingRollbackUserId) {
      await deleteUserById(pendingRollbackUserId).catch(() => {});
    }
    console.error("register", e);
    const code = e?.code;
    if (code === "ER_DUP_ENTRY") {
      return res.status(409).json({ message: "账号或邮箱已存在" });
    }
    res.status(500).json({ message: "注册失败，请稍后再试" });
  }
});

app.post("/api/auth/login", async (req, res) => {
  const { username, password } = req.body || {};
  if (!username || !password) {
    return res.status(400).json({ message: "请输入账号和密码" });
  }
  const user = await findUserByUsername(username);
  if (!user) return res.status(401).json({ message: "账号或密码错误" });
  const hash = user.password_hash ?? user.passwordHash ?? "";
  const ok = await bcrypt.compare(password, hash);
  if (!ok) return res.status(401).json({ message: "账号或密码错误" });
  const token = signToken(user);
  res.json({ message: "登录成功", token, user: safeUser(user) });
});

app.get("/api/auth/me", auth, (req, res) => {
  res.json({ user: req.user });
});

app.get("/api/homework", auth, async (req, res) => {
  const list =
    req.user.role === "student"
      ? await listHomeworkWithStatus(req.user.id, {
          onlyForClass: (await getStudentClassContextByAccount(req.user.username))?.classId ?? "",
        })
      : await listHomeworkWithStatus(req.user.id);
  const { status } = req.query;
  const filtered =
    status && ["pending", "completed"].includes(status)
      ? list.filter((item) => item.status === status)
      : list;
  res.json({ list: filtered });
});

app.post("/api/homework", auth, requireRole("teacher"), async (req, res) => {
  const { classId, deadline, title, description, questions } = req.body || {};
  const cid = String(classId || "").trim();
  if (!cid) {
    return res.status(400).json({ message: "请选择班级（classId）" });
  }
  if (!(await teacherOwnsClass(req.user.id, cid))) {
    return res.status(403).json({ message: "无权向该班级发布作业" });
  }
  if (!title || !title.trim()) {
    return res.status(400).json({ message: "任务标题不能为空" });
  }
  if (!Array.isArray(questions) || questions.length === 0) {
    return res.status(400).json({ message: "请至少添加一道题目" });
  }
  const item = await createHomework({
    subject: "数学",
    subjectClass: "math",
    subjectIcon: "fas fa-calculator",
    title: title.trim(),
    teacherId: req.user.id,
    teacherName: req.user.name,
    classId: cid,
    deadline: deadline || "三日内",
    type: "综合题",
    questionCount: questions.length,
    difficultyText: "进阶",
    description: description || "",
    questions,
  });
  res.status(201).json({ message: "发布成功", item });
});

app.get("/api/homework/:id", auth, async (req, res) => {
  const studentClass =
    req.user.role === "student"
      ? (await getStudentClassContextByAccount(req.user.username))?.classId ?? ""
      : undefined;
  const item = await getHomeworkDetailForStudent(req.params.id, req.user.id, studentClass);
  if (!item) return res.status(404).json({ message: "作业不存在或无权查看" });
  res.json({ item });
});

app.post("/api/homework/:id/submit", auth, async (req, res) => {
  const homeworkId = req.params.id;
  if (req.user.role !== "student") {
    return res.status(403).json({ message: "仅学生可提交作业，请切换到学生账号后重试" });
  }
  if (!(await homeworkExists(homeworkId))) {
    return res.status(404).json({ message: "作业不存在" });
  }
  const myClass = (await getStudentClassContextByAccount(req.user.username))?.classId ?? "";
  const detail = await getHomeworkDetailForStudent(homeworkId, req.user.id, myClass);
  if (!detail) {
    return res.status(403).json({ message: "该作业不属于你所在班级，无法提交" });
  }
  const answers = req.body?.answers;
  if (Array.isArray(answers)) {
    await createSubmissionWithAnswers(homeworkId, req.user.id, answers);
    return res.json({ message: "提交成功，可查看答案解析" });
  }
  if (await hasSubmitted(homeworkId, req.user.id)) {
    return res.json({ message: "你已提交过该作业" });
  }
  await createSubmission(homeworkId, req.user.id);
  res.json({ message: "提交成功" });
});

app.get("/api/teacher/homework/:id/submissions", auth, requireRole("teacher"), async (req, res) => {
  const data = await listHomeworkSubmissionsForTeacher(req.params.id, req.user.id);
  if (!data) return res.status(404).json({ message: "作业不存在或无权限查看" });
  res.json(data);
});

app.get("/api/teacher/homework/:id/submission-detail", auth, requireRole("teacher"), async (req, res) => {
  const studentNo = String(req.query.studentNo || "").trim();
  if (!studentNo) return res.status(400).json({ message: "studentNo 不能为空" });
  const data = await getHomeworkSubmissionDetailForTeacher(req.params.id, req.user.id, studentNo);
  if (!data) return res.status(404).json({ message: "未找到该学生的作答详情" });
  res.json(data);
});

// 固定路径，避免部分环境下「/homework/:id/grade」未匹配导致 Cannot POST
app.post("/api/teacher/homework/grade", auth, requireRole("teacher"), async (req, res) => {
  const homeworkId = String(req.body?.homeworkId || "").trim();
  const studentNo = String(req.body?.studentNo || "").trim();
  const grade = req.body?.grade;
  if (!homeworkId) return res.status(400).json({ message: "homeworkId 不能为空" });
  if (!studentNo) return res.status(400).json({ message: "studentNo 不能为空" });
  const out = await setHomeworkSubmissionGrade(homeworkId, req.user.id, studentNo, grade);
  if (!out.ok) return res.status(400).json({ message: out.message });
  res.json({ teacherGrade: out.teacherGrade, message: "评级已保存" });
});

app.get("/api/teacher/homework", auth, requireRole("teacher"), async (req, res) => {
  const list = await listHomeworkManageForTeacher(req.user.id);
  res.json({ list });
});

app.delete("/api/teacher/homework/:id", auth, requireRole("teacher"), async (req, res) => {
  const ok = await deleteHomeworkForTeacher(req.params.id, req.user.id);
  if (!ok) return res.status(404).json({ message: "作业不存在或无权限删除" });
  res.json({ message: "作业已删除" });
});

app.get("/api/signin/classes", auth, requireRole("teacher"), async (req, res) => {
  res.json({ classes: await listTeacherClasses(req.user.id) });
});

app.get("/api/signin/records", auth, requireRole("teacher"), async (req, res) => {
  const classId = String(req.query.classId || "").trim();
  if (!classId) return res.status(400).json({ message: "缺少 classId" });
  if (!(await teacherOwnsClass(req.user.id, classId))) {
    return res.status(403).json({ message: "无权查看该班级签到" });
  }
  const crow = await getClassDisplayById(classId);
  const data = await getSignData(classId);
  const signedCount = data.records.filter((r) => !!r.time).length;
  res.json({
    classId,
    className: crow?.className || "",
    signCode: data.session?.signCode || "ZKYS-0000",
    validUntil: data.session?.validUntil || new Date(Date.now() + 15 * 60000),
    totalStudents: data.records.length,
    signedCount,
    records: data.records,
  });
});

app.post("/api/signin/refresh", auth, requireRole("teacher"), async (req, res) => {
  const classId = String(req.body?.classId || "").trim();
  if (!classId) return res.status(400).json({ message: "缺少 classId" });
  if (!(await teacherOwnsClass(req.user.id, classId))) {
    return res.status(403).json({ message: "无权操作该班级" });
  }
  const crow = await getClassDisplayById(classId);
  const data = await refreshSignCode(classId);
  res.json({ classId, className: crow?.className || "", ...data });
});

app.post("/api/signin/mark", auth, requireRole("teacher"), async (req, res) => {
  const { classId, studentId, method } = req.body || {};
  const cid = String(classId || "").trim();
  if (!cid || !studentId) return res.status(400).json({ message: "缺少班级或学生信息" });
  if (!(await teacherOwnsClass(req.user.id, cid))) {
    return res.status(403).json({ message: "无权操作该班级" });
  }
  await markSignRecord(cid, studentId, method || "手动");
  res.json({ message: "签到已更新" });
});

app.post("/api/signin/undo", auth, requireRole("teacher"), async (req, res) => {
  const { classId, studentId } = req.body || {};
  const cid = String(classId || "").trim();
  if (!cid || !studentId) return res.status(400).json({ message: "缺少班级或学生信息" });
  if (!(await teacherOwnsClass(req.user.id, cid))) {
    return res.status(403).json({ message: "无权操作该班级" });
  }
  await undoSignRecord(cid, studentId);
  res.json({ message: "已撤销签到" });
});

// 导出签到表（真实文件）：返回 xlsx。
app.get("/api/signin/export", auth, requireRole("teacher"), async (req, res) => {
  const classId = String(req.query.classId || "").trim();
  if (!classId) return res.status(400).json({ message: "缺少 classId" });
  if (!(await teacherOwnsClass(req.user.id, classId))) {
    return res.status(403).json({ message: "无权导出该班级" });
  }
  const crow = await getClassDisplayById(classId);
  const className = crow?.className || classId;
  const data = await getSignData(classId);
  const rows = (data.records || []).map((r) => ({
    学号: r.studentId,
    姓名: r.name,
    签到时间: r.time || "",
    签到方式: r.method && r.method !== "null" ? r.method : "",
    状态: r.time ? "已签到" : "未签到",
  }));

  const ws = XLSX.utils.json_to_sheet(rows);
  const wb = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(wb, ws, "签到表");
  const buffer = XLSX.write(wb, { type: "buffer", bookType: "xlsx" });

  const safeClass = String(className).replace(/[\\/:*?"<>|]/g, "_");
  const filename = encodeURIComponent(`${safeClass}-签到表.xlsx`);
  res.setHeader("Content-Disposition", `attachment; filename*=UTF-8''${filename}`);
  res.setHeader(
    "Content-Type",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
  );
  res.send(Buffer.from(buffer));
});

// 课堂照片签到（人脸识别模型预留）：当前仅接收上传信息并返回占位结果。
app.post("/api/signin/photo-recognition", auth, requireRole("teacher"), async (req, res) => {
  const classId = String(req.body?.classId || "").trim();
  const imageName = String(req.body?.imageName || "").trim();
  const imageData = String(req.body?.imageData || "");
  if (!classId || !imageName) {
    return res.status(400).json({ message: "classId 和 imageName 不能为空" });
  }
  if (!(await teacherOwnsClass(req.user.id, classId))) {
    return res.status(403).json({ message: "无权操作该班级" });
  }
  const crow = await getClassDisplayById(classId);
  const className = crow?.className || classId;
  if (!requireExternalEndpoint(res, EXTERNAL_SIGNIN_FACE_API, "人脸签到服务")) return;
  const auditReq = { teacher: safeUser(req.user), className, imageName, imageData };
  try {
    const data = await callExternalJson(EXTERNAL_SIGNIN_FACE_API, {
      teacher: req.user,
      className,
      imageName,
      imageData,
    });
    await auditAiCall("signin-face", auditReq, true, data?.message ?? null, data);
    res.json(data);
  } catch (e) {
    await auditAiCall("signin-face", auditReq, false, e.message || "人脸签到服务调用失败", null);
    res.status(502).json({ message: e.message || "人脸签到服务调用失败" });
  }
});

app.get("/api/teacher/students", auth, requireRole("teacher"), async (req, res) => {
  const classId = String(req.query.classId || "").trim();
  if (!classId) return res.status(400).json({ message: "缺少 classId" });
  if (!(await teacherOwnsClass(req.user.id, classId))) {
    return res.status(403).json({ message: "无权查看该班级学生" });
  }
  const list = await listStudents(classId);
  res.json({ list });
});

app.get("/api/teacher/class-invites", auth, requireRole("teacher"), async (req, res) => {
  const list = await getClassInviteCodesForTeacher(req.user.id);
  res.json({ list });
});

app.post("/api/teacher/classes", auth, requireRole("teacher"), async (req, res) => {
  const className = String(req.body?.className || "").trim();
  if (!className) return res.status(400).json({ message: "班级名称不能为空" });
  const out = await createTeacherClass(req.user.id, className);
  if (!out.ok) {
    return res.status(out.message?.includes("同名") ? 409 : 400).json({ message: out.message || "创建失败" });
  }
  res.status(201).json({
    message: "班级已创建",
    classId: out.classId,
    className: out.className,
    inviteCode: out.inviteCode,
  });
});

app.post("/api/teacher/class-invites/reset", auth, requireRole("teacher"), async (req, res) => {
  const classId = String(req.body?.classId || "").trim();
  if (!classId) return res.status(400).json({ message: "缺少 classId" });
  const data = await resetClassInviteCode(req.user.id, classId);
  if (!data) return res.status(404).json({ message: "班级不存在或无权限" });
  res.json({ message: "邀请码已重置", ...data });
});

app.post("/api/teacher/students", auth, requireRole("teacher"), async (req, res) => {
  const { id, name, classId, accountUsername } = req.body || {};
  const idTrim = String(id ?? "").trim();
  const nameTrim = String(name ?? "").trim();
  const classIdTrim = String(classId ?? "").trim();
  if (!idTrim || !nameTrim || !classIdTrim) {
    return res.status(400).json({ message: "学号、姓名、班级不能为空" });
  }
  if (!(await teacherOwnsClass(req.user.id, classIdTrim))) {
    return res.status(403).json({ message: "无权向该班级添加学生" });
  }
  if (!/^\d{6,20}$/.test(idTrim)) {
    return res.status(400).json({ message: "学号应为6-20位数字" });
  }
  const accRaw = accountUsername != null ? String(accountUsername).trim() : "";
  if (accRaw) {
    const account = await findUserByUsername(accRaw);
    if (!account || account.role !== "student") {
      return res.status(400).json({ message: "学生账号不存在或不是学生角色" });
    }
  }
  try {
    await addStudent({
      id: idTrim,
      name: nameTrim,
      classId: classIdTrim,
      accountUsername: accRaw || null,
    });
    res.status(201).json({ message: "已录入本班花名册（学号、姓名）" });
  } catch (e) {
    if (String(e?.message || "").includes("Duplicate")) {
      return res.status(409).json({ message: "学号或学生账号已存在" });
    }
    throw e;
  }
});

app.delete("/api/teacher/students/:id", auth, requireRole("teacher"), async (req, res) => {
  const ok = await removeStudent(req.params.id);
  if (!ok) return res.status(404).json({ message: "学生不存在" });
  res.json({ message: "删除成功" });
});

app.put("/api/teacher/students/:id", auth, requireRole("teacher"), async (req, res) => {
  const { name, classId, accountUsername } = req.body || {};
  const nameTrim = String(name ?? "").trim();
  const classIdTrim = String(classId ?? "").trim();
  if (!nameTrim || !classIdTrim) {
    return res.status(400).json({ message: "姓名、班级不能为空" });
  }
  if (!(await teacherOwnsClass(req.user.id, classIdTrim))) {
    return res.status(403).json({ message: "无权将该学生调整到该班级" });
  }
  let accArg;
  if (accountUsername === undefined || accountUsername === null) {
    accArg = undefined;
  } else {
    const t = String(accountUsername).trim();
    if (t) {
      const account = await findUserByUsername(t);
      if (!account || account.role !== "student") {
        return res.status(400).json({ message: "学生账号不存在或不是学生角色" });
      }
    }
    accArg = t || null;
  }
  const ok = await updateStudent(req.params.id, {
    name: nameTrim,
    classId: classIdTrim,
    accountUsername: accArg,
  });
  if (!ok) return res.status(404).json({ message: "学生不存在" });
  res.json({ message: "更新成功" });
});

app.post("/api/teacher/students/batch", auth, requireRole("teacher"), async (req, res) => {
  const items = req.body?.items;
  if (!Array.isArray(items) || items.length === 0) {
    return res.status(400).json({ message: "请提供导入数据" });
  }
  const normalized = [];
  for (const it of items) {
    const accountUsername = String(it?.accountUsername || "").trim();
    if (!accountUsername) {
      normalized.push({ ...it, accountUsername: "" });
      continue;
    }
    const account = await findUserByUsername(accountUsername);
    if (!account || account.role !== "student") {
      normalized.push({ ...it, accountUsername: "" });
      continue;
    }
    normalized.push({ ...it, accountUsername });
  }
  const result = await batchUpsertStudents(normalized, req.user.id);
  res.json({
    message: `导入完成：新增${result.created}，更新${result.updated}，跳过${result.skipped}`,
    ...result,
  });
});

app.post("/api/student/join-class", auth, requireRole("student"), async (req, res) => {
  const inviteCode = String(req.body?.inviteCode || "").trim();
  if (!inviteCode) return res.status(400).json({ message: "邀请码不能为空" });
  const cls = await findClassByInviteCode(inviteCode);
  if (!cls?.classId) return res.status(404).json({ message: "邀请码无效" });
  const link = await linkStudentAccountToClassFromRegistration(
    req.user.username,
    req.user.name || req.user.username,
    cls.classId
  );
  if (!link.ok) {
    return res.status(409).json({ message: link.message || "无法加入该班级" });
  }
  res.json({ message: "加入班级成功，已写入花名册", className: cls.className });
});

app.get("/api/selftest/exercises", auth, requireRole("student"), async (req, res) => {
  res.json({ list: await getExercises(req.user.id) });
});

app.post("/api/selftest/exercises/:id/collect", auth, requireRole("student"), async (req, res) => {
  const collected = await toggleExerciseCollection(req.user.id, Number(req.params.id));
  res.json({ collected });
});

app.get("/api/selftest/recommendations", auth, requireRole("student"), async (req, res) => {
  const all = await getExercises(req.user.id);
  const allowedSubject = new Set(["math", "chinese", "english", "science", "art"]);
  const elementaryTopicHint = /(口算|乘法|阅读|写话|对话|观察|色彩|词语|拼写|图形|美术|科学|小学)/;
  const fallback = [
    { id: 9001, subject: "数学", subjectClass: "math", title: "乘法应用题闯关", knowledge: "乘法应用题", type: "填空题", time: "6分钟", difficultyClass: "medium", difficultyText: "进阶" },
    { id: 9002, subject: "科学", subjectClass: "science", title: "日常现象科学观察", knowledge: "科学观察", type: "解答题", time: "8分钟", difficultyClass: "hard", difficultyText: "挑战" },
    { id: 9003, subject: "英语", subjectClass: "english", title: "日常问候对话训练", knowledge: "日常对话", type: "选择题", time: "4分钟", difficultyClass: "medium", difficultyText: "进阶" },
  ];
  const list = [...all]
    .filter((x) => allowedSubject.has(String(x.subjectClass || "")))
    .filter((x) => elementaryTopicHint.test(`${x.subject} ${x.title} ${x.knowledge}`))
    .sort((a, b) => a.accuracy - b.accuracy)
    .slice(0, 3)
    .map((x) => ({
      id: x.id,
      subject: x.subject,
      subjectClass: x.subjectClass,
      title: x.title,
      knowledge: x.knowledge,
      type: x.type,
      time: x.time,
      difficultyClass: x.difficultyClass,
      difficultyText: x.difficultyText,
    }));
  if (list.length < 3) {
    const existing = new Set(list.map((x) => `${x.subject}|${x.title}`));
    for (const item of fallback) {
      if (!existing.has(`${item.subject}|${item.title}`)) list.push(item);
      if (list.length >= 3) break;
    }
  }
  res.json({ list: list.slice(0, 3) });
});

app.get("/api/selftest/history", auth, requireRole("student"), (req, res) => {
  res.json({
    list: [
      { id: 1, subject: "数学", subjectClass: "math", icon: "fas fa-calculator", title: "乘除法口算练习", date: "2026-04-05", score: 18, total: 20 },
      { id: 2, subject: "语文", subjectClass: "chinese", icon: "fas fa-book-open", title: "阅读理解训练", date: "2026-04-04", score: 22, total: 25 },
      { id: 3, subject: "英语", subjectClass: "english", icon: "fas fa-language", title: "日常对话练习", date: "2026-04-03", score: 13, total: 15},
    ],
  });
});

app.get("/api/platform/resources", auth, requireRole("student"), async (req, res) => {
  const keyword = String(req.query.keyword || "").trim();
  const type = String(req.query.type || "all").trim();
  const grade = String(req.query.grade || "小学").trim();
  if (!["all", "course", "exercise"].includes(type)) {
    return res.status(400).json({ message: "type 参数不合法" });
  }

  const list = buildNationalPlatformFallback({ keyword, type, grade });
  res.json({ list, source: "jump" });
});

app.get("/api/teacher/student-board", auth, requireRole("teacher"), async (req, res) => {
  const classId = String(req.query.classId || "").trim();
  if (classId && !(await teacherOwnsClass(req.user.id, classId))) {
    return res.status(403).json({ message: "无权查看该班级数据" });
  }
  const data = await getTeacherStudentBoardData(req.user.id, classId);
  res.json(data);
});

app.get("/api/teacher/overview", auth, requireRole("teacher"), async (req, res) => {
  const classes = await listTeacherClasses(req.user.id);
  const classSummary = [];
  let totalStudents = 0;
  let totalSigned = 0;

  for (const c of classes) {
    const cid = c.classId;
    const data = await getSignData(cid);
    const should = data.records.length;
    const arrived = data.records.filter((r) => !!r.time).length;
    totalStudents += should;
    totalSigned += arrived;
    classSummary.push({
      classId: cid,
      className: c.className,
      should,
      arrived,
      absent: should - arrived,
      rate: should ? Math.round((arrived / should) * 100) : 0,
    });
  }

  const teacherHomework = await listHomeworkWithStatus(req.user.id);
  const pendingReview = teacherHomework.filter((h) => h.status === "pending").length;

  const jsDay = new Date().getDay();
  const weekday = jsDay === 0 ? 7 : jsDay;
  const schedulesInDb = await getTodayClassSchedules(weekday);
  const classSummaryMap = new Map(classSummary.map((c) => [c.className, c]));

  let todos = await listTeacherTodos(req.user.id);
  if (!todos.length) {
    await createTeacherTodo(req.user.id, {
      title: "准备明日课程课件",
      meta: "明日 10:00 前完成",
      urgent: false,
    });
    await createTeacherTodo(req.user.id, {
      title: "查看学生薄弱点报告",
      meta: "建议今日完成",
      urgent: totalSigned < totalStudents,
    });
    todos = await listTeacherTodos(req.user.id);
  }

  const todayCourses = schedulesInDb.length;
  const finishedCourses = Math.floor(todayCourses / 2);

  const schedules = schedulesInDb.map((s, i) => {
    const cls = classSummaryMap.get(s.className);
    return {
      time: `${s.startTime}-${s.endTime}`,
      name: `小学数学 · ${s.lessonName}`,
      classText: `${s.className} · ${cls?.should || 0}人`,
      status: i < finishedCourses ? "finished" : "waiting",
      statusText: i < finishedCourses ? "已完成" : "待上课",
    };
  });

  const warnings = await getTeacherWeakWarningsFromHomework(req.user.id, {
    days: 30,
    minAttempts: 8,
    minWrongRate: 30,
  });

  res.json({
    teacher: { name: req.user.name || "老师" },
    stats: {
      todayCourses,
      finishedCourses,
      totalStudents,
      classCount: classes.length,
      pendingReview,
      todoCount: todos.length,
      urgentTodo: todos.filter((t) => t.urgent).length,
    },
    todos,
    schedules,
    classSummary,
    warnings,
    warningSource: {
      mode: "rules",
      basedOn: "submitted-homework-answers",
      dataRange: "近30天",
    },
  });
});

// 智能体占位接口：后续可接入“错题分析智能体”输出策略建议。
app.post("/api/teacher/warnings/agent-plan", auth, requireRole("teacher"), async (req, res) => {
  if (!requireExternalEndpoint(res, EXTERNAL_WARNINGS_AGENT_API, "薄弱点智能体服务")) return;
  const auditReq = { teacher: safeUser(req.user), payload: req.body || {} };
  try {
    const data = await callExternalJson(EXTERNAL_WARNINGS_AGENT_API, {
      teacher: req.user,
      payload: req.body || {},
    });
    await auditAiCall("warnings-agent", auditReq, true, data?.message ?? null, data);
    res.json(data);
  } catch (e) {
    await auditAiCall("warnings-agent", auditReq, false, e.message || "薄弱点智能体服务调用失败", null);
    res.status(502).json({ message: e.message || "薄弱点智能体服务调用失败" });
  }
});

// 教师 AI 课件生成接口（后端占位）：统一返回结构，前端按此渲染。
app.post("/api/teacher/ai-courseware/generate", auth, requireRole("teacher"), async (req, res) => {
  const topic = String(req.body?.topic || "").trim();
  if (!topic) return res.status(400).json({ message: "topic 不能为空" });
  if (!requireExternalEndpoint(res, EXTERNAL_COURSEWARE_GEN_API, "AI课件生成服务")) return;
  const auditReq = { teacher: safeUser(req.user), payload: req.body || {} };
  try {
    const data = await callExternalJson(EXTERNAL_COURSEWARE_GEN_API, {
      teacher: req.user,
      payload: req.body || {},
    });
    await auditAiCall("ai-courseware", auditReq, true, data?.message ?? null, data);
    res.json(data);
  } catch (e) {
    await auditAiCall("ai-courseware", auditReq, false, e.message || "AI课件生成服务调用失败", null);
    res.status(502).json({ message: e.message || "AI课件生成服务调用失败" });
  }
});

// 教师 AI 课件导出接口：生成真实 .pptx 文件并返回下载。
app.post("/api/teacher/ai-courseware/download", auth, requireRole("teacher"), async (req, res) => {
  const topic = String(req.body?.topic || "").trim();
  const slides = Array.isArray(req.body?.slides) ? req.body.slides : [];
  if (!topic) return res.status(400).json({ message: "topic 不能为空" });
  if (!slides.length) return res.status(400).json({ message: "slides 不能为空" });

  const pptx = new pptxgen();
  pptx.layout = "LAYOUT_WIDE";
  pptx.author = req.user.name || "老师";
  pptx.company = "zhi-keyunshi";
  pptx.subject = topic;
  pptx.title = `${topic} AI课件`;

  for (const s of slides) {
    const title = String(s?.title || "课件内容").trim() || "课件内容";
    const desc = String(s?.desc || "").trim();
    const slide = pptx.addSlide();
    slide.background = { color: "F4F8FF" };
    slide.addText(title, {
      x: 0.6,
      y: 0.7,
      w: 11.8,
      h: 0.9,
      fontFace: "Microsoft YaHei",
      bold: true,
      fontSize: 30,
      color: "103A66",
    });
    slide.addShape(pptx.ShapeType.roundRect, {
      x: 0.6,
      y: 1.9,
      w: 11.8,
      h: 4.8,
      radius: 0.08,
      fill: { color: "FFFFFF" },
      line: { color: "DDE8F8", pt: 1 },
      shadow: { type: "outer", color: "D5E2F5", blur: 2, angle: 45, distance: 2, opacity: 0.2 },
    });
    slide.addText(desc || "本页暂无描述内容，可在在线编辑中补充。", {
      x: 1.0,
      y: 2.5,
      w: 11.0,
      h: 3.6,
      fontFace: "Microsoft YaHei",
      fontSize: 22,
      color: "274D72",
      valign: "top",
      breakLine: true,
    });
  }

  const buffer = await pptx.write({ outputType: "nodebuffer" });
  const safeName = topic.replace(/[\\/:*?"<>|]/g, "_");
  const filename = encodeURIComponent(`${safeName}.pptx`);
  res.setHeader(
    "Content-Disposition",
    `attachment; filename*=UTF-8''${filename}`
  );
  res.setHeader("Content-Type", "application/vnd.openxmlformats-officedocument.presentationml.presentation");
  res.send(Buffer.from(buffer));
});

// 教师 AI 教学设计生成接口（后端占位）：前端按此结构渲染。
app.post("/api/teacher/ai-teaching-design/generate", auth, requireRole("teacher"), async (req, res) => {
  const topic = String(req.body?.topic || "").trim();
  if (!topic) return res.status(400).json({ message: "topic 不能为空" });
  if (!requireExternalEndpoint(res, EXTERNAL_TEACHING_GEN_API, "AI教学设计生成服务")) return;
  const auditReq = { teacher: safeUser(req.user), payload: req.body || {} };
  try {
    const data = await callExternalJson(EXTERNAL_TEACHING_GEN_API, {
      teacher: req.user,
      payload: req.body || {},
    });
    await auditAiCall("instructional-design", auditReq, true, data?.message ?? null, data);
    res.json(data);
  } catch (e) {
    await auditAiCall("instructional-design", auditReq, false, e.message || "AI教学设计生成服务调用失败", null);
    res.status(502).json({ message: e.message || "AI教学设计生成服务调用失败" });
  }
});

// 教师 AI 教学设计导出接口：生成真实 docx 并下载。
app.post("/api/teacher/ai-teaching-design/download", auth, requireRole("teacher"), async (req, res) => {
  const topic = String(req.body?.topic || "").trim();
  const grade = String(req.body?.grade || "").trim();
  const subject = String(req.body?.subject || "").trim();
  const design = req.body?.design || {};
  if (!topic) return res.status(400).json({ message: "topic 不能为空" });

  const objectives = Array.isArray(design.objectives) ? design.objectives : [];
  const methods = Array.isArray(design.methods) ? design.methods : [];
  const preparations = Array.isArray(design.preparations) ? design.preparations : [];
  const process = Array.isArray(design.process) ? design.process : [];
  const homework = Array.isArray(design.homework) ? design.homework : [];

  const doc = new Document({
    sections: [
      {
        children: [
          new Paragraph({
            heading: HeadingLevel.HEADING_1,
            children: [new TextRun(`${topic} 教学设计`)],
          }),
          new Paragraph(`${grade} · ${subject}`),
          new Paragraph(""),
          new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun("教学目标")] }),
          ...objectives.map((x) => new Paragraph(`• ${x}`)),
          new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun("教学重难点")] }),
          new Paragraph(`重点：${String(design.keyPoints || "")}`),
          new Paragraph(`难点：${String(design.difficultPoints || "")}`),
          new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun("教学方法")] }),
          ...methods.map((x) => new Paragraph(`• ${x}`)),
          new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun("教学准备")] }),
          ...preparations.map((x) => new Paragraph(`• ${x}`)),
          new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun("教学过程")] }),
          ...process.map((x, i) =>
            new Paragraph(`${i + 1}. ${String(x?.title || "")}（${String(x?.time || "")}）：${String(x?.desc || "")}`)
          ),
          new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun("作业布置")] }),
          ...homework.map((x) => new Paragraph(`• ${x}`)),
          new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun("AI优化建议")] }),
          new Paragraph(String(design.aiSuggestion || "")),
        ],
      },
    ],
  });

  const buffer = await Packer.toBuffer(doc);
  const safeName = topic.replace(/[\\/:*?"<>|]/g, "_");
  const filename = encodeURIComponent(`${safeName}-教学设计.docx`);
  res.setHeader("Content-Disposition", `attachment; filename*=UTF-8''${filename}`);
  res.setHeader(
    "Content-Type",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
  );
  res.send(Buffer.from(buffer));
});

// 智能体预留接口：后续接入教学设计智能体编排。
app.post("/api/teacher/ai-teaching-design/agent-plan", auth, requireRole("teacher"), async (req, res) => {
  if (!requireExternalEndpoint(res, EXTERNAL_TEACHING_AGENT_API, "教学设计智能体服务")) return;
  const auditReq = { teacher: safeUser(req.user), payload: req.body || {} };
  try {
    const data = await callExternalJson(EXTERNAL_TEACHING_AGENT_API, {
      teacher: req.user,
      payload: req.body || {},
    });
    await auditAiCall("instructional-design-agent", auditReq, true, data?.message ?? null, data);
    res.json(data);
  } catch (e) {
    await auditAiCall("instructional-design-agent", auditReq, false, e.message || "教学设计智能体服务调用失败", null);
    res.status(502).json({ message: e.message || "教学设计智能体服务调用失败" });
  }
});

// 教师上传课件（后端占位）：先存元数据，后续可替换为真实文件存储与PPT解析。
app.post("/api/teacher/ppt-assets", auth, requireRole("teacher"), async (req, res) => {
  const name = String(req.body?.name || "").trim();
  const slides = Array.isArray(req.body?.slides)
    ? req.body.slides.map((x) => String(x || "").trim()).filter(Boolean)
    : [];
  if (!name) return res.status(400).json({ message: "课件名称不能为空" });
  const item = {
    id: `ppt-${Date.now()}-${Math.floor(Math.random() * 10000)}`,
    teacherId: req.user.id,
    teacherUsername: req.user.username,
    name,
    uploadedAt: new Date().toISOString(),
    slides: slides.length
      ? slides
      : [`${name} · 课程导入`, `${name} · 核心知识点`, `${name} · 例题讲解`, `${name} · 课堂总结`],
  };
  const list = await readPptAssets();
  const next = [item, ...list].slice(0, 200);
  await writePptAssets(next);
  res.status(201).json({ message: "上传成功", item });
});

// 教师查看自己上传的课件历史。
app.get("/api/teacher/ppt-assets", auth, requireRole("teacher"), async (req, res) => {
  const source = await readPptAssets();
  const teacherUsername = String(req.user.username || "").trim();
  const teacherId = String(req.user.id || "").trim();
  const list = source.filter((x) => {
    const ownerUsername = String(x.teacherUsername || "").trim();
    const ownerId = String(x.teacherId || "").trim();
    if (ownerUsername) return ownerUsername === teacherUsername;
    return ownerId === teacherId;
  });
  res.json({ list });
});

// 教师删除自己上传的课件。
app.delete("/api/teacher/ppt-assets/:id", auth, requireRole("teacher"), async (req, res) => {
  const id = String(req.params.id || "").trim();
  if (!id) return res.status(400).json({ message: "课件ID不能为空" });
  const source = await readPptAssets();
  const target = source.find((x) => x.id === id);
  if (!target) return res.status(404).json({ message: "课件不存在" });
  const ownerUsername = String(target.teacherUsername || "").trim();
  const ownerId = String(target.teacherId || "").trim();
  const currentUsername = String(req.user.username || "").trim();
  const currentId = String(req.user.id || "").trim();
  const canDelete = ownerUsername ? ownerUsername === currentUsername : ownerId === currentId;
  if (!canDelete) {
    return res.status(403).json({ message: "无权限删除该课件" });
  }
  const next = source.filter((x) => x.id !== id);
  await writePptAssets(next);
  res.json({ message: "删除成功" });
});

// 学生读取可用课件列表。
app.get("/api/student/ppt-assets", auth, requireRole("student"), async (req, res) => {
  const source = await readPptAssets();
  const list = source.map((x) => ({
    id: x.id,
    name: x.name,
    uploadedAt: x.uploadedAt,
    slides: x.slides,
  }));
  res.json({ list });
});

// 学生触发“智能体讲解”生成：转发到外部服务。
app.post("/api/student/ppt-lecture/generate", auth, requireRole("student"), async (req, res) => {
  const pptId = String(req.body?.pptId || "").trim();
  if (!pptId) return res.status(400).json({ message: "pptId 不能为空" });
  const source = await readPptAssets();
  const item = source.find((x) => x.id === pptId);
  if (!item) return res.status(404).json({ message: "课件不存在或已失效" });
  if (!requireExternalEndpoint(res, EXTERNAL_PPT_LECTURE_API, "PPT讲解服务")) return;
  const auditReq = { student: safeUser(req.user), ppt: { id: item.id, name: item.name }, payload: req.body || {} };
  try {
    const data = await callExternalJson(EXTERNAL_PPT_LECTURE_API, {
      student: req.user,
      ppt: item,
      payload: req.body || {},
    });
    await auditAiCall("ppt-lecture", auditReq, true, data?.message ?? null, data);
    res.json(data);
  } catch (e) {
    await auditAiCall("ppt-lecture", auditReq, false, e.message || "PPT讲解服务调用失败", null);
    res.status(502).json({ message: e.message || "PPT讲解服务调用失败" });
  }
});

app.get("/api/student/overview", auth, requireRole("student"), async (req, res) => {
  const sctx = await getStudentClassContextByAccount(req.user.username);
  const list = await listHomeworkWithStatus(req.user.id, {
    onlyForClass: sctx?.classId ?? "",
  });
  const pending = list.filter((h) => h.status === "pending");
  const todayPending = pending.filter((h) => String(h.deadline || "").includes("今日") || h.isUrgent).length || Math.min(pending.length, 4);
  const done = list.filter((h) => h.status === "completed").length;
  const weekProgress = list.length ? Math.round((done / list.length) * 100) : 0;

  const className = sctx?.className || "";
  const messages = sctx?.classId ? await listStudentMessages(req.user.id, sctx.classId) : [];
  res.json({
    user: { name: req.user.name, className: className || null, classId: sctx?.classId || null },
    stats: { todayPending, weekProgress, totalHours: 12.5 },
    todoHomework: pending.slice(0, 5),
    courses: [
      { subject: "小学数学", teacher: "王老师 · 乘法与应用题", progress: weekProgress || 68, subjectClass: "math", icon: "fas fa-calculator" },
      { subject: "小学语文", teacher: "李老师 · 阅读与写话", progress: 52, subjectClass: "chinese", icon: "fas fa-book-open" },
      { subject: "小学英语", teacher: "周老师 · 单词与对话", progress: 46, subjectClass: "english", icon: "fas fa-language" },
      { subject: "小学科学", teacher: "陈老师 · 植物与观察", progress: 35, subjectClass: "science", icon: "fas fa-seedling" },
      { subject: "小学美术", teacher: "林老师 · 色彩与构图", progress: 31, subjectClass: "art", icon: "fas fa-palette" },
    ],
    schoolMessages: messages,
  });
});

app.get("/api/teacher/todos", auth, requireRole("teacher"), async (req, res) => {
  res.json({ list: await listTeacherTodos(req.user.id) });
});

app.post("/api/teacher/todos", auth, requireRole("teacher"), async (req, res) => {
  const title = String(req.body?.title || "").trim();
  if (!title) return res.status(400).json({ message: "待办标题不能为空" });
  await createTeacherTodo(req.user.id, {
    title,
    meta: String(req.body?.meta || "").trim(),
    urgent: !!req.body?.urgent,
  });
  res.json({ message: "待办已添加" });
});

app.put("/api/teacher/todos/:id", auth, requireRole("teacher"), async (req, res) => {
  const ok = await updateTeacherTodo(req.user.id, Number(req.params.id), {
    title: req.body?.title,
    meta: req.body?.meta,
    urgent: req.body?.urgent,
    done: req.body?.done,
  });
  if (!ok) return res.status(404).json({ message: "待办不存在" });
  res.json({ message: "待办已更新" });
});

app.delete("/api/teacher/todos/:id", auth, requireRole("teacher"), async (req, res) => {
  const ok = await deleteTeacherTodo(req.user.id, Number(req.params.id));
  if (!ok) return res.status(404).json({ message: "待办不存在" });
  res.json({ message: "待办已删除" });
});

app.post("/api/teacher/messages", auth, requireRole("teacher"), async (req, res) => {
  const classId = String(req.body?.classId || "").trim();
  const title = String(req.body?.title || "").trim();
  const content = String(req.body?.content || "").trim();
  if (!classId || !title) return res.status(400).json({ message: "班级与标题不能为空" });
  if (!(await teacherOwnsClass(req.user.id, classId))) {
    return res.status(403).json({ message: "无权向该班级发送消息" });
  }
  const crow = await getClassDisplayById(classId);
  const className = crow?.className || "";
  if (!className) return res.status(400).json({ message: "班级不存在" });
  await createTeacherMessage(req.user.id, req.user.name || "老师", {
    classId,
    className,
    title,
    content,
    type: req.body?.type || "notice",
  });
  res.json({ message: "消息已发送" });
});

app.post("/api/student/messages/:id/read", auth, requireRole("student"), async (req, res) => {
  await markStudentMessageRead(req.user.id, Number(req.params.id));
  res.json({ message: "已标记已读" });
});

app.get("/api/llm/status", auth, async (req, res) => {
  const providerOrder = getProviderOrder();
  const [ollamaAvailable] = await Promise.all([checkOllamaAvailable()]);
  res.json({
    providerOrder,
    llmProvider: "ollama",
    ollamaModel: OLLAMA_MODEL,
    digitalHuman: {
      enabled: !!EXTERNAL_DIGITAL_HUMAN_API,
      mode: EXTERNAL_DIGITAL_HUMAN_API ? "external-service" : "unconfigured",
      endpoint: "/api/digital-human/chat",
      note: EXTERNAL_DIGITAL_HUMAN_API ? "数字人服务已配置外部接口。" : "数字人服务未配置。",
    },
    status: {
      ollama: {
        configured: true,
        available: ollamaAvailable,
      },
    },
  });
});

app.post("/api/llm/chat", auth, async (req, res) => {
  const message = String(req.body?.message || "").trim();
  if (!message) {
    return res.status(400).json({ message: "提问内容不能为空" });
  }
  const auditReq = { user: safeUser(req.user), message };
  try {
    const { answer, providerUsed } = await chatWithLLM(message);
    await auditAiCall("llm-chat", auditReq, true, null, { answer, provider: providerUsed });
    res.json({ answer, provider: providerUsed });
  } catch (e) {
    await auditAiCall("llm-chat", auditReq, false, e.message || "模型服务调用失败", null);
    res.status(500).json({ message: e.message || "模型服务调用失败" });
  }
});

// 数字人接口：转发到外部数字人服务。
app.post("/api/digital-human/chat", auth, async (req, res) => {
  const message = String(req.body?.message || "").trim();
  if (!message) {
    return res.status(400).json({ message: "提问内容不能为空" });
  }
  if (!requireExternalEndpoint(res, EXTERNAL_DIGITAL_HUMAN_API, "数字人服务")) return;
  const auditReq = {
    user: safeUser(req.user),
    message,
    sessionId: req.body?.sessionId || "",
    voiceId: req.body?.voiceId || "",
    avatarId: req.body?.avatarId || "",
  };
  try {
    const data = await callExternalJson(EXTERNAL_DIGITAL_HUMAN_API, {
      user: req.user,
      message,
      sessionId: req.body?.sessionId || "",
      voiceId: req.body?.voiceId || "",
      avatarId: req.body?.avatarId || "",
    });
    await auditAiCall("digital-human", auditReq, true, data?.message ?? null, data);
    res.json(data);
  } catch (e) {
    await auditAiCall("digital-human", auditReq, false, e.message || "数字人服务调用失败", null);
    res.status(502).json({ message: e.message || "数字人服务调用失败" });
  }
});

bootstrapSeedData()
  .then(() => {
    app.listen(PORT, () => {
      console.log(`Backend running at http://localhost:${PORT}`);
    });
  })
  .catch((err) => {
    console.error("Failed to initialize server:", err.message || err);
    process.exit(1);
  });
