package com.community.trial;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.ad.repository.master.AdAdvertiserStore;
import com.community.addressbook.dto.AddressInput;
import com.community.addressbook.service.AddressBookService;
import com.community.bizapplication.BizApplicationApiTestBase;
import com.community.bizapplication.entity.BizApplicationKind;
import com.community.bizapplication.entity.BizApplicationStatus;
import com.community.bizapplication.entity.BizDomain;
import com.community.bizapplication.repository.master.BizApplicationStore.AppRow;
import com.community.bizapplication.service.BizApplicationReader;
import com.community.board.service.BoardCatalog;
import com.community.business.dto.BusinessSuspendPlan;
import com.community.business.entity.BusinessServiceKind;
import com.community.trial.batch.TrialDailyJob;
import com.community.trial.dto.TrialViews;
import com.community.trial.service.TrialMemberService;
import com.community.trial.service.TrialService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 유료 체험단 결제 연결(trial-group-contract 「유료 결제 연결」 — 점검 결정 D1·D7·D9 금액·L5, 커머스 규칙이라 규칙마다 시험) — 공개 사이트 앱 문맥 + 실제 PostgreSQL + 옮길 수 있는 시계 +
 * 가짜 PG. 체험단 처리(만들기·뽑기·입고·발송·취소)는 shared 서비스를 바로 부르고, 광고 신청 건 처리(승인·결제·만료·반려·취소·정지 훅)는 광고 신청 시험 바탕의 도우미로 부른다.
 * 관리자 HTTP 모양(심사 큐·상세·승인 본문·대시보드)은 관리자 시험 {@code TrialPaymentAdminHttpTest}.
 */
class TrialPaymentTest extends BizApplicationApiTestBase {

    @Autowired
    private BoardCatalog catalog;
    @Autowired
    private TrialService trials;
    @Autowired
    private TrialMemberService trialMembers;
    @Autowired
    private TrialDailyJob daily;
    @Autowired
    private AddressBookService addresses;
    @Autowired
    private AdAdvertiserStore advertisers;

    private long board;
    private long creator;
    private long advertiserUser;
    private long profile;
    private long advertiser;
    private Map<String, Object> savedRule;

    @BeforeEach
    void setUp() {
        board = ids.nextId();
        String slug = "tp" + Long.toString(board, 36);
        boardFixture.board(board, slug, "체험결제 " + slug.substring(slug.length() - 6), 962);
        // SQL 로 남김: 게시판 정책 칸은 관리자 저장 API 로 바뀐다 — 사용기 게시판 값(체험단 사용기 받기)만 세운다
        jdbc.update("UPDATE boards SET purchase_source_mode = 'REQUIRED', trial_reviews_enabled = true WHERE id = ?", board);
        catalog.invalidate();
        creator = newOperator(); // 모집을 만든 운영자 — 승인은 바탕의 다른 운영자(자기 승인 아님)
        advertiserUser = approvedBusiness();
        profile = profileOf(advertiserUser);
        advertiser = ids.nextId();
        tx.executeWithoutResult(s -> advertisers.insert(new AdAdvertiserStore.NewAdvertiser(advertiser, "결제광고주" + advertiser % 1_000_000, profile, null, null, null, null,
                null, null, null, creator, Instant.now())));
        savedRule = jdbc.queryForMap("SELECT unit_price, price_unit, shipping_fee_per_person FROM biz_price_rules WHERE product = 'TRIAL'");
    }

    @AfterEach
    void restoreRule() {
        jdbc.update("UPDATE biz_price_rules SET unit_price = ?, price_unit = ?, shipping_fee_per_person = ? WHERE product = 'TRIAL'", savedRule.get("unit_price"),
                savedRule.get("price_unit"), savedRule.get("shipping_fee_per_person"));
    }

    // ── 준비 ───────────────────────────────────────────────────────────

    /** 체험단 요금표 줄(모집을 만들 때의 값이 모집에 사본으로 남는다) */
    private void price(long unitPrice, String unit, long shippingFee) {
        jdbc.update("UPDATE biz_price_rules SET unit_price = ?, price_unit = ?, shipping_fee_per_person = ? WHERE product = 'TRIAL'", unitPrice, unit, shippingFee);
    }

