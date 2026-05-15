package com.zkys.backend.learning;

import com.zkys.backend.security.UserPrincipal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LearningService {
    private final JdbcTemplate jdbcTemplate;

    public LearningService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> selftestExercises(UserPrincipal me) {
        return Map.of("list", getExercises(me.id()));
    }

    @Transactional
    public Map<String, Object> toggleCollect(UserPrincipal me, int exerciseId) {
        Integer exists = queryOne("SELECT 1 FROM exercise_collections WHERE student_id = ? AND exercise_id = ? LIMIT 1", Integer.class, me.id(), exerciseId);
        if (exists != null) {
            jdbcTemplate.update("DELETE FROM exercise_collections WHERE student_id = ? AND exercise_id = ?", me.id(), exerciseId);
            return Map.of("collected", false);
        }
        jdbcTemplate.update("INSERT INTO exercise_collections (student_id, exercise_id) VALUES (?, ?)", me.id(), exerciseId);
        return Map.of("collected", true);
    }

    public Map<String, Object> selftestRecommendations(UserPrincipal me) {
        List<Map<String, Object>> all = getExercises(me.id());
        Set<String> allowedSubject = Set.of("math", "chinese", "english", "science", "art");
        List<Map<String, Object>> fallback = List.of(
                Map.of("id", 9001, "subject", "数学", "subjectClass", "math", "title", "乘法应用题闯关", "knowledge", "乘法应用题", "type", "填空题", "time", "6分钟", "difficultyClass", "medium", "difficultyText", "进阶"),
                Map.of("id", 9002, "subject", "科学", "subjectClass", "science", "title", "日常现象科学观察", "knowledge", "科学观察", "type", "解答题", "time", "8分钟", "difficultyClass", "hard", "difficultyText", "挑战"),
                Map.of("id", 9003, "subject", "英语", "subjectClass", "english", "title", "日常问候对话训练", "knowledge", "日常对话", "type", "选择题", "time", "4分钟", "difficultyClass", "medium", "difficultyText", "进阶")
        );

        List<Map<String, Object>> list = all.stream()
                .filter(x -> allowedSubject.contains(s(x.get("subjectClass"))))
                .filter(x -> {
                    String t = s(x.get("subject")) + " " + s(x.get("title")) + " " + s(x.get("knowledge"));
                    return t.matches(".*(口算|乘法|阅读|写话|对话|观察|色彩|词语|拼写|图形|美术|科学|小学).*");
                })
                .sorted((a, b) -> Integer.compare(intVal(a.get("accuracy")), intVal(b.get("accuracy"))))
                .limit(3)
                .map(x -> {
                    Map<String, Object> y = new HashMap<>();
                    y.put("id", x.get("id"));
                    y.put("subject", x.get("subject"));
                    y.put("subjectClass", x.get("subjectClass"));
                    y.put("title", x.get("title"));
                    y.put("knowledge", x.get("knowledge"));
                    y.put("type", x.get("type"));
                    y.put("time", x.get("time"));
                    y.put("difficultyClass", x.get("difficultyClass"));
                    y.put("difficultyText", x.get("difficultyText"));
                    return y;
                })
                .toList();

        List<Map<String, Object>> merged = new ArrayList<>(list);
        Set<String> keys = new HashSet<>(list.stream().map(x -> s(x.get("subject")) + "|" + s(x.get("title"))).toList());
        for (Map<String, Object> f : fallback) {
            String k = s(f.get("subject")) + "|" + s(f.get("title"));
            if (!keys.contains(k)) merged.add(f);
            if (merged.size() >= 3) break;
        }
        return Map.of("list", merged.subList(0, Math.min(3, merged.size())));
    }

    public Map<String, Object> selftestHistory() {
        return Map.of("list", List.of(
                Map.of("id", 1, "subject", "数学", "subjectClass", "math", "icon", "fas fa-calculator", "title", "乘除法口算练习", "date", "2026-04-05", "score", 18, "total", 20),
                Map.of("id", 2, "subject", "语文", "subjectClass", "chinese", "icon", "fas fa-book-open", "title", "阅读理解训练", "date", "2026-04-04", "score", 22, "total", 25),
                Map.of("id", 3, "subject", "英语", "subjectClass", "english", "icon", "fas fa-language", "title", "日常对话练习", "date", "2026-04-03", "score", 13, "total", 15)
        ));
    }

    public Map<String, Object> platformResources(String keyword, String type, String grade) {
        String t = s(type);
        if (t.isBlank()) t = "all";
        String g = s(grade);
        if (g.isBlank()) g = "小学";
        if (!Set.of("all", "course", "exercise").contains(t)) throw new IllegalArgumentException("type 参数不合法");
        List<Map<String, Object>> list = buildNationalPlatformFallback(s(keyword), t, g);
        return Map.of("list", list, "source", "jump");
    }

    private List<Map<String, Object>> getExercises(String studentId) {
        return jdbcTemplate.query(
                "SELECT e.*, EXISTS(SELECT 1 FROM exercise_collections c WHERE c.student_id = ? AND c.exercise_id = e.id) AS collected FROM exercises e ORDER BY e.id",
                (rs, i) -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", rs.getInt("id"));
                    m.put("subject", rs.getString("subject"));
                    m.put("subjectClass", rs.getString("subject_class"));
                    m.put("subjectIcon", rs.getString("subject_icon"));
                    m.put("title", rs.getString("title"));
                    m.put("type", rs.getString("type"));
                    m.put("typeClass", rs.getString("type_class"));
                    m.put("knowledge", rs.getString("knowledge"));
                    m.put("difficulty", rs.getString("difficulty"));
                    m.put("difficultyText", rs.getString("difficulty_text"));
                    m.put("difficultyClass", rs.getString("difficulty_class"));
                    m.put("accuracy", rs.getInt("accuracy"));
                    m.put("time", rs.getInt("duration"));
                    m.put("collected", rs.getInt("collected") == 1);
                    return m;
                },
                studentId
        );
    }

    private List<Map<String, Object>> buildNationalPlatformFallback(String keyword, String type, String grade) {
        String base = "https://www.zxx.edu.cn/";
        String k = s(keyword);
        List<Map<String, Object>> list = List.of(
                Map.of("id", "np-course-1", "title", withKeyword("国家平台 · " + grade + "数学《两位数乘法》微课", k), "type", "course", "source", "国家中小学智慧教育平台", "subject", "数学", "grade", grade, "duration", "15分钟", "url", buildJumpUrl(base, grade, "数学", "course", k), "cover", ""),
                Map.of("id", "np-course-2", "title", withKeyword("国家平台 · " + grade + "语文《阅读理解策略》课堂资源", k), "type", "course", "source", "国家中小学智慧教育平台", "subject", "语文", "grade", grade, "duration", "18分钟", "url", buildJumpUrl(base, grade, "语文", "course", k), "cover", ""),
                Map.of("id", "np-ex-1", "title", withKeyword("国家平台 · " + grade + "数学应用题在线练习", k), "type", "exercise", "source", "国家中小学智慧教育平台", "subject", "数学", "grade", grade, "duration", "10题", "url", buildJumpUrl(base, grade, "数学", "exercise", k), "cover", ""),
                Map.of("id", "np-ex-2", "title", withKeyword("国家平台 · " + grade + "英语单词拼写训练", k), "type", "exercise", "source", "国家中小学智慧教育平台", "subject", "英语", "grade", grade, "duration", "12题", "url", buildJumpUrl(base, grade, "英语", "exercise", k), "cover", "")
        );
        return list.stream().filter(item -> {
            if (!"all".equals(type) && !type.equals(s(item.get("type")))) return false;
            if (k.isBlank()) return true;
            return s(item.get("title")).contains(k) || s(item.get("subject")).contains(k);
        }).toList();
    }

    private String buildJumpUrl(String base, String grade, String subject, String resourceType, String keyword) {
        String q = String.join(" ", List.of(grade, subject, "exercise".equals(resourceType) ? "习题" : "课程", keyword).stream().filter(x -> !s(x).isBlank()).toList());
        return base + "search?keyword=" + java.net.URLEncoder.encode(q, java.nio.charset.StandardCharsets.UTF_8);
    }

    private String withKeyword(String label, String keyword) {
        return keyword.isBlank() ? label : label + "（关键词：" + keyword + "）";
    }

    private <T> T queryOne(String sql, Class<T> type, Object... args) {
        List<T> rows = jdbcTemplate.query(sql, (rs, i) -> rs.getObject(1, type), args);
        return rows.isEmpty() ? null : rows.get(0);
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
