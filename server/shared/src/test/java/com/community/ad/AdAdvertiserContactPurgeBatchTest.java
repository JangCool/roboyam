package com.community.ad;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.batch.core.BatchCatalog;
import com.community.testsupport.CommerceTestDatabase;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 광고주 연락처 파기 DB 배치 {@code ad-advertiser-contact-purge}({@code R__batch_35}, V131 — ad-advertiser-contract 규칙 9) — 실제 PostgreSQL 에서 프로시저를 {@code CALL} 한다.
 * 보관한 직접 등록 광고주 중 마지막 광고 끝(건이 없으면 보관 시각) + 5년이 지난 것만 담당자·연락처·이메일 암호문·가린 값을 비우고 {@code contact_purged_at} 을 찍는다. 이름·메모·열람 기록·처리
 * 기록은 남는다. 보관하지 않은 광고주 · 5년이 안 된 광고주 · 마지막 광고 끝이 5년 안인 광고주는 그대로. 카탈로그는 DB 배치 · 매일 04:10 KST · 보관 5년, 번들 두 언어.
 */
class AdAdvertiserContactPurgeBatchTest {

    private static final String JOB = "ad-advertiser-contact-purge";
    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime() % 1_000_000_000L * 1000);

    private static JdbcTemplate jdbc;

    @BeforeAll
    static void database() {
        DataSource dataSource = CommerceTestDatabase.migrated();
        jdbc = new JdbcTemplate(dataSource);
    }

    private static long id() {
        return 7_000_000_000_000_000_000L + SEQ.incrementAndGet();
    }

    private static Instant yearsAgo(double years) {
        return ZonedDateTime.now(ZoneOffset.UTC).minusDays(Math.round(years * 365.25)).toInstant().truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
    }

    /** 연락처가 다 있는 직접 등록 광고주(보관 시각 선택) + 열람 기록·처리 기록 한 줄씩 */
    private long advertiser(Instant archivedAt) {
        long id = id();
        jdbc.update("INSERT INTO ad_advertisers (id, name, contact_name_enc, contact_phone_enc, email_enc, contact_name_masked, contact_phone_masked, email_masked, memo, "
                        + "created_by, created_at, updated_at, archived_at, archived_by) VALUES (?, ?, 'd1:x', 'd1:y', 'd1:z', '김*당', '010-****-1234', 'ad***@example.com', "
                        + "'메모', 1, now(), now(), ?, ?)", id, "파기시험" + id, archivedAt == null ? null : Timestamp.from(archivedAt),
                archivedAt == null ? null : 1L);
        jdbc.update("INSERT INTO ad_advertiser_views (id, advertiser_id, operator_id, reason, created_at) VALUES (?, ?, 1, '계약 확인 전화', now())", id(), id);
        jdbc.update("INSERT INTO ad_advertiser_events (id, advertiser_id, action, actor_id, created_at) VALUES (?, ?, 'CREATE', 1, now())", id(), id);
        return id;
    }

    /** 그 광고주의 신청 건(심사 대기에서 취소된 모양 — 상태와 상관없이 끝 시각만 본다) */
    private void booking(long advertiserId, Instant end) {
        long id = id();
        jdbc.update("INSERT INTO biz_applications (id, application_no, kind, status, source, advertiser_id, advertiser_name, title, start_at, end_at, preferred_method, "
                        + "quote_supply, quote_vat, quote_total, closed_at, stop_fee_rate, created_by, created_at, updated_at) "
                        + "VALUES (?, ?, 'AD_TEXT_LINE', 'CANCELED', 'OPERATOR', ?, '파기시험', '파기 시험', ?, ?, 'MANUAL', 1000, 100, 1100, ?, 0, 1, now(), now())",
                id, "PG-" + (id % 1_000_000_000_000L), advertiserId, Timestamp.from(end.minusSeconds(86_400 * 7)), Timestamp.from(end), Timestamp.from(end));
    }

    private Map<String, Object> row(long id) {
        return jdbc.queryForMap("SELECT name, memo, contact_name_enc, contact_phone_enc, email_enc, contact_name_masked, contact_phone_masked, email_masked, contact_purged_at "
                + "FROM ad_advertisers WHERE id = ?", id);
    }

    private void assertPurged(long id) {
        Map<String, Object> r = row(id);
        for (String c : new String[] {"contact_name_enc", "contact_phone_enc", "email_enc", "contact_name_masked", "contact_phone_masked", "email_masked"}) {
            assertThat(r.get(c)).as(c).isNull();
        }
        assertThat(r.get("contact_purged_at")).isNotNull();
        assertThat(r.get("name")).isEqualTo("파기시험" + id);
        assertThat(r.get("memo")).isEqualTo("메모");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ad_advertiser_views WHERE advertiser_id = ?", Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ad_advertiser_events WHERE advertiser_id = ?", Integer.class, id)).isEqualTo(1);
    }

    private void assertKept(long id) {
        Map<String, Object> r = row(id);
        assertThat(r.get("contact_phone_enc")).isEqualTo("d1:y");
        assertThat(r.get("contact_phone_masked")).isEqualTo("010-****-1234");
        assertThat(r.get("contact_purged_at")).isNull();
    }

    @Test
    void 카탈로그는_DB_배치이고_매일_04시_10분_보관_5년_번들_두_언어() throws Exception {
        assertThat(BatchCatalog.ofKey(JOB)).isEqualTo(BatchCatalog.AD_ADVERTISER_CONTACT_PURGE);
        assertThat(BatchCatalog.AD_ADVERTISER_CONTACT_PURGE.isJava()).isFalse();
        assertThat(BatchCatalog.AD_ADVERTISER_CONTACT_PURGE.procedureName()).isEqualTo("batch_ad_advertiser_contact_purge");
        Map<String, Object> defined = jdbc.queryForMap("SELECT kst_hour, kst_minute, cadence FROM batch_db_jobs WHERE job_key = ?", JOB);
        assertThat(((Number) defined.get("kst_hour")).intValue()).isEqualTo(4);
        assertThat(((Number) defined.get("kst_minute")).intValue()).isEqualTo(10);
        assertThat(defined.get("cadence")).isEqualTo("DAILY");
        assertThat(jdbc.queryForObject("SELECT param_value FROM batch_params WHERE job_key = ? AND param_key = 'retention_years'", Long.class, JOB)).isEqualTo(5L);
        for (String lang : new String[] {"ko", "en"}) {
            Properties bundle = new Properties();
            try (var in = new java.io.InputStreamReader(AdAdvertiserContactPurgeBatchTest.class.getResourceAsStream("/messages/messages_" + lang + ".properties"),
                    java.nio.charset.StandardCharsets.UTF_8)) {
                bundle.load(in);
            }
            assertThat(bundle).containsKeys("batch.job." + JOB + ".name", "batch.job." + JOB + ".desc", "batch.result.adAdvertiserContactPurge",
                    "batch.result.adAdvertiserContactPurge.none");
        }
    }

    @Test
    void 보관하고_마지막_광고_끝_또는_보관_시각에서_5년이_지난_직접_등록_광고주만_연락처를_비운다() {
        long oldNoBookings = advertiser(yearsAgo(5.2));            // 건 없음 · 보관 5년 지남 → 파기
        long oldBookingEnded = advertiser(yearsAgo(5.1));          // 마지막 광고 끝 6년 전 → 파기
        booking(oldBookingEnded, yearsAgo(6));
        long recentBooking = advertiser(yearsAgo(5.5));            // 보관은 오래됐지만 마지막 광고 끝이 2년 전 → 그대로
        booking(recentBooking, yearsAgo(2));
        long recentArchive = advertiser(yearsAgo(1));              // 보관 1년 → 그대로
        long notArchived = advertiser(null);                       // 보관하지 않음 → 그대로
        booking(notArchived, yearsAgo(9));

        jdbc.execute("CALL batch_ad_advertiser_contact_purge('MANUAL', NULL, NULL)");

        assertPurged(oldNoBookings);
        assertPurged(oldBookingEnded);
        assertKept(recentBooking);
        assertKept(recentArchive);
        assertKept(notArchived);
        assertThat(jdbc.queryForObject("SELECT message_key FROM batch_runs WHERE job_key = ? ORDER BY started_at DESC, id DESC LIMIT 1", String.class, JOB))
                .isEqualTo("batch.result.adAdvertiserContactPurge");

        // 다시 돌리면 이미 파기한 줄은 대상이 아니다 — 이 시험 줄이 그대로이고, 보관을 풀어도 연락처는 비어 있다
        jdbc.execute("CALL batch_ad_advertiser_contact_purge('MANUAL', NULL, NULL)");
        jdbc.update("UPDATE ad_advertisers SET archived_at = NULL, archived_by = NULL WHERE id = ?", oldNoBookings);
        assertPurged(oldNoBookings);
        assertKept(recentBooking);
    }
}