    private long member() {
        long id = newMember();
        String b36 = Long.toString(id, 36);
        memberFixture.verifyEmail(id);
        memberFixture.profile(id, "p" + b36.substring(Math.max(0, b36.length() - 11)), "REGULAR");
        String hash = String.format("%064x", id);
        // SQL 로 남김: identity_verifications 는 엔티티가 없는 표다(가짜 본인인증 결과를 바로 둔다)
        jdbc.update("INSERT INTO identity_verifications (user_id, di_hash, ci_hash, birth_date, provider, provider_tx_id, verified_at) VALUES (?, ?, ?, ?, 'FAKE', ?, now())",
                id, hash, hash.replace('0', 'c'), java.time.LocalDate.of(1990, 1, 1), "tx-" + id);
        return id;
    }

    private static List<String> consents(String shipper) {
        return "ADVERTISER".equals(shipper) ? List.of("PROVIDE_CONTACT") : List.of(); // 제공형만 쓴다
    }

    /** 제공형 모집 만들기 + 열기 — 신청 시작 = 지금 */
    private long trial(String shipper, int recruit) {
        Instant start = clock.instant().truncatedTo(ChronoUnit.MINUTES);
        TrialViews.TrialForm f = new TrialViews.TrialForm(Long.toString(advertiser), "결제 체험 " + ids.nextId() % 100000, "무선 이어폰", 30000L, "GIVE", Long.toString(board),
                recruit, 0, 48, 24, start.toString(), start.plus(Duration.ofDays(3)).toString(), 14, null, shipper, "설명", null, null);
        String id = trials.create(creator, "127.0.0.1", f).id();
        trials.update(creator, "127.0.0.1", id, action("PUBLISH"));
        return Long.parseLong(id);
    }

    private static TrialViews.TrialForm action(String action) {
        return new TrialViews.TrialForm(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, action);
    }

