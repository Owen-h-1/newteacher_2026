package com.zkys.backend.auth;

import com.zkys.backend.auth.dto.AuthDtos;
import com.zkys.backend.security.JwtService;
import com.zkys.backend.security.UserPrincipal;
import com.zkys.backend.user.User;
import com.zkys.backend.user.UserRepository;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JdbcTemplate jdbcTemplate;
    @Value("${app.admin.username:admin}")
    private String adminUsername;
    @Value("${app.admin.password:admin123456}")
    private String adminPassword;
    @Value("${app.admin.name:系统管理员}")
    private String adminName;
    @Value("${app.admin.email:admin@zkys.local}")
    private String adminEmail;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService, JdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest req) {
        String role = normalizeRole(req.role());
        if (userRepository.findByUsername(req.username()).isPresent()) {
            throw new IllegalArgumentException("用户名已存在");
        }
        if (userRepository.findByEmail(req.email()).isPresent()) {
            throw new IllegalArgumentException("邮箱已存在");
        }

        User user = new User();
        user.setId(UUID.randomUUID().toString().replace("-", ""));
        user.setUsername(req.username().trim());
        user.setEmail(req.email().trim().toLowerCase());
        user.setRole(role);
        user.setName(StringUtils.hasText(req.name()) ? req.name().trim() : req.username().trim());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        userRepository.save(user);

        String className = null;
        if ("student".equals(role)) {
            className = bindStudentToRosterOnRegister(req, user.getUsername());
        }
        if ("teacher".equals(role)) {
            assertTeacherAllowed(req.username(), req.email());
        }

        String token = jwtService.generateToken(user.getId(), user.getRole());
        return new AuthDtos.AuthResponse(token, toSafeUser(user), className);
    }

    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest req) {
        if (isAdminLogin(req.username(), req.password())) {
            User admin = ensureAdminAccount();
            String token = jwtService.generateToken(admin.getId(), admin.getRole());
            return new AuthDtos.AuthResponse(token, toSafeUser(admin), null);
        }
        User user = userRepository.findByUsername(req.username().trim())
                .orElseThrow(() -> new IllegalArgumentException("用户名或密码错误"));
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        String token = jwtService.generateToken(user.getId(), user.getRole());
        return new AuthDtos.AuthResponse(token, toSafeUser(user), null);
    }

    public AuthDtos.SafeUser me(UserPrincipal principal) {
        User user = userRepository.findById(principal.id())
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        return toSafeUser(user);
    }

    private AuthDtos.SafeUser toSafeUser(User user) {
        return new AuthDtos.SafeUser(user.getId(), user.getUsername(), user.getRole(), user.getEmail(), user.getName());
    }

    private String normalizeRole(String role) {
        String value = role == null ? "" : role.trim().toLowerCase();
        if (!value.equals("teacher") && !value.equals("student")) {
            throw new IllegalArgumentException("角色不合法");
        }
        return value;
    }

    private boolean isAdminLogin(String username, String password) {
        return s(username).equalsIgnoreCase(s(adminUsername)) && s(password).equals(adminPassword);
    }

    private User ensureAdminAccount() {
        String username = s(adminUsername);
        User existing = userRepository.findByUsername(username).orElse(null);
        if (existing != null) {
            if (!"admin".equals(existing.getRole())) {
                throw new IllegalStateException("管理员账号角色配置错误");
            }
            return existing;
        }
        User user = new User();
        user.setId(UUID.randomUUID().toString().replace("-", ""));
        user.setUsername(username);
        user.setEmail(s(adminEmail).isBlank() ? "admin@zkys.local" : s(adminEmail));
        user.setRole("admin");
        user.setName(s(adminName).isBlank() ? "系统管理员" : s(adminName));
        user.setPasswordHash(passwordEncoder.encode(s(adminPassword)));
        return userRepository.save(user);
    }

    private void assertTeacherAllowed(String usernameRaw, String emailRaw) {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS teacher_registry (
                  username VARCHAR(50) PRIMARY KEY,
                  name VARCHAR(80) NOT NULL,
                  email VARCHAR(120) NULL,
                  subject VARCHAR(80) NOT NULL,
                  enabled TINYINT(1) NOT NULL DEFAULT 1,
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        String username = s(usernameRaw);
        String email = s(emailRaw).toLowerCase();
        List<Map<String, Object>> rows = jdbcTemplate.query(
                "SELECT username, email, enabled FROM teacher_registry WHERE username = ? LIMIT 1",
                (rs, i) -> Map.of(
                        "username", s(rs.getString("username")),
                        "email", s(rs.getString("email")),
                        "enabled", rs.getBoolean("enabled")
                ),
                username
        );
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("该教师ID号未被管理员录入，无法注册");
        }
        Map<String, Object> row = rows.get(0);
        if (!Boolean.TRUE.equals(row.get("enabled"))) {
            throw new IllegalArgumentException("该教师账号已被禁用，请联系管理员");
        }
        String allowEmail = s(row.get("email")).toLowerCase();
        if (!allowEmail.isBlank() && !allowEmail.equals(email)) {
            throw new IllegalArgumentException("教师邮箱与管理员配置不一致");
        }
    }

    private String bindStudentToRosterOnRegister(AuthDtos.RegisterRequest req, String username) {
        String inviteCode = normalizeInviteCode(req.inviteCode());
        if (inviteCode.isBlank()) throw new IllegalArgumentException("学生注册必须填写班级邀请码");
        String studentNo = s(req.studentNo());
        if (!studentNo.matches("^\\d{6,20}$")) throw new IllegalArgumentException("学号须为6-20位数字");
        String studentName = s(req.studentName());
        if (studentName.isBlank()) throw new IllegalArgumentException("学生注册必须填写真实姓名");

        List<Map<String, Object>> classRows = jdbcTemplate.query(
                "SELECT id, name, invite_code FROM classes",
                (rs, i) -> Map.of(
                        "id", s(rs.getString("id")),
                        "name", s(rs.getString("name")),
                        "inviteCode", s(rs.getString("invite_code"))
                )
        );
        List<Map<String, Object>> hit = classRows.stream()
                .filter(x -> normalizeInviteCode(s(x.get("inviteCode"))).equals(inviteCode))
                .toList();
        if (hit.size() != 1) throw new IllegalArgumentException("邀请码无效，请联系老师确认");

        String classId = s(hit.get(0).get("id"));
        String className = s(hit.get(0).get("name"));
        List<Map<String, Object>> roster = jdbcTemplate.query(
                "SELECT id, name, account_username FROM students WHERE id = ? AND class_id = ? LIMIT 1",
                (rs, i) -> Map.of(
                        "id", s(rs.getString("id")),
                        "name", s(rs.getString("name")),
                        "accountUsername", s(rs.getString("account_username"))
                ),
                studentNo, classId
        );
        if (roster.isEmpty()) throw new IllegalArgumentException("该学号不在此班花名册，请联系老师先录入");
        String rosterName = s(roster.get(0).get("name"));
        if (!rosterName.equals(studentName)) {
            throw new IllegalArgumentException("姓名与花名册不一致，请填写老师录入的姓名");
        }
        String existingBind = s(roster.get(0).get("accountUsername"));
        if (!existingBind.isBlank() && !existingBind.equalsIgnoreCase(username)) {
            throw new IllegalArgumentException("该学号已绑定其他账号，请联系老师处理");
        }
        Integer usernameUsed = queryOne(
                "SELECT 1 FROM students WHERE LOWER(TRIM(account_username)) = LOWER(TRIM(?)) AND id <> ? LIMIT 1",
                Integer.class,
                username, studentNo
        );
        if (usernameUsed != null) throw new IllegalArgumentException("该账号已绑定其他学生，请更换账号");

        jdbcTemplate.update(
                "UPDATE students SET account_username = ?, joined_at = ? WHERE id = ? AND class_id = ?",
                username, Timestamp.valueOf(LocalDateTime.now()), studentNo, classId
        );
        jdbcTemplate.update(
                "INSERT INTO sign_records (class_id, student_id, time, method) VALUES (?, ?, NULL, NULL) ON DUPLICATE KEY UPDATE class_id = VALUES(class_id)",
                classId, studentNo
        );
        return className;
    }

    private String normalizeInviteCode(String raw) {
        if (raw == null) return "";
        return raw.trim().replace('\u2013', '-').replace('\u2014', '-').replace('\uff0d', '-').replaceAll("\\s+", "");
    }

    private String s(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private <T> T queryOne(String sql, Class<T> type, Object... args) {
        List<T> rows = jdbcTemplate.query(sql, (rs, i) -> rs.getObject(1, type), args);
        return rows.isEmpty() ? null : rows.get(0);
    }
}
