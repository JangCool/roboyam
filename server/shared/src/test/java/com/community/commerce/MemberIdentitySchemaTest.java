package com.community.commerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.testsupport.CommerceTestDatabase;
import java.sql.Connection;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * shared V62(커머스 0-4 표)의 제약을 실제 PostgreSQL 에서 확인한다(commerce.md 18절 — 유일 제약·CHECK 는 DB 가 마지막으로 지킨다).
 * DB 를 다른 시험과 같이 쓴다 — 겹치지 않는 ID·해시만 쓰고 표를 비우지 않는다.
 */
class MemberIdentitySchemaTest {

    private static final AtomicLong IDS = new AtomicLong(8_400_000_000_000L + (System.nanoTime() % 1_000_000L) * 1_000L);

    private static JdbcTemplate jdbc;
    private static DataSource dataSource;

    @BeforeAll
    static void setUp() {
        dataSource = CommerceTestDatabase.migrated();
        jdbc = new JdbcTemplate(dataSource);
    }

    private static long id() {
        return IDS.incrementAndGet();
    }

    /** 64자 16진 해시(겹치지 않게) */
    private static String hash() {
        return String.format("%064x", id());
    }

    private static void verification(long userId, String di, String ci, String tx) {
        jdbc.update("INSERT INTO identity_verifications (user_id, di_hash, ci_hash, birth_date, provider, provider_tx_id, verified_at) VALUES (?, ?, ?, '1990-01-01', 'FAKE', ?, now())",
                userId, di, ci, tx);
    }

    @Nested
    class 본인인증 {

