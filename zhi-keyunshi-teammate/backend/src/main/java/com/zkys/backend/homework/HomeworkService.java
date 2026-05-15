package com.zkys.backend.homework;

import com.zkys.backend.security.UserPrincipal;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.sql.Timestamp;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class HomeworkService {
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public HomeworkService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> listHomework(UserPrincipal me, String status) {
        String onlyForClass = null;
        if ("student".equals(me.role())) {
            onlyForClass = getStudentClassId(me.username());
            if (onlyForClass == null || onlyForClass.isBlank()) {
                return List.of();
            }
        }
        List<Map<String, Object>> list = listHomeworkWithStatus(me.id(), onlyForClass);
        if (status != null && ("pending".equals(status) || "completed".equals(status))) {
            return list.stream().filter(x -> Objects.equals(x.get("status"), status)).toList();
        }
        return list;
    }

    @Transactional
    public Map<String, Object> createHomework(UserPrincipal me, Map<String, Object> body) {
        String classId = s(body.get("classId"));
        String title = s(body.get("title"));
        String deadline = s(body.getOrDefault("deadline", "三日内"));
        String description = s(body.getOrDefault("description", ""));
        List<Map<String, Object>> questions = body.get("questions") instanceof List<?> q ? (List<Map<String, Object>>) q : List.of();
        if (classId.isBlank()) throw new IllegalArgumentException("请选择班级（classId）");
        if (title.isBlank()) throw new IllegalArgumentException("任务标题不能为空");
        if (questions.isEmpty()) throw new IllegalArgumentException("请至少添加一道题目");
        if (!teacherOwnsClass(me.id(), classId)) throw new IllegalArgumentException("无权向该班级发布作业");
        String className = queryOne("SELECT name FROM classes WHERE id = ? LIMIT 1", String.class, classId);
        if (className == null || className.isBlank()) throw new IllegalArgumentException("班级不存在");

        String id = UUID.randomUUID().toString();
        jdbcTemplate.update(
                "INSERT INTO homework (id, subject, subject_class, subject_icon, title, teacher_id, teacher_name, class_name, class_id, deadline, type, question_count, difficulty_text, description, questions_json, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, "数学", "math", "fas fa-calculator", title, me.id(), me.name(), className, classId, deadline, "综合题", questions.size(), "进阶", description, toJson(questions), Timestamp.valueOf(LocalDateTime.now())
        );
        Map<String, Object> item = jdbcTemplate.queryForMap("SELECT * FROM homework WHERE id = ?", id);
        return Map.of("message", "发布成功", "item", item);
    }

    public Map<String, Object> getHomeworkDetail(UserPrincipal me, String homeworkId) {
        String studentClass = "student".equals(me.role()) ? s(getStudentClassId(me.username())) : null;
        Map<String, Object> item = getHomeworkDetailForStudent(homeworkId, me.id(), studentClass);
        if (item == null) throw new IllegalArgumentException("作业不存在或无权查看");
        return Map.of("item", item);
    }

    @Transactional
    public Map<String, Object> submitHomework(UserPrincipal me, String homeworkId, List<Map<String, Object>> answers) {
        if (!"student".equals(me.role())) throw new IllegalArgumentException("仅学生可提交作业，请切换到学生账号后重试");
        Integer c = queryOne("SELECT 1 FROM homework WHERE id = ? LIMIT 1", Integer.class, homeworkId);
        if (c == null) throw new IllegalArgumentException("作业不存在");
        String myClass = s(getStudentClassId(me.username()));
        Map<String, Object> detail = getHomeworkDetailForStudent(homeworkId, me.id(), myClass);
        if (detail == null) throw new IllegalArgumentException("该作业不属于你所在班级，无法提交");
        if (answers != null) {
            upsertSubmission(homeworkId, me.id(), toJson(answers));
            storeHomeworkWrongQuestions(me.id(), homeworkId, detail, answers);
            return Map.of("message", "提交成功，可查看答案解析");
        }
        Integer has = queryOne("SELECT 1 FROM submissions WHERE homework_id = ? AND student_id = ? LIMIT 1", Integer.class, homeworkId, me.id());
        if (has != null) return Map.of("message", "你已提交过该作业");
        upsertSubmission(homeworkId, me.id(), null);
        return Map.of("message", "提交成功");
    }

    public Map<String, Object> listTeacherHomeworkManage(String teacherId, String classId) {
        String cid = s(classId);
        List<Map<String, Object>> list = jdbcTemplate.query(
                """
                SELECT h.id, h.title, h.class_name AS className, h.deadline, h.created_at AS createdAt,
                       h.class_id AS classId,
                       (SELECT COUNT(1) FROM students st WHERE st.class_id = h.class_id) AS totalStudents,
                       (SELECT COUNT(1) FROM submissions s WHERE s.homework_id = h.id) AS submittedStudents
                FROM homework h
                WHERE h.teacher_id = ?
                  AND (? = '' OR h.class_id = ?)
                ORDER BY h.created_at DESC
                """,
                (rs, i) -> {
                    int total = rs.getInt("totalStudents");
                    int submitted = rs.getInt("submittedStudents");
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", rs.getString("id"));
                    m.put("title", rs.getString("title"));
                    m.put("className", rs.getString("className"));
                    m.put("classId", rs.getString("classId"));
                    m.put("deadline", rs.getString("deadline"));
                    m.put("createdAt", rs.getTimestamp("createdAt"));
                    m.put("totalStudents", total);
                    m.put("submittedStudents", submitted);
                    m.put("pendingStudents", Math.max(total - submitted, 0));
                    m.put("submitRate", total > 0 ? Math.round((submitted * 100.0) / total) : 0);
                    return m;
                },
                teacherId,
                cid,
                cid
        );
        return Map.of("list", list);
    }

    @Transactional
    public Map<String, Object> deleteTeacherHomework(String homeworkId, String teacherId) {
        Integer c = queryOne("SELECT 1 FROM homework WHERE id = ? AND teacher_id = ? LIMIT 1", Integer.class, homeworkId, teacherId);
        if (c == null) throw new IllegalArgumentException("作业不存在或无权限删除");
        jdbcTemplate.update("DELETE FROM submissions WHERE homework_id = ?", homeworkId);
        jdbcTemplate.update("DELETE FROM homework WHERE id = ? AND teacher_id = ?", homeworkId, teacherId);
        return Map.of("message", "作业已删除");
    }

    public Map<String, Object> studentOverview(UserPrincipal me) {
        String classId = getStudentClassId(me.username());
        String className = getStudentClassName(me.username());
        List<Map<String, Object>> list = listHomeworkWithStatus(me.id(), classId == null ? "" : classId);
        List<Map<String, Object>> pending = list.stream().filter(x -> "pending".equals(x.get("status"))).toList();
        int done = (int) list.stream().filter(x -> "completed".equals(x.get("status"))).count();
        int todayPending = (int) pending.stream().filter(h -> s(h.get("deadline")).contains("今日") || Boolean.TRUE.equals(h.get("isUrgent"))).count();
        if (todayPending == 0) todayPending = Math.min(pending.size(), 4);
        int weekProgress = list.isEmpty() ? 0 : Math.round((done * 100f) / list.size());
        List<Map<String, Object>> messages = classId == null ? List.of() : listStudentMessages(me.id(), classId);

        Map<String, Object> user = new HashMap<>();
        user.put("name", me.name());
        user.put("className", className);
        user.put("classId", classId);
        Map<String, Object> stats = Map.of("todayPending", todayPending, "weekProgress", weekProgress, "totalHours", 12.5);
        List<Map<String, Object>> courses = List.of(
                Map.of("subject", "小学数学", "teacher", "王老师 · 乘法与应用题", "progress", weekProgress == 0 ? 68 : weekProgress, "subjectClass", "math", "icon", "fas fa-calculator"),
                Map.of("subject", "小学语文", "teacher", "李老师 · 阅读与写话", "progress", 52, "subjectClass", "chinese", "icon", "fas fa-book-open"),
                Map.of("subject", "小学英语", "teacher", "周老师 · 单词与对话", "progress", 46, "subjectClass", "english", "icon", "fas fa-language"),
                Map.of("subject", "小学科学", "teacher", "陈老师 · 植物与观察", "progress", 35, "subjectClass", "science", "icon", "fas fa-seedling"),
                Map.of("subject", "小学美术", "teacher", "林老师 · 色彩与构图", "progress", 31, "subjectClass", "art", "icon", "fas fa-palette")
        );
        return Map.of("user", user, "stats", stats, "todoHomework", pending.stream().limit(5).toList(), "courses", courses, "schoolMessages", messages);
    }

    public Map<String, Object> listSubmissionsForTeacher(String homeworkId, String teacherId) {
        Map<String, Object> hw = queryHomeworkById(homeworkId);
        if (hw == null || !Objects.equals(s(hw.get("teacherId")), s(teacherId))) {
            throw new IllegalArgumentException("作业不存在或无权限查看");
        }
        String classId = s(hw.get("classId"));
        List<Map<String, Object>> questions = parseList(s(hw.get("questionsJson")));

        List<Map<String, Object>> roster = jdbcTemplate.query(
                "SELECT st.id AS studentNo, st.name AS studentName, st.account_username AS accountUsername FROM students st WHERE st.class_id = ? ORDER BY st.id",
                (rs, i) -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("studentNo", rs.getString("studentNo"));
                    m.put("studentName", rs.getString("studentName"));
                    m.put("accountUsername", rs.getString("accountUsername"));
                    return m;
                },
                classId
        );

        List<Map<String, Object>> submittedRows = jdbcTemplate.query(
                """
                SELECT COALESCE(st.id, u.username) AS joinStudentNo,
                       COALESCE(st.name, u.name, u.username) AS joinStudentName,
                       u.username AS submitAccount,
                       st.class_id AS studentClassId,
                       s.submit_time AS submitTimeRaw,
                       s.teacher_grade AS teacherGrade,
                       s.answer_json AS answerJson
                FROM submissions s
                INNER JOIN users u ON u.id = s.student_id
                LEFT JOIN students st ON st.account_username IS NOT NULL
                  AND TRIM(st.account_username) <> ''
                  AND LOWER(TRIM(st.account_username)) = LOWER(TRIM(u.username))
                WHERE s.homework_id = ?
                ORDER BY s.submit_time DESC
                """,
                (rs, i) -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("joinStudentNo", rs.getString("joinStudentNo"));
                    m.put("joinStudentName", rs.getString("joinStudentName"));
                    m.put("submitAccount", rs.getString("submitAccount"));
                    m.put("studentClassId", rs.getString("studentClassId"));
                    m.put("submitTimeRaw", rs.getTimestamp("submitTimeRaw"));
                    m.put("teacherGrade", normalizeGrade(rs.getString("teacherGrade")));
                    m.put("answerJson", rs.getString("answerJson"));
                    return m;
                },
                homeworkId
        );

        Map<String, Map<String, Object>> submissionByUsername = new HashMap<>();
        for (Map<String, Object> row : submittedRows) {
            String submitAccount = s(row.get("submitAccount"));
            String key = loginKey(submitAccount);
            if (submitAccount.isBlank() || submissionByUsername.containsKey(key)) continue;
            List<Map<String, Object>> answers = parseList(s(row.get("answerJson")));
            Map<String, Object> item = new HashMap<>();
            item.put("joinStudentNo", row.get("joinStudentNo"));
            item.put("joinStudentName", row.get("joinStudentName"));
            item.put("submitAccount", submitAccount);
            item.put("studentClassId", row.get("studentClassId"));
            item.put("submitTimeRaw", row.get("submitTimeRaw"));
            item.put("teacherGrade", row.get("teacherGrade"));
            item.put("accuracyPercent", computeObjectiveAccuracyPercent(questions, answers));
            submissionByUsername.put(key, item);
        }

        Set<String> matched = new HashSet<>();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> st : roster) {
            String account = s(st.get("accountUsername"));
            Map<String, Object> sub = account.isBlank() ? null : submissionByUsername.get(loginKey(account));
            if (sub != null) {
                matched.add(loginKey(account));
                list.add(Map.of(
                        "studentNo", st.get("studentNo"),
                        "studentName", st.get("studentName"),
                        "submitAccount", sub.get("submitAccount"),
                        "submitted", true,
                        "submitTime", format((Timestamp) sub.get("submitTimeRaw")),
                        "accuracyPercent", sub.get("accuracyPercent"),
                        "teacherGrade", sub.get("teacherGrade")
                ));
            } else {
                Map<String, Object> no = new HashMap<>();
                no.put("studentNo", st.get("studentNo"));
                no.put("studentName", st.get("studentName"));
                no.put("submitAccount", account.isBlank() ? null : account);
                no.put("submitted", false);
                no.put("submitTime", null);
                no.put("accuracyPercent", null);
                no.put("teacherGrade", null);
                list.add(no);
            }
        }

        for (Map<String, Object> sub : submissionByUsername.values()) {
            String key = loginKey(s(sub.get("submitAccount")));
            if (matched.contains(key)) continue;
            String subCid = s(sub.get("studentClassId"));
            String suffix = subCid.isBlank() ? "（花名册未绑定该登录账号）" : (!classId.isBlank() && !classId.equals(subCid) ? "（跨班提交）" : "");
            Map<String, Object> ext = new HashMap<>();
            ext.put("studentNo", sub.get("joinStudentNo"));
            ext.put("studentName", s(sub.get("joinStudentName")) + suffix);
            ext.put("submitAccount", sub.get("submitAccount"));
            ext.put("submitted", true);
            ext.put("submitTime", format((Timestamp) sub.get("submitTimeRaw")));
            ext.put("accuracyPercent", sub.get("accuracyPercent"));
            ext.put("teacherGrade", sub.get("teacherGrade"));
            list.add(ext);
        }

        return Map.of("homework", Map.of("id", hw.get("id"), "title", hw.get("title"), "className", hw.get("className")), "list", list);
    }

    public Map<String, Object> submissionDetailForTeacher(String homeworkId, String teacherId, String studentNo) {
        if (studentNo == null || studentNo.trim().isEmpty()) throw new IllegalArgumentException("studentNo 不能为空");
        Map<String, Object> hw = queryHomeworkById(homeworkId);
        if (hw == null || !Objects.equals(s(hw.get("teacherId")), s(teacherId))) {
            throw new IllegalArgumentException("未找到该学生的作答详情");
        }
        Map<String, String> identity = resolveStudentIdentityForHomeworkClass(s(hw.get("classId")), studentNo);
        if (identity == null) throw new IllegalArgumentException("未找到该学生的作答详情");

        List<Map<String, Object>> rows = jdbcTemplate.query(
                "SELECT s.submit_time AS submitTime, s.teacher_grade AS teacherGrade, s.answer_json AS answerJson " +
                        "FROM submissions s INNER JOIN users u ON u.id = s.student_id WHERE s.homework_id = ? AND u.username = ? LIMIT 1",
                (rs, i) -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("submitTime", rs.getTimestamp("submitTime"));
                    m.put("teacherGrade", normalizeGrade(rs.getString("teacherGrade")));
                    m.put("answerJson", rs.getString("answerJson"));
                    return m;
                },
                homeworkId, identity.get("accountUsername")
        );
        Map<String, Object> submit = rows.isEmpty() ? new HashMap<>() : rows.get(0);
        List<Map<String, Object>> questions = parseList(s(hw.get("questionsJson")));
        List<Map<String, Object>> answers = parseList(s(submit.get("answerJson")));
        Integer accuracyPercent = computeObjectiveAccuracyPercent(questions, answers);
        Timestamp submitTime = (Timestamp) submit.get("submitTime");

        Map<String, Object> student = new HashMap<>();
        student.put("studentNo", identity.get("studentNo"));
        student.put("studentName", identity.get("studentName"));
        student.put("submitted", submitTime != null);
        student.put("submitTime", format(submitTime));
        student.put("accuracyPercent", accuracyPercent);
        student.put("teacherGrade", submit.get("teacherGrade"));

        return Map.of(
                "homework", Map.of("id", hw.get("id"), "title", hw.get("title"), "className", hw.get("className")),
                "student", student,
                "questions", questions,
                "answers", answers
        );
    }

    @Transactional
    public Map<String, Object> setSubmissionGrade(String homeworkId, String teacherId, String studentNo, Object gradeRaw) {
        String grade = s(gradeRaw).toUpperCase();
        String normalized = (grade.isBlank() || "-".equals(grade) || "CLEAR".equals(grade)) ? null : grade;
        if (normalized != null && !List.of("A", "B", "C", "D").contains(normalized)) {
            throw new IllegalArgumentException("评级须为 A、B、C、D，或传空清除");
        }
        Map<String, Object> hw = queryHomeworkById(homeworkId);
        if (hw == null || !Objects.equals(s(hw.get("teacherId")), s(teacherId))) {
            throw new IllegalArgumentException("作业不存在或无权限");
        }
        Map<String, String> identity = resolveStudentIdentityForHomeworkClass(s(hw.get("classId")), studentNo);
        if (identity == null) throw new IllegalArgumentException("未找到该学生");
        String account = s(identity.get("accountUsername"));
        if (account.isBlank()) throw new IllegalArgumentException("该学生未绑定登录账号，无法保存评级");
        String userId = queryOne("SELECT id FROM users WHERE username = ? LIMIT 1", String.class, account);
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("未找到学生用户");
        int affected = jdbcTemplate.update("UPDATE submissions SET teacher_grade = ? WHERE homework_id = ? AND student_id = ?", normalized, homeworkId, userId);
        if (affected <= 0) throw new IllegalArgumentException("该学生尚未提交作业，无法评级");
        return Map.of("teacherGrade", normalized, "message", "评级已保存");
    }

    public Map<String, Object> importQuestionsFromDocument(String teacherId, MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) throw new IllegalArgumentException("请先上传文档");
            String filename = s(file.getOriginalFilename());
            String ext = fileExtension(filename);
            if (!Set.of("txt", "md", "docx").contains(ext)) {
                throw new IllegalArgumentException("仅支持 txt、md、docx 文档导入");
            }
            String text;
            if ("docx".equals(ext)) {
                text = extractDocxText(file);
            } else {
                text = new String(file.getBytes(), StandardCharsets.UTF_8);
            }
            List<Map<String, Object>> questions = parseImportedQuestions(text);
            if (questions.isEmpty()) {
                throw new IllegalArgumentException("未识别到题目，请检查文档格式（建议按 1. 2. 编号）");
            }
            return Map.of("list", questions, "count", questions.size());
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("文档处理失败：" + s(ex.getMessage()));
        }
    }

    private List<Map<String, Object>> listHomeworkWithStatus(String studentId, String onlyForClass) {
        String where = "";
        List<Object> params = new ArrayList<>(List.of(studentId, studentId, studentId));
        if (onlyForClass != null) {
            if (onlyForClass.isBlank()) {
                where = " WHERE 1=0 ";
            } else {
                where = " WHERE h.class_id = ? ";
                params.add(onlyForClass);
            }
        }
        return jdbcTemplate.query(
                "SELECT h.*, EXISTS(SELECT 1 FROM submissions s WHERE s.homework_id = h.id AND s.student_id = ?) AS done, " +
                        "(SELECT s.submit_time FROM submissions s WHERE s.homework_id = h.id AND s.student_id = ? LIMIT 1) AS submit_time, " +
                        "(SELECT s.teacher_grade FROM submissions s WHERE s.homework_id = h.id AND s.student_id = ? LIMIT 1) AS teacher_grade " +
                        "FROM homework h " + where + " ORDER BY h.created_at DESC",
                (rs, i) -> {
                    boolean done = rs.getBoolean("done");
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", rs.getString("id"));
                    m.put("subject", rs.getString("subject"));
                    m.put("subjectClass", rs.getString("subject_class"));
                    m.put("subjectIcon", rs.getString("subject_icon"));
                    m.put("title", rs.getString("title"));
                    m.put("teacher", rs.getString("teacher_name"));
                    m.put("deadline", rs.getString("deadline"));
                    m.put("type", rs.getString("type"));
                    m.put("questionCount", rs.getInt("question_count"));
                    m.put("difficultyText", rs.getString("difficulty_text"));
                    m.put("description", rs.getString("description"));
                    m.put("className", rs.getString("class_name"));
                    m.put("status", done ? "completed" : "pending");
                    m.put("isUrgent", !done && s(rs.getString("deadline")).contains("今日"));
                    if (done) m.put("score", 90);
                    m.put("submitTime", format(rs.getTimestamp("submit_time")));
                    String grade = rs.getString("teacher_grade");
                    m.put("teacherGrade", grade == null || grade.isBlank() ? null : grade.trim().toUpperCase());
                    return m;
                },
                params.toArray()
        );
    }

    private Map<String, Object> getHomeworkDetailForStudent(String homeworkId, String studentId, String studentClass) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
                "SELECT h.*, s.submit_time AS submitTime, s.teacher_grade AS teacherGrade, s.answer_json AS answerJson " +
                        "FROM homework h LEFT JOIN submissions s ON s.homework_id = h.id AND s.student_id = ? WHERE h.id = ? LIMIT 1",
                new Object[]{studentId, homeworkId},
                (rs, i) -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", rs.getString("id"));
                    m.put("title", rs.getString("title"));
                    m.put("subject", rs.getString("subject"));
                    m.put("subjectClass", rs.getString("subject_class"));
                    m.put("subjectIcon", rs.getString("subject_icon"));
                    m.put("teacher", rs.getString("teacher_name"));
                    m.put("className", rs.getString("class_name"));
                    m.put("classId", rs.getString("class_id"));
                    m.put("deadline", rs.getString("deadline"));
                    m.put("description", rs.getString("description"));
                    m.put("submitTime", rs.getTimestamp("submitTime"));
                    m.put("teacherGrade", rs.getString("teacherGrade"));
                    m.put("questionsJson", rs.getString("questions_json"));
                    m.put("answerJson", rs.getString("answerJson"));
                    return m;
                }
        );
        if (rows.isEmpty()) return null;
        Map<String, Object> row = rows.get(0);
        if (studentClass != null) {
            String cid = s(row.get("classId"));
            if (studentClass.isBlank() || cid.isBlank() || !studentClass.equals(cid)) return null;
        }
        List<Map<String, Object>> questions = parseList(s(row.get("questionsJson")));
        List<Map<String, Object>> answers = parseList(s(row.get("answerJson")));
        Timestamp submitTime = (Timestamp) row.get("submitTime");
        Map<String, Object> out = new HashMap<>();
        out.put("id", row.get("id"));
        out.put("title", row.get("title"));
        out.put("subject", row.get("subject"));
        out.put("subjectClass", row.get("subjectClass"));
        out.put("subjectIcon", row.get("subjectIcon"));
        out.put("teacher", row.get("teacher"));
        out.put("className", row.get("className"));
        out.put("deadline", row.get("deadline"));
        out.put("description", s(row.get("description")));
        out.put("status", submitTime != null ? "completed" : "pending");
        out.put("submitTime", format(submitTime));
        out.put("teacherGrade", row.get("teacherGrade"));
        out.put("questions", questions);
        out.put("answers", answers);
        return out;
    }

    private List<Map<String, Object>> listStudentMessages(String studentId, String classId) {
        return jdbcTemplate.query(
                "SELECT m.id, m.title, m.content, m.msg_type AS type, m.teacher_name AS teacher, m.created_at AS createdAt, " +
                        "EXISTS(SELECT 1 FROM student_message_reads r WHERE r.student_id = ? AND r.message_id = m.id) AS isRead " +
                        "FROM teacher_messages m WHERE m.class_id = ? ORDER BY m.created_at DESC LIMIT 50",
                (rs, i) -> Map.of(
                        "id", rs.getInt("id"),
                        "title", rs.getString("title"),
                        "content", s(rs.getString("content")),
                        "type", rs.getString("type") == null ? "notice" : rs.getString("type"),
                        "teacher", rs.getString("teacher"),
                        "time", rs.getTimestamp("createdAt"),
                        "read", rs.getBoolean("isRead")
                ),
                studentId, classId
        );
    }

    private void upsertSubmission(String homeworkId, String studentId, String answerJson) {
        jdbcTemplate.update(
                "INSERT INTO submissions (id, homework_id, student_id, submit_time, answer_json) VALUES (?, ?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE submit_time = VALUES(submit_time), answer_json = VALUES(answer_json)",
                UUID.randomUUID().toString(), homeworkId, studentId, Timestamp.valueOf(LocalDateTime.now()), answerJson
        );
    }

    private void storeHomeworkWrongQuestions(String studentUserId, String homeworkId, Map<String, Object> detail, List<Map<String, Object>> answers) {
        ensureWrongQuestionTable();
        jdbcTemplate.update(
                "DELETE FROM wrong_question_records WHERE source_type = 'homework' AND student_user_id = ? AND homework_id = ?",
                studentUserId, homeworkId
        );
        List<Map<String, Object>> questions = detail == null ? List.of() : (List<Map<String, Object>>) detail.getOrDefault("questions", List.of());
        if (questions.isEmpty()) return;

        Map<Integer, String> answerMap = new HashMap<>();
        for (Map<String, Object> a : answers == null ? List.<Map<String, Object>>of() : answers) {
            Object idx = a.get("index");
            int i = idx instanceof Number n ? n.intValue() : -1;
            if (i >= 0) answerMap.put(i, normalizeAnswerText(a.get("value")));
        }
        String subject = s(detail == null ? "" : detail.get("subject"));
        String grade = "";
        String knowledgePoint = s(detail == null ? "" : detail.get("title"));

        for (int i = 0; i < questions.size(); i++) {
            Map<String, Object> q = questions.get(i);
            String type = s(q.get("type"));
            if ("essay".equals(type)) continue;
            String ref = s(q.get("answer"));
            if (ref.isBlank()) continue;
            String studentAnswer = answerMap.getOrDefault(i, "");
            if (isCorrect(type, ref, studentAnswer)) continue;

            jdbcTemplate.update(
                    "INSERT INTO wrong_question_records (student_user_id, source_type, paper_id, homework_id, question_no, question_kind, subject, grade, knowledge_point, stem, student_answer, correct_answer) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    studentUserId,
                    "homework",
                    homeworkId,
                    homeworkId,
                    i + 1,
                    type,
                    subject,
                    grade,
                    knowledgePoint,
                    s(q.get("content")),
                    studentAnswer,
                    ref
            );
        }
    }

    private void ensureWrongQuestionTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS wrong_question_records (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  student_user_id VARCHAR(64) NOT NULL,
                  source_type VARCHAR(20) NOT NULL DEFAULT 'selftest',
                  paper_id VARCHAR(64) NOT NULL,
                  homework_id VARCHAR(64) NULL,
                  question_no INT NOT NULL,
                  question_kind VARCHAR(20) NOT NULL,
                  subject VARCHAR(50) NULL,
                  grade VARCHAR(30) NULL,
                  knowledge_point VARCHAR(120) NULL,
                  stem TEXT NOT NULL,
                  student_answer TEXT NULL,
                  correct_answer TEXT NULL,
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  INDEX idx_wrong_student_time (student_user_id, created_at),
                  INDEX idx_wrong_source_homework (source_type, homework_id),
                  INDEX idx_wrong_subject_grade (subject, grade)
                )
                """);
        // Some MySQL variants do not support "ADD COLUMN IF NOT EXISTS".
        if (!hasColumn("wrong_question_records", "source_type")) {
            jdbcTemplate.execute("ALTER TABLE wrong_question_records ADD COLUMN source_type VARCHAR(20) NOT NULL DEFAULT 'selftest'");
        }
        if (!hasColumn("wrong_question_records", "homework_id")) {
            jdbcTemplate.execute("ALTER TABLE wrong_question_records ADD COLUMN homework_id VARCHAR(64) NULL");
        }
    }

    private boolean hasColumn(String tableName, String columnName) {
        Integer c = queryOne(
                "SELECT COUNT(1) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class,
                tableName,
                columnName
        );
        return c != null && c > 0;
    }

    private Map<String, Object> queryHomeworkById(String homeworkId) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
                "SELECT id, title, class_name AS className, class_id AS classId, teacher_id AS teacherId, questions_json AS questionsJson FROM homework WHERE id = ? LIMIT 1",
                (rs, i) -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", rs.getString("id"));
                    m.put("title", rs.getString("title"));
                    m.put("className", rs.getString("className"));
                    m.put("classId", rs.getString("classId"));
                    m.put("teacherId", rs.getString("teacherId"));
                    m.put("questionsJson", rs.getString("questionsJson"));
                    return m;
                },
                homeworkId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, String> resolveStudentIdentityForHomeworkClass(String classId, String studentNoParam) {
        String studentNo = s(studentNoParam);
        String cid = s(classId);
        if (studentNo.isBlank() || cid.isBlank()) return null;

        List<Map<String, String>> inClass = jdbcTemplate.query(
                "SELECT st.id AS studentNo, st.name AS studentName, st.account_username AS accountUsername FROM students st " +
                        "WHERE st.class_id = ? AND (st.id = ? OR LOWER(TRIM(st.account_username)) = LOWER(TRIM(?))) LIMIT 1",
                (rs, i) -> Map.of(
                        "studentNo", s(rs.getString("studentNo")),
                        "studentName", s(rs.getString("studentName")),
                        "accountUsername", s(rs.getString("accountUsername"))
                ),
                cid, studentNo, studentNo
        );
        if (!inClass.isEmpty()) return inClass.get(0);

        List<Map<String, String>> fromUser = jdbcTemplate.query(
                "SELECT username AS studentNo, COALESCE(name, username) AS studentName, username AS accountUsername FROM users WHERE username = ? LIMIT 1",
                (rs, i) -> Map.of(
                        "studentNo", s(rs.getString("studentNo")),
                        "studentName", s(rs.getString("studentName")),
                        "accountUsername", s(rs.getString("accountUsername"))
                ),
                studentNo
        );
        return fromUser.isEmpty() ? null : fromUser.get(0);
    }

    private boolean teacherOwnsClass(String teacherId, String classId) {
        Integer out = queryOne("SELECT 1 FROM classes WHERE id = ? AND owner_teacher_id = ? LIMIT 1", Integer.class, classId, teacherId);
        return out != null;
    }

    private String getStudentClassId(String username) {
        return queryOne("SELECT class_id FROM students WHERE LOWER(TRIM(account_username)) = LOWER(TRIM(?)) LIMIT 1", String.class, username);
    }

    private String getStudentClassName(String username) {
        return queryOne("SELECT class_name FROM students WHERE LOWER(TRIM(account_username)) = LOWER(TRIM(?)) LIMIT 1", String.class, username);
    }

    private <T> T queryOne(String sql, Class<T> type, Object... args) {
        List<T> rows = jdbcTemplate.query(sql, (rs, i) -> rs.getObject(1, type), args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String s(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String fileExtension(String filename) {
        String name = s(filename).toLowerCase();
        int idx = name.lastIndexOf('.');
        if (idx < 0 || idx + 1 >= name.length()) return "";
        return name.substring(idx + 1);
    }

    private String extractDocxText(MultipartFile file) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(file.getBytes()))) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph p : doc.getParagraphs()) {
                String line = s(p.getText());
                if (!line.isBlank()) sb.append(line).append('\n');
            }
            return sb.toString();
        }
    }

    private List<Map<String, Object>> parseImportedQuestions(String rawText) {
        String normalized = s(rawText).replace("\r\n", "\n").replace('\r', '\n');
        if (normalized.isBlank()) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        Pattern questionStart = Pattern.compile("^\\s*(?:第\\s*\\d+\\s*题[\\.、:：]?|\\d+\\s*[\\.、\\)）]\\s*)(.+)$");
        Pattern optionLine = Pattern.compile("^\\s*([A-Da-d])[\\.．、\\):：]\\s*(.+)$");
        Pattern answerLine = Pattern.compile("^\\s*(?:答案|参考答案)\\s*[:：]\\s*(.+)$");
        Pattern scoreLine = Pattern.compile("^\\s*(?:分值|分数)\\s*[:：]\\s*(\\d+).*$");

        Map<String, Object> cur = null;
        List<String> curOptions = new ArrayList<>();
        StringBuilder contentBuf = new StringBuilder();
        String curAnswer = "";
        int curScore = 5;

        for (String raw : normalized.split("\n")) {
            String line = s(raw);
            if (line.isBlank()) continue;
            Matcher qMatcher = questionStart.matcher(line);
            if (qMatcher.matches()) {
                if (cur != null) {
                    finalizeImportedQuestion(out, cur, contentBuf.toString(), curOptions, curAnswer, curScore);
                }
                cur = new HashMap<>();
                curOptions = new ArrayList<>();
                contentBuf = new StringBuilder(s(qMatcher.group(1)));
                curAnswer = "";
                curScore = 5;
                continue;
            }
            if (cur == null) continue;
            Matcher optMatcher = optionLine.matcher(line);
            if (optMatcher.matches()) {
                String key = optMatcher.group(1).toUpperCase();
                String value = s(optMatcher.group(2));
                if (!value.isBlank()) curOptions.add(key + ". " + value);
                continue;
            }
            Matcher aMatcher = answerLine.matcher(line);
            if (aMatcher.matches()) {
                curAnswer = s(aMatcher.group(1));
                continue;
            }
            Matcher sMatcher = scoreLine.matcher(line);
            if (sMatcher.matches()) {
                try {
                    curScore = Math.max(1, Integer.parseInt(sMatcher.group(1)));
                } catch (Exception ignore) {
                    curScore = 5;
                }
                continue;
            }
            if (contentBuf.length() > 0) contentBuf.append('\n');
            contentBuf.append(line);
        }
        if (cur != null) {
            finalizeImportedQuestion(out, cur, contentBuf.toString(), curOptions, curAnswer, curScore);
        }
        return out.stream().limit(100).toList();
    }

    private void finalizeImportedQuestion(
            List<Map<String, Object>> out,
            Map<String, Object> cur,
            String content,
            List<String> options,
            String answer,
            int score
    ) {
        String c = s(content);
        if (c.isBlank()) return;
        String type = "essay";
        if (!options.isEmpty()) {
            type = "choice";
            cur.put("options", String.join("\n", options));
        } else if (c.contains("____") || c.contains("（ ）") || c.contains("()")) {
            type = "fill";
        }
        cur.put("type", type);
        cur.put("content", c);
        cur.put("answer", s(answer));
        cur.put("score", Math.max(1, score));
        out.add(cur);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<Map<String, Object>> parseList(String json) {
        try {
            return objectMapper.readValue(json == null || json.isBlank() ? "[]" : json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private String loginKey(String username) {
        return s(username).toLowerCase();
    }

    private String normalizeGrade(String g) {
        String v = s(g).toUpperCase();
        return List.of("A", "B", "C", "D").contains(v) ? v : null;
    }

    private Integer computeObjectiveAccuracyPercent(List<Map<String, Object>> questions, List<Map<String, Object>> answers) {
        Map<Integer, String> answerMap = new HashMap<>();
        for (Map<String, Object> a : answers) {
            Object idx = a.get("index");
            int i = idx instanceof Number n ? n.intValue() : -1;
            if (i >= 0) answerMap.put(i, normalizeAnswerText(a.get("value")));
        }
        int graded = 0;
        int correct = 0;
        for (int i = 0; i < questions.size(); i++) {
            Map<String, Object> q = questions.get(i);
            String type = s(q.get("type"));
            if ("essay".equals(type)) continue;
            String ref = s(q.get("answer"));
            if (ref.isBlank()) continue;
            graded++;
            if (isCorrect(type, ref, answerMap.getOrDefault(i, ""))) correct++;
        }
        if (graded == 0) return null;
        return Math.round((correct * 100f) / graded);
    }

    private boolean isCorrect(String type, String ref, String student) {
        if ("choice".equals(type)) {
            return normalizeAnswerText(ref).equals(normalizeAnswerText(student));
        }
        String[] refs = ref.split("/");
        String answer = normalizeAnswerText(student);
        for (String r : refs) {
            if (normalizeAnswerText(r).equals(answer)) return true;
        }
        return false;
    }

    private String normalizeAnswerText(Object value) {
        return s(value).replaceAll("\\s+", "").toLowerCase();
    }

    private String format(Timestamp t) {
        return t == null ? null : t.toLocalDateTime().format(DT);
    }
}
