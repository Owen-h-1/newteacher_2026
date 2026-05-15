package com.zkys.backend.dashboard;

import com.zkys.backend.security.UserPrincipal;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public DashboardService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> teacherStudentBoard(UserPrincipal me, String classIdRaw) {
        String classId = s(classIdRaw);
        if (!classId.isBlank() && !teacherOwnsClass(me.id(), classId)) {
            throw new IllegalArgumentException("无权查看该班级数据");
        }
        List<Map<String, Object>> homeworks = classId.isBlank()
                ? jdbcTemplate.query("SELECT id, class_name AS className, class_id AS classId, subject, questions_json AS questionsJson FROM homework WHERE teacher_id = ?",
                (rs, i) -> mapOf("id", rs.getString("id"), "className", rs.getString("className"), "classId", rs.getString("classId"), "subject", rs.getString("subject"), "questionsJson", rs.getString("questionsJson")), me.id())
                : jdbcTemplate.query("SELECT id, class_name AS className, class_id AS classId, subject, questions_json AS questionsJson FROM homework WHERE teacher_id = ? AND class_id = ?",
                (rs, i) -> mapOf("id", rs.getString("id"), "className", rs.getString("className"), "classId", rs.getString("classId"), "subject", rs.getString("subject"), "questionsJson", rs.getString("questionsJson")), me.id(), classId);

        List<String> classIds = classId.isBlank()
                ? homeworks.stream().map(h -> s(h.get("classId"))).filter(x -> !x.isBlank()).distinct().toList()
                : List.of(classId);

        int totalStudents = 0;
        Map<String, Integer> classSizeMap = new HashMap<>();
        for (String cid : classIds) {
            Integer c = queryOne("SELECT COUNT(1) FROM students WHERE class_id = ?", Integer.class, cid);
            int n = c == null ? 0 : c;
            classSizeMap.put(cid, n);
            totalStudents += n;
        }

        List<String> homeworkIds = homeworks.stream().map(h -> s(h.get("id"))).toList();
        List<Map<String, Object>> subRows = homeworkIds.isEmpty() ? List.of() : inQuerySubmissions(homeworkIds);

        int totalShould = 0;
        int totalSubmitted = 0;
        Map<String, Set<String>> submittedByHomework = new HashMap<>();
        for (Map<String, Object> r : subRows) {
            String hid = s(r.get("homeworkId"));
            submittedByHomework.computeIfAbsent(hid, k -> new HashSet<>()).add(s(r.get("studentId")));
        }
        for (Map<String, Object> h : homeworks) {
            String cid = s(h.get("classId"));
            totalShould += classSizeMap.getOrDefault(cid, 0);
            totalSubmitted += submittedByHomework.getOrDefault(s(h.get("id")), Set.of()).size();
        }
        int completionRate = totalShould == 0 ? 0 : Math.round(totalSubmitted * 100f / totalShould);

        List<Map<String, Object>> weekData = buildWeekData(subRows);
        int avgStudyHours = calcAvgStudyHours(subRows);

        Map<String, Object> weakData = buildWeakDataFromWrongRecordsForBoard(me.id(), classId, classIds, 30);
        if (((List<?>) weakData.getOrDefault("weakPoints", List.of())).isEmpty()) {
            weakData = buildWeakData(homeworks, subRows);
            weakData.put("source", "submitted-homework-answers");
        }
        String weakPointSource = s(weakData.get("source"));
        if (weakPointSource.isBlank()) {
            weakPointSource = "submitted-homework-answers";
        }
        int avgAccuracy = (int) weakData.get("avgAccuracy");
        List<Map<String, Object>> weakPoints = (List<Map<String, Object>>) weakData.get("weakPoints");
        List<Map<String, Object>> classWeakness = (List<Map<String, Object>>) weakData.get("classWeakness");

        List<Map<String, Object>> studentActivities = buildStudentActivities(homeworks, classIds, subRows);
        return Map.of(
                "stats", Map.of("totalStudents", totalStudents, "completionRate", completionRate, "avgStudyHours", avgStudyHours, "avgAccuracy", avgAccuracy),
                "weakPoints", weakPoints,
                "weekData", weekData,
                "classWeakness", classWeakness,
                "studentActivities", studentActivities,
                "weakPointSource", weakPointSource
        );
    }

    /**
     * 老师看板：从错题库聚合薄弱点（优先）。
     */
    private Map<String, Object> buildWeakDataFromWrongRecordsForBoard(String teacherId, String classId, List<String> classIds, int days) {
        List<Map<String, Object>> rows;
        try {
            if (!s(classId).isBlank()) {
                rows = jdbcTemplate.query(
                        """
                        SELECT s.class_name AS className,
                               COALESCE(NULLIF(TRIM(w.knowledge_point), ''), NULLIF(TRIM(w.subject), ''), '基础能力') AS knowledgeName,
                               COUNT(1) AS wrongCount
                        FROM wrong_question_records w
                        INNER JOIN users u ON u.id = w.student_user_id
                        INNER JOIN students s ON s.account_username = u.username
                        INNER JOIN classes c ON c.id = s.class_id
                        WHERE c.owner_teacher_id = ?
                          AND c.id = ?
                          AND w.created_at >= DATE_SUB(NOW(), INTERVAL ? DAY)
                        GROUP BY s.class_name, knowledgeName
                        """,
                        (rs, i) -> mapOf(
                                "className", rs.getString("className"),
                                "name", rs.getString("knowledgeName"),
                                "wrongCount", rs.getInt("wrongCount")
                        ),
                        teacherId, classId, days
                );
            } else {
                rows = jdbcTemplate.query(
                        """
                        SELECT s.class_name AS className,
                               COALESCE(NULLIF(TRIM(w.knowledge_point), ''), NULLIF(TRIM(w.subject), ''), '基础能力') AS knowledgeName,
                               COUNT(1) AS wrongCount
                        FROM wrong_question_records w
                        INNER JOIN users u ON u.id = w.student_user_id
                        INNER JOIN students s ON s.account_username = u.username
                        INNER JOIN classes c ON c.id = s.class_id
                        WHERE c.owner_teacher_id = ?
                          AND w.created_at >= DATE_SUB(NOW(), INTERVAL ? DAY)
                        GROUP BY s.class_name, knowledgeName
                        """,
                        (rs, i) -> mapOf(
                                "className", rs.getString("className"),
                                "name", rs.getString("knowledgeName"),
                                "wrongCount", rs.getInt("wrongCount")
                        ),
                        teacherId, days
                );
            }
        } catch (Exception e) {
            return mapOf("avgAccuracy", 0, "weakPoints", List.of(), "classWeakness", List.of(), "source", "wrong-question-records");
        }
        if (rows.isEmpty()) {
            return mapOf("avgAccuracy", 0, "weakPoints", List.of(), "classWeakness", List.of(), "source", "wrong-question-records");
        }

        Map<String, Integer> classWrongTotals = new HashMap<>();
        for (Map<String, Object> r : rows) {
            String cls = s(r.get("className"));
            classWrongTotals.put(cls, classWrongTotals.getOrDefault(cls, 0) + intVal(r.get("wrongCount")));
        }
        int totalWrong = 0;
        for (Integer v : classWrongTotals.values()) totalWrong += v;

        String[] palette = {"#ff9f1c", "#1e6df2", "#2eb85c", "#6f42c1", "#e83e8c"};
        List<Map<String, Object>> weakPoints = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            String cls = s(r.get("className"));
            int classTotal = classWrongTotals.getOrDefault(cls, 0);
            int wrong = intVal(r.get("wrongCount"));
            int rate = classTotal == 0 ? 0 : Math.round(wrong * 100f / classTotal);
            weakPoints.add(mapOf(
                    "name", r.get("name"),
                    "sub", "错题库 · " + cls,
                    "errorRate", rate,
                    "wrongCount", wrong
            ));
        }
        weakPoints.sort((a, b) -> Integer.compare(intVal(b.get("errorRate")), intVal(a.get("errorRate"))));
        if (weakPoints.size() > 5) weakPoints = weakPoints.subList(0, 5);
        for (int i = 0; i < weakPoints.size(); i++) weakPoints.get(i).put("color", palette[i % palette.length]);

        List<Map<String, Object>> classWeakness = new ArrayList<>();
        int idx = 0;
        for (Map.Entry<String, Integer> e : classWrongTotals.entrySet()) {
            int weakness = totalWrong == 0 ? 0 : Math.round(e.getValue() * 100f / totalWrong);
            classWeakness.add(mapOf(
                    "className", e.getKey(),
                    "weakness", weakness,
                    "color", palette[idx++ % palette.length],
                    "level", weakness >= 40 ? "high" : (weakness >= 25 ? "medium" : "low")
            ));
        }
        classWeakness.sort((a, b) -> Integer.compare(intVal(b.get("weakness")), intVal(a.get("weakness"))));

        int avgAccuracy = totalWrong == 0 ? 0 : Math.max(0, 100 - Math.min(100, totalWrong));
        return mapOf(
                "avgAccuracy", avgAccuracy,
                "weakPoints", weakPoints,
                "classWeakness", classWeakness,
                "source", "wrong-question-records"
        );
    }

    public Map<String, Object> teacherOverview(UserPrincipal me) {
        List<Map<String, Object>> classes = jdbcTemplate.query(
                "SELECT id AS classId, name AS className FROM classes WHERE owner_teacher_id = ? ORDER BY name",
                (rs, i) -> mapOf("classId", rs.getString("classId"), "className", rs.getString("className")),
                me.id()
        );
        List<Map<String, Object>> classSummary = new ArrayList<>();
        int totalStudents = 0;
        int totalSigned = 0;
        for (Map<String, Object> c : classes) {
            String cid = s(c.get("classId"));
            List<Map<String, Object>> records = signRecords(cid);
            int should = records.size();
            int arrived = (int) records.stream().filter(r -> !s(r.get("time")).isBlank()).count();
            totalStudents += should;
            totalSigned += arrived;
            classSummary.add(mapOf(
                    "classId", cid,
                    "className", c.get("className"),
                    "should", should,
                    "arrived", arrived,
                    "absent", should - arrived,
                    "rate", should == 0 ? 0 : Math.round(arrived * 100f / should)
            ));
        }
        Integer pendingReview = queryOne("SELECT COUNT(1) FROM homework WHERE teacher_id = ?", Integer.class, me.id());
        int jsDay = LocalDate.now().getDayOfWeek().getValue();
        int weekday = jsDay == 7 ? 7 : jsDay;
        List<Map<String, Object>> schedulesInDb = jdbcTemplate.query(
                "SELECT weekday, start_time AS startTime, end_time AS endTime, lesson_name AS lessonName, class_name AS className FROM class_schedules WHERE weekday = ? ORDER BY start_time",
                (rs, i) -> mapOf("startTime", rs.getString("startTime"), "endTime", rs.getString("endTime"), "lessonName", rs.getString("lessonName"), "className", rs.getString("className")),
                weekday
        );
        List<Map<String, Object>> todos = listTodos(me.id());
        if (todos.isEmpty()) {
            jdbcTemplate.update("INSERT INTO teacher_todos (teacher_id, title, meta, urgent, is_done, created_at) VALUES (?, ?, ?, 0, 0, NOW())", me.id(), "准备明日课程课件", "明日 10:00 前完成");
            jdbcTemplate.update("INSERT INTO teacher_todos (teacher_id, title, meta, urgent, is_done, created_at) VALUES (?, ?, ?, ?, 0, NOW())", me.id(), "查看学生薄弱点报告", "建议今日完成", totalSigned < totalStudents ? 1 : 0);
            todos = listTodos(me.id());
        }
        int todayCourses = schedulesInDb.size();
        int finishedCourses = (int) Math.floor(todayCourses / 2.0);
        Map<String, Map<String, Object>> classSummaryMap = new HashMap<>();
        for (Map<String, Object> c : classSummary) classSummaryMap.put(s(c.get("className")), c);
        List<Map<String, Object>> schedules = new ArrayList<>();
        for (int i = 0; i < schedulesInDb.size(); i++) {
            Map<String, Object> sc = schedulesInDb.get(i);
            Map<String, Object> cls = classSummaryMap.getOrDefault(s(sc.get("className")), Map.of("should", 0));
            schedules.add(mapOf(
                    "time", s(sc.get("startTime")) + "-" + s(sc.get("endTime")),
                    "name", "小学数学 · " + s(sc.get("lessonName")),
                    "classText", s(sc.get("className")) + " · " + cls.get("should") + "人",
                    "status", i < finishedCourses ? "finished" : "waiting",
                    "statusText", i < finishedCourses ? "已完成" : "待上课"
            ));
        }
        List<Map<String, Object>> warnings = teacherWeakWarningsFromWrongRecords(me.id(), 30, 1, 1);
        String warningBasedOn = "wrong-question-records";
        if (warnings.isEmpty()) {
            warnings = teacherWeakWarnings(me.id(), 30, 8, 30);
            warningBasedOn = "submitted-homework-answers";
        }
        if (warnings.isEmpty()) {
            warnings = weakWarningsFromWrongRecordsGlobal(30, 1, 1);
            warningBasedOn = "wrong-question-records-global";
        }
        if (warnings.isEmpty()) {
            Map<String, Object> board = teacherStudentBoard(me, "");
            List<Map<String, Object>> weakPoints = (List<Map<String, Object>>) board.getOrDefault("weakPoints", List.of());
            List<Map<String, Object>> boardFallback = new ArrayList<>();
            for (Map<String, Object> p : weakPoints) {
                String sub = s(p.get("sub"));
                String className = sub.contains("·") ? s(sub.substring(sub.indexOf("·") + 1)) : "综合";
                boardFallback.add(mapOf(
                        "name", s(p.get("name")),
                        "className", className.isBlank() ? "综合" : className,
                        "errorRate", intVal(p.get("errorRate")),
                        "attemptCount", 0,
                        "wrongCount", 0,
                        "affectedStudents", 0,
                        "dataRange", "近30天",
                        "sourceType", "student-board-weak-points"
                ));
            }
            warnings = boardFallback.size() > 8 ? boardFallback.subList(0, 8) : boardFallback;
            warningBasedOn = "student-board-weak-points";
        }
        return Map.of(
                "teacher", Map.of("name", s(me.name()).isBlank() ? "老师" : me.name()),
                "stats", mapOf(
                        "todayCourses", todayCourses,
                        "finishedCourses", finishedCourses,
                        "totalStudents", totalStudents,
                        "classCount", classes.size(),
                        "pendingReview", pendingReview == null ? 0 : pendingReview,
                        "todoCount", todos.size(),
                        "urgentTodo", (int) todos.stream().filter(t -> Boolean.TRUE.equals(t.get("urgent"))).count()
                ),
                "todos", todos,
                "schedules", schedules,
                "classSummary", classSummary,
                "warnings", warnings,
                "warningSource", Map.of("mode", "rules", "basedOn", warningBasedOn, "dataRange", "近30天")
        );
    }

    /**
     * 优先使用错题库(wrong_question_records)统计老师端薄弱点预警。
     * errorRate 在这里表示该班级错题中该知识点的占比（用于薄弱点排序）。
     */
    private List<Map<String, Object>> teacherWeakWarningsFromWrongRecords(String teacherId, int days, int minWrongCount, int minRate) {
        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.query(
                    """
                    SELECT s.class_name AS className,
                           COALESCE(NULLIF(TRIM(w.knowledge_point), ''), NULLIF(TRIM(w.subject), ''), '基础能力') AS knowledgeName,
                           COUNT(1) AS wrongCount,
                           COUNT(DISTINCT w.student_user_id) AS affectedStudents
                    FROM wrong_question_records w
                    INNER JOIN users u ON u.id = w.student_user_id
                    INNER JOIN students s ON s.account_username = u.username
                    INNER JOIN classes c ON c.id = s.class_id
                    WHERE c.owner_teacher_id = ?
                      AND w.created_at >= DATE_SUB(NOW(), INTERVAL ? DAY)
                    GROUP BY s.class_name, knowledgeName
                    """,
                    (rs, i) -> mapOf(
                            "className", rs.getString("className"),
                            "name", rs.getString("knowledgeName"),
                            "wrongCount", rs.getInt("wrongCount"),
                            "affectedStudents", rs.getInt("affectedStudents")
                    ),
                    teacherId, days
            );
        } catch (Exception e) {
            return List.of();
        }
        if (rows.isEmpty()) return List.of();

        Map<String, Integer> classTotals = new HashMap<>();
        for (Map<String, Object> r : rows) {
            String className = s(r.get("className"));
            classTotals.put(className, classTotals.getOrDefault(className, 0) + intVal(r.get("wrongCount")));
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            int wrongCount = intVal(r.get("wrongCount"));
            if (wrongCount < minWrongCount) continue;
            String className = s(r.get("className"));
            int totalInClass = classTotals.getOrDefault(className, 0);
            int rate = totalInClass == 0 ? 0 : Math.round(wrongCount * 100f / totalInClass);
            if (rate < minRate) continue;
            out.add(mapOf(
                    "name", r.get("name"),
                    "className", className,
                    "errorRate", rate,
                    "attemptCount", totalInClass,
                    "wrongCount", wrongCount,
                    "affectedStudents", intVal(r.get("affectedStudents")),
                    "dataRange", "近" + days + "天",
                    "sourceType", "wrong-question-records"
            ));
        }
        out.sort((a, b) -> Integer.compare(intVal(b.get("errorRate")), intVal(a.get("errorRate"))));
        return out.size() > 8 ? out.subList(0, 8) : out;
    }

    private List<Map<String, Object>> teacherWeakWarnings(String teacherId, int days, int minAttempts, int minWrongRate) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
                "SELECT h.class_name AS className, h.questions_json AS questionsJson, s.student_id AS studentId, s.answer_json AS answerJson FROM homework h INNER JOIN submissions s ON s.homework_id = h.id WHERE h.teacher_id = ? AND s.submit_time >= DATE_SUB(NOW(), INTERVAL ? DAY)",
                (rs, i) -> mapOf("className", rs.getString("className"), "questionsJson", rs.getString("questionsJson"), "studentId", rs.getString("studentId"), "answerJson", rs.getString("answerJson")),
                teacherId, days
        );
        Map<String, Map<String, Object>> agg = new HashMap<>();
        for (Map<String, Object> r : rows) {
            List<Map<String, Object>> questions = parseList(s(r.get("questionsJson")));
            List<Map<String, Object>> answers = parseList(s(r.get("answerJson")));
            Map<Integer, String> answerMap = new HashMap<>();
            for (Map<String, Object> a : answers) answerMap.put(intVal(a.get("index")), s(a.get("value")));
            for (int i = 0; i < questions.size(); i++) {
                Map<String, Object> q = questions.get(i);
                if ("essay".equals(s(q.get("type")))) continue;
                String knowledge = inferKnowledge(q);
                String key = s(r.get("className")) + "__" + knowledge;
                Map<String, Object> st = agg.getOrDefault(key, mapOf("className", r.get("className"), "name", knowledge, "attemptCount", 0, "wrongCount", 0, "students", new HashSet<String>()));
                st.put("attemptCount", intVal(st.get("attemptCount")) + 1);
                if (!isCorrect(q, answerMap.getOrDefault(i, ""))) {
                    st.put("wrongCount", intVal(st.get("wrongCount")) + 1);
                    ((Set<String>) st.get("students")).add(s(r.get("studentId")));
                }
                agg.put(key, st);
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> x : agg.values()) {
            int attempt = intVal(x.get("attemptCount"));
            int wrong = intVal(x.get("wrongCount"));
            int rate = attempt == 0 ? 0 : Math.round(wrong * 100f / attempt);
            if (attempt >= minAttempts && rate >= minWrongRate) {
                out.add(mapOf("name", x.get("name"), "className", x.get("className"), "errorRate", rate, "attemptCount", attempt, "wrongCount", wrong, "affectedStudents", ((Set<?>) x.get("students")).size(), "dataRange", "近" + days + "天"));
            }
        }
        out.sort((a, b) -> Integer.compare(intVal(b.get("errorRate")), intVal(a.get("errorRate"))));
        return out.size() > 8 ? out.subList(0, 8) : out;
    }

    /**
     * 当老师维度统计不到数据时，回退展示全库错题统计，避免老师端空白。
     */
    private List<Map<String, Object>> weakWarningsFromWrongRecordsGlobal(int days, int minWrongCount, int minRate) {
        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.query(
                    """
                    SELECT COALESCE(NULLIF(TRIM(s.class_name), ''), '未知班级') AS className,
                           COALESCE(NULLIF(TRIM(w.knowledge_point), ''), NULLIF(TRIM(w.subject), ''), '基础能力') AS knowledgeName,
                           COUNT(1) AS wrongCount,
                           COUNT(DISTINCT w.student_user_id) AS affectedStudents
                    FROM wrong_question_records w
                    LEFT JOIN users u ON u.id = w.student_user_id
                    LEFT JOIN students s ON s.account_username = u.username
                    WHERE w.created_at >= DATE_SUB(NOW(), INTERVAL ? DAY)
                    GROUP BY className, knowledgeName
                    """,
                    (rs, i) -> mapOf(
                            "className", rs.getString("className"),
                            "name", rs.getString("knowledgeName"),
                            "wrongCount", rs.getInt("wrongCount"),
                            "affectedStudents", rs.getInt("affectedStudents")
                    ),
                    days
            );
        } catch (Exception e) {
            return List.of();
        }
        if (rows.isEmpty()) return List.of();
        Map<String, Integer> classTotals = new HashMap<>();
        for (Map<String, Object> r : rows) {
            String className = s(r.get("className"));
            classTotals.put(className, classTotals.getOrDefault(className, 0) + intVal(r.get("wrongCount")));
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            int wrongCount = intVal(r.get("wrongCount"));
            if (wrongCount < minWrongCount) continue;
            String className = s(r.get("className"));
            int totalInClass = classTotals.getOrDefault(className, 0);
            int rate = totalInClass == 0 ? 0 : Math.round(wrongCount * 100f / totalInClass);
            if (rate < minRate) continue;
            out.add(mapOf(
                    "name", r.get("name"),
                    "className", className,
                    "errorRate", rate,
                    "attemptCount", totalInClass,
                    "wrongCount", wrongCount,
                    "affectedStudents", intVal(r.get("affectedStudents")),
                    "dataRange", "近" + days + "天",
                    "sourceType", "wrong-question-records-global"
            ));
        }
        out.sort((a, b) -> Integer.compare(intVal(b.get("errorRate")), intVal(a.get("errorRate"))));
        return out.size() > 8 ? out.subList(0, 8) : out;
    }

    private List<Map<String, Object>> listTodos(String teacherId) {
        return jdbcTemplate.query(
                "SELECT id, title, meta, urgent, is_done AS isDone, created_at AS createdAt FROM teacher_todos WHERE teacher_id = ? ORDER BY is_done ASC, urgent DESC, created_at DESC",
                (rs, i) -> mapOf("id", rs.getInt("id"), "title", rs.getString("title"), "meta", rs.getString("meta") == null ? "" : rs.getString("meta"), "urgent", rs.getInt("urgent") == 1, "done", rs.getInt("isDone") == 1, "createdAt", rs.getTimestamp("createdAt")),
                teacherId
        );
    }

    private List<Map<String, Object>> signRecords(String classId) {
        return jdbcTemplate.query(
                "SELECT s.id AS studentId, s.name, r.time, r.method FROM students s LEFT JOIN sign_records r ON r.class_id = s.class_id AND r.student_id = s.id WHERE s.class_id = ? ORDER BY s.id",
                (rs, i) -> mapOf("studentId", rs.getString("studentId"), "name", rs.getString("name"), "time", rs.getString("time"), "method", rs.getString("method")),
                classId
        );
    }

    private List<Map<String, Object>> buildStudentActivities(List<Map<String, Object>> homeworks, List<String> classIds, List<Map<String, Object>> subRows) {
        if (classIds.isEmpty()) return List.of();
        List<Map<String, Object>> students = inQueryStudents(classIds);
        Map<String, String> usernameToUserId = new HashMap<>();
        List<String> usernames = students.stream().map(s -> s(s.get("accountUsername"))).filter(x -> !x.isBlank()).toList();
        if (!usernames.isEmpty()) {
            for (Map<String, Object> u : inQueryUsersByUsernames(usernames)) {
                usernameToUserId.put(s(u.get("username")), s(u.get("userId")));
            }
        }
        Map<String, Map<String, Object>> submitAggByUser = new HashMap<>();
        Map<String, String> hwTitle = new HashMap<>();
        for (Map<String, Object> h : homeworks) hwTitle.put(s(h.get("id")), s(h.get("title")));
        for (Map<String, Object> r : subRows) {
            String uid = s(r.get("studentId"));
            Map<String, Object> old = submitAggByUser.getOrDefault(uid, mapOf("submittedCount", 0, "latestSubmitAt", "", "latestHomeworkTitle", ""));
            old.put("submittedCount", intVal(old.get("submittedCount")) + 1);
            Timestamp ts = (Timestamp) r.get("submitTime");
            String prev = s(old.get("latestSubmitAt"));
            if (prev.isBlank() || (ts != null && ts.toString().compareTo(prev) >= 0)) {
                old.put("latestSubmitAt", ts == null ? "" : ts.toString());
                old.put("latestHomeworkTitle", hwTitle.getOrDefault(s(r.get("homeworkId")), "作业"));
            }
            submitAggByUser.put(uid, old);
        }
        List<Map<String, Object>> signs = inQuerySigns(classIds);
        Map<String, Map<String, Object>> signMap = new HashMap<>();
        for (Map<String, Object> s : signs) signMap.put(s(s.get("classId")) + "__" + s(s.get("studentNo")), s);
        Map<String, Integer> homeworkCountByClass = new HashMap<>();
        for (Map<String, Object> h : homeworks) homeworkCountByClass.put(s(h.get("classId")), homeworkCountByClass.getOrDefault(s(h.get("classId")), 0) + 1);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> st : students) {
            String uid = usernameToUserId.getOrDefault(s(st.get("accountUsername")), "");
            Map<String, Object> sub = submitAggByUser.getOrDefault(uid, mapOf("submittedCount", 0, "latestSubmitAt", "", "latestHomeworkTitle", ""));
            String cid = s(st.get("classId"));
            Map<String, Object> sign = signMap.get(cid + "__" + s(st.get("studentNo")));
            String latestAction = !s(sub.get("latestSubmitAt")).isBlank() ? "提交作业《" + s(sub.get("latestHomeworkTitle")) + "》" :
                    (sign != null && !s(sign.get("time")).isBlank() ? "课堂签到（" + (s(sign.get("method")).isBlank() ? "手动" : s(sign.get("method"))) + "）" : "暂无学习记录");
            out.add(mapOf(
                    "studentNo", st.get("studentNo"),
                    "studentName", st.get("studentName"),
                    "className", st.get("className"),
                    "submittedCount", sub.get("submittedCount"),
                    "totalHomework", homeworkCountByClass.getOrDefault(cid, 0),
                    "signStatus", sign != null && !s(sign.get("time")).isBlank() ? "已签到" : "未签到",
                    "latestAction", latestAction,
                    "latestTime", !s(sub.get("latestSubmitAt")).isBlank() ? s(sub.get("latestSubmitAt")) : (sign == null ? "" : s(sign.get("time")))
            ));
        }
        return out;
    }

    private Map<String, Object> buildWeakData(List<Map<String, Object>> homeworks, List<Map<String, Object>> subRows) {
        Map<String, Map<String, Object>> homeworkMap = new HashMap<>();
        for (Map<String, Object> h : homeworks) homeworkMap.put(s(h.get("id")), h);
        Map<String, List<Map<String, Object>>> questionCache = new HashMap<>();
        Map<String, Map<String, Object>> knowledgeAgg = new HashMap<>();
        Map<String, Map<String, Object>> classAgg = new HashMap<>();
        int totalAttempts = 0;
        int totalCorrect = 0;
        for (Map<String, Object> r : subRows) {
            Map<String, Object> hw = homeworkMap.get(s(r.get("homeworkId")));
            if (hw == null) continue;
            List<Map<String, Object>> questions = questionCache.computeIfAbsent(s(r.get("homeworkId")), x -> parseList(s(hw.get("questionsJson"))));
            List<Map<String, Object>> answers = parseList(s(r.get("answerJson")));
            Map<Integer, String> answerMap = new HashMap<>();
            for (Map<String, Object> a : answers) answerMap.put(intVal(a.get("index")), s(a.get("value")));
            for (int i = 0; i < questions.size(); i++) {
                Map<String, Object> q = questions.get(i);
                if ("essay".equals(s(q.get("type")))) continue;
                totalAttempts++;
                boolean ok = isCorrect(q, answerMap.getOrDefault(i, ""));
                if (ok) totalCorrect++;
                String knowledge = inferKnowledge(q);
                String kk = knowledge + "__" + s(hw.get("className"));
                Map<String, Object> ks = knowledgeAgg.getOrDefault(kk, mapOf("name", knowledge, "sub", s(hw.get("subject")) + " · " + s(hw.get("className")), "errorCount", 0, "attempts", 0));
                ks.put("attempts", intVal(ks.get("attempts")) + 1);
                if (!ok) ks.put("errorCount", intVal(ks.get("errorCount")) + 1);
                knowledgeAgg.put(kk, ks);
                String ck = s(hw.get("className"));
                Map<String, Object> cs = classAgg.getOrDefault(ck, mapOf("className", ck, "errorCount", 0, "attempts", 0));
                cs.put("attempts", intVal(cs.get("attempts")) + 1);
                if (!ok) cs.put("errorCount", intVal(cs.get("errorCount")) + 1);
                classAgg.put(ck, cs);
            }
        }
        String[] palette = {"#ff9f1c", "#1e6df2", "#2eb85c", "#6f42c1", "#e83e8c"};
        List<Map<String, Object>> weakPoints = new ArrayList<>();
        for (Map<String, Object> x : knowledgeAgg.values()) {
            int attempts = intVal(x.get("attempts"));
            weakPoints.add(mapOf("name", x.get("name"), "sub", x.get("sub"), "errorRate", attempts == 0 ? 0 : Math.round(intVal(x.get("errorCount")) * 100f / attempts)));
        }
        weakPoints.sort((a, b) -> Integer.compare(intVal(b.get("errorRate")), intVal(a.get("errorRate"))));
        if (weakPoints.size() > 5) weakPoints = weakPoints.subList(0, 5);
        for (int i = 0; i < weakPoints.size(); i++) weakPoints.get(i).put("color", palette[i % palette.length]);
        List<Map<String, Object>> classWeakness = new ArrayList<>();
        int idx = 0;
        for (Map<String, Object> x : classAgg.values()) {
            int attempts = intVal(x.get("attempts"));
            int weakness = attempts == 0 ? 0 : Math.round(intVal(x.get("errorCount")) * 100f / attempts);
            classWeakness.add(mapOf("className", x.get("className"), "weakness", weakness, "color", palette[idx++ % palette.length], "level", weakness >= 40 ? "high" : (weakness >= 25 ? "medium" : "low")));
        }
        classWeakness.sort((a, b) -> Integer.compare(intVal(b.get("weakness")), intVal(a.get("weakness"))));
        int avgAccuracy = totalAttempts == 0 ? 0 : Math.round(totalCorrect * 100f / totalAttempts);
        return mapOf("avgAccuracy", avgAccuracy, "weakPoints", weakPoints, "classWeakness", classWeakness, "source", "submitted-homework-answers");
    }

    private List<Map<String, Object>> buildWeekData(List<Map<String, Object>> subRows) {
        String[] days = {"日", "一", "二", "三", "四", "五", "六"};
        List<LocalDate> keys = new ArrayList<>();
        for (int i = 6; i >= 0; i--) keys.add(LocalDate.now().minusDays(i));
        Map<LocalDate, Set<String>> dayStudentSet = new HashMap<>();
        for (LocalDate d : keys) dayStudentSet.put(d, new HashSet<>());
        for (Map<String, Object> r : subRows) {
            Timestamp t = (Timestamp) r.get("submitTime");
            if (t == null) continue;
            LocalDate d = t.toLocalDateTime().toLocalDate();
            if (dayStudentSet.containsKey(d)) dayStudentSet.get(d).add(s(r.get("studentId")));
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (LocalDate d : keys) out.add(mapOf("day", days[d.getDayOfWeek().getValue() % 7], "value", dayStudentSet.get(d).size()));
        return out;
    }

    private int calcAvgStudyHours(List<Map<String, Object>> subRows) {
        int weekSubmitCount = 0;
        Set<String> active = new HashSet<>();
        LocalDate min = LocalDate.now().minusDays(6);
        for (Map<String, Object> r : subRows) {
            Timestamp t = (Timestamp) r.get("submitTime");
            if (t == null) continue;
            LocalDate d = t.toLocalDateTime().toLocalDate();
            if (d.isBefore(min)) continue;
            weekSubmitCount++;
            active.add(s(r.get("studentId")));
        }
        if (active.isEmpty()) return 0;
        return Math.round((weekSubmitCount / (float) active.size()) * 6f) / 10;
    }

    private List<Map<String, Object>> inQuerySubmissions(List<String> ids) {
        String in = String.join(",", ids.stream().map(x -> "?").toList());
        return jdbcTemplate.query(
                "SELECT homework_id AS homeworkId, student_id AS studentId, submit_time AS submitTime, answer_json AS answerJson FROM submissions WHERE homework_id IN (" + in + ")",
                (rs, i) -> mapOf("homeworkId", rs.getString("homeworkId"), "studentId", rs.getString("studentId"), "submitTime", rs.getTimestamp("submitTime"), "answerJson", rs.getString("answerJson")),
                ids.toArray()
        );
    }

    private List<Map<String, Object>> inQueryStudents(List<String> classIds) {
        String in = String.join(",", classIds.stream().map(x -> "?").toList());
        return jdbcTemplate.query(
                "SELECT id AS studentNo, name AS studentName, class_name AS className, class_id AS classId, account_username AS accountUsername FROM students WHERE class_id IN (" + in + ") ORDER BY class_name, id",
                (rs, i) -> mapOf("studentNo", rs.getString("studentNo"), "studentName", rs.getString("studentName"), "className", rs.getString("className"), "classId", rs.getString("classId"), "accountUsername", rs.getString("accountUsername")),
                classIds.toArray()
        );
    }

    private List<Map<String, Object>> inQueryUsersByUsernames(List<String> usernames) {
        String in = String.join(",", usernames.stream().map(x -> "?").toList());
        return jdbcTemplate.query(
                "SELECT id AS userId, username FROM users WHERE username IN (" + in + ")",
                (rs, i) -> mapOf("userId", rs.getString("userId"), "username", rs.getString("username")),
                usernames.toArray()
        );
    }

    private List<Map<String, Object>> inQuerySigns(List<String> classIds) {
        String in = String.join(",", classIds.stream().map(x -> "?").toList());
        return jdbcTemplate.query(
                "SELECT class_id AS classId, student_id AS studentNo, time, method FROM sign_records WHERE class_id IN (" + in + ")",
                (rs, i) -> mapOf("classId", rs.getString("classId"), "studentNo", rs.getString("studentNo"), "time", rs.getString("time"), "method", rs.getString("method")),
                classIds.toArray()
        );
    }

    private boolean teacherOwnsClass(String teacherId, String classId) {
        Integer ok = queryOne("SELECT 1 FROM classes WHERE id = ? AND owner_teacher_id = ? LIMIT 1", Integer.class, classId, teacherId);
        return ok != null;
    }

    private boolean isCorrect(Map<String, Object> q, String ans) {
        String ref = s(q.get("answer"));
        if (ref.isBlank()) return false;
        if ("choice".equals(s(q.get("type")))) return normalize(ans).equals(normalize(ref));
        for (String x : ref.split("/")) if (normalize(x).equals(normalize(ans))) return true;
        return false;
    }

    private String inferKnowledge(Map<String, Object> q) {
        String text = s(q.get("knowledge")) + " " + s(q.get("content"));
        if (text.contains("乘法")) return "两位数乘法";
        if (text.contains("除法")) return "除法竖式";
        if (text.contains("应用题") || text.contains("审题")) return "应用题审题";
        if (text.contains("口算")) return "口算训练";
        return "基础计算";
    }

    private String normalize(String v) {
        return s(v).replaceAll("\\s+", "").toLowerCase();
    }

    private List<Map<String, Object>> parseList(String json) {
        try {
            return objectMapper.readValue((json == null || json.isBlank()) ? "[]" : json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private <T> T queryOne(String sql, Class<T> type, Object... args) {
        List<T> rows = jdbcTemplate.query(sql, (rs, i) -> rs.getObject(1, type), args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(String.valueOf(kv[i]), kv[i + 1]);
        return m;
    }

    private int intVal(Object v) {
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return 0;
        }
    }

    private String s(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }
}