        @Test
        void DI_CI_업체_건_번호는_각각_유일하고_둘_다_없으면_안_된다() {
            String di = hash();
            String ci = hash();
            verification(id(), di, ci, "tx-" + id());

            assertThatThrownBy(() -> verification(id(), di, hash(), "tx-" + id())).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> verification(id(), hash(), ci, "tx-" + id())).isInstanceOf(DataIntegrityViolationException.class);
            String tx = "tx-" + id();
            verification(id(), hash(), null, tx);
            assertThatThrownBy(() -> verification(id(), null, hash(), tx)).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> verification(id(), null, null, "tx-" + id())).isInstanceOf(DataIntegrityViolationException.class);
            // 한쪽만 있는 줄은 NULL 끼리 겹쳐도 된다
            assertThatCode(() -> {
                verification(id(), hash(), null, "tx-" + id());
                verification(id(), null, hash(), "tx-" + id());
            }).doesNotThrowAnyException();
        }

        @Test
        void 해시_칸은_64자_16진만_구현은_PORTONE_FAKE_만() {
            assertThatThrownBy(() -> verification(id(), "raw-di-value", null, "tx-" + id())).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("INSERT INTO identity_verifications (user_id, di_hash, birth_date, provider, provider_tx_id, verified_at) "
                    + "VALUES (?, ?, '1990-01-01', 'NICE', ?, now())", id(), hash(), "tx-" + id())).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void 동시에_같은_DI_를_넣으면_하나만_들어간다() throws Exception {
            String di = hash();
            try (Connection a = dataSource.getConnection(); Connection b = dataSource.getConnection()) {
                a.setAutoCommit(false);
                b.setAutoCommit(false);
                JdbcTemplate first = new JdbcTemplate(new SingleConnectionDataSource(a, true));
                JdbcTemplate second = new JdbcTemplate(new SingleConnectionDataSource(b, true));
                String sql = "INSERT INTO identity_verifications (user_id, di_hash, birth_date, provider, provider_tx_id, verified_at) VALUES (?, ?, '1990-01-01', 'FAKE', ?, now()) "
                        + "ON CONFLICT DO NOTHING";
                assertThat(first.update(sql, id(), di, "tx-" + id())).isEqualTo(1);
                var pending = java.util.concurrent.CompletableFuture.supplyAsync(() -> second.update(sql, id(), di, "tx-" + id())); // 첫 트랜잭션을 기다린다
                Thread.sleep(300);
                assertThat(pending).isNotDone();
                a.commit();
                assertThat(pending.get(10, java.util.concurrent.TimeUnit.SECONDS)).isZero();
                b.commit();
            }
            assertThat(jdbc.queryForObject("SELECT count(*) FROM identity_verifications WHERE di_hash = ?", Integer.class, di)).isEqualTo(1);
        }

        @Test
        void 시도의_상태와_끝_시각_실패_코드는_서로_맞아야_한다() {
            String sql = "INSERT INTO identity_verification_attempts (id, user_id, provider, status, failure_code, created_at, completed_at) VALUES (?, ?, 'FAKE', ?, ?, now(), ?)";
            assertThatCode(() -> jdbc.update(sql, "iv-" + id(), id(), "PENDING", null, null)).doesNotThrowAnyException();
            assertThatCode(() -> jdbc.update(sql, "iv-" + id(), id(), "VERIFIED", null, java.sql.Timestamp.from(java.time.Instant.now()))).doesNotThrowAnyException();
            assertThatCode(() -> jdbc.update(sql, "iv-" + id(), id(), "FAILED", "IDENTITY_PROVIDER_FAILED", java.sql.Timestamp.from(java.time.Instant.now())))
                    .doesNotThrowAnyException();
            assertThatThrownBy(() -> jdbc.update(sql, "iv-" + id(), id(), "PENDING", null, java.sql.Timestamp.from(java.time.Instant.now())))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update(sql, "iv-" + id(), id(), "FAILED", null, java.sql.Timestamp.from(java.time.Instant.now())))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update(sql, "iv-" + id(), id(), "VERIFIED", "X", java.sql.Timestamp.from(java.time.Instant.now())))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update(sql, "iv-" + id(), id(), "DONE", null, java.sql.Timestamp.from(java.time.Instant.now())))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    class 기능_제한 {

        private static final String INSERT = "INSERT INTO member_restrictions (id, user_id, type, reason, starts_at, until, source, auto_rule, created_by, created_at, "
                + "lifted_by, lifted_at, lift_reason) VALUES (?, ?, ?, ?, now(), ?, ?, ?, ?, now(), ?, ?, ?)";

        private int insert(String type, String reason, String untilSql, String source, String autoRule, Long createdBy, Long liftedBy, boolean lifted, String liftReason) {
            java.sql.Timestamp until = untilSql == null ? null : jdbc.queryForObject("SELECT " + untilSql, java.sql.Timestamp.class);
            return jdbc.update(INSERT, id(), id(), type, reason, until, source, autoRule, createdBy, liftedBy, lifted ? java.sql.Timestamp.from(java.time.Instant.now()) : null,
                    liftReason);
        }

        @Test
        void 올바른_줄() {
            assertThatCode(() -> insert("POINT_FREEZE", "사유", null, "OPERATOR", null, 1L, null, false, null)).doesNotThrowAnyException();
            assertThatCode(() -> insert("GROUP_BUY_BAN", null, "now() + interval '1 day'", "AUTO", "GROUP_BUY_HOLD_EXPIRED", null, null, false, null))
                    .doesNotThrowAnyException();
            assertThatCode(() -> insert("CHARGE_BAN", "사유", null, "OPERATOR", null, 1L, 2L, true, "해제 사유")).doesNotThrowAnyException();
        }

        @Test
        void 종류_출처_값은_정해진_것만() {
            assertThatThrownBy(() -> insert("WRITE_BAN", "사유", null, "OPERATOR", null, 1L, null, false, null)).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> insert("CHARGE_BAN", "사유", null, "SYSTEM", null, 1L, null, false, null)).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void 끝은_시작보다_뒤여야_한다() {
            assertThatThrownBy(() -> insert("CHARGE_BAN", "사유", "now() - interval '1 second'", "OPERATOR", null, 1L, null, false, null))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> insert("CHARGE_BAN", "사유", "now()", "OPERATOR", null, 1L, null, false, null)).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void 자동은_규칙이_있고_만든_운영자가_없다_운영자는_반대이고_사유가_필수다() {
            assertThatThrownBy(() -> insert("CHARGE_BAN", null, null, "AUTO", null, null, null, false, null)).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> insert("CHARGE_BAN", null, null, "AUTO", "R", 1L, null, false, null)).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> insert("CHARGE_BAN", "사유", null, "OPERATOR", "R", 1L, null, false, null)).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> insert("CHARGE_BAN", "사유", null, "OPERATOR", null, null, null, false, null)).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> insert("CHARGE_BAN", null, null, "OPERATOR", null, 1L, null, false, null)).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void 해제_칸은_시각_사람_사유가_함께다() {
            assertThatThrownBy(() -> insert("CHARGE_BAN", "사유", null, "OPERATOR", null, 1L, null, true, "해제 사유")).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> insert("CHARGE_BAN", "사유", null, "OPERATOR", null, 1L, 2L, true, null)).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> insert("CHARGE_BAN", "사유", null, "OPERATOR", null, 1L, 2L, false, null)).isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    class 탈퇴_대비_포트원 {

        @Test
        void 재가입_제한_해시는_종류_해시_형식_기간이_맞아야_한다() {
            String sql = "INSERT INTO identity_withdrawals (id, kind, hash, withdrawn_at, expires_at) VALUES (?, ?, ?, now(), now() + ?::interval)";
            assertThatCode(() -> jdbc.update(sql, id(), "EMAIL", hash(), "30 days")).doesNotThrowAnyException();
            assertThatThrownBy(() -> jdbc.update(sql, id(), "PHONE", hash(), "30 days")).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update(sql, id(), "EMAIL", "someone@test.local", "30 days")).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update(sql, id(), "EMAIL", hash(), "0 days")).isInstanceOf(DataIntegrityViolationException.class);
            // 누구의 것인지 잇는 칸이 없다
            assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_name = 'identity_withdrawals'", String.class))
                    .containsExactlyInAnyOrder("id", "kind", "hash", "withdrawn_at", "expires_at");
        }

        @Test
        void 연락처는_데이터_키_암호문만_포트원_값은_설정_비밀값_암호문만() {
            String contact = "INSERT INTO withdrawn_member_contacts (user_id, email_cipher, withdrawn_at, purge_after) VALUES (?, ?, now(), now())";
            assertThatCode(() -> jdbc.update(contact, id(), "d1:AAAA")).doesNotThrowAnyException();
            assertThatThrownBy(() -> jdbc.update(contact, id(), "plain@test.local")).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update(contact, id(), "v1:AAAA")).isInstanceOf(DataIntegrityViolationException.class);

            String setting = "INSERT INTO portone_settings (key, value_cipher, updated_at) VALUES (?, ?, now())";
            String key = "zzTest" + id();
            assertThatCode(() -> jdbc.update(setting, key, "v1:AAAA")).doesNotThrowAnyException();
            assertThatCode(() -> jdbc.update(setting, key + "n", null)).doesNotThrowAnyException(); // 지운 칸
            assertThatThrownBy(() -> jdbc.update(setting, key + "p", "plain-secret")).isInstanceOf(DataIntegrityViolationException.class);
            jdbc.update("DELETE FROM portone_settings WHERE key LIKE ?", key + "%");
        }

        @Test
        void 회원_알림_종류에_기능_제한이_더해졌다() {
            long user = id();
            jdbc.update("INSERT INTO users (id, terms_version, terms_agreed_at, age_confirmed_at) VALUES (?, 'v1', now(), now())", user);
            assertThatCode(() -> jdbc.update("INSERT INTO member_notifications (id, user_id, type, created_at) VALUES (?, ?, 'FEATURE_RESTRICTED', now())", id(), user))
                    .doesNotThrowAnyException();
            assertThatThrownBy(() -> jdbc.update("INSERT INTO member_notifications (id, user_id, type, created_at) VALUES (?, ?, 'NOPE', now())", id(), user))
                    .isInstanceOf(DataAccessException.class);
        }

        @Test
        void 새_커머스_표는_다른_표에_외래_키를_걸지_않는다() {
            Integer foreignKeys = jdbc.queryForObject("SELECT count(*) FROM information_schema.table_constraints WHERE constraint_type = 'FOREIGN KEY' AND table_name IN "
                    + "('identity_verifications', 'identity_verification_attempts', 'member_restrictions', 'identity_withdrawals', 'withdrawn_member_contacts', 'portone_settings')",
                    Integer.class);
            assertThat(foreignKeys).isZero();
        }
    }
}
