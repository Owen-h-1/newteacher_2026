import "dotenv/config";
import mysql from "mysql2/promise";
import crypto from "node:crypto";

const DB_HOST = process.env.DB_HOST || "127.0.0.1";
const DB_PORT = Number(process.env.DB_PORT || 3306);
const DB_USER = process.env.DB_USER || "root";
const DB_PASSWORD = process.env.DB_PASSWORD || "";
const DB_NAME = process.env.DB_NAME || "zhi_keyunshi";

const baseConfig = {
  host: DB_HOST,
  port: DB_PORT,
  user: DB_USER,
  password: DB_PASSWORD,
  waitForConnections: true,
  connectionLimit: 10,
  charset: "utf8mb4",
};

let pool = null;

function normalizeSubjectClass(code) {
  return code;
}

/** 当前日历日在 Asia/Shanghai（北京时间），与数据库服务器时区无关。 */
function beijingCalendarYmd() {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Shanghai" }).format(new Date());
}

/** 北京时间 YYYY-MM-DD HH:mm:ss；用于接口 JSON，避免 Date 被序列化成 ISO。 */
function formatBeijingYmdHms(value) {
  if (value == null || value === "") return null;
  const d = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(d.getTime())) return null;
  const s = new Intl.DateTimeFormat("sv-SE", {
    timeZone: "Asia/Shanghai",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
  }).format(d);
  return s.replace("T", " ");
}

/** mysql2 / 驱动可能返回不同大小写的列名。 */
function readSubmitTimeFromRow(r) {
  if (!r || typeof r !== "object") return undefined;
  return (
    r.submitTimeRaw ??
    r.submit_timeraw ??
    r.submitTime ??
    r.submit_time ??
    r.SubmitTimeRaw ??
    r.SubmitTime
  );
}

function readTeacherGradeFromRow(r) {
  if (!r || typeof r !== "object") return null;
  const g = r.teacherGrade ?? r.teacher_grade ?? r.TeacherGrade;
  if (g == null || g === "") return null;
  const u = String(g).trim().toUpperCase();
  return ["A", "B", "C", "D"].includes(u) ? u : null;
}

/** 花名册 account_username 与 users.username 比对用（忽略首尾空格与大小写）。 */
function accountLoginKey(username) {
  return String(username ?? "").trim().toLowerCase();
}

