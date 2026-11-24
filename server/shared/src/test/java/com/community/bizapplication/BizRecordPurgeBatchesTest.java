package com.community.bizapplication;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.batch.core.BatchCatalog;
import com.community.testsupport.CommerceTestDatabase;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 사업자·광고 기록 보관 기간 파기 DB 배치 두 개(2026-11-24 사용자 결정 — 개인정보처리방침 1-3·2절) — 실제 PostgreSQL 에서 프로시저를 {@code CALL} 한다.
 * <ul>
 *   <li>{@code business-profile-purge}(R__batch_45): 반려·탈퇴 5년 지난 사업자 신원을 딸린 기록과 함께 지운다. 신청 건이 가리키면 남기고 결과 문구에 센다. 연결된 광고주는 연결만 푼다.</li>
 *   <li>{@code biz-payment-record-purge}(R__batch_46, V263 ①②): 게재가 끝나고 5년 지난 신청 건의 계약서 사본 내용·입금자명·불일치 사유를 비운다. 입금액·입금일·사본 줄은 남는다.
 *       5년 안 · 열린 환불이 있는 건은 그대로.</li>
 * </ul>
 */
class BizRecordPurgeBatchesTest {

    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime() % 1_000_000_000L * 1000);

    private static JdbcTemplate jdbc;

    @BeforeAll
    static void database() {
        DataSource dataSource = CommerceTestDatabase.migrated();
        jdbc = new JdbcTemplate(dataSource);
    }

    private static long id() {
        return 6_000_000_000_000_000_000L + SEQ.incrementAndGet();
    }

    private static Instant yearsAgo(double years) {
        return ZonedDateTime.now(ZoneOffset.UTC).minusDays(Math.round(years * 365.25)).toInstant().truncatedTo(ChronoUnit.MINUTES);
    }

    private static Timestamp ts(Instant i) {
        return Timestamp.from(i);
    }

    private int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    private void assertCatalog(String job, BatchCatalog entry, String procedure, int hour, int minute, String resultKey) throws Exception {
        assertThat(BatchCatalog.ofKey(job)).isEqualTo(entry);
        assertThat(entry.isJava()).isFalse();
        assertThat(entry.procedureName()).isEqualTo(procedure);
        Map<String, Object> defined = jdbc.queryForMap("SELECT kst_hour, kst_minute, cadence FROM batch_db_jobs WHERE job_key = ?", job);
        assertThat(((Number) defined.get("kst_hour")).intValue()).isEqualTo(hour);
        assertThat(((Number) defined.get("kst_minute")).intValue()).isEqualTo(minute);
        assertThat(jdbc.queryForObject("SELECT param_value FROM batch_params WHERE job_key = ? AND param_key = 'retention_years'", Long.class, job)).isEqualTo(5L);
        for (String lang : new String[] {"ko", "en"}) {
            Properties bundle = new Properties();
            try (var in = new java.io.InputStreamReader(BizRecordPurgeBatchesTest.class.getResourceAsStream("/messages/messages_" + lang + ".properties"),
                    java.nio.charset.StandardCharsets.UTF_8)) {
                bundle.load(in);
            }
            assertThat(bundle).containsKeys("batch.job." + job + ".name", "batch.job." + job + ".desc", resultKey, resultKey + ".none");
        }
    }

    // ── 사업자 신원 ─────────────────────────────────────────────────────────

    private long user() {
        long id = id();
        jdbc.update("INSERT INTO users (id, email, email_verified_at, terms_version, terms_agreed_at, age_confirmed_at) VALUES (?, ?, now(), 'v1', now(), now())", id,
                "bp-" + id + "@test.local");
        return id;
    }

    /** 90일 파기가 끝난 모양의 반려·탈퇴 신원 + 딸린 기록 한 줄씩(등록증·국세청 조회·처리·열람·서비스·종이 접수·광고주 화면 열람) */
    private long profile(String status, Instant at) {
        long id = id();
        long userId = user();
        boolean rejected = "REJECTED".equals(status);
        jdbc.update("INSERT INTO business_profiles (id, user_id, status, company_name, biz_no_last4, terms_version, privacy_version, agreed_at, submitted_at, reviewed_at, "
                        + "reject_code, reject_text, withdrawn_at, opened_on, erased_at, created_at, updated_at) "
                        + "VALUES (?, ?, ?, '파기상회', '1234', 'v1', 'v1', ?, ?, ?, ?, ?, ?, DATE '2015-03-01', ?, ?, ?)",
                id, userId, status, ts(at), ts(at), ts(at), rejected ? "INFO_MISMATCH" : null, rejected ? "정보가 달라요" : null, rejected ? null : ts(at), ts(at), ts(at), ts(at));
        long doc = id();
        jdbc.update("INSERT INTO business_documents (id, user_id, profile_id, kind, file_name, content_type, size_bytes, content, sha256, created_at, attached_at, erased_at) "
                + "VALUES (?, ?, ?, 'CERTIFICATE', 'cert.pdf', 'application/pdf', 3, NULL, ?, ?, ?, ?)", doc, userId, id, "a".repeat(64), ts(at), ts(at), ts(at));
        jdbc.update("UPDATE business_profiles SET certificate_id = ? WHERE id = ?", doc, id);
        jdbc.update("INSERT INTO business_services (id, profile_id, service, status, updated_at) VALUES (?, ?, 'AD', 'OFF', ?)", id(), id, ts(at));
        jdbc.update("INSERT INTO business_nts_checks (id, profile_id, status, validity, checked_at) VALUES (?, ?, 'CONTINUING', 'MATCH', ?)", id(), id, ts(at));
        jdbc.update("INSERT INTO business_history (id, profile_id, action, actor_type, created_at) VALUES (?, ?, 'APPLIED', 'MEMBER', ?)", id(), id, ts(at));
        jdbc.update("INSERT INTO business_sensitive_views (id, profile_id, kind, operator_id, created_at) VALUES (?, ?, 'CERTIFICATE', 1, ?)", id(), id, ts(at));
        jdbc.update("INSERT INTO biz_doc_intakes (id, doc, profile_id, channel, received_on, handling, recorded_at, updated_at) "
                + "VALUES (?, 'CERTIFICATE', ?, 'MAIL', DATE '2019-01-02', 'KEEP', ?, ?)", id(), id, ts(at), ts(at));
        jdbc.update("INSERT INTO ad_advertiser_views (id, business_profile_id, source, operator_id, reason, created_at) VALUES (?, ?, 'BUSINESS', 1, '계약 확인 전화', ?)", id(), id,
                ts(at));
        return id;
    }

    private int children(long profileId) {
        return count("SELECT (SELECT count(*) FROM business_documents WHERE profile_id = ?) + (SELECT count(*) FROM business_services WHERE profile_id = ?) "
                        + "+ (SELECT count(*) FROM business_nts_checks WHERE profile_id = ?) + (SELECT count(*) FROM business_history WHERE profile_id = ?) "
                        + "+ (SELECT count(*) FROM business_sensitive_views WHERE profile_id = ?) + (SELECT count(*) FROM biz_doc_intakes WHERE profile_id = ?) "
                        + "+ (SELECT count(*) FROM ad_advertiser_views WHERE business_profile_id = ?)",
                profileId, profileId, profileId, profileId, profileId, profileId, profileId);
    }

    @Test
    void 사업자_신원_삭제_카탈로그는_DB_배치이고_매일_05시_보관_5년() throws Exception {
        assertCatalog("business-profile-purge", BatchCatalog.BUSINESS_PROFILE_PURGE, "batch_business_profile_purge", 5, 0, "batch.result.businessProfilePurge");
    }

    @Test
    void 반려_탈퇴_5년이_지난_사업자_신원은_지우고_신청_건이_가리키면_건이_끝나고_5년_뒤_익명화한다() {
        long rejectedOld = profile("REJECTED", yearsAgo(6));
        long withdrawnOld = profile("WITHDRAWN", yearsAgo(5.5));
        long advertiser = id();
        jdbc.update("INSERT INTO ad_advertisers (id, name, business_profile_id, created_by, created_at, updated_at) VALUES (?, ?, ?, 1, now(), now())", advertiser,
                "파기상회" + advertiser, withdrawnOld);
        long withdrawnRecent = profile("WITHDRAWN", yearsAgo(3));
        long withOldBooking = profile("WITHDRAWN", yearsAgo(7));          // 신청 건 끝 7.9년 전 → 익명화
        long app = businessBooking(withOldBooking, yearsAgo(7.9));
        long linked = id();
        jdbc.update("INSERT INTO ad_advertisers (id, name, business_profile_id, created_by, created_at, updated_at) VALUES (?, ?, ?, 1, now(), now())", linked,
                "파기상회" + linked, withOldBooking);
        long withRecentBooking = profile("WITHDRAWN", yearsAgo(6));       // 탈퇴는 6년 전이지만 신청 건 끝이 2년 전 → 미룬다
        long recentApp = businessBooking(withRecentBooking, yearsAgo(2));

        jdbc.execute("CALL batch_business_profile_purge('MANUAL', NULL, NULL)");

        for (long gone : new long[] {rejectedOld, withdrawnOld}) {
            assertThat(count("SELECT count(*) FROM business_profiles WHERE id = ?", gone)).as("신원 삭제 " + gone).isZero();
            assertThat(children(gone)).as("딸린 기록 삭제 " + gone).isZero();
        }
        Map<String, Object> unlinked = jdbc.queryForMap("SELECT name, business_profile_id FROM ad_advertisers WHERE id = ?", advertiser);
        assertThat(unlinked.get("business_profile_id")).as("광고주는 연결을 푼다").isNull();
        assertThat(unlinked.get("name")).as("광고주 이름(= 상호)도 바꾼다").isEqualTo("삭제된 사업자 " + advertiser);
        assertThat(count("SELECT count(*) FROM business_profiles WHERE id = ?", withdrawnRecent)).as("탈퇴 3년 — 그대로").isEqualTo(1);
        assertThat(children(withdrawnRecent)).isEqualTo(7);

        // 익명화 — 줄은 남고 식별 칸은 비거나 중립 값, 딸린 기록은 서비스 권한(상태만)만 남는다
        Map<String, Object> anon = jdbc.queryForMap("SELECT status, company_name, biz_no_last4, opened_on, anonymized_at, erased_at FROM business_profiles WHERE id = ?",
                withOldBooking);
        assertThat(anon.get("status")).isEqualTo("WITHDRAWN");
        assertThat(anon.get("company_name")).isEqualTo("삭제된 사업자");
        assertThat(anon.get("biz_no_last4")).isNull();
        assertThat(anon.get("opened_on")).isNull();
        assertThat(anon.get("anonymized_at")).isNotNull();
        assertThat(anon.get("erased_at")).isNotNull();
        assertThat(children(withOldBooking)).as("서비스 권한 한 줄만 남는다").isEqualTo(1);
        assertThat(count("SELECT count(*) FROM business_services WHERE profile_id = ?", withOldBooking)).isEqualTo(1);
        Map<String, Object> snap = jdbc.queryForMap("SELECT company_name, biz_no_last4, advertiser_name, title, status, quote_total FROM biz_applications WHERE id = ?", app);
        assertThat(snap.get("advertiser_name")).as("신청 건 스냅샷 광고주 이름").isEqualTo("삭제된 사업자");
        assertThat(snap.get("title")).as("소재(공개 광고물)는 그대로").isEqualTo("파기 시험");
        assertThat(snap.get("company_name")).as("신청 건 스냅샷 상호").isEqualTo("삭제된 사업자");
        assertThat(snap.get("biz_no_last4")).as("신청 건 스냅샷 끝 4자리").isNull();
        assertThat(((Number) snap.get("quote_total")).longValue()).as("금액은 남는다").isEqualTo(1100L);
        Map<String, Object> linkedRow = jdbc.queryForMap("SELECT name, business_profile_id FROM ad_advertisers WHERE id = ?", linked);
        assertThat(linkedRow.get("business_profile_id")).as("익명화한 신원의 광고주는 연결을 둔다").isEqualTo(withOldBooking);
        assertThat(linkedRow.get("name")).isEqualTo("삭제된 사업자 " + linked);

        assertThat(jdbc.queryForObject("SELECT company_name FROM business_profiles WHERE id = ?", String.class, withRecentBooking)).as("신청 건 끝 2년 — 그대로")
                .isEqualTo("파기상회");
        assertThat(children(withRecentBooking)).isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT biz_no_last4 FROM biz_applications WHERE id = ?", String.class, recentApp)).isEqualTo("1234");
        assertThat(jdbc.queryForObject("SELECT advertiser_name FROM biz_applications WHERE id = ?", String.class, recentApp)).isEqualTo("파기상회");

        Map<String, Object> last = jdbc.queryForMap("SELECT status, message_key FROM batch_runs WHERE job_key = 'business-profile-purge' "
                + "ORDER BY started_at DESC, id DESC LIMIT 1");
        assertThat(last.get("status")).isEqualTo("OK");
        assertThat(last.get("message_key")).isEqualTo("batch.result.businessProfilePurge");

        // 다시 돌려도 익명화한 줄은 다시 대상이 아니다
        Object mark = anon.get("anonymized_at");
        jdbc.execute("CALL batch_business_profile_purge('MANUAL', NULL, NULL)");
        assertThat(jdbc.queryForObject("SELECT anonymized_at FROM business_profiles WHERE id = ?", Object.class, withOldBooking)).isEqualTo(mark);
    }

    /** 그 사업자가 신청한 광고 건(취소로 닫힘 — 끝 시각 {@code end}) — 거래 근거 스냅샷(상호·끝 4자리)이 있다 */
    private long businessBooking(long profileId, Instant end) {
        long id = id();
        long userId = jdbc.queryForObject("SELECT user_id FROM business_profiles WHERE id = ?", Long.class, profileId);
        jdbc.update("INSERT INTO biz_applications (id, application_no, kind, status, source, business_profile_id, user_id, company_name, biz_no_last4, advertiser_name, title, start_at, end_at, "
                        + "preferred_method, quote_supply, quote_vat, quote_total, closed_at, stop_fee_rate, ad_policy_version, refund_policy_version, agreed_at, created_at, updated_at) "
                        + "VALUES (?, ?, 'AD_TEXT_LINE', 'CANCELED', 'BUSINESS', ?, ?, '파기상회', '1234', '파기상회', '파기 시험', ?, ?, 'MANUAL', 1000, 100, 1100, ?, 0, 'v1', 'v1', ?, ?, ?)",
                id, "BP-" + (id % 1_000_000_000_000L), profileId, userId, ts(end.minus(7, ChronoUnit.DAYS)), ts(end), ts(end), ts(end.minus(8, ChronoUnit.DAYS)),
                ts(end.minus(8, ChronoUnit.DAYS)), ts(end));
        return id;
    }

    // ── 계약서 사본 · 입금 기록 ─────────────────────────────────────────────────

    /** 끝난 수동 결제 광고 건(예약 상태로 게재 끝) + 계약서 사본 한 장 + 입금 기록 */
    private long endedManualAd(Instant end) {
        long id = id();
        jdbc.update("INSERT INTO biz_applications (id, application_no, kind, status, source, advertiser_name, title, start_at, end_at, preferred_method, payment_method, "
                        + "quote_supply, quote_vat, quote_total, approved_supply, approved_vat, approved_total, approved_at, active_contract_count, deposit_confirmed_at, "
                        + "stop_fee_rate, created_by, created_at, updated_at) "
                        + "VALUES (?, ?, 'AD_TEXT_LINE', 'SCHEDULED', 'OPERATOR', '입금시험', '입금 시험', ?, ?, 'MANUAL', 'MANUAL', 1000, 100, 1100, 1000, 100, 1100, ?, 1, ?, "
                        + "0, 1, ?, ?)",
                id, "BD-" + (id % 1_000_000_000_000L), ts(end.minus(7, ChronoUnit.DAYS)), ts(end), ts(end.minus(10, ChronoUnit.DAYS)), ts(end.minus(9, ChronoUnit.DAYS)),
                ts(end.minus(10, ChronoUnit.DAYS)), ts(end));
        jdbc.update("INSERT INTO biz_contract_files (id, application_id, file_name, content_type, size_bytes, content, sha256, uploaded_by, uploaded_at) "
                + "VALUES (?, ?, '계약서.pdf', 'application/pdf', 3, '\\x010203'::bytea, ?, 1, ?)", id(), id, "b".repeat(64), ts(end.minus(9, ChronoUnit.DAYS)));
        jdbc.update("INSERT INTO biz_deposits (application_id, amount, deposited_on, depositor_name, mismatch_reason, confirmed_by, confirmed_at) VALUES (?, 1100, ?, '홍길동', "
                + "'100원 더 보냄', 1, ?)", id, java.sql.Date.valueOf(LocalDate.ofInstant(end.minus(9, ChronoUnit.DAYS), ZoneOffset.UTC)), ts(end.minus(9, ChronoUnit.DAYS)));
        return id;
    }

    private Map<String, Object> payment(long applicationId) {
        return jdbc.queryForMap("SELECT d.amount, d.deposited_on, d.depositor_name, d.mismatch_reason, d.erased_at AS deposit_erased, f.content, f.file_name, f.erased_at AS file_erased "
                + "FROM biz_deposits d JOIN biz_contract_files f ON f.application_id = d.application_id WHERE d.application_id = ?", applicationId);
    }

    @Test
    void 계약서_입금_파기_카탈로그는_DB_배치이고_매일_05시_05분_보관_5년() throws Exception {
        assertCatalog("biz-payment-record-purge", BatchCatalog.BIZ_PAYMENT_RECORD_PURGE, "batch_biz_payment_record_purge", 5, 5, "batch.result.bizPaymentRecordPurge");
    }

    @Test
    void 게재가_끝나고_5년이_지난_건의_계약서_내용과_입금자명만_지우고_금액_날짜는_남긴다() {
        long old = endedManualAd(yearsAgo(5.5));
        long recent = endedManualAd(yearsAgo(4));
        long openRefund = endedManualAd(yearsAgo(6));
        jdbc.update("INSERT INTO biz_refunds (id, application_id, type, status, method, paid_amount, proposed_amount, used_days, total_days, requested_at) "
                + "VALUES (?, ?, 'PRORATED', 'REQUESTED', 'MANUAL', 1100, 0, 1, 2, ?)", id(), openRefund, ts(yearsAgo(6)));
        Object depositedOn = payment(old).get("deposited_on");
        long doneRefund = id();
        jdbc.update("INSERT INTO biz_refunds (id, application_id, type, status, method, paid_amount, proposed_amount, used_days, total_days, approved_amount, transfer_status, "
                + "transferred_at, transfer_note, completed_at, requested_at, decided_at) VALUES (?, ?, 'PRORATED', 'APPROVED', 'MANUAL', 1100, 0, 1, 2, 0, 'DONE', ?, "
                + "'국민 123-45-678 홍길동', ?, ?, ?)", doneRefund, old, ts(yearsAgo(5.6)), ts(yearsAgo(5.6)), ts(yearsAgo(5.7)), ts(yearsAgo(5.6)));
        long oldDraft = draftContract(yearsAgo(6));
        long newDraft = draftContract(yearsAgo(1));

        jdbc.execute("CALL batch_biz_payment_record_purge('MANUAL', NULL, NULL)");

        Map<String, Object> erased = payment(old);
        assertThat(erased.get("content")).as("계약서 내용").isNull();
        assertThat(erased.get("file_erased")).isNotNull();
        assertThat(erased.get("file_name")).as("사본 줄은 남는다").isEqualTo("계약서.pdf");
        assertThat(erased.get("depositor_name")).as("입금자명").isNull();
        assertThat(erased.get("mismatch_reason")).as("불일치 사유").isNull();
        assertThat(erased.get("deposit_erased")).isNotNull();
        assertThat(((Number) erased.get("amount")).longValue()).as("입금액은 남는다").isEqualTo(1100L);
        assertThat(erased.get("deposited_on")).as("입금일은 남는다").isEqualTo(depositedOn);
        assertThat(count("SELECT count(*) FROM biz_applications WHERE id = ? AND status = 'SCHEDULED' AND approved_total = 1100", old)).as("신청 건 그대로").isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT transfer_note FROM biz_refunds WHERE id = ?", String.class, doneRefund)).as("환불 송금 메모").isNull();
        assertThat(count("SELECT count(*) FROM biz_refunds WHERE id = ? AND approved_amount = 0 AND transfer_status = 'DONE'", doneRefund)).as("환불 기록은 남는다").isEqualTo(1);
        assertThat(count("SELECT count(*) FROM biz_contract_files WHERE id = ? AND content IS NULL AND erased_at IS NOT NULL", oldDraft)).as("초안 사본 6년 — 비움").isEqualTo(1);
        assertThat(count("SELECT count(*) FROM biz_contract_files WHERE id = ? AND content IS NOT NULL AND erased_at IS NULL", newDraft)).as("초안 사본 1년 — 그대로").isEqualTo(1);
        for (long kept : new long[] {recent, openRefund}) {
            Map<String, Object> k = payment(kept);
            assertThat(k.get("content")).as("그대로 " + kept).isNotNull();
            assertThat(k.get("depositor_name")).as("그대로 " + kept).isEqualTo("홍길동");
        }
        assertThat(jdbc.queryForObject("SELECT message_key FROM batch_runs WHERE job_key = 'biz-payment-record-purge' ORDER BY started_at DESC, id DESC LIMIT 1", String.class))
                .isEqualTo("batch.result.bizPaymentRecordPurge");
    }

    /** 건에 붙지 않은 계약서 사본(운영자 등록 서랍의 초안 — application_id NULL) */
    private long draftContract(Instant uploadedAt) {
        long id = id();
        jdbc.update("INSERT INTO biz_contract_files (id, application_id, file_name, content_type, size_bytes, content, sha256, uploaded_by, uploaded_at) "
                + "VALUES (?, NULL, '초안.pdf', 'application/pdf', 3, '\\x010203'::bytea, ?, 1, ?)", id, "c".repeat(64), ts(uploadedAt));
        return id;
    }
}
