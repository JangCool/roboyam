package com.community.commerce;

import com.community.admin.AdminApplication;
import com.community.adminauth.service.AdminActorService;
import com.community.permission.dto.AdminActor;
import com.community.testsupport.CommerceTestDatabase;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 0-5 관리자 쪽 규칙 시험의 바탕 — 관리자 앱 문맥 전체를 커머스 시험 DB({@link CommerceTestDatabase}, 실제 PostgreSQL)에 띄운다(commerce.md 18절).
 * 배치(예약·요청 집기·메일 대기열 워커)와 알림 감시자는 끈다. 운영자는 시험마다 새로 만들고, 루트는 하나뿐이라 있으면 쓴다.
 */
@SpringBootTest(classes = AdminApplication.class, properties = {
        "roboyam.crypto.key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "roboyam.batch.enabled=false",
        "roboyam.notification.watcher-enabled=false"
})
public abstract class CommerceAdminTestBase {

    protected static final String IP = "127.0.0.1";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        CommerceTestDatabase.migrated("admin");
        registry.add("spring.datasource.url", CommerceTestDatabase::jdbcUrl);
        registry.add("spring.datasource.username", CommerceTestDatabase::username);
        registry.add("spring.datasource.password", CommerceTestDatabase::password);
    }

    @Autowired
    protected DataSource dataSource;
    @Autowired
    protected SnowflakeIdGenerator ids;
    @Autowired
    protected AdminActorService actors;

    protected JdbcTemplate jdbc;

    @BeforeEach
    void baseSetUp() {
        jdbc = new JdbcTemplate(dataSource);
    }

    /** 역할 없는 활성 운영자 */
    protected long newOperator() {
        long id = ids.nextId();
        jdbc.update("INSERT INTO operators (id, email, operator_name, password_hash, approved, created_at, updated_at) VALUES (?, ?, ?, 'x', true, now(), now())",
                id, "c05-" + id + "@test.local", "c" + (id % 10_000_000_000_000L));
        return id;
    }

    /** 루트(하나뿐) — 없으면 만든다 */
    protected long rootId() {
        List<Long> found = jdbc.queryForList("SELECT id FROM operators WHERE is_root", Long.class);
        if (!found.isEmpty()) {
            return found.get(0);
        }
        long id = ids.nextId();
        jdbc.update("INSERT INTO operators (id, email, operator_name, password_hash, is_root, approved, created_at, updated_at) VALUES (?, ?, ?, 'x', true, true, now(), now())",
                id, "c05-root-" + id + "@test.local", "root" + (id % 1_000_000_000L));
        return id;
    }

    protected AdminActor actor(long operatorId) {
        return actors.load(operatorId, IP).orElseThrow();
    }

    /** 이 운영자가 남긴 가장 최근 감사 한 줄(행위로) */
    protected Map<String, Object> lastAudit(long operatorId, String action) {
        return jdbc.queryForMap("SELECT * FROM audit_logs WHERE actor_id = ? AND action = ? ORDER BY occurred_at DESC, id DESC LIMIT 1", operatorId, action);
    }

    protected int auditCount(long operatorId, String action) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE actor_id = ? AND action = ?", Integer.class, operatorId, action);
    }
}