    /** 신청자 {@code n} 명 — 회원 ID 들 */
    private List<Long> applicants(long trialId, String shipper, int n) {
        List<Long> out = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            long m = member();
            long addr = addresses.create(m, new AddressInput("집", "홍길동", "01012345678", "06236", "서울 강남구 테헤란로 1", "101호", null, true)).id();
            trialMembers.apply(m, Long.toString(trialId), new TrialViews.ApplyRequest(Long.toString(addr), consents(shipper)));
            out.add(m);
        }
        return out;
    }

    /** 신청 마감 뒤로 시계를 옮기고 모두 뽑는다 */
    private void selectAll(long trialId) {
        Instant end = jdbc.queryForObject("SELECT apply_end_at FROM trials WHERE id = ?", java.sql.Timestamp.class, trialId).toInstant();
        if (clock.instant().isBefore(end)) {
            clock.set(end.plusSeconds(60));
        }
        List<String> ids = jdbc.queryForList("SELECT id::text FROM trial_applications WHERE trial_id = ? AND status = 'APPLIED' ORDER BY id", String.class, trialId);
        trials.select(creator, "127.0.0.1", Long.toString(trialId), new TrialViews.SelectRequest(ids));
    }

    /** 뽑기까지 — 모집 ID */
    private long selectedTrial(String shipper, int count) {
        long t = trial(shipper, count);
        applicants(t, shipper, count);
        selectAll(t);
        return t;
    }

    private long bizId(long trialId) {
        return jdbc.queryForObject("SELECT biz_application_id FROM trials WHERE id = ?", Long.class, trialId);
    }

    private long participant(long trialId, int index) {
        return jdbc.queryForList("SELECT id FROM trial_participants WHERE trial_id = ? ORDER BY id", Long.class, trialId).get(index);
    }

    private long userOf(long participantId) {
        return jdbc.queryForObject("SELECT user_id FROM trial_participants WHERE id = ?", Long.class, participantId);
    }

    private String trialStatus(long trialId) {
        return jdbc.queryForObject("SELECT status FROM trials WHERE id = ?", String.class, trialId);
    }

    private TrialViews.TrialDetail detail(long trialId) {
        return trials.detail(Long.toString(trialId), op());
    }

    private TrialService.Actor op() {
        return TrialService.Actor.operator(creator, "127.0.0.1");
    }

    private TrialService.Actor adv() {
        return TrialService.Actor.advertiser(advertiserUser, advertiser, "127.0.0.1");
    }

    /** 승인 — 결제 기한·기간·금액은 비운다(체험단은 견적 그대로 · 기한 = 승인 + 설정값) */
    private void approveTrial(long appId, String method) {
        review.approve(operator, appId, method, null, null, null, null, null, null);
    }

    private int notices(long memberId, String type) {
        return jdbc.queryForObject("SELECT count(*) FROM member_notifications WHERE user_id = ? AND type = ?", Integer.class, memberId, type);
    }

    private static byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
        image.setRGB(3, 3, 0xff0000);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private String photo(TrialService.Actor actor, long trialId, String kind) throws Exception {
        return trials.uploadPhoto(actor, Long.toString(trialId), kind, png()).id();
    }

    /** 운영자 입고(사진 1장) */
    private void stock(long trialId, int quantity) throws Exception {
        trials.stock(creator, "127.0.0.1", Long.toString(trialId), new TrialViews.StockRequest(quantity, null, List.of(photo(op(), trialId, "STOCK"))));
    }

    /** 광고주 발송(발송 전 사진 1장) */
    private void shipByAdvertiser(long trialId, long participantId) throws Exception {
        trials.ship(adv(), Long.toString(participantId), new TrialViews.ShipmentRequest("CJ", "T" + participantId, List.of(photo(adv(), trialId, "PRE_SHIP"))));
    }

    private boolean inAdQueue(long appId) {
        return store.forAdmin(BizDomain.AD, null, null).stream().anyMatch(a -> a.id() == appId);
    }

    // ── 견적 · 승인 · 결제 · 발송 ───────────────────────────────────────────

    @Test
    void 유료_뽑기는_견적을_굳혀_심사큐에_나오고_승인은_견적_그대로_결제_전에는_입고_발송_409_결제는_광고주_회원만() throws Exception {
        price(50000, "PER_PERSON", 3000);
        long t = selectedTrial("OPERATOR", 2);
        long appId = bizId(t);
        AppRow a = app(appId);
        // 뽑기 = 견적 굳힘(인원 2 × 50,000 + 대신 발송 2 × 3,000 = 106,000 + 부가세) · 광고주 회원을 건의 회원으로 · 심사 대기
        assertThat(a.status()).isEqualTo(BizApplicationStatus.SUBMITTED);
        assertThat(a.quote().total()).isEqualTo(116600L);
        assertThat(a.userId()).isEqualTo(advertiserUser);
        assertThat(store.items(appId)).extracting(i -> i.product().name() + ":" + i.unitPrice() + "x" + i.units()).containsExactlyInAnyOrder("TRIAL:50000x2",
                "TRIAL_SHIPPING:3000x2");
        assertThat(inAdQueue(appId)).isTrue();
        assertThat(store.forAdmin(BizDomain.AD, BizApplicationKind.TRIAL, null)).extracting(AppRow::id).contains(appId);
        assertThat(BizApplicationReader.queue(a, null, clock.instant())).isEqualTo(BizApplicationReader.Queue.REVIEW);
        assertThat(store.events(appId)).extracting(e -> e.action() + ":" + e.note()).contains("TRIAL_QUOTED:116600");
        TrialViews.Payment p = detail(t).payment();
        assertThat(p.required()).isTrue();
        assertThat(p.status()).isEqualTo("AWAITING_APPROVAL");
        assertThat(p.shippingAllowed()).isFalse();
        assertThat(p.total()).isEqualTo(116600L);
        assertThat(p.bizApplicationId()).isEqualTo(Long.toString(appId));

        // 결제 전 — 입고·발송 409
        assertThat(codeOf(() -> stock(t, 2))).isEqualTo("TRIAL_PAYMENT_PENDING");
        long p1 = participant(t, 0);
        assertThat(codeOf(() -> trials.ship(op(), Long.toString(p1), new TrialViews.ShipmentRequest("CJ", "1", null)))).isEqualTo("TRIAL_PAYMENT_PENDING");

        // 승인 — 금액은 견적 그대로(다른 공급가 400 fields.supply) · 기간 검사 없음(시작이 지났어도) · 줄을 바꾸지 않는다
        List<Long> itemIds = store.items(appId).stream().map(i -> i.id()).toList();
        assertThat(fieldsOf(() -> review.approve(operator, appId, "ONLINE", 100000L, null, null, null, null, null))).containsExactly("supply");
        Instant approvedAt = clock.instant();
        approveTrial(appId, "ONLINE");
        a = app(appId);
        assertThat(a.status()).isEqualTo(BizApplicationStatus.AWAITING_PAYMENT);
        assertThat(a.approved().total()).isEqualTo(116600L);
        assertThat(a.approved().supply()).isEqualTo(106000L);
        assertThat(a.paymentDeadlineAt()).isEqualTo(approvedAt.plus(Duration.ofHours(72)).truncatedTo(ChronoUnit.MICROS)); // 결제 기한 = 승인 + 72시간(설정값)
        assertThat(store.items(appId).stream().map(i -> i.id()).toList()).containsExactlyInAnyOrderElementsOf(itemIds);
        assertThat(detail(t).payment().status()).isEqualTo("AWAITING_PAYMENT");
        assertThat(detail(t).payment().payOnline()).isTrue();
        assertThat(codeOf(() -> trials.ship(op(), Long.toString(p1), new TrialViews.ShipmentRequest("CJ", "1", null)))).isEqualTo("TRIAL_PAYMENT_PENDING");

        // 온라인 결제는 그 건의 회원(광고주 사업자 회원)만 — 남은 404
        long stranger = approvedBusiness();
        assertThat(codeOf(() -> members.startPayment(stranger, appId))).isEqualTo("BIZ_APPLICATION_NOT_FOUND");
        pay(advertiserUser, appId);
        assertThat(app(appId).status()).isEqualTo(BizApplicationStatus.SCHEDULED);
        assertThat(BizApplicationReader.adminStatus(app(appId), clock.instant())).isEqualTo("RUNNING"); // 체험단 건은 시각이 아니라 모집이 끝났는지로
        p = detail(t).payment();
        assertThat(p.status()).isEqualTo("PAID");
        assertThat(p.shippingAllowed()).isTrue();

        // 결제 뒤 — 입고·발송 통과
        stock(t, 2);
        trials.ship(op(), Long.toString(p1), new TrialViews.ShipmentRequest("CJ", "1", null));
        assertThat(notices(userOf(p1), "TRIAL_SHIPPED")).isEqualTo(1);

        // 끝내기(L5) — 결제 끝 건은 상태 그대로 닫은 시각만 · 끝내 보내지 않은 줄 주소 파기
        long p2 = participant(t, 1);
        trials.update(creator, "127.0.0.1", Long.toString(t), action("CLOSE"));
        a = app(appId);
        assertThat(a.status()).isEqualTo(BizApplicationStatus.SCHEDULED);
        assertThat(a.closedAt()).isNotNull();
        assertThat(BizApplicationReader.adminStatus(a, clock.instant())).isEqualTo("ENDED");
        assertThat(BizApplicationReader.queue(a, null, clock.instant())).isEqualTo(BizApplicationReader.Queue.CLOSED);
        assertThat(store.events(appId)).extracting(e -> e.action()).contains("TRIAL_CLOSED");
        assertThat(jdbc.queryForObject("SELECT a.address_purged_at IS NOT NULL FROM trial_participants p JOIN trial_applications a ON a.id = p.application_id WHERE p.id = ?",
                Boolean.class, p2)).isTrue();
        assertThat(jdbc.queryForObject("SELECT a.address_purged_at IS NULL FROM trial_participants p JOIN trial_applications a ON a.id = p.application_id WHERE p.id = ?",
                Boolean.class, p1)).isTrue();
    }

    @Test
    void 영원_체험단은_결제_없이_입고_발송_심사큐_밖_끝내면_신청건_정리() throws Exception {
        price(0, "PER_PERSON", 0);
        long t = selectedTrial("OPERATOR", 2);
        long appId = bizId(t);
        AppRow a = app(appId);
        assertThat(a.status()).isEqualTo(BizApplicationStatus.SUBMITTED);
        assertThat(a.quote().total()).isZero();
        assertThat(a.userId()).isNull();
        assertThat(inAdQueue(appId)).isFalse();
        assertThat(detail(t).payment().status()).isEqualTo("NOT_REQUIRED");
        assertThat(detail(t).payment().required()).isFalse();
        assertThat(detail(t).payment().shippingAllowed()).isTrue();
        assertThat(codeOf(() -> approveTrial(appId, "MANUAL"))).isEqualTo("BIZ_APPLICATION_STATE_CHANGED"); // 0원은 승인할 것이 없다

        stock(t, 2);
        trials.ship(op(), Long.toString(participant(t, 0)), new TrialViews.ShipmentRequest("CJ", "1", null));
        trials.update(creator, "127.0.0.1", Long.toString(t), action("CLOSE"));
        a = app(appId);
        assertThat(a.status()).isEqualTo(BizApplicationStatus.CANCELED); // L5 — 심사 대기로 남은 건 정리
        assertThat(a.closedAt()).isNotNull();
        assertThat(store.events(appId)).extracting(e -> e.action() + ":" + e.note()).contains("CANCELED:TRIAL_CLOSED");
        assertThat(inAdQueue(appId)).isFalse();
    }

    // ── 모집 취소 ↔ 신청 건 ──────────────────────────────────────────────

    @Test
    void 모집_취소_결제대기면_신청건_취소_입금기록이면_수동환불_결제뒤_발송전이면_전액환불_발송이_있으면_409() throws Exception {
        price(50000, "PER_PERSON", 0);
        // ① 수동 승인 + 입금 기록 → 모집 취소 = 신청 건 취소 + 입금액 수동 전액 환불(송금 대기)
        long t1 = selectedTrial("ADVERTISER", 1);
        long app1 = bizId(t1);
        approveTrial(app1, "MANUAL");
        assertThat(app(app1).status()).isEqualTo(BizApplicationStatus.AWAITING_DEPOSIT);
        review.saveDeposit(operator, app1, 55000L, today().toString(), "시험상사", null);
        long m1 = userOf(participant(t1, 0));
        trials.update(creator, "127.0.0.1", Long.toString(t1), action("CANCEL"));
        assertThat(trialStatus(t1)).isEqualTo("CANCELLED");
        assertThat(app(app1).status()).isEqualTo(BizApplicationStatus.CANCELED);
        assertThat(jdbc.queryForMap("SELECT type, status, method, approved_amount, transfer_status FROM biz_refunds WHERE application_id = ?", app1))
                .containsEntry("type", "FULL_AUTO").containsEntry("status", "APPROVED").containsEntry("method", "MANUAL").containsEntry("approved_amount", 55000L)
                .containsEntry("transfer_status", "PENDING");
        assertThat(notices(m1, "TRIAL_CANCELLED_BY_OPERATOR")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM trial_participants WHERE trial_id = ? AND cancelled_at IS NOT NULL", Integer.class, t1)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM trial_applications WHERE trial_id = ? AND address_purged_at IS NULL", Integer.class, t1)).isZero();
        assertThat(lastAudit("TRIAL", Long.toString(t1), "TRIAL_CANCEL").get("detail").toString()).contains("55000");

        // ② 온라인 결제 뒤 · 발송 전 → 모집 취소 = 전액 환불(일할 없음 — PG 취소 요청)
        long t2 = selectedTrial("ADVERTISER", 1);
        long app2 = bizId(t2);
        approveTrial(app2, "ONLINE");
        pay(advertiserUser, app2);
        trials.update(creator, "127.0.0.1", Long.toString(t2), action("CANCEL"));
        assertThat(app(app2).status()).isEqualTo(BizApplicationStatus.CANCELED);
        Map<String, Object> refund = jdbc.queryForMap("SELECT type, status, approved_amount, payment_cancel_id FROM biz_refunds WHERE application_id = ?", app2);
        assertThat(refund).containsEntry("type", "FULL_AUTO").containsEntry("status", "APPROVED").containsEntry("approved_amount", 55000L);
        assertThat(refund.get("payment_cancel_id")).isNotNull();

        // ③ 결제 뒤 발송이 있었으면 모집 취소·광고 쪽 취소 모두 409 — 돌려줄 몫은 운영자 추가 환불(클레임)
        long t3 = selectedTrial("ADVERTISER", 1);
        long app3 = bizId(t3);
        approveTrial(app3, "ONLINE");
        pay(advertiserUser, app3);
        shipByAdvertiser(t3, participant(t3, 0));
        assertThat(codeOf(() -> trials.update(creator, "127.0.0.1", Long.toString(t3), action("CANCEL")))).isEqualTo("TRIAL_SHIPPING_STARTED");
        assertThat(codeOf(() -> review.cancel(operator, app3, "광고주 요청"))).isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
        assertThat(app(app3).status()).isEqualTo(BizApplicationStatus.SCHEDULED);
        review.claimRefund(operator, app3, 20000L, "일부 인원 못 보냄");
        assertThat(jdbc.queryForObject("SELECT approved_amount FROM biz_refunds WHERE application_id = ? AND type = 'CLAIM'", Long.class, app3)).isEqualTo(20000L);
    }

    @Test
    void 광고쪽_결제기한_만료_반려_운영자취소는_모집을_취소하고_사업자는_체험단_건을_취소_환불요청할_수_없다() throws Exception {
        price(40000, "PER_RECRUIT", 0);
        // 만료
        long t1 = selectedTrial("ADVERTISER", 1);
        long app1 = bizId(t1);
        assertThat(app(app1).quote().total()).isEqualTo(44000L); // 건당 — 인원과 상관없이
        approveTrial(app1, "ONLINE");
        assertThat(codeOf(() -> members.cancel(advertiserUser, app1))).isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
        assertThat(app(app1).status()).isEqualTo(BizApplicationStatus.AWAITING_PAYMENT);
        clock.set(app(app1).paymentDeadlineAt().plusSeconds(1));
        assertThat(schedules.expire(app1)).isTrue();
        assertThat(app(app1).status()).isEqualTo(BizApplicationStatus.EXPIRED);
        assertThat(trialStatus(t1)).isEqualTo("CANCELLED");
        assertThat(notices(userOf(participant(t1, 0)), "TRIAL_CANCELLED_BY_OPERATOR")).isEqualTo(1);

        // 반려
        long t2 = selectedTrial("ADVERTISER", 1);
        long app2 = bizId(t2);
        review.reject(operator, app2, "OTHER", "광고주 연락 안 됨");
        assertThat(trialStatus(t2)).isEqualTo("CANCELLED");

        // 결제 뒤·발송 전 운영자 취소 → 전액 환불 + 모집 취소
        long t3 = selectedTrial("ADVERTISER", 1);
        long app3 = bizId(t3);
        approveTrial(app3, "ONLINE");
        pay(advertiserUser, app3);
        assertThat(codeOf(() -> members.requestRefund(advertiserUser, app3, "필요 없어짐"))).isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
        review.cancel(operator, app3, "광고주 요청");
        assertThat(app(app3).status()).isEqualTo(BizApplicationStatus.CANCELED);
        assertThat(jdbc.queryForObject("SELECT approved_amount FROM biz_refunds WHERE application_id = ? AND type = 'FULL_AUTO'", Long.class, app3)).isEqualTo(44000L);
        assertThat(trialStatus(t3)).isEqualTo("CANCELLED");
    }

    // ── 뽑힌 줄 취소(D9) ─────────────────────────────────────────────────

    @Test
    void 뽑힌줄_취소는_발송_전만_결제_전이면_금액_다시셈_승인뒤면_심사대기로_결제뒤면_금액_그대로_취소한_줄은_자동끝남_판정에서_빠진다() throws Exception {
        price(50000, "PER_PERSON", 0);
        long t = selectedTrial("ADVERTISER", 4);
        long appId = bizId(t);
        long p1 = participant(t, 0);
        long p2 = participant(t, 1);
        long p3 = participant(t, 2);
        long p4 = participant(t, 3);
        assertThat(app(appId).quote().total()).isEqualTo(220000L);

        assertThat(fieldsOf(() -> trials.cancelParticipant(creator, "ip", Long.toString(p1), new TrialViews.ReasonRequest(" ")))).containsExactly("reason");
        // ① 심사 대기 — 3명으로 다시 셈
        TrialViews.TrialDetail d = trials.cancelParticipant(creator, "ip", Long.toString(p1), new TrialViews.ReasonRequest("연락이 닿지 않음"));
        assertThat(d.selectedCount()).isEqualTo(3);
        assertThat(d.amount()).isEqualTo(150000L);
        assertThat(d.counts().cancelled()).isEqualTo(1);
        assertThat(d.participants().stream().filter(x -> x.id().equals(Long.toString(p1))).findFirst().orElseThrow().cancelReason()).isEqualTo("연락이 닿지 않음");
        assertThat(app(appId).quote().total()).isEqualTo(165000L);
        assertThat(store.items(appId)).extracting(i -> i.product().name() + "x" + i.units()).containsExactly("TRIALx3");
        assertThat(store.events(appId)).extracting(e -> e.action() + ":" + e.note()).contains("TRIAL_REQUOTED:165000");
        assertThat(notices(userOf(p1), "TRIAL_CANCELLED_BY_OPERATOR")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT a.address_purged_at IS NOT NULL FROM trial_participants p JOIN trial_applications a ON a.id = p.application_id WHERE p.id = ?",
                Boolean.class, p1)).isTrue();
        assertThat(lastAudit("TRIAL", Long.toString(t), "TRIAL_PARTICIPANT_CANCEL").get("detail").toString()).contains("연락이 닿지 않음");
        assertThat(codeOf(() -> trials.cancelParticipant(creator, "ip", Long.toString(p1), new TrialViews.ReasonRequest("다시")))).isEqualTo("TRIAL_PARTICIPANT_CANCELLED");
        assertThat(codeOf(() -> shipByAdvertiser(t, p1))).isEqualTo("TRIAL_PARTICIPANT_CANCELLED");

        // ② 승인 뒤 결제 전(입금 기록 없음) — 심사 대기로 되돌리고 2명으로 다시 셈
        approveTrial(appId, "MANUAL");
        trials.cancelParticipant(creator, "ip", Long.toString(p2), new TrialViews.ReasonRequest("주소 오류"));
        AppRow a = app(appId);
        assertThat(a.status()).isEqualTo(BizApplicationStatus.SUBMITTED);
        assertThat(a.approved()).isNull();
        assertThat(a.quote().total()).isEqualTo(110000L);
        assertThat(inAdQueue(appId)).isTrue();

        // ③ 결제 뒤 — 뽑힌 수만 줄고 금액은 그대로(돌려줄 몫은 운영자 추가 환불)
        approveTrial(appId, "ONLINE");
        pay(advertiserUser, appId);
        d = trials.cancelParticipant(creator, "ip", Long.toString(p3), new TrialViews.ReasonRequest("본인 요청"));
        assertThat(d.selectedCount()).isEqualTo(1);
        assertThat(d.amount()).isEqualTo(100000L);
        assertThat(app(appId).approved().total()).isEqualTo(110000L);
        assertThat(app(appId).status()).isEqualTo(BizApplicationStatus.SCHEDULED);

        // ④ 보낸 줄은 취소할 수 없다
        shipByAdvertiser(t, p4);
        assertThat(codeOf(() -> trials.cancelParticipant(creator, "ip", Long.toString(p4), new TrialViews.ReasonRequest("늦음")))).isEqualTo("TRIAL_SHIPPING_STARTED");

        // ⑤ 남은 줄이 판정되면 자동 끝남 — 취소한 세 줄은 판정을 기다리지 않는다(L5 — 결제 끝 건은 닫은 시각)
        // SQL 로 남김: 약속 지킴 판정은 사용기 글 연결·반납 흐름이 만든다 — 자동 끝남 판정만 보려고 남은 줄의 판정을 바로 둔다
        jdbc.update("UPDATE trial_participants SET kept = true, kept_at = now() WHERE id = ?", p4);
        daily.runOnce();
        assertThat(trialStatus(t)).isEqualTo("CLOSED");
        assertThat(app(appId).closedAt()).isNotNull();
        assertThat(app(appId).status()).isEqualTo(BizApplicationStatus.SCHEDULED);
    }

    // ── 사업자 정지·탈퇴 훅(D7) ──────────────────────────────────────────

    @Test
    void 사업자_정지는_모집_전_단계_모집을_취소하고_진행중은_그대로_영향은_체험단을_따로_센다_탈퇴도_같다() throws Exception {
        price(0, "PER_PERSON", 0);
        long recruiting = trial("ADVERTISER", 2);
        long applicant = applicants(recruiting, "ADVERTISER", 1).get(0);
        long running = selectedTrial("ADVERTISER", 1);
        clock.set(Instant.now().truncatedTo(ChronoUnit.MICROS)); // 뽑기로 옮긴 시계를 되돌린다(모집 중 모집이 마감 전이 되게)
        long runningApp = bizId(running);

        assertThat(hook.impact(profile)).isEqualTo(new com.community.business.service.BusinessApplicationHook.SuspendImpact(0, 0, 0, 1, 1));
        assertThat(hook.counts(profile, BusinessServiceKind.AD)).isEqualTo(new com.community.business.service.BusinessApplicationHook.ServiceCounts(0, 0, 1, 1));
        assertThat(hook.counts(profile, BusinessServiceKind.HOTDEAL)).isEqualTo(new com.community.business.service.BusinessApplicationHook.ServiceCounts(0, 0));
        assertThat(hook.withdrawalCheck(profile).blocked()).isFalse(); // 진행 중 체험단은 탈퇴를 막지 않는다(그대로 진행)

        tx.executeWithoutResult(s -> hook.onSuspended(profile, new BusinessSuspendPlan(BusinessSuspendPlan.Pending.KEEP, BusinessSuspendPlan.Scheduled.HOLD,
                BusinessSuspendPlan.Running.TAKEDOWN_ONLY), operator.operatorId()));
        assertThat(trialStatus(recruiting)).isEqualTo("CANCELLED");
        assertThat(app(bizId(recruiting)).status()).isEqualTo(BizApplicationStatus.CANCELED);
        assertThat(notices(applicant, "TRIAL_NOT_SELECTED")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT address_purged_at IS NOT NULL FROM trial_applications WHERE trial_id = ?", Boolean.class, recruiting)).isTrue();
        assertThat(trialStatus(running)).isEqualTo("RUNNING");
        assertThat(app(runningApp).status()).isEqualTo(BizApplicationStatus.SUBMITTED);
        assertThat(lastAudit("TRIAL", Long.toString(recruiting), "TRIAL_CANCEL").get("detail").toString()).contains("SUSPENDED");

        long draft = Long.parseLong(trials.create(creator, "ip", new TrialViews.TrialForm(Long.toString(advertiser), "초안 체험", "이어폰", 10000L, "GIVE",
                Long.toString(board), 1, 0, 48, 24, clock.instant().truncatedTo(ChronoUnit.MINUTES).toString(),
                clock.instant().plus(Duration.ofDays(2)).truncatedTo(ChronoUnit.MINUTES).toString(), 14, null, "ADVERTISER", "", null, null)).id());
        tx.executeWithoutResult(s -> hook.onWithdrawn(profile, false));
        assertThat(trialStatus(draft)).isEqualTo("CANCELLED");
        assertThat(trialStatus(running)).isEqualTo("RUNNING");
    }
}
