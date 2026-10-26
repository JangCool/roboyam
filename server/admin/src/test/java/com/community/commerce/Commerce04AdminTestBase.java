package com.community.commerce;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.admin.AdminApplication;
import com.community.installation.service.InstallationState;
import com.community.permission.entity.Permission;
import com.community.testsupport.CommerceTestDatabase;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 커머스 0-4 관리자 API 시험의 바탕 — 관리자 앱 문맥 전체를 커머스 시험 DB({@link CommerceTestDatabase}, 실제 PostgreSQL)에 띄우고 실제 보안 필터 체인·세션 저장소·권한 인터셉터를 거친다
 * (commerce.md 18절). 설치 가드는 설치 완료로, 관리자 2차 인증은 시험 동안 끈다(끝나면 원래 값).
 *
 * <ul>
 *   <li>운영자는 시험마다 새로 만들고 고른 권한만 가진 역할을 준다({@link #operatorWith}) — 권한 없이 부르면 403 을 본다.</li>
 *   <li>회원은 겹치지 않는 Snowflake ID 로 새로 만든다. DB 를 다른 시험과 같이 쓰므로 표를 비우지 않는다.</li>
 *   <li>설정 비밀값 키·데이터 키는 시험용 고정값이다(포트원 값 암호화·탈퇴 연락처 암호화).</li>
 * </ul>
 */
@SpringBootTest(classes = AdminApplication.class, properties = {
        "roboyam.crypto.key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "roboyam.data-crypto.key=" + Commerce04AdminTestBase.DATA_KEY,
        "roboyam.batch.enabled=false",
        "roboyam.batch.scheduler-enabled=false",
        "roboyam.notification.watcher-enabled=false"
})
public abstract class Commerce04AdminTestBase {

    /** 시험용 데이터 키(공개된 값 — 시험 전용) */
    static final String DATA_KEY = "1:Nkfi1vUhkXnb6dM1b8wzSxEbPhDjKWCqih4IwYR9o34=";
    protected static final String PASSWORD = "Passw0rd-c04-test";
    protected static final String XSRF = "test-xsrf-token";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        CommerceTestDatabase.migrated("admin");
        registry.add("spring.datasource.url", CommerceTestDatabase::jdbcUrl);
        registry.add("spring.datasource.username", CommerceTestDatabase::username);
        registry.add("spring.datasource.password", CommerceTestDatabase::password);
    }

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private InstallationState installation;
    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter securityFilterChain;
    @Autowired
    @Qualifier("springSessionRepositoryFilter")
    private Filter sessionFilter;
    @Autowired
    protected DataSource dataSource;
    @Autowired
    protected SnowflakeIdGenerator ids;
    @Autowired
    private PasswordEncoder encoder;

    protected MockMvc mvc;
    protected JdbcTemplate jdbc;
    private Boolean savedMfa;
    private static String passwordHash;

    @BeforeEach
    void commerceBaseSetUp() {
        jdbc = new JdbcTemplate(dataSource);
        installation.markCompleted();
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(sessionFilter, securityFilterChain).build();
        savedMfa = jdbc.queryForObject("SELECT mfa_enabled FROM admin_security_settings WHERE id = 1", Boolean.class);
        jdbc.update("UPDATE admin_security_settings SET mfa_enabled = false WHERE id = 1");
        if (passwordHash == null) {
            passwordHash = encoder.encode(PASSWORD);
        }
    }

    @AfterEach
    void commerceBaseTearDown() {
        jdbc.update("UPDATE admin_security_settings SET mfa_enabled = ? WHERE id = 1", savedMfa);
    }

    // ── 운영자 ─────────────────────────────────────────────────────────────

    /** 고른 권한만 가진 역할 하나를 받은 활성 운영자(권한이 없으면 역할도 없다) */
    protected long operatorWith(Permission... permissions) {
        long id = ids.nextId();
        jdbc.update("INSERT INTO operators (id, email, operator_name, password_hash, approved, created_at, updated_at) VALUES (?, ?, ?, ?, true, now(), now())",
                id, email(id), "c" + (id % 10_000_000_000_000L), passwordHash);
        if (permissions.length > 0) {
            String roleId = "ROLE_ZZ_C04_" + id;
            jdbc.update("INSERT INTO roles (role_id, role_type_id, axis, role_name) VALUES (?, 'op_general', 'OPERATOR', ?)", roleId, "c04-" + (id % 1_000_000_000L));
            for (Permission permission : permissions) {
                jdbc.update("INSERT INTO role_permissions (id, role_id, permission_code) VALUES (?, ?, ?)", ids.nextId(), roleId, permission.code());
            }
            jdbc.update("INSERT INTO operator_roles (id, operator_id, role_id) VALUES (?, ?, ?)", ids.nextId(), id, roleId);
        }
        return id;
    }

    protected static String email(long operatorId) {
        return "c04-" + operatorId + "@test.local";
    }

    protected String operatorName(long operatorId) {
        return jdbc.queryForObject("SELECT operator_name FROM operators WHERE id = ?", String.class, operatorId);
    }

    /** 이메일 로그인 → 세션 쿠키 */
    protected Cookie login(long operatorId) throws Exception {
        MvcResult result = mvc.perform(withCsrf(post("/api/admin/auth/login"))
                .content("{\"email\":\"" + email(operatorId) + "\",\"password\":\"" + PASSWORD + "\"}")).andExpect(status().isOk()).andReturn();
        Cookie admin = result.getResponse().getCookie("ADMIN_SESSION");
        return admin != null ? admin : result.getResponse().getCookie("SESSION");
    }

    /** CSRF 쿠키·헤더와 JSON 본문 형식을 붙인다(null 쿠키는 건너뛴다) */
    protected MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request, Cookie... cookies) {
        List<Cookie> all = new ArrayList<>();
        all.add(new Cookie("XSRF-TOKEN", XSRF));
        Arrays.stream(cookies).filter(java.util.Objects::nonNull).forEach(all::add);
        return request.cookie(all.toArray(Cookie[]::new)).header("X-XSRF-TOKEN", XSRF).contentType(MediaType.APPLICATION_JSON);
    }

    // ── 회원 ─────────────────────────────────────────────────────────────

    /** 활성 회원(프로필 포함) */
    protected long newMember() {
        long id = ids.nextId();
        jdbc.update("INSERT INTO users (id, email, status, terms_version, terms_agreed_at, age_confirmed_at) VALUES (?, ?, 'ACTIVE', 'v1', now(), now())", id,
                "m" + id + "@test.local");
        String base36 = Long.toString(id, 36);
        jdbc.update("INSERT INTO profiles (user_id, nickname) VALUES (?, ?)", id, "n" + base36.substring(Math.max(0, base36.length() - 11))); // 닉네임 12자 — 뒤쪽이 겹치지 않는다
        return id;
    }

    /** 탈퇴(익명화)한 회원 — 이메일 없음, 상태 WITHDRAWN, 탈퇴 시각 */
    protected long withdrawnMember(Instant withdrawnAt) {
        long id = newMember();
        jdbc.update("UPDATE users SET email = NULL, status = 'WITHDRAWN', deleted_at = ? WHERE id = ?", ts(withdrawnAt), id);
        return id;
    }

    /** 본인인증 기록(해시는 시험용 64자) */
    protected void verified(long userId, LocalDate birthDate, String provider, Instant verifiedAt) {
        String hash = String.format("%064x", userId);
        jdbc.update("INSERT INTO identity_verifications (user_id, di_hash, ci_hash, birth_date, provider, provider_tx_id, verified_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                userId, hash, hash.replace('0', 'c'), birthDate, provider, "tx-" + userId, ts(verifiedAt));
    }

    protected static OffsetDateTime ts(Instant at) {
        return at == null ? null : OffsetDateTime.ofInstant(at, ZoneOffset.UTC);
    }
}
