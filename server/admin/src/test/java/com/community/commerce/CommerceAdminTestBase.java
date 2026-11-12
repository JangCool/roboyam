package com.community.commerce;

import com.community.admin.AdminApplication;
import com.community.adminauth.service.AdminActorService;
import com.community.permission.dto.AdminActor;
import com.community.testsupport.AuditLogQuery;
import com.community.testsupport.AuditLogQuery.AuditRow;
import com.community.testsupport.CommerceTestDatabase;
import com.community.testsupport.OperatorFixture;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 0-5 관리자 쪽 규칙 시험의 바탕 — 관리자 앱 문맥 전체를 커머스 시험 DB({@link CommerceTestDatabase}, 실제 PostgreSQL)에 띄운다(commerce.md 18절).
 * 배치(예약·요청 집기·메일 대기열 워커)와 알림 감시자는 끈다. 운영자는 시험마다 새로 만들고, 루트는 하나뿐이라 있으면 쓴다.
 * 운영자는 엔티티 픽스처({@link OperatorFixture}), 감사 기록 확인은 {@link AuditLogQuery} 로 한다.
 */
@SpringBootTest(classes = AdminApplication.class, properties = {
        "irobit.crypto.key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "irobit.batch.enabled=false",
        "irobit.notification.watcher-enabled=false"
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
    @Autowired
    private ApplicationContext context;

    protected JdbcTemplate jdbc;
    protected OperatorFixture operatorFixture;
    protected AuditLogQuery auditLogs;

    @BeforeEach
    void baseSetUp() {
        jdbc = new JdbcTemplate(dataSource);
        operatorFixture = new OperatorFixture(context);
        auditLogs = new AuditLogQuery(context);
    }

    /** 역할 없는 활성 운영자 */
    protected long newOperator() {
        long id = ids.nextId();
        operatorFixture.operator(id, "c05-" + id + "@test.local", "c" + (id % 10_000_000_000_000L), "x");
        return id;
    }

    /** 루트(하나뿐) — 없으면 만든다 */
    protected long rootId() {
        return operatorFixture.rootOrCreate(id -> "c05-root-" + id + "@test.local", id -> "root" + (id % 1_000_000_000L), "x");
    }

    protected AdminActor actor(long operatorId) {
        return actors.load(operatorId, IP).orElseThrow();
    }

    /** 운영자 이름 */
    protected String operatorName(long operatorId) {
        return operatorFixture.operatorName(operatorId);
    }

    /** 이 운영자가 남긴 가장 최근 감사 한 줄(행위로) */
    protected AuditRow lastAudit(long operatorId, String action) {
        return auditLogs.where().actor(operatorId).action(action).latest().orElseThrow();
    }

    protected int auditCount(long operatorId, String action) {
        return auditLogs.where().actor(operatorId).action(action).count();
    }
}