async function ensurePool() {
  if (pool) return pool;
  const conn = await mysql.createConnection(baseConfig);
  await conn.query(
    `CREATE DATABASE IF NOT EXISTS \`${DB_NAME.replace(/`/g, "")}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci`
  );
  await conn.end();
  pool = mysql.createPool({ ...baseConfig, database: DB_NAME });
  return pool;
}

export async function initDatabase() {
  const p = await ensurePool();
  await p.query(`
    CREATE TABLE IF NOT EXISTS users (
      id VARCHAR(64) PRIMARY KEY,
      username VARCHAR(50) UNIQUE NOT NULL,
      email VARCHAR(120) UNIQUE NOT NULL,
      role VARCHAR(20) NOT NULL,
      name VARCHAR(80) NOT NULL,
      password_hash VARCHAR(255) NOT NULL
    )
  `);

  await p.query(`
    CREATE TABLE IF NOT EXISTS homework (
      id VARCHAR(64) PRIMARY KEY,
      subject VARCHAR(30) NOT NULL,
      subject_class VARCHAR(30) NOT NULL,
      subject_icon VARCHAR(80) NOT NULL,
      title VARCHAR(255) NOT NULL,
      teacher_id VARCHAR(64) NOT NULL,
      teacher_name VARCHAR(80) NOT NULL,
      class_name VARCHAR(50) NOT NULL,
      deadline VARCHAR(80),
      type VARCHAR(50),
      question_count INT DEFAULT 0,
      difficulty_text VARCHAR(20),
      description TEXT,
      created_at DATETIME NOT NULL
    )
  `);

  await p.query(`
    CREATE TABLE IF NOT EXISTS submissions (
      id VARCHAR(64) PRIMARY KEY,
      homework_id VARCHAR(64) NOT NULL,
      student_id VARCHAR(64) NOT NULL,
      submit_time DATETIME NOT NULL,
      teacher_grade CHAR(1) NULL,
      UNIQUE KEY uq_submission (homework_id, student_id)
    )
  `);

  await p.query(`
    CREATE TABLE IF NOT EXISTS classes (
      name VARCHAR(50) PRIMARY KEY,
      invite_code VARCHAR(20) UNIQUE
    )
  `);

  await p.query(`
    CREATE TABLE IF NOT EXISTS students (
      id VARCHAR(30) PRIMARY KEY,
      name VARCHAR(80) NOT NULL,
      class_name VARCHAR(50) NOT NULL,
      account_username VARCHAR(50) UNIQUE,
      joined_at DATETIME NULL
    )
  `);

  const ensureColumn = async (table, column, ddl) => {
    const [rows] = await p.query(
      `SELECT 1
       FROM information_schema.columns
       WHERE table_schema = ?
         AND table_name = ?
         AND column_name = ?
       LIMIT 1`,
      [DB_NAME, table, column]
    );
    if (!rows.length) {
      await p.query(`ALTER TABLE ${table} ADD COLUMN ${ddl}`);
    }
  };

  await ensureColumn("classes", "invite_code", "invite_code VARCHAR(20) UNIQUE");
  await ensureColumn("students", "account_username", "account_username VARCHAR(50) UNIQUE");
  await ensureColumn("students", "joined_at", "joined_at DATETIME NULL");
  await ensureColumn("homework", "questions_json", "questions_json LONGTEXT NULL");
  await ensureColumn("submissions", "answer_json", "answer_json LONGTEXT NULL");
  await ensureColumn("submissions", "teacher_grade", "teacher_grade CHAR(1) NULL");

  await p.query(`
    CREATE TABLE IF NOT EXISTS sign_sessions (
      class_name VARCHAR(50) PRIMARY KEY,
      sign_code VARCHAR(30) NOT NULL,
      valid_until DATETIME NOT NULL,
      last_sign_day DATE NULL
    )
  `);

  await p.query(`
    CREATE TABLE IF NOT EXISTS sign_records (
      class_name VARCHAR(50) NOT NULL,
      student_id VARCHAR(30) NOT NULL,
      time VARCHAR(20),
      method VARCHAR(30),
      PRIMARY KEY (class_name, student_id)
    )
  `);

  await ensureColumn("sign_sessions", "last_sign_day", "last_sign_day DATE NULL");
  await p.query("UPDATE sign_sessions SET last_sign_day = ? WHERE last_sign_day IS NULL", [beijingCalendarYmd()]);

  await p.query(`
    CREATE TABLE IF NOT EXISTS exercises (
      id INT PRIMARY KEY,
      subject VARCHAR(30) NOT NULL,
      subject_class VARCHAR(30) NOT NULL,
      subject_icon VARCHAR(80) NOT NULL,
      title VARCHAR(255) NOT NULL,
      type VARCHAR(20) NOT NULL,
      type_class VARCHAR(20) NOT NULL,
      knowledge VARCHAR(80) NOT NULL,
      difficulty VARCHAR(20) NOT NULL,
      difficulty_text VARCHAR(20) NOT NULL,
      difficulty_class VARCHAR(20) NOT NULL,
      accuracy INT NOT NULL,
      duration INT NOT NULL
    )
  `);

  await p.query(`
    CREATE TABLE IF NOT EXISTS exercise_collections (
      student_id VARCHAR(64) NOT NULL,
      exercise_id INT NOT NULL,
      PRIMARY KEY (student_id, exercise_id)
    )
  `);

  await p.query(`
    CREATE TABLE IF NOT EXISTS class_schedules (
      id INT AUTO_INCREMENT PRIMARY KEY,
      weekday TINYINT NOT NULL,
      start_time VARCHAR(20) NOT NULL,
      end_time VARCHAR(20) NOT NULL,
      lesson_name VARCHAR(80) NOT NULL,
      class_name VARCHAR(50) NOT NULL,
      UNIQUE KEY uq_schedule (weekday, start_time, class_name, lesson_name)
    )
  `);

  await p.query(`
    CREATE TABLE IF NOT EXISTS teacher_todos (
      id INT AUTO_INCREMENT PRIMARY KEY,
      teacher_id VARCHAR(64) NOT NULL,
      title VARCHAR(255) NOT NULL,
      meta VARCHAR(255) DEFAULT '',
      urgent TINYINT(1) DEFAULT 0,
      is_done TINYINT(1) DEFAULT 0,
      created_at DATETIME NOT NULL
    )
  `);

  await p.query(`
    CREATE TABLE IF NOT EXISTS teacher_messages (
      id INT AUTO_INCREMENT PRIMARY KEY,
      teacher_id VARCHAR(64) NOT NULL,
      teacher_name VARCHAR(80) NOT NULL,
      class_name VARCHAR(50) NOT NULL,
      title VARCHAR(255) NOT NULL,
      content TEXT,
      msg_type VARCHAR(20) DEFAULT 'notice',
      created_at DATETIME NOT NULL
    )
  `);

  await p.query(`
    CREATE TABLE IF NOT EXISTS student_message_reads (
      student_id VARCHAR(64) NOT NULL,
      message_id INT NOT NULL,
      read_at DATETIME NOT NULL,
      PRIMARY KEY (student_id, message_id)
    )
  `);

  /** 智能体调用审计（与 smart_education.ai_generation_record 对齐；success 用 TINYINT 便于 Node 读写） */
  await p.query(`
    CREATE TABLE IF NOT EXISTS ai_generation_record (
      id BIGINT NOT NULL AUTO_INCREMENT,
      created_at DATETIME(6) NOT NULL,
      feature_type VARCHAR(64) NOT NULL,
      message VARCHAR(500) DEFAULT NULL,
      request_payload LONGTEXT,
      response_payload LONGTEXT,
      success TINYINT(1) NOT NULL,
      PRIMARY KEY (id),
      KEY idx_ai_gen_created (created_at),
      KEY idx_ai_gen_feature (feature_type)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  `);

  await ensureColumn("classes", "id", "`id` VARCHAR(36) NULL");
  await ensureColumn("classes", "owner_teacher_id", "`owner_teacher_id` VARCHAR(64) NULL");
  await ensureColumn("students", "class_id", "`class_id` VARCHAR(36) NULL");
  await ensureColumn("homework", "class_id", "`class_id` VARCHAR(36) NULL");
  await ensureColumn("sign_sessions", "class_id", "`class_id` VARCHAR(36) NULL");
  await ensureColumn("sign_records", "class_id", "`class_id` VARCHAR(36) NULL");
  await ensureColumn("teacher_messages", "class_id", "`class_id` VARCHAR(36) NULL");

  await migrateTeacherScopedClassesIfNeeded(p);
}

/** 将班级从「全局同名唯一」迁移为「每教师独立班级实体」（classes.id + owner_teacher_id，业务表 class_id）。可重复执行。 */
async function migrateTeacherScopedClassesIfNeeded(p) {
  const [[clsPk]] = await p.query(
    `SELECT COLUMN_NAME AS col
     FROM information_schema.KEY_COLUMN_USAGE
     WHERE table_schema = ? AND table_name = 'classes' AND constraint_name = 'PRIMARY'
     ORDER BY ORDINAL_POSITION
     LIMIT 1`,
    [DB_NAME]
  );
  const [[{ classCnt }]] = await p.query("SELECT COUNT(1) AS classCnt FROM classes");
  if (clsPk?.col === "name") {
    if (Number(classCnt) === 0) {
      await p.query("DELETE FROM sign_records");
      await p.query("DELETE FROM sign_sessions");
      await p.query("DROP TABLE classes");
      await p.query(`
        CREATE TABLE classes (
          id VARCHAR(36) PRIMARY KEY,
          owner_teacher_id VARCHAR(64) NOT NULL,
          name VARCHAR(50) NOT NULL,
          invite_code VARCHAR(20) UNIQUE,
          UNIQUE KEY uq_classes_owner_name (owner_teacher_id, name)
        )
      `);
    } else {
      const [tList] = await p.query("SELECT id FROM users WHERE role = 'teacher' ORDER BY id ASC LIMIT 1");
      const defaultOwner = tList[0]?.id || "system";
      const [crows] = await p.query("SELECT name FROM classes");
      for (const c of crows) {
        const [exist] = await p.query(
          "SELECT id FROM classes WHERE name = ? AND id IS NOT NULL AND TRIM(id) <> '' LIMIT 1",
          [c.name]
        );
        if (!exist[0]) {
          const nid = crypto.randomUUID();
          await p.query("UPDATE classes SET id = ?, owner_teacher_id = COALESCE(owner_teacher_id, ?) WHERE name = ?", [
            nid,
            defaultOwner,
            c.name,
          ]);
        }
      }
      await p.query("ALTER TABLE classes DROP PRIMARY KEY");
      await p.query("ALTER TABLE classes MODIFY COLUMN id VARCHAR(36) NOT NULL");
      await p.query("ALTER TABLE classes ADD PRIMARY KEY (id)");
      try {
        await p.query("ALTER TABLE classes ADD UNIQUE KEY uq_classes_owner_name (owner_teacher_id, name)");
      } catch {
        /* 已存在 */
      }
    }
  }

  await p.query(
    `UPDATE students s
     INNER JOIN classes c ON c.name = s.class_name AND c.id IS NOT NULL
     SET s.class_id = c.id
     WHERE s.class_id IS NULL OR s.class_id = ''`
  );

  await p.query(
    `UPDATE homework h
     INNER JOIN classes c ON c.name = h.class_name AND c.id IS NOT NULL
     SET h.class_id = c.id
     WHERE h.class_id IS NULL OR h.class_id = ''`
  );

  await p.query(
    `UPDATE teacher_messages m
     INNER JOIN classes c ON c.name = m.class_name AND c.id IS NOT NULL
     SET m.class_id = c.id
     WHERE m.class_id IS NULL OR m.class_id = ''`
  );

  const [[ssPk]] = await p.query(
    `SELECT COLUMN_NAME AS col
     FROM information_schema.KEY_COLUMN_USAGE
     WHERE table_schema = ? AND table_name = 'sign_sessions' AND constraint_name = 'PRIMARY'
     LIMIT 1`,
    [DB_NAME]
  );
  if (ssPk?.col === "class_name") {
    await p.query(
      `UPDATE sign_sessions ss
       INNER JOIN classes c ON c.name = ss.class_name AND c.id IS NOT NULL
       SET ss.class_id = c.id
       WHERE ss.class_id IS NULL OR ss.class_id = ''`
    );
    await p.query("DELETE FROM sign_sessions WHERE class_id IS NULL OR class_id = ''");
    await p.query("ALTER TABLE sign_sessions DROP PRIMARY KEY");
    await p.query("ALTER TABLE sign_sessions MODIFY COLUMN class_id VARCHAR(36) NOT NULL");
    await p.query("ALTER TABLE sign_sessions ADD PRIMARY KEY (class_id)");
    try {
      await p.query("ALTER TABLE sign_sessions DROP COLUMN class_name");
    } catch {
      /* 已删 */
    }
  }

  const [srPkRows] = await p.query(
    `SELECT COLUMN_NAME AS col
     FROM information_schema.KEY_COLUMN_USAGE
     WHERE table_schema = ? AND table_name = 'sign_records' AND constraint_name = 'PRIMARY'
     ORDER BY ORDINAL_POSITION`,
    [DB_NAME]
  );
  const srPkCols = srPkRows.map((r) => r.col);
  if (srPkCols.includes("class_name") || (srPkCols.length === 2 && !srPkCols.includes("class_id"))) {
    await p.query(
      `UPDATE sign_records sr
       INNER JOIN classes c ON c.name = sr.class_name AND c.id IS NOT NULL
       SET sr.class_id = c.id
       WHERE sr.class_id IS NULL OR sr.class_id = ''`
    );
    await p.query("DELETE FROM sign_records WHERE class_id IS NULL OR class_id = ''");
    await p.query("ALTER TABLE sign_records DROP PRIMARY KEY");
    await p.query("ALTER TABLE sign_records MODIFY COLUMN class_id VARCHAR(36) NOT NULL");
    await p.query("ALTER TABLE sign_records ADD PRIMARY KEY (class_id, student_id)");
    try {
      await p.query("ALTER TABLE sign_records DROP COLUMN class_name");
    } catch {
      /* 已删 */
    }
  }

  try {
    await p.query("ALTER TABLE students MODIFY COLUMN class_id VARCHAR(36) NOT NULL");
  } catch {
    /* 仍有空值则跳过 */
  }
}

export async function seedIfEmpty(seed) {
  const [[uCount]] = await (await ensurePool()).query("SELECT COUNT(1) AS c FROM users");
  if (uCount.c === 0 && seed?.users?.length) {
    for (const u of seed.users) {
      await (await ensurePool()).query(
        "INSERT INTO users (id, username, email, role, name, password_hash) VALUES (?, ?, ?, ?, ?, ?)",
        [u.id, u.username, u.email, u.role, u.name || u.username, u.passwordHash]
      );
    }
  }

  const pool = await ensurePool();
  const classIdsByName = Object.create(null);
  const [[cCount]] = await pool.query("SELECT COUNT(1) AS c FROM classes");
  if (cCount.c === 0) {
    const [trows] = await pool.query("SELECT id FROM users WHERE role = 'teacher' ORDER BY id ASC LIMIT 1");
    const ownerId = trows[0]?.id;
    if (ownerId) {
      const classList = ["三年级(1)班", "三年级(2)班", "三年级(3)班"];
      for (const cls of classList) {
        const cid = crypto.randomUUID();
        classIdsByName[cls] = cid;
        const inviteCode = `ZKYS-${Math.floor(100000 + Math.random() * 900000)}`;
        await pool.query("INSERT INTO classes (id, owner_teacher_id, name, invite_code) VALUES (?, ?, ?, ?)", [
          cid,
          ownerId,
          cls,
          inviteCode,
        ]);
        await pool.query(
          "INSERT INTO sign_sessions (class_id, sign_code, valid_until, last_sign_day) VALUES (?, ?, ?, ?)",
          [cid, `ZKYS-${Math.floor(1000 + Math.random() * 9000)}`, new Date(Date.now() + 15 * 60000), beijingCalendarYmd()]
        );
      }
    }
  } else {
    const [existing] = await pool.query("SELECT id, name FROM classes");
    for (const r of existing) {
      if (r.name) classIdsByName[String(r.name)] = r.id;
    }
  }

  const [[hCount]] = await pool.query("SELECT COUNT(1) AS c FROM homework");
  if (hCount.c === 0 && seed?.homework?.length) {
    for (const h of seed.homework) {
      const cn = h.className || "三年级(1)班";
      let hwClassId = classIdsByName[cn];
      if (!hwClassId) {
        const [cr] = await pool.query("SELECT id FROM classes WHERE name = ? LIMIT 1", [cn]);
        hwClassId = cr[0]?.id;
      }
      if (!hwClassId) continue;
      await pool.query(
        `INSERT INTO homework
         (id, subject, subject_class, subject_icon, title, teacher_id, teacher_name,
          class_name, class_id, deadline, type, question_count, difficulty_text, description, questions_json, created_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
        [
          h.id,
          h.subject,
          h.subjectClass,
          h.subjectIcon,
          h.title,
          h.teacherId,
          h.teacher,
          cn,
          hwClassId,
          h.deadline,
          h.type,
          h.questionCount || 0,
          h.difficultyText || "进阶",
          h.description || "",
          JSON.stringify(Array.isArray(h.questions) ? h.questions : []),
          new Date(h.createdAt || Date.now()),
        ]
      );
    }
  }

  const [[sCount]] = await pool.query("SELECT COUNT(1) AS c FROM students");
  if (sCount.c === 0) {
    const id1 = classIdsByName["三年级(1)班"];
    const id2 = classIdsByName["三年级(2)班"];
    const id3 = classIdsByName["三年级(3)班"];
    if (id1 && id2 && id3) {
      for (let i = 1; i <= 42; i++) {
        const id = `202401${String(i).padStart(2, "0")}`;
        await pool.query(
          "INSERT INTO students (id, name, class_name, class_id) VALUES (?, ?, ?, ?)",
          [id, `学生${i}`, "三年级(1)班", id1]
        );
        await pool.query("INSERT INTO sign_records (class_id, student_id, time, method) VALUES (?, ?, ?, ?)", [
          id1,
          id,
          i <= 35 ? "08:05:00" : null,
          i <= 35 ? "扫码" : null,
        ]);
      }
      for (let i = 1; i <= 40; i++) {
        const id = `202402${String(i).padStart(2, "0")}`;
        await pool.query(
          "INSERT INTO students (id, name, class_name, class_id) VALUES (?, ?, ?, ?)",
          [id, `学生${i}`, "三年级(2)班", id2]
        );
        await pool.query("INSERT INTO sign_records (class_id, student_id, time, method) VALUES (?, ?, ?, ?)", [
          id2,
          id,
          i <= 30 ? "08:06:00" : null,
          i <= 30 ? "扫码" : null,
        ]);
      }
      for (let i = 1; i <= 36; i++) {
        const id = `202403${String(i).padStart(2, "0")}`;
        await pool.query(
          "INSERT INTO students (id, name, class_name, class_id) VALUES (?, ?, ?, ?)",
          [id, `学生${i}`, "三年级(3)班", id3]
        );
        await pool.query("INSERT INTO sign_records (class_id, student_id, time, method) VALUES (?, ?, ?, ?)", [
          id3,
          id,
          i <= 22 ? "08:07:00" : null,
          i <= 22 ? "扫码" : null,
        ]);
      }
    }
  }

  const [[eCount]] = await (await ensurePool()).query("SELECT COUNT(1) AS c FROM exercises");
  if (eCount.c === 0) {
    const rows = [
      [1, "数学", "math", "fas fa-calculator", "两位数乘法练习", "选择题", "choice", "两位数乘法", "medium", "进阶", "medium", 68, 5],
      [2, "语文", "chinese", "fas fa-book-open", "阅读理解训练", "填空题", "fill", "阅读理解", "medium", "进阶", "medium", 62, 4],
      [3, "英语", "english", "fas fa-language", "日常对话专项训练", "选择题", "choice", "日常对话", "medium", "进阶", "medium", 57, 4],
      [4, "科学", "science", "fas fa-seedling", "植物观察记录", "解答题", "essay", "科学观察", "hard", "挑战", "hard", 45, 8],
      [5, "美术", "art", "fas fa-palette", "色彩搭配练习", "填空题", "fill", "色彩认知", "hard", "挑战", "hard", 36, 6],
    ];
    for (const r of rows) {
      await (await ensurePool()).query(
        `INSERT INTO exercises
         (id, subject, subject_class, subject_icon, title, type, type_class, knowledge, difficulty, difficulty_text, difficulty_class, accuracy, duration)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
        r
      );
    }
  }

  const [[scCount]] = await (await ensurePool()).query("SELECT COUNT(1) AS c FROM class_schedules");
  if (scCount.c === 0) {
    const rows = [
      [1, "08:00", "08:45", "乘法与除法", "三年级(1)班"],
      [1, "09:00", "09:45", "阅读理解", "三年级(2)班"],
      [1, "10:30", "11:15", "英语对话", "三年级(3)班"],
      [1, "14:00", "14:45", "科学观察", "三年级(1)班"],
      [2, "08:00", "08:45", "口算训练", "三年级(2)班"],
      [2, "09:00", "09:45", "课文朗读", "三年级(3)班"],
      [2, "10:30", "11:15", "单词拼写", "三年级(1)班"],
      [2, "14:00", "14:45", "手工美术", "三年级(2)班"],
      [3, "08:00", "08:45", "应用题训练", "三年级(3)班"],
      [3, "09:00", "09:45", "写话练习", "三年级(1)班"],
      [3, "10:30", "11:15", "英语听说", "三年级(2)班"],
      [3, "14:00", "14:45", "科学实验", "三年级(3)班"],
      [4, "08:00", "08:45", "几何图形", "三年级(1)班"],
      [4, "09:00", "09:45", "阅读赏析", "三年级(2)班"],
      [4, "10:30", "11:15", "情景对话", "三年级(3)班"],
      [4, "14:00", "14:45", "自然观察", "三年级(1)班"],
      [5, "08:00", "08:45", "周测讲评", "三年级(2)班"],
      [5, "09:00", "09:45", "错题整理", "三年级(3)班"],
      [5, "10:30", "11:15", "综合练习", "三年级(1)班"],
      [5, "14:00", "14:45", "班会活动", "三年级(2)班"],
    ];
    for (const r of rows) {
      await (await ensurePool()).query(
        `INSERT INTO class_schedules (weekday, start_time, end_time, lesson_name, class_name)
         VALUES (?, ?, ?, ?, ?)`,
        r
      );
    }
  }
}

export async function findUserByUsername(username) {
  const [rows] = await (await ensurePool()).query("SELECT * FROM users WHERE username = ?", [username]);
  return rows[0] || null;
}

export async function findUserByEmail(email) {
  const [rows] = await (await ensurePool()).query("SELECT * FROM users WHERE email = ?", [email]);
  return rows[0] || null;
}

export async function findUserById(id) {
  const [rows] = await (await ensurePool()).query("SELECT * FROM users WHERE id = ?", [id]);
  return rows[0] || null;
}

export async function createUser({ username, email, role, name, passwordHash }) {
  const id = crypto.randomUUID();
  await (await ensurePool()).query(
    "INSERT INTO users (id, username, email, role, name, password_hash) VALUES (?, ?, ?, ?, ?, ?)",
    [id, username, email, role, name, passwordHash]
  );
  return findUserById(id);
}

export async function deleteUserById(id) {
  if (!id) return;
  await (await ensurePool()).query("DELETE FROM users WHERE id = ?", [id]);
}

/** 学生注册成功后：写入/更新花名册并标记已入班（与邀请码对应班级一致）。 */
export async function linkStudentAccountToClassFromRegistration(username, displayName, classId) {
  const p = await ensurePool();
  const cid = String(classId || "").trim();
  if (!cid) return { ok: false, message: "班级信息无效" };
  const [rows] = await p.query(
    "SELECT id, class_id AS classId FROM students WHERE LOWER(TRIM(account_username)) = LOWER(TRIM(?)) LIMIT 1",
    [username]
  );
  const existing = rows[0];
  if (existing) {
    if (String(existing.classId) !== cid) {
      return { ok: false, message: "该账号已在其他班级登记，请联系老师后再试" };
    }
    await markStudentJoinedByAccount(username, cid);
    return { ok: true };
  }
  const uid = crypto.randomUUID().replace(/-/g, "");
  const rosterId = `u${uid.slice(0, 29)}`;
  await addStudent({
    id: rosterId,
    name: displayName || username,
    classId: cid,
    accountUsername: username,
  });
  await markStudentJoinedByAccount(username, cid);
  return { ok: true };
}

/**
 * 学生注册：用邀请码确定班级后，将登录账号绑定到老师预先录入的花名册（学号+姓名+班级一致）。
 */
export async function bindRegisteredStudentToRoster(username, studentName, studentNo, classId) {
  const nameTrim = String(studentName || "").trim();
  const noTrim = String(studentNo || "").trim();
  const cid = String(classId || "").trim();
  if (!nameTrim || !noTrim || !cid) {
    return { ok: false, message: "学号、姓名、班级信息不完整" };
  }
  const p = await ensurePool();
  const [rows] = await p.query(
    "SELECT id, name, class_name AS className, account_username AS accountUsername FROM students WHERE class_id = ? AND id = ? LIMIT 1",
    [cid, noTrim]
  );
  const row = rows[0];
  if (!row) {
    return {
      ok: false,
      message: "本班花名册中无该学号，请确认学号正确或请老师先在「学生管理」中录入",
    };
  }
  if (String(row.name || "").trim() !== nameTrim) {
    return { ok: false, message: "姓名与老师登记的不一致，请与老师系统中登记的姓名完全一致" };
  }
  const existingAcc = row.accountUsername != null ? String(row.accountUsername).trim() : "";
  if (existingAcc && accountLoginKey(existingAcc) !== accountLoginKey(username)) {
    return { ok: false, message: "该学号已绑定其他登录账号" };
  }
  await p.query(
    "UPDATE students SET account_username = ?, joined_at = COALESCE(joined_at, ?) WHERE id = ? AND class_id = ?",
    [username, new Date(), noTrim, cid]
  );
  return { ok: true };
}

/** opts.onlyForClass：有则只返回该班级的作业；传空字符串表示无班级（结果为空）。不传 opts 则不过滤。 */
export async function listHomeworkWithStatus(userId, opts = {}) {
  const onlyForClass = opts.onlyForClass;
  const p = await ensurePool();
  let where = "";
  const params = [userId, userId, userId];
  if (onlyForClass !== undefined) {
    const cn = String(onlyForClass || "").trim();
    if (!cn) {
      where = " WHERE 1=0 ";
    } else {
      where = " WHERE h.class_id = ? ";
      params.push(cn);
    }
  }
  const [rows] = await p.query(
    `SELECT h.*,
      EXISTS(SELECT 1 FROM submissions s WHERE s.homework_id = h.id AND s.student_id = ?) AS done,
      (SELECT s.submit_time FROM submissions s WHERE s.homework_id = h.id AND s.student_id = ? LIMIT 1) AS submit_time,
      (SELECT s.teacher_grade FROM submissions s WHERE s.homework_id = h.id AND s.student_id = ? LIMIT 1) AS teacher_grade
     FROM homework h
     ${where}
     ORDER BY h.created_at DESC`,
    params
  );
  return rows.map((r) => ({
    id: r.id,
    subject: r.subject,
    subjectClass: normalizeSubjectClass(r.subject_class),
    subjectIcon: r.subject_icon,
    title: r.title,
    teacher: r.teacher_name,
    deadline: r.deadline,
    type: r.type,
    questionCount: r.question_count,
    difficultyText: r.difficulty_text,
    description: r.description,
    className: r.class_name,
    status: r.done ? "completed" : "pending",
    isUrgent: !r.done && String(r.deadline || "").includes("今日"),
    score: r.done ? 90 : undefined,
    submitTime: formatBeijingYmdHms(readSubmitTimeFromRow(r) ?? r.submit_time) || null,
    teacherGrade: readTeacherGradeFromRow({ teacher_grade: r.teacher_grade }),
  }));
}

export async function createHomework(payload) {
  const id = crypto.randomUUID();
  const cid = String(payload.classId || "").trim();
  if (!cid) throw new Error("classId required");
  const p = await ensurePool();
  const [[crow]] = await p.query("SELECT name FROM classes WHERE id = ? LIMIT 1", [cid]);
  const displayName = String(crow?.name || "").trim();
  if (!displayName) throw new Error("class not found");
  await p.query(
    `INSERT INTO homework
     (id, subject, subject_class, subject_icon, title, teacher_id, teacher_name, class_name, class_id, deadline, type, question_count, difficulty_text, description, questions_json, created_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    [
      id,
      payload.subject,
      normalizeSubjectClass(payload.subjectClass),
      payload.subjectIcon,
      payload.title,
      payload.teacherId,
      payload.teacherName,
      displayName,
      cid,
      payload.deadline,
      payload.type,
      payload.questionCount,
      payload.difficultyText,
      payload.description || "",
      JSON.stringify(Array.isArray(payload.questions) ? payload.questions : []),
      new Date(),
    ]
  );
  const [rows] = await p.query("SELECT * FROM homework WHERE id = ?", [id]);
  return rows[0];
}

export async function homeworkExists(id) {
  const [rows] = await (await ensurePool()).query("SELECT 1 AS c FROM homework WHERE id = ?", [id]);
  return !!rows[0];
}

export async function hasSubmitted(homeworkId, studentId) {
  const [rows] = await (await ensurePool()).query(
    "SELECT 1 AS c FROM submissions WHERE homework_id = ? AND student_id = ?",
    [homeworkId, studentId]
  );
  return !!rows[0];
}

export async function createSubmission(homeworkId, studentId) {
  const id = crypto.randomUUID();
  await (await ensurePool()).query(
    `INSERT INTO submissions (id, homework_id, student_id, submit_time, answer_json)
     VALUES (?, ?, ?, ?, NULL)
     ON DUPLICATE KEY UPDATE submit_time = VALUES(submit_time)`,
    [id, homeworkId, studentId, new Date()]
  );
}

export async function createSubmissionWithAnswers(homeworkId, studentId, answers) {
  const id = crypto.randomUUID();
  const answerJson = JSON.stringify(Array.isArray(answers) ? answers : []);
  await (await ensurePool()).query(
    `INSERT INTO submissions (id, homework_id, student_id, submit_time, answer_json)
     VALUES (?, ?, ?, ?, ?)
     ON DUPLICATE KEY UPDATE submit_time = VALUES(submit_time), answer_json = VALUES(answer_json)`,
    [id, homeworkId, studentId, new Date(), answerJson]
  );
}

/** studentClass 传入时须与作业 class_id 一致才返回详情（学生端防跨班）。 */
export async function getHomeworkDetailForStudent(homeworkId, studentId, studentClass) {
  const [rows] = await (await ensurePool()).query(
    `SELECT h.*,
      s.submit_time AS submitTime,
      s.teacher_grade AS teacherGrade,
      s.answer_json AS answerJson
     FROM homework h
     LEFT JOIN submissions s ON s.homework_id = h.id AND s.student_id = ?
     WHERE h.id = ?
     LIMIT 1`,
    [studentId, homeworkId]
  );
  const row = rows[0];
  if (!row) return null;
  if (studentClass !== undefined) {
    const sc = String(studentClass || "").trim();
    const hid = String(row.class_id ?? row.classId ?? "").trim();
    if (!sc || !hid || hid !== sc) return null;
  }
  let questions = [];
  let answers = [];
  try {
    questions = JSON.parse(row.questions_json || "[]");
  } catch {
    questions = [];
  }
  try {
    answers = JSON.parse(row.answerJson || "[]");
  } catch {
    answers = [];
  }
  const rawSubmit = readSubmitTimeFromRow(row) ?? row.submitTime ?? row.submit_time;
  return {
    id: row.id,
    title: row.title,
    subject: row.subject,
    subjectClass: normalizeSubjectClass(row.subject_class),
    subjectIcon: row.subject_icon,
    teacher: row.teacher_name,
    className: row.class_name,
    deadline: row.deadline,
    description: row.description || "",
    status: rawSubmit ? "completed" : "pending",
    submitTime: formatBeijingYmdHms(rawSubmit) || null,
    teacherGrade: readTeacherGradeFromRow(row),
    questions: Array.isArray(questions) ? questions : [],
    answers: Array.isArray(answers) ? answers : [],
  };
}

export async function listHomeworkSubmissionsForTeacher(homeworkId, teacherId) {
  const [hRows] = await (await ensurePool()).query(
    "SELECT id, title, class_name AS className, class_id AS classId, teacher_id AS teacherId, questions_json AS questionsJson FROM homework WHERE id = ? LIMIT 1",
    [homeworkId]
  );
  const hw = hRows[0];
  if (!hw) return null;
  if (String(hw.teacherId) !== String(teacherId)) return null;
  let homeworkQuestions = [];
  try {
    homeworkQuestions = JSON.parse(hw.questionsJson || "[]");
  } catch {
    homeworkQuestions = [];
  }
  if (!Array.isArray(homeworkQuestions)) homeworkQuestions = [];

  const [rosterRows] = await (await ensurePool()).query(
    `SELECT st.id AS studentNo, st.name AS studentName, st.account_username AS accountUsername
     FROM students st
     WHERE st.class_id = ?
     ORDER BY st.id`,
    [hw.classId]
  );
  const [submittedRows] = await (await ensurePool()).query(
    `SELECT
      COALESCE(st.id, u.username) AS joinStudentNo,
      COALESCE(st.name, u.name, u.username) AS joinStudentName,
      u.username AS submitAccount,
      st.class_id AS studentClassId,
      s.submit_time AS submitTimeRaw,
      s.teacher_grade AS teacherGrade,
      s.answer_json AS answerJson
     FROM submissions s
     INNER JOIN users u ON u.id = s.student_id
     LEFT JOIN students st
       ON st.account_username IS NOT NULL
      AND TRIM(st.account_username) <> ''
      AND LOWER(TRIM(st.account_username)) = LOWER(TRIM(u.username))
     WHERE s.homework_id = ?
     ORDER BY s.submit_time DESC`,
    [homeworkId]
  );

  /** 每个提交账号一条：用该生自己的 answer_json 算个人客观题正确率。 */
  const submissionByUsername = new Map();
  for (const r of submittedRows) {
    const un = String(r.submitAccount || "").trim();
    const uk = accountLoginKey(un);
    if (!un || submissionByUsername.has(uk)) continue;
    let answers = [];
    try {
      answers = JSON.parse(r.answerJson || "[]");
    } catch {
      answers = [];
    }
    submissionByUsername.set(uk, {
      joinStudentNo: r.joinStudentNo,
      joinStudentName: r.joinStudentName,
      submitAccount: r.submitAccount,
      studentClassId: r.studentClassId ?? r.student_class_id,
      submitTimeRaw: readSubmitTimeFromRow(r),
      teacherGrade: readTeacherGradeFromRow(r),
      accuracyPercent: computeObjectiveAccuracyPercent(homeworkQuestions, answers),
    });
  }

  const matchedToRoster = new Set();
  const list = rosterRows.map((st) => {
    const accountRaw = st.accountUsername ?? st.account_username;
    const account = accountRaw ? String(accountRaw).trim() : "";
    const sub = account ? submissionByUsername.get(accountLoginKey(account)) : undefined;
    if (sub) matchedToRoster.add(accountLoginKey(account));
    if (sub) {
      return {
        studentNo: st.studentNo,
        studentName: st.studentName,
        submitAccount: sub.submitAccount,
        submitted: true,
        submitTime: formatBeijingYmdHms(sub.submitTimeRaw) || null,
        accuracyPercent: sub.accuracyPercent,
        teacherGrade: sub.teacherGrade ?? null,
      };
    }
    return {
      studentNo: st.studentNo,
      studentName: st.studentName,
      submitAccount: account || null,
      submitted: false,
      submitTime: null,
      accuracyPercent: null,
      teacherGrade: null,
    };
  });

  for (const [, sub] of submissionByUsername) {
    if (matchedToRoster.has(accountLoginKey(sub.submitAccount))) continue;
    const hwCid = String(hw.classId ?? "").trim();
    const subCid = String(sub.studentClassId ?? "").trim();
    const outOfClass = subCid && hwCid && subCid !== hwCid;
    const unbound = !subCid;
    const suffix = unbound ? "（花名册未绑定该登录账号）" : outOfClass ? "（跨班提交）" : "";
    list.push({
      studentNo: sub.joinStudentNo,
      studentName: `${sub.joinStudentName}${suffix}`,
      submitAccount: sub.submitAccount,
      submitted: true,
      submitTime: formatBeijingYmdHms(sub.submitTimeRaw) || null,
      accuracyPercent: sub.accuracyPercent,
      teacherGrade: sub.teacherGrade ?? null,
    });
  }

  return {
    homework: { id: hw.id, title: hw.title, className: hw.className },
    list,
  };
}

async function resolveStudentIdentityForHomeworkClass(classId, studentNoParam) {
  const studentNo = String(studentNoParam || "").trim();
  if (!studentNo) return null;
  const cid = String(classId || "").trim();
  if (!cid) return null;
  const [identityRows] = await (await ensurePool()).query(
    `SELECT
      st.id AS studentNo,
      st.name AS studentName,
      st.account_username AS accountUsername
     FROM students st
     WHERE st.class_id = ?
       AND (st.id = ? OR LOWER(TRIM(st.account_username)) = LOWER(TRIM(?)))
     LIMIT 1`,
    [cid, studentNo, studentNo]
  );
  let studentIdentity = identityRows[0] || null;
  if (!studentIdentity) {
    const [userRows] = await (await ensurePool()).query(
      `SELECT username AS studentNo, COALESCE(name, username) AS studentName, username AS accountUsername
       FROM users
       WHERE username = ?
       LIMIT 1`,
      [studentNo]
    );
    studentIdentity = userRows[0] || null;
  }
  return studentIdentity;
}

export async function getHomeworkSubmissionDetailForTeacher(homeworkId, teacherId, studentNo) {
  const [hRows] = await (await ensurePool()).query(
    "SELECT id, title, class_name AS className, class_id AS classId, teacher_id AS teacherId, questions_json AS questionsJson FROM homework WHERE id = ? LIMIT 1",
    [homeworkId]
  );
  const hw = hRows[0];
  if (!hw) return null;
  if (String(hw.teacherId) !== String(teacherId)) return null;
  const studentIdentity = await resolveStudentIdentityForHomeworkClass(hw.classId, studentNo);
  if (!studentIdentity) return null;

  const [rows] = await (await ensurePool()).query(
    `SELECT
      s.submit_time AS submitTime,
      s.teacher_grade AS teacherGrade,
      s.answer_json AS answerJson
     FROM submissions s
     INNER JOIN users u ON u.id = s.student_id
     WHERE s.homework_id = ?
       AND u.username = ?
     LIMIT 1`,
    [homeworkId, studentIdentity.accountUsername]
  );
  const finalRow = rows[0] || null;
  let questions = [];
  let answers = [];
  try {
    questions = JSON.parse(hw.questionsJson || "[]");
  } catch {
    questions = [];
  }
  try {
    answers = JSON.parse(finalRow.answerJson || "[]");
  } catch {
    answers = [];
  }
  const accuracyPercent = computeObjectiveAccuracyPercent(questions, answers);
  const rawSubmit = readSubmitTimeFromRow(finalRow) ?? finalRow?.submitTime ?? finalRow?.submit_time;
  return {
    homework: { id: hw.id, title: hw.title, className: hw.className },
    student: {
      studentNo: studentIdentity.studentNo,
      studentName: studentIdentity.studentName,
      submitted: !!rawSubmit,
      submitTime: formatBeijingYmdHms(rawSubmit) || null,
      accuracyPercent,
      teacherGrade: readTeacherGradeFromRow(finalRow),
    },
    questions: Array.isArray(questions) ? questions : [],
    answers: Array.isArray(answers) ? answers : [],
  };
}

export async function setHomeworkSubmissionGrade(homeworkId, teacherId, studentNoParam, grade) {
  const raw = String(grade ?? "").trim().toUpperCase();
  const normalized = raw === "" || raw === "-" || raw === "CLEAR" ? null : raw;
  if (normalized != null && !["A", "B", "C", "D"].includes(normalized)) {
    return { ok: false, message: "评级须为 A、B、C、D，或传空清除" };
  }
  const [hRows] = await (await ensurePool()).query(
    "SELECT id, class_name AS className, class_id AS classId, teacher_id AS teacherId FROM homework WHERE id = ? LIMIT 1",
    [homeworkId]
  );
  const hw = hRows[0];
  if (!hw || String(hw.teacherId) !== String(teacherId)) {
    return { ok: false, message: "作业不存在或无权限" };
  }
  const studentIdentity = await resolveStudentIdentityForHomeworkClass(hw.classId, studentNoParam);
  if (!studentIdentity) {
    return { ok: false, message: "未找到该学生" };
  }
  const accountUsername = String(studentIdentity.accountUsername || "").trim();
  if (!accountUsername) {
    return { ok: false, message: "该学生未绑定登录账号，无法保存评级" };
  }
  const [uRows] = await (await ensurePool()).query("SELECT id FROM users WHERE username = ? LIMIT 1", [
    accountUsername,
  ]);
  const uid = uRows[0]?.id;
  if (!uid) {
    return { ok: false, message: "未找到学生用户" };
  }
  const [ret] = await (await ensurePool()).query(
    "UPDATE submissions SET teacher_grade = ? WHERE homework_id = ? AND student_id = ?",
    [normalized, homeworkId, uid]
  );
  if (!ret?.affectedRows) {
    return { ok: false, message: "该学生尚未提交作业，无法评级" };
  }
  return { ok: true, teacherGrade: normalized };
}

export async function listHomeworkManageForTeacher(teacherId) {
  const [rows] = await (await ensurePool()).query(
    `SELECT
      h.id,
      h.title,
      h.class_name AS className,
      h.deadline,
      h.created_at AS createdAt,
      (SELECT COUNT(1) FROM students st WHERE st.class_id = h.class_id) AS totalStudents,
      (SELECT COUNT(1)
       FROM submissions s
       WHERE s.homework_id = h.id) AS submittedStudents
     FROM homework h
     WHERE h.teacher_id = ?
     ORDER BY h.created_at DESC`,
    [teacherId]
  );
  return rows.map((r) => {
    const total = Number(r.totalStudents || 0);
    const submitted = Number(r.submittedStudents || 0);
    return {
      id: r.id,
      title: r.title,
      className: r.className,
      deadline: r.deadline,
      createdAt: r.createdAt,
      totalStudents: total,
      submittedStudents: submitted,
      pendingStudents: Math.max(total - submitted, 0),
      submitRate: total > 0 ? Math.round((submitted / total) * 100) : 0,
    };
  });
}

export async function deleteHomeworkForTeacher(homeworkId, teacherId) {
  const [rows] = await (await ensurePool()).query(
    "SELECT id FROM homework WHERE id = ? AND teacher_id = ? LIMIT 1",
    [homeworkId, teacherId]
  );
  if (!rows[0]) return false;
  await (await ensurePool()).query("DELETE FROM submissions WHERE homework_id = ?", [homeworkId]);
  const [ret] = await (await ensurePool()).query("DELETE FROM homework WHERE id = ? AND teacher_id = ?", [
    homeworkId,
    teacherId,
  ]);
  return !!ret?.affectedRows;
}

export async function getClassDisplayById(classId) {
  const cid = String(classId || "").trim();
  if (!cid) return null;
  const [rows] = await (await ensurePool()).query("SELECT name AS className FROM classes WHERE id = ? LIMIT 1", [
    cid,
  ]);
  return rows[0] || null;
}

export async function teacherOwnsClass(teacherId, classId) {
  if (!teacherId || !classId) return false;
  const [rows] = await (await ensurePool()).query(
    "SELECT 1 AS ok FROM classes WHERE id = ? AND owner_teacher_id = ? LIMIT 1",
    [String(classId).trim(), String(teacherId).trim()]
  );
  return !!rows[0]?.ok;
}

/** 当前教师名下的班级（不同教师可有同名班级，以 classId 区分）。 */
export async function listTeacherClasses(teacherId) {
  const [rows] = await (await ensurePool()).query(
    "SELECT id AS classId, name AS className FROM classes WHERE owner_teacher_id = ? ORDER BY name",
    [String(teacherId).trim()]
  );
  return rows.map((r) => ({ classId: r.classId, className: r.className }));
}

export async function getClassInviteCodesForTeacher(teacherId) {
  const [rows] = await (await ensurePool()).query(
    "SELECT id AS classId, name AS className, invite_code AS inviteCode FROM classes WHERE owner_teacher_id = ? ORDER BY name",
    [String(teacherId).trim()]
  );
  return rows;
}

/** 统一邀请码输入：去首尾空格、全角横线转半角、去掉中间空格，便于与库内比对。 */
export function normalizeInviteCodeInput(raw) {
  if (raw == null) return "";
  let s = String(raw).trim();
  s = s.replace(/[\u2013\u2014\uff0d]/g, "-");
  s = s.replace(/\s+/g, "");
  return s;
}

async function pickUnusedInviteCode(p, excludeClassId) {
  const ex = excludeClassId != null ? String(excludeClassId) : "";
  for (let i = 0; i < 60; i++) {
    const code = `ZKYS-${Math.floor(100000 + Math.random() * 900000)}`;
    const [[hit]] = await p.query(
      "SELECT 1 AS taken FROM classes WHERE invite_code = ? AND (? = '' OR id <> ?) LIMIT 1",
      [code, ex, ex]
    );
    if (!hit?.taken) return code;
  }
  return `ZKYS-${String(Date.now()).slice(-6)}`;
}

async function assignUniqueInviteForClass(p, classId) {
  const code = await pickUnusedInviteCode(p, classId);
  await p.query("UPDATE classes SET invite_code = ? WHERE id = ?", [code, classId]);
  return code;
}

/** 修复空邀请码、同一邀请码对应多个班级等异常，避免「填二班码却解析到一班」。 */
export async function reconcileClassInviteCodes() {
  const p = await ensurePool();
  const [all] = await p.query("SELECT id AS classId, invite_code AS inviteCode FROM classes");
  const normToOwners = new Map();
  for (const row of all) {
    const norm = normalizeInviteCodeInput(row.inviteCode ?? row.invite_code);
    if (!norm) continue;
    const cid = row.classId ?? row.class_id;
    if (!cid) continue;
    if (!normToOwners.has(norm)) normToOwners.set(norm, []);
    normToOwners.get(norm).push(cid);
  }
  for (const owners of normToOwners.values()) {
    if (owners.length <= 1) continue;
    const keep = [...owners].sort()[0];
    for (const classId of owners) {
      if (classId === keep) continue;
      await assignUniqueInviteForClass(p, classId);
    }
  }
  const [missing] = await p.query(
    "SELECT id AS classId FROM classes WHERE invite_code IS NULL OR TRIM(invite_code) = ''"
  );
  for (const row of missing) {
    await assignUniqueInviteForClass(p, row.classId);
  }
}

export async function resetClassInviteCode(teacherId, classId) {
  const p = await ensurePool();
  const cid = String(classId || "").trim();
  const tid = String(teacherId || "").trim();
  const [[own]] = await p.query(
    "SELECT name AS className FROM classes WHERE id = ? AND owner_teacher_id = ? LIMIT 1",
    [cid, tid]
  );
  if (!own) return null;
  const code = await pickUnusedInviteCode(p, cid);
  const [ret] = await p.query("UPDATE classes SET invite_code = ? WHERE id = ?", [code, cid]);
  if (!ret?.affectedRows) return null;
  return { classId: cid, className: own.className, inviteCode: code };
}

export async function findClassByInviteCode(inviteCode) {
  const norm = normalizeInviteCodeInput(inviteCode);
  if (!norm) return null;
  const p = await ensurePool();
  const [rows] = await p.query("SELECT id AS classId, name AS className, invite_code AS inviteCode FROM classes");
  const matches = rows.filter((r) => {
    const ic = r.inviteCode ?? r.invite_code ?? "";
    return normalizeInviteCodeInput(ic) === norm;
  });
  if (matches.length > 1) {
    console.error("[findClassByInviteCode] duplicate invite_code for normalized key:", norm, matches);
    return null;
  }
  const row = matches[0];
  if (!row) return null;
  return {
    classId: row.classId ?? row.class_id,
    className: row.className ?? row.class_name,
    inviteCode: row.inviteCode ?? row.invite_code,
  };
}

/** 教师新建班级：写入 classes 与签到会话，并生成不重复的班级邀请码（仅在本账号下校验重名）。 */
export async function createTeacherClass(teacherId, classNameRaw) {
  const tid = String(teacherId || "").trim();
  if (!tid) return { ok: false, message: "教师信息无效" };
  const name = String(classNameRaw || "").trim();
  if (!name) return { ok: false, message: "班级名称不能为空" };
  if (name.length > 50) return { ok: false, message: "班级名称不能超过50个字" };
  const p = await ensurePool();
  const [[exists]] = await p.query("SELECT 1 AS ok FROM classes WHERE owner_teacher_id = ? AND name = ? LIMIT 1", [
    tid,
    name,
  ]);
  if (exists?.ok) return { ok: false, message: "您已拥有同名班级，请换一个名称" };
  const id = crypto.randomUUID();
  const code = await pickUnusedInviteCode(p, id);
  await p.query("INSERT INTO classes (id, name, invite_code, owner_teacher_id) VALUES (?, ?, ?, ?)", [
    id,
    name,
    code,
    tid,
  ]);
  const sessionCode = `ZKYS-${Math.floor(1000 + Math.random() * 9000)}`;
  const validUntil = new Date(Date.now() + 15 * 60000);
  await p.query(
    `INSERT INTO sign_sessions (class_id, sign_code, valid_until, last_sign_day) VALUES (?, ?, ?, ?)`,
    [id, sessionCode, validUntil, beijingCalendarYmd()]
  );
  return { ok: true, classId: id, className: name, inviteCode: code };
}

export async function listStudents(classId) {
  const sql = classId
    ? "SELECT id, name, class_name AS className, class_id AS classId, account_username AS accountUsername, joined_at AS joinedAt FROM students WHERE class_id = ? ORDER BY id"
    : "SELECT id, name, class_name AS className, class_id AS classId, account_username AS accountUsername, joined_at AS joinedAt FROM students ORDER BY class_name, id";
  const params = classId ? [String(classId).trim()] : [];
  const [rows] = await (await ensurePool()).query(sql, params);
  return rows;
}

/** 写入本班花名册（students：学号 id + 姓名 name），并初始化该生当日签到占位（sign_records）。 */
export async function addStudent({ id, name, classId, accountUsername }) {
  const sid = String(id ?? "").trim();
  const sname = String(name ?? "").trim();
  const cid = String(classId ?? "").trim();
  if (!cid) throw new Error("classId required");
  const p = await ensurePool();
  const [[crow]] = await p.query("SELECT name FROM classes WHERE id = ? LIMIT 1", [cid]);
  const displayName = String(crow?.name || "").trim();
  if (!displayName) throw new Error("class not found");
  const acc = accountUsername != null && String(accountUsername).trim() !== "" ? String(accountUsername).trim() : null;
  await p.query("INSERT INTO students (id, name, class_name, class_id, account_username) VALUES (?, ?, ?, ?, ?)", [
    sid,
    sname,
    displayName,
    cid,
    acc,
  ]);
  await p.query(
    `INSERT INTO sign_records (class_id, student_id, time, method)
     VALUES (?, ?, NULL, NULL)
     ON DUPLICATE KEY UPDATE class_id = VALUES(class_id)`,
    [cid, sid]
  );
}

export async function removeStudent(studentId) {
  const [rows] = await (await ensurePool()).query("SELECT class_name AS className FROM students WHERE id = ?", [studentId]);
  if (!rows[0]) return false;
  await (await ensurePool()).query("DELETE FROM students WHERE id = ?", [studentId]);
  await (await ensurePool()).query("DELETE FROM sign_records WHERE student_id = ?", [studentId]);
  return true;
}

export async function updateStudent(studentId, { name, classId, accountUsername }) {
  const p = await ensurePool();
  const [rows] = await p.query(
    "SELECT class_id AS classId, class_name AS className FROM students WHERE id = ?",
    [studentId]
  );
  if (!rows[0]) return false;
  const oldClassId = String(rows[0].classId || "").trim();
  const nameT = String(name ?? "").trim();
  const cid = String(classId ?? "").trim();
  if (!cid) return false;
  const [[crow]] = await p.query("SELECT name FROM classes WHERE id = ? LIMIT 1", [cid]);
  const displayName = String(crow?.name || "").trim();
  if (!displayName) return false;
  const accT =
    accountUsername === undefined
      ? undefined
      : accountUsername != null && String(accountUsername).trim() !== ""
        ? String(accountUsername).trim()
        : null;
  if (accT === undefined) {
    await p.query("UPDATE students SET name = ?, class_name = ?, class_id = ? WHERE id = ?", [
      nameT,
      displayName,
      cid,
      studentId,
    ]);
  } else {
    await p.query("UPDATE students SET name = ?, class_name = ?, class_id = ?, account_username = ? WHERE id = ?", [
      nameT,
      displayName,
      cid,
      accT,
      studentId,
    ]);
  }
  if (oldClassId !== cid) {
    await p.query("DELETE FROM sign_records WHERE student_id = ?", [studentId]);
    await p.query("INSERT INTO sign_records (class_id, student_id, time, method) VALUES (?, ?, NULL, NULL)", [
      cid,
      studentId,
    ]);
  } else {
    await p.query("UPDATE sign_records SET class_id = ? WHERE student_id = ?", [cid, studentId]);
  }
  return true;
}

export async function markStudentJoinedByAccount(accountUsername, classId) {
  const cid = String(classId || "").trim();
  if (!cid) return false;
  const [ret] = await (await ensurePool()).query(
    "UPDATE students SET joined_at = ? WHERE LOWER(TRIM(account_username)) = LOWER(TRIM(?)) AND class_id = ?",
    [new Date(), accountUsername, cid]
  );
  return ret?.affectedRows > 0;
}

export async function batchUpsertStudents(items, teacherId) {
  const tid = String(teacherId || "").trim();
  let created = 0;
  let updated = 0;
  let skipped = 0;
  const errors = [];
  const p = await ensurePool();
  for (const item of items) {
    const id = String(item.id || "").trim();
    const name = String(item.name || "").trim();
    const className = String(item.className || "").trim();
    const accountUsername = String(item.accountUsername || "").trim();
    if (!id || !name || !className) {
      skipped += 1;
      errors.push({ id, reason: "缺少学号、姓名或班级" });
      continue;
    }
    if (!/^\d{6,20}$/.test(id)) {
      skipped += 1;
      errors.push({ id, reason: "学号格式错误" });
      continue;
    }
    const [crows] = await p.query(
      "SELECT id FROM classes WHERE owner_teacher_id = ? AND name = ? LIMIT 1",
      [tid, className]
    );
    const classId = crows[0]?.id;
    if (!classId) {
      skipped += 1;
      errors.push({ id, reason: `班级「${className}」不存在或不属于当前教师` });
      continue;
    }
    const [existRows] = await p.query("SELECT id FROM students WHERE id = ?", [id]);
    if (existRows[0]) {
      await updateStudent(id, { name, classId, accountUsername });
      updated += 1;
    } else {
      await addStudent({ id, name, classId, accountUsername });
      created += 1;
    }
  }
  return { created, updated, skipped, errors };
}

async function ensureSignSessionRow(classId) {
  const cid = String(classId || "").trim();
  if (!cid) return;
  const code = `ZKYS-${Math.floor(1000 + Math.random() * 9000)}`;
  const day = beijingCalendarYmd();
  const validUntil = new Date(Date.now() + 15 * 60000);
  await (await ensurePool()).query(
    `INSERT IGNORE INTO sign_sessions (class_id, sign_code, valid_until, last_sign_day)
     VALUES (?, ?, ?, ?)`,
    [cid, code, validUntil, day]
  );
}

/** 按北京时间自然日清空上一日的课堂签到记录。 */
async function maybeRollSignDayForClass(classId) {
  const cls = String(classId || "").trim();
  if (!cls) return;
  const pool = await ensurePool();
  await ensureSignSessionRow(cls);
  const day = beijingCalendarYmd();
  const [result] = await pool.query(
    `UPDATE sign_sessions
     SET last_sign_day = ?
     WHERE class_id = ?
       AND (last_sign_day IS NULL OR last_sign_day < ?)`,
    [day, cls, day]
  );
  if (result.affectedRows > 0) {
    await pool.query(`UPDATE sign_records SET time = NULL, method = NULL WHERE class_id = ?`, [cls]);
  }
}

async function maybeRollSignDayForClasses(classIds = []) {
  const uniq = new Set(
    (Array.isArray(classIds) ? classIds : []).map((c) => String(c || "").trim()).filter(Boolean)
  );
  for (const c of uniq) {
    await maybeRollSignDayForClass(c);
  }
}

export async function getSignData(classId) {
  const cid = String(classId || "").trim();
  if (!cid) return { session: null, records: [] };
  await maybeRollSignDayForClass(cid);
  const [sessionRows] = await (await ensurePool()).query(
    "SELECT sign_code AS signCode, valid_until AS validUntil FROM sign_sessions WHERE class_id = ?",
    [cid]
  );
  const [rows] = await (await ensurePool()).query(
    `SELECT s.id AS studentId, s.name, r.time, r.method
     FROM students s
     LEFT JOIN sign_records r ON r.class_id = s.class_id AND r.student_id = s.id
     WHERE s.class_id = ?
     ORDER BY s.id`,
    [cid]
  );
  return {
    session: sessionRows[0] || null,
    records: rows.map((r) => ({
      studentId: r.studentId,
      name: r.name,
      time: r.time || null,
      method: r.method || "--",
    })),
  };
}

export async function refreshSignCode(classId) {
  const cid = String(classId || "").trim();
  if (!cid) return { signCode: "", validUntil: new Date() };
  await maybeRollSignDayForClass(cid);
  const code = `ZKYS-${Math.floor(1000 + Math.random() * 9000)}`;
  const validUntil = new Date(Date.now() + 15 * 60000);
  await (await ensurePool()).query(
    `INSERT INTO sign_sessions (class_id, sign_code, valid_until, last_sign_day)
     VALUES (?, ?, ?, ?)
     ON DUPLICATE KEY UPDATE sign_code = VALUES(sign_code), valid_until = VALUES(valid_until)`,
    [cid, code, validUntil, beijingCalendarYmd()]
  );
  return { signCode: code, validUntil };
}

export async function markSignRecord(classId, studentId, method = "手动") {
  const cid = String(classId || "").trim();
  if (!cid) return;
  await maybeRollSignDayForClass(cid);
  const now = new Date().toLocaleTimeString("en-GB", {
    hour12: false,
    timeZone: "Asia/Shanghai",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
  await (await ensurePool()).query(
    `INSERT INTO sign_records (class_id, student_id, time, method)
     VALUES (?, ?, ?, ?)
     ON DUPLICATE KEY UPDATE time = VALUES(time), method = VALUES(method)`,
    [cid, studentId, now, method]
  );
}

export async function undoSignRecord(classId, studentId) {
  const cid = String(classId || "").trim();
  if (!cid) return;
  await maybeRollSignDayForClass(cid);
  await (await ensurePool()).query(
    `INSERT INTO sign_records (class_id, student_id, time, method)
     VALUES (?, ?, NULL, NULL)
     ON DUPLICATE KEY UPDATE time = NULL, method = NULL`,
    [cid, studentId]
  );
}

export async function getExercises(studentId) {
  const [rows] = await (await ensurePool()).query(
    `SELECT e.*,
      EXISTS(SELECT 1 FROM exercise_collections c WHERE c.student_id = ? AND c.exercise_id = e.id) AS collected
     FROM exercises e
     ORDER BY e.id`,
    [studentId]
  );
  return rows.map((r) => ({
    id: r.id,
    subject: r.subject,
    subjectClass: normalizeSubjectClass(r.subject_class),
    subjectIcon: r.subject_icon,
    title: r.title,
    type: r.type,
    typeClass: r.type_class,
    knowledge: r.knowledge,
    difficulty: r.difficulty,
    difficultyText: r.difficulty_text,
    difficultyClass: r.difficulty_class,
    accuracy: r.accuracy,
    time: r.duration,
    collected: !!r.collected,
  }));
}

export async function toggleExerciseCollection(studentId, exerciseId) {
  const [rows] = await (await ensurePool()).query(
    "SELECT 1 AS c FROM exercise_collections WHERE student_id = ? AND exercise_id = ?",
    [studentId, exerciseId]
  );
  if (rows[0]) {
    await (await ensurePool()).query(
      "DELETE FROM exercise_collections WHERE student_id = ? AND exercise_id = ?",
      [studentId, exerciseId]
    );
    return false;
  }
  await (await ensurePool()).query(
    "INSERT INTO exercise_collections (student_id, exercise_id) VALUES (?, ?)",
    [studentId, exerciseId]
  );
  return true;
}

export async function getTodayClassSchedules(weekday) {
  const [rows] = await (await ensurePool()).query(
    `SELECT weekday, start_time AS startTime, end_time AS endTime, lesson_name AS lessonName, class_name AS className
     FROM class_schedules
     WHERE weekday = ?
     ORDER BY start_time`,
    [weekday]
  );
  return rows;
}

function normalizeAnswerText(v) {
  return String(v || "").trim().replace(/\s+/g, "").toLowerCase();
}

function inferKnowledgeFromQuestion(q = {}) {
  const text = `${q.knowledge || ""} ${q.content || ""}`;
  if (/乘法/.test(text)) return "两位数乘法";
  if (/除法/.test(text)) return "除法竖式";
  if (/应用题|审题/.test(text)) return "应用题审题";
  if (/口算/.test(text)) return "口算训练";
  return "基础计算";
}

function isObjectiveQuestionCorrect(question, studentAnswerRaw) {
  const q = question || {};
  const ans = normalizeAnswerText(studentAnswerRaw);
  const ref = String(q.answer || "").trim();
  if (!ref) return false;
  if (q.type === "choice") return ans === normalizeAnswerText(ref);
  const refs = ref
    .split("/")
    .map((x) => normalizeAnswerText(x))
    .filter(Boolean);
  return refs.includes(ans);
}

/** 客观题（非作文、且有参考答案）正确率，0–100；无可判题目时返回 null。 */
function computeObjectiveAccuracyPercent(questions, answers) {
  const qArr = Array.isArray(questions) ? questions : [];
  const ansArr = Array.isArray(answers) ? answers : [];
  const answerMap = new Map(ansArr.map((a) => [Number(a?.index), String(a?.value || "")]));
  let graded = 0;
  let correct = 0;
  for (let i = 0; i < qArr.length; i++) {
    const q = qArr[i] || {};
    if (q.type === "essay") continue;
    if (!String(q.answer || "").trim()) continue;
    graded += 1;
    if (isObjectiveQuestionCorrect(q, answerMap.get(i) || "")) correct += 1;
  }
  if (graded === 0) return null;
  return Math.round((correct / graded) * 100);
}

export async function getTeacherWeakWarningsFromHomework(teacherId, options = {}) {
  const days = Number(options.days || 30);
  const minAttempts = Number(options.minAttempts || 8);
  const minWrongRate = Number(options.minWrongRate || 30);
  const [rows] = await (await ensurePool()).query(
    `SELECT
      h.id AS homeworkId,
      h.class_name AS className,
      h.questions_json AS questionsJson,
      s.student_id AS studentId,
      s.answer_json AS answerJson,
      s.submit_time AS submitTime
     FROM homework h
     INNER JOIN submissions s ON s.homework_id = h.id
     WHERE h.teacher_id = ?
       AND s.submit_time >= DATE_SUB(NOW(), INTERVAL ? DAY)`,
    [teacherId, days]
  );

  const agg = new Map();
  for (const r of rows) {
    let questions = [];
    let answers = [];
    try {
      questions = JSON.parse(r.questionsJson || "[]");
    } catch {
      questions = [];
    }
    try {
      answers = JSON.parse(r.answerJson || "[]");
    } catch {
      answers = [];
    }
    const answerMap = new Map(
      (Array.isArray(answers) ? answers : []).map((a) => [Number(a?.index), String(a?.value || "")])
    );
    for (let i = 0; i < questions.length; i++) {
      const q = questions[i] || {};
      if (q.type === "essay") continue;
      const knowledge = inferKnowledgeFromQuestion(q);
      const key = `${r.className}__${knowledge}`;
      const stat = agg.get(key) || {
        className: r.className,
        name: knowledge,
        attemptCount: 0,
        wrongCount: 0,
        students: new Set(),
      };
      stat.attemptCount += 1;
      const studentAnswer = answerMap.get(i) || "";
      const ok = isObjectiveQuestionCorrect(q, studentAnswer);
      if (!ok) {
        stat.wrongCount += 1;
        stat.students.add(String(r.studentId || ""));
      }
      agg.set(key, stat);
    }
  }

  const list = Array.from(agg.values())
    .map((x) => {
      const errorRate = x.attemptCount ? Math.round((x.wrongCount / x.attemptCount) * 100) : 0;
      return {
        name: x.name,
        className: x.className,
        errorRate,
        attemptCount: x.attemptCount,
        wrongCount: x.wrongCount,
        affectedStudents: x.students.size,
        dataRange: `近${days}天`,
      };
    })
    .filter((x) => x.attemptCount >= minAttempts && x.errorRate >= minWrongRate)
    .sort((a, b) => b.errorRate - a.errorRate)
    .slice(0, 8);

  return list;
}

export async function getTeacherStudentBoardData(teacherId, classId = "") {
  const p = await ensurePool();
  const normalizedClassId = String(classId || "").trim();
  const [homeworks] = normalizedClassId
    ? await p.query(
        `SELECT id, class_name AS className, class_id AS classId, subject, questions_json AS questionsJson
         FROM homework
         WHERE teacher_id = ? AND class_id = ?`,
        [teacherId, normalizedClassId]
      )
    : await p.query(
        `SELECT id, class_name AS className, class_id AS classId, subject, questions_json AS questionsJson
         FROM homework
         WHERE teacher_id = ?`,
        [teacherId]
      );
  const emptyWeek = (() => {
    const out = [];
    const dayMap = ["日", "一", "二", "三", "四", "五", "六"];
    for (let i = 6; i >= 0; i--) {
      const d = new Date();
      d.setDate(d.getDate() - i);
      out.push({ day: dayMap[d.getDay()], value: 0 });
    }
    return out;
  })();
  const homeworkMap = new Map(homeworks.map((h) => [h.id, h]));
  const classIds = normalizedClassId
    ? [normalizedClassId]
    : Array.from(new Set(homeworks.map((h) => h.classId || h.class_id).filter(Boolean)));
  const inPlaceholders = (n) => Array.from({ length: n }, () => "?").join(",");

  const [classCountRows] = classIds.length
    ? await p.query(
        `SELECT class_id AS classId, COUNT(1) AS c
         FROM students
         WHERE class_id IN (${inPlaceholders(classIds.length)})
         GROUP BY class_id`,
        classIds
      )
    : [[]];
  const classSizeMap = new Map(classCountRows.map((r) => [r.classId, Number(r.c || 0)]));
  const totalStudents = classIds.reduce((sum, c) => sum + (classSizeMap.get(c) || 0), 0);

  const homeworkIds = homeworks.map((h) => h.id);
  const [subRows] = homeworkIds.length
    ? await p.query(
        `SELECT homework_id AS homeworkId, student_id AS studentId, submit_time AS submitTime, answer_json AS answerJson
         FROM submissions
         WHERE homework_id IN (${inPlaceholders(homeworkIds.length)})`,
        homeworkIds
      )
    : [[]];

  const submittedByHomework = new Map();
  for (const r of subRows) {
    const set = submittedByHomework.get(r.homeworkId) || new Set();
    set.add(String(r.studentId || ""));
    submittedByHomework.set(r.homeworkId, set);
  }
  let totalShould = 0;
  let totalSubmitted = 0;
  for (const h of homeworks) {
    const hid = h.classId || h.class_id;
    totalShould += classSizeMap.get(hid) || 0;
    totalSubmitted += submittedByHomework.get(h.id)?.size || 0;
  }
  const completionRate = totalShould ? Math.round((totalSubmitted / totalShould) * 100) : 0;

  const dayMap = ["日", "一", "二", "三", "四", "五", "六"];
  const dayKeys = [];
  const dayLabelMap = new Map();
  for (let i = 6; i >= 0; i--) {
    const d = new Date();
    d.setDate(d.getDate() - i);
    const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(
      d.getDate()
    ).padStart(2, "0")}`;
    dayKeys.push(key);
    dayLabelMap.set(key, dayMap[d.getDay()]);
  }
  const dayStudentSetMap = new Map(dayKeys.map((k) => [k, new Set()]));
  const recentSubRows = subRows.filter((r) => {
    const d = new Date(r.submitTime);
    if (Number.isNaN(d.getTime())) return false;
    const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(
      d.getDate()
    ).padStart(2, "0")}`;
    if (!dayStudentSetMap.has(key)) return false;
    dayStudentSetMap.get(key).add(String(r.studentId || ""));
    return true;
  });
  const weekData = dayKeys.map((k) => ({ day: dayLabelMap.get(k), value: dayStudentSetMap.get(k).size }));
  const weekSubmitCount = recentSubRows.length;
  const weekActiveStudents = new Set(recentSubRows.map((r) => String(r.studentId || ""))).size;
  const avgStudyHours = weekActiveStudents
    ? Math.round(((weekSubmitCount / weekActiveStudents) * 0.6) * 10) / 10
    : 0;

  const knowledgeAgg = new Map();
  const classAgg = new Map();
  let totalAttempts = 0;
  let totalCorrect = 0;
  const questionCache = new Map();

  for (const r of subRows) {
    const hw = homeworkMap.get(r.homeworkId);
    if (!hw) continue;
    let questions = questionCache.get(r.homeworkId);
    if (!questions) {
      try {
        questions = JSON.parse(hw.questionsJson || "[]");
      } catch {
        questions = [];
      }
      questionCache.set(r.homeworkId, questions);
    }
    let answers = [];
    try {
      answers = JSON.parse(r.answerJson || "[]");
    } catch {
      answers = [];
    }
    const answerMap = new Map((Array.isArray(answers) ? answers : []).map((a) => [Number(a?.index), String(a?.value || "")]));
    for (let i = 0; i < questions.length; i++) {
      const q = questions[i] || {};
      if (q.type === "essay") continue;
      totalAttempts += 1;
      const ok = isObjectiveQuestionCorrect(q, answerMap.get(i) || "");
      if (ok) totalCorrect += 1;
      const knowledge = inferKnowledgeFromQuestion(q);
      const kKey = `${knowledge}__${hw.className}`;
      const kStat = knowledgeAgg.get(kKey) || {
        name: knowledge,
        sub: `${hw.subject || "小学课程"} · ${hw.className}`,
        errorCount: 0,
        attempts: 0,
      };
      kStat.attempts += 1;
      if (!ok) kStat.errorCount += 1;
      knowledgeAgg.set(kKey, kStat);

      const cStat = classAgg.get(hw.className) || { className: hw.className, errorCount: 0, attempts: 0 };
      cStat.attempts += 1;
      if (!ok) cStat.errorCount += 1;
      classAgg.set(hw.className, cStat);
    }
  }

  const palette = ["#ff9f1c", "#1e6df2", "#2eb85c", "#6f42c1", "#e83e8c"];
  const weakPoints = Array.from(knowledgeAgg.values())
    .map((x) => ({
      name: x.name,
      sub: x.sub,
      errorRate: x.attempts ? Math.round((x.errorCount / x.attempts) * 100) : 0,
    }))
    .sort((a, b) => b.errorRate - a.errorRate)
    .slice(0, 5)
    .map((x, i) => ({ ...x, color: palette[i % palette.length] }));

  const classWeakness = Array.from(classAgg.values())
    .map((x, i) => {
      const weakness = x.attempts ? Math.round((x.errorCount / x.attempts) * 100) : 0;
      return {
        className: x.className,
        weakness,
        color: palette[i % palette.length],
        level: weakness >= 40 ? "high" : weakness >= 25 ? "medium" : "low",
      };
    })
    .sort((a, b) => b.weakness - a.weakness);

  const avgAccuracy = totalAttempts ? Math.round((totalCorrect / totalAttempts) * 100) : 0;

  const [studentRows] = classIds.length
    ? await p.query(
        `SELECT id AS studentNo, name AS studentName, class_name AS className, class_id AS classId, account_username AS accountUsername
         FROM students
         WHERE class_id IN (${inPlaceholders(classIds.length)})
         ORDER BY class_name, id`,
        classIds
      )
    : [[]];

  const [userRows] = studentRows.length
    ? await p.query(
        `SELECT id AS userId, username
         FROM users
         WHERE username IN (${inPlaceholders(studentRows.length)})`,
        studentRows.map((x) => x.accountUsername || "__none__")
      )
    : [[]];
  const usernameToUserId = new Map(userRows.map((u) => [u.username, u.userId]));

  const submitAggByUser = new Map();
  for (const r of subRows) {
    const key = String(r.studentId || "");
    const hw = homeworkMap.get(r.homeworkId);
    const old = submitAggByUser.get(key) || {
      submittedCount: 0,
      latestSubmitAt: "",
      latestHomeworkTitle: "",
    };
    old.submittedCount += 1;
    const t = new Date(r.submitTime);
    const tMs = Number.isNaN(t.getTime()) ? 0 : t.getTime();
    const oldMs = old.latestSubmitAt ? new Date(old.latestSubmitAt).getTime() : 0;
    if (tMs >= oldMs) {
      old.latestSubmitAt = r.submitTime;
      old.latestHomeworkTitle = hw?.title || "作业";
    }
    submitAggByUser.set(key, old);
  }

  if (classIds.length) {
    await maybeRollSignDayForClasses(classIds);
  }

  const [signRows] = classIds.length
    ? await p.query(
        `SELECT class_id AS classId, student_id AS studentNo, time, method
         FROM sign_records
         WHERE class_id IN (${inPlaceholders(classIds.length)})`,
        classIds
      )
    : [[]];
  const signMap = new Map(signRows.map((r) => [`${r.classId}__${r.studentNo}`, r]));
  const homeworkCountByClass = new Map();
  for (const h of homeworks) {
    const hid = h.classId || h.class_id;
    homeworkCountByClass.set(hid, (homeworkCountByClass.get(hid) || 0) + 1);
  }
  const studentActivities = studentRows.map((s) => {
    const userId = usernameToUserId.get(s.accountUsername || "") || "";
    const submitAgg = submitAggByUser.get(String(userId)) || {
      submittedCount: 0,
      latestSubmitAt: "",
      latestHomeworkTitle: "",
    };
    const scid = s.classId || s.class_id;
    const sign = signMap.get(`${scid}__${s.studentNo}`) || null;
    const totalHomework = homeworkCountByClass.get(scid) || 0;
    const latestAction = submitAgg.latestSubmitAt
      ? `提交作业《${submitAgg.latestHomeworkTitle || "作业"}》`
      : sign?.time
        ? `课堂签到（${sign.method || "手动"}）`
        : "暂无学习记录";
    return {
      studentNo: s.studentNo,
      studentName: s.studentName,
      className: s.className,
      submittedCount: submitAgg.submittedCount,
      totalHomework,
      signStatus: sign?.time ? "已签到" : "未签到",
      latestAction,
      latestTime: submitAgg.latestSubmitAt || sign?.time || "",
    };
  });

  return {
    stats: { totalStudents, completionRate, avgStudyHours, avgAccuracy },
    weakPoints,
    weekData,
    classWeakness,
    studentActivities,
  };
}

export async function listTeacherTodos(teacherId) {
  const [rows] = await (await ensurePool()).query(
    `SELECT id, title, meta, urgent, is_done AS isDone, created_at AS createdAt
     FROM teacher_todos
     WHERE teacher_id = ?
     ORDER BY is_done ASC, urgent DESC, created_at DESC`,
    [teacherId]
  );
  return rows.map((r) => ({
    id: r.id,
    title: r.title,
    meta: r.meta || "",
    urgent: !!r.urgent,
    done: !!r.isDone,
    createdAt: r.createdAt,
  }));
}

export async function createTeacherTodo(teacherId, payload) {
  const [ret] = await (await ensurePool()).query(
    `INSERT INTO teacher_todos (teacher_id, title, meta, urgent, is_done, created_at)
     VALUES (?, ?, ?, ?, 0, ?)`,
    [teacherId, payload.title, payload.meta || "", payload.urgent ? 1 : 0, new Date()]
  );
  return ret.insertId;
}

export async function updateTeacherTodo(teacherId, todoId, payload) {
  const [ret] = await (await ensurePool()).query(
    `UPDATE teacher_todos
     SET title = COALESCE(?, title),
         meta = COALESCE(?, meta),
         urgent = COALESCE(?, urgent),
         is_done = COALESCE(?, is_done)
     WHERE id = ? AND teacher_id = ?`,
    [
      payload.title ?? null,
      payload.meta ?? null,
      payload.urgent === undefined ? null : payload.urgent ? 1 : 0,
      payload.done === undefined ? null : payload.done ? 1 : 0,
      todoId,
      teacherId,
    ]
  );
  return !!ret.affectedRows;
}

export async function deleteTeacherTodo(teacherId, todoId) {
  const [ret] = await (await ensurePool()).query(
    "DELETE FROM teacher_todos WHERE id = ? AND teacher_id = ?",
    [todoId, teacherId]
  );
  return !!ret.affectedRows;
}

export async function createTeacherMessage(teacherId, teacherName, payload) {
  const cid = String(payload.classId || "").trim();
  const cname = String(payload.className || "").trim();
  if (!cid || !cname) throw new Error("classId and className required");
  const [ret] = await (await ensurePool()).query(
    `INSERT INTO teacher_messages (teacher_id, teacher_name, class_name, class_id, title, content, msg_type, created_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
    [
      teacherId,
      teacherName,
      cname,
      cid,
      payload.title,
      payload.content || "",
      payload.type || "notice",
      new Date(),
    ]
  );
  return ret.insertId;
}

export async function getStudentClassContextByAccount(accountUsername) {
  const [rows] = await (await ensurePool()).query(
    "SELECT class_id AS classId, class_name AS className FROM students WHERE LOWER(TRIM(account_username)) = LOWER(TRIM(?)) LIMIT 1",
    [accountUsername]
  );
  const r = rows[0];
  if (!r) return null;
  return { classId: r.classId || null, className: r.className || null };
}

/** @deprecated 使用 getStudentClassContextByAccount */
export async function getStudentClassByAccount(accountUsername) {
  const ctx = await getStudentClassContextByAccount(accountUsername);
  return ctx?.className || null;
}

export async function listStudentMessages(studentId, classId) {
  const cid = String(classId || "").trim();
  const [rows] = await (await ensurePool()).query(
    `SELECT m.id,
      m.title,
      m.content,
      m.msg_type AS type,
      m.teacher_name AS teacher,
      m.created_at AS createdAt,
      EXISTS(
        SELECT 1 FROM student_message_reads r
        WHERE r.student_id = ? AND r.message_id = m.id
      ) AS isRead
     FROM teacher_messages m
     WHERE m.class_id = ?
     ORDER BY m.created_at DESC
     LIMIT 50`,
    [studentId, cid]
  );
  return rows.map((r) => ({
    id: r.id,
    title: r.title,
    content: r.content || "",
    type: r.type || "notice",
    teacher: r.teacher,
    time: r.createdAt,
    read: !!r.isRead,
  }));
}

export async function markStudentMessageRead(studentId, messageId) {
  await (await ensurePool()).query(
    `INSERT INTO student_message_reads (student_id, message_id, read_at)
     VALUES (?, ?, ?)
     ON DUPLICATE KEY UPDATE read_at = VALUES(read_at)`,
    [studentId, messageId, new Date()]
  );
}

const AI_LOG_TEXT_MAX = 200000;

/** 智能体 / 外部生成调用审计；失败不影响主流程 */
export async function insertAiGenerationRecord({
  featureType,
  message = null,
  requestPayload = null,
  responsePayload = null,
  success = false,
}) {
  try {
    const p = await ensurePool();
    const clip = (s) => {
      if (s == null) return null;
      const t = String(s);
      return t.length <= AI_LOG_TEXT_MAX ? t : `${t.slice(0, AI_LOG_TEXT_MAX)}\n...[truncated]`;
    };
    await p.query(
      `INSERT INTO ai_generation_record (created_at, feature_type, message, request_payload, response_payload, success)
       VALUES (?, ?, ?, ?, ?, ?)`,
      [
        new Date(),
        String(featureType || "unknown").slice(0, 64),
        message != null ? String(message).slice(0, 500) : null,
        clip(requestPayload),
        clip(responsePayload),
        success ? 1 : 0,
      ]
    );
  } catch (e) {
    console.error("insertAiGenerationRecord:", e.message || e);
  }
}

