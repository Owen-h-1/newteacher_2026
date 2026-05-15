package com.zkys.backend.common;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DataConsistencyAuditRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DataConsistencyAuditRunner.class);
    private final JdbcTemplate jdbcTemplate;

    @Value("${app.audit.enabled:true}")
    private boolean enabled;

    public DataConsistencyAuditRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;
        try {
            String report = buildReport();
            Path reportPath = Path.of(System.getProperty("user.dir")).resolve("../data-consistency-report.txt").normalize();
            Files.writeString(reportPath, report, StandardCharsets.UTF_8);
            log.warn("Data consistency report generated: {}", reportPath);
        } catch (Exception ex) {
            log.error("Data consistency audit failed", ex);
        }
    }

    private String buildReport() {
        int classCount = count("SELECT COUNT(1) FROM classes");
        int studentCount = count("SELECT COUNT(1) FROM students");
        int homeworkCount = count("SELECT COUNT(1) FROM homework");

        int ownerMissing = count(
                """
                SELECT COUNT(1)
                FROM classes c
                LEFT JOIN users u ON u.id = c.owner_teacher_id AND u.role = 'teacher'
                WHERE c.owner_teacher_id IS NULL OR TRIM(c.owner_teacher_id) = '' OR u.id IS NULL
                """
        );
        int studentClassOrphan = count(
                """
                SELECT COUNT(1)
                FROM students s
                LEFT JOIN classes c ON c.id = s.class_id
                WHERE s.class_id IS NULL OR TRIM(s.class_id) = '' OR c.id IS NULL
                """
        );
        int homeworkClassOrphan = count(
                """
                SELECT COUNT(1)
                FROM homework h
                LEFT JOIN classes c ON c.id = h.class_id
                WHERE h.class_id IS NULL OR TRIM(h.class_id) = '' OR c.id IS NULL
                """
        );

        StringBuilder sb = new StringBuilder();
        sb.append("Data Consistency Audit Report\n");
        sb.append("Generated At: ")
                .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .append("\n\n");

        sb.append("=== Summary ===\n");
        sb.append("classes.total = ").append(classCount).append('\n');
        sb.append("students.total = ").append(studentCount).append('\n');
        sb.append("homework.total = ").append(homeworkCount).append('\n');
        sb.append("classes.owner_teacher_id.invalid = ").append(ownerMissing).append('\n');
        sb.append("students.class_id.orphan = ").append(studentClassOrphan).append('\n');
        sb.append("homework.class_id.orphan = ").append(homeworkClassOrphan).append('\n');

        appendSection(
                sb,
                "Invalid class owner samples (top 20)",
                """
                SELECT c.id, c.name, c.owner_teacher_id
                FROM classes c
                LEFT JOIN users u ON u.id = c.owner_teacher_id AND u.role = 'teacher'
                WHERE c.owner_teacher_id IS NULL OR TRIM(c.owner_teacher_id) = '' OR u.id IS NULL
                ORDER BY c.id
                LIMIT 20
                """
        );
        appendSection(
                sb,
                "Student class orphan samples (top 20)",
                """
                SELECT s.id, s.name, s.class_id
                FROM students s
                LEFT JOIN classes c ON c.id = s.class_id
                WHERE s.class_id IS NULL OR TRIM(s.class_id) = '' OR c.id IS NULL
                ORDER BY s.id
                LIMIT 20
                """
        );
        appendSection(
                sb,
                "Homework class orphan samples (top 20)",
                """
                SELECT h.id, h.title, h.class_id, h.teacher_id
                FROM homework h
                LEFT JOIN classes c ON c.id = h.class_id
                WHERE h.class_id IS NULL OR TRIM(h.class_id) = '' OR c.id IS NULL
                ORDER BY h.created_at DESC
                LIMIT 20
                """
        );

        return sb.toString();
    }

    private void appendSection(StringBuilder sb, String title, String sql) {
        sb.append("\n=== ").append(title).append(" ===\n");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        if (rows.isEmpty()) {
            sb.append("(none)\n");
            return;
        }
        for (Map<String, Object> row : rows) {
            sb.append(row).append('\n');
        }
    }

    private int count(String sql) {
        Integer n = jdbcTemplate.queryForObject(sql, Integer.class);
        return n == null ? 0 : n;
    }
}
