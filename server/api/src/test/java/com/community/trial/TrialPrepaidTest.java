package com.community.trial;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.ad.repository.master.AdAdvertiserStore;
import com.community.addressbook.dto.AddressInput;
import com.community.addressbook.service.AddressBookService;
import com.community.bizapplication.BizApplicationApiTestBase;
import com.community.bizapplication.entity.BizApplicationStatus;
import com.community.bizapplication.entity.BizDomain;
import com.community.bizapplication.repository.master.BizApplicationStore.AppRow;
import com.community.board.service.BoardCatalog;
import com.community.trial.dto.TrialViews;
import com.community.trial.service.TrialMemberService;
import com.community.trial.service.TrialPayments;
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
 * 체험단 광고비 <b>선결제</b>(V244 기본 — trial-group-contract 「유료 결제 연결」, 2026-11-17 사용자 결정 「선결제 차액 환불 없어야지 … 선결제가 우선」, 커머스 규칙이라 규칙마다 시험) —
 * 공개 사이트 앱 문맥 + 실제 PostgreSQL + 옮길 수 있는 시계 + 가짜 PG. 후불(POSTPAID) 규칙은 {@code TrialPaymentTest}.
 *
 * <ul>
 *   <li>견적 = 모집 인원으로(인원당 단가 × 모집 인원 · 건당 단가, 운영자 대신 발송이면 + 1명당 발송비 × 모집 인원), 저장 때 굳혀 광고 심사 큐 → 승인 → 결제.</li>
 *   <li>결제가 끝나기 전 공개 409 {@code TRIAL_PREPAY_REQUIRED} · 0원은 결제 없이 공개.</li>
 *   <li>결제 전 고치기는 다시 셈(승인 뒤면 심사 대기로) · 결제 뒤 금액에 닿는 칸 409 {@code TRIAL_PREPAID_LOCKED}.</li>
 *   <li>덜 뽑혀도·뽑힌 줄을 취소해도 금액 그대로(차액 환불 없음).</li>
 *   <li>결제 뒤 모집 취소 = 전액 환불(공개 전·발송 전) · 발송이 있으면 409. 공개 전 만료·반려·광고 쪽 취소면 모집은 초안 그대로 → 「결제 다시 요청」 = 새 신청 건.</li>
 * </ul>
 */
class TrialPrepaidTest extends BizApplicationApiTestBase {

    @Autowired
    private BoardCatalog catalog;
    @Autowired
    private TrialService trials;
    @Autowired
    private TrialMemberService trialMembers;
    @Autowired
    private TrialPayments payments;
    @Autowired
    private AddressBookService addresses;
    @Autowired
    private AdAdvertiserStore advertisers;

    private long board;
    private long creator;
    private long advertiserUser;
    private long advertiser;
    private Map<String, Object> savedRule;

    @BeforeEach
    void setUp() {
        board = ids.nextId();
        String slug = "tv" + Long.toString(board, 36);
        boardFixture.board(board, slug, "선결제 " + slug.substring(slug.length() - 6), 963);
        // SQL 로 남김: 게시판 정책 칸은 관리자 저장 API 로 바뀐다 — 사용기 게시판 값(체험단 사용기 받기)만 세운다
        jdbc.update("UPDATE boards SET purchase_source_mode = 'REQUIRED', trial_reviews_enabled = true WHERE id = ?", board);
        catalog.invalidate();
        creator = newOperator(); // 모집을 만든 운영자 — 승인은 바탕의 다른 운영자(자기 승인 아님)
        advertiserUser = approvedBusiness();
        long profile = profileOf(advertiserUser);
        advertiser = ids.nextId();
        tx.executeWithoutResult(s -> advertisers.insert(new AdAdvertiserStore.NewAdvertiser(advertiser, "선결제광고주" + advertiser % 1_000_000, profile, null, null, null,
                null, null, null, null, creator, Instant.now())));
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

    private TrialViews.TrialForm form(String title, String product, String shipper, int recruit, String timing, Instant start) {
        return new TrialViews.TrialForm(Long.toString(advertiser), title, product, 30000L, "GIVE", Long.toString(board), recruit, 0, 48, 24, start.toString(),
                start.plus(Duration.ofDays(3)).toString(), 14, null, shipper, "설명", null, timing, null);
    }

    /** 제공형 모집 만들기(초안 — 결제 때 칸을 비우면 선결제 기본) · 신청 시작 = 지금 */
    private long create(String shipper, int recruit, String timing) {
        Instant start = clock.instant().truncatedTo(ChronoUnit.MINUTES);
        return Long.parseLong(trials.create(creator, "127.0.0.1", form("선결제 체험 " + ids.nextId() % 100000, "무선 이어폰", shipper, recruit, timing, start)).id());
    }

    /** 칸 고치기 — 신청 기간은 그대로 */
    private TrialViews.TrialDetail edit(long trialId, String title, String product, String shipper, int recruit, String timing) {
        Instant start = jdbc.queryForObject("SELECT apply_start_at FROM trials WHERE id = ?", java.sql.Timestamp.class, trialId).toInstant();
        return trials.update(creator, "127.0.0.1", Long.toString(trialId), form(title, product, shipper, recruit, timing, start));
    }

    private String title(long trialId) {
        return jdbc.queryForObject("SELECT title FROM trials WHERE id = ?", String.class, trialId);
    }

    private TrialViews.TrialDetail act(long trialId, String action) {
        return trials.update(creator, "127.0.0.1", Long.toString(trialId),
                new TrialViews.TrialForm(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, action));
    }

    private long member() {
        long id = newMember();
        String b36 = Long.toString(id, 36);
        memberFixture.verifyEmail(id);
        memberFixture.profile(id, "v" + b36.substring(Math.max(0, b36.length() - 11)), "REGULAR");
        String hash = String.format("%064x", id);
        // SQL 로 남김: identity_verifications 는 엔티티가 없는 표다(가짜 본인인증 결과를 바로 둔다)
        jdbc.update("INSERT INTO identity_verifications (user_id, di_hash, ci_hash, birth_date, provider, provider_tx_id, verified_at) VALUES (?, ?, ?, ?, 'FAKE', ?, now())",
                id, hash, hash.replace('0', 'c'), java.time.LocalDate.of(1990, 1, 1), "tx-" + id);
        return id;
    }

    private void applicants(long trialId, String shipper, int n) {
        for (int i = 0; i < n; i++) {
            long m = member();
            long addr = addresses.create(m, new AddressInput("집", "홍길동", "01012345678", "06236", "서울 강남구 테헤란로 1", "101호", null, true)).id();
            trialMembers.apply(m, Long.toString(trialId), new TrialViews.ApplyRequest(Long.toString(addr), "ADVERTISER".equals(shipper) ? List.of("PROVIDE_CONTACT")
                    : List.of()));
        }
    }

    /** 신청 마감 뒤로 시계를 옮기고 모두 뽑는다 */
    private TrialViews.TrialDetail selectAll(long trialId) {
        Instant end = jdbc.queryForObject("SELECT apply_end_at FROM trials WHERE id = ?", java.sql.Timestamp.class, trialId).toInstant();
        if (clock.instant().isBefore(end)) {
            clock.set(end.plusSeconds(60));
        }
        List<String> picked = jdbc.queryForList("SELECT id::text FROM trial_applications WHERE trial_id = ? AND status = 'APPLIED' ORDER BY id", String.class, trialId);
        return trials.select(creator, "127.0.0.1", Long.toString(trialId), new TrialViews.SelectRequest(picked));
    }

    private long bizId(long trialId) {
        return jdbc.queryForObject("SELECT biz_application_id FROM trials WHERE id = ?", Long.class, trialId);
    }

    private long participant(long trialId, int index) {
        return jdbc.queryForList("SELECT id FROM trial_participants WHERE trial_id = ? ORDER BY id", Long.class, trialId).get(index);
    }

    private String trialStatus(long trialId) {
        return jdbc.queryForObject("SELECT status FROM trials WHERE id = ?", String.class, trialId);
    }

    private Long amount(long trialId) {
        return jdbc.queryForObject("SELECT amount FROM trials WHERE id = ?", Long.class, trialId);
    }

    private TrialViews.Payment payment(long trialId) {
        return trials.detail(Long.toString(trialId), TrialService.Actor.operator(creator, "127.0.0.1")).payment();
    }

    private boolean inAdQueue(long appId) {
        return store.forAdmin(BizDomain.AD, null, null).stream().anyMatch(a -> a.id() == appId);
    }

    /** 승인 — 결제 기한·기간·금액은 비운다(체험단은 견적 그대로 · 기한 = 승인 + 설정값) */
    private void approveTrial(long appId, String method) {
        review.approve(operator, appId, method, null, null, null, null, null, null);
    }

    private List<String> events(long appId) {
        return store.events(appId).stream().map(e -> e.action() + ":" + e.note()).toList();
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

    private TrialService.Actor op() {
        return TrialService.Actor.operator(creator, "127.0.0.1");
    }

    private TrialService.Actor adv() {
        return TrialService.Actor.advertiser(advertiserUser, advertiser, "127.0.0.1");
    }

    // ── 견적 · 공개 · 고치기 · 뽑기 ──────────────────────────────────────

    @Test
    void 선결제_견적은_모집인원_인원당_발송비로_저장때_굳고_결제전_공개409_결제전_고치기는_다시셈_결제뒤_금액칸_409_덜뽑혀도_취소해도_금액그대로() throws Exception {
        price(50000, "PER_PERSON", 3000);
        long t = create("OPERATOR", 3, null); // 결제 때 칸 없음 = 선결제
        long appId = bizId(t);
        AppRow a = app(appId);
        // 견적 = 모집 3명 × 50,000 + 대신 발송 3명 × 3,000 = 159,000 + 부가세 — 저장 때 굳혀 심사 대기
        assertThat(a.status()).isEqualTo(BizApplicationStatus.SUBMITTED);
        assertThat(a.quote().supply()).isEqualTo(159000L);
        assertThat(a.quote().total()).isEqualTo(174900L);
        assertThat(a.userId()).isEqualTo(advertiserUser);
        assertThat(store.items(appId)).extracting(i -> i.product().name() + ":" + i.unitPrice() + "x" + i.units()).containsExactlyInAnyOrder("TRIAL:50000x3",
                "TRIAL_SHIPPING:3000x3");
        assertThat(inAdQueue(appId)).isTrue();
        assertThat(events(appId)).contains("TRIAL_QUOTED:174900");
        TrialViews.TrialDetail d = trials.detail(Long.toString(t), op());
        assertThat(d.paymentTiming()).isEqualTo("PREPAID");
        assertThat(d.amount()).isEqualTo(159000L);
        assertThat(d.selectedCount()).isNull();
        assertThat(d.payment().timing()).isEqualTo("PREPAID");
        assertThat(d.payment().status()).isEqualTo("AWAITING_APPROVAL");
        assertThat(d.payment().required()).isTrue();
        assertThat(d.payment().publishAllowed()).isFalse();
        assertThat(d.payment().requestAllowed()).isFalse();
        assertThat(trials.list(null)).filteredOn(r -> r.id().equals(Long.toString(t))).singleElement()
                .satisfies(r -> assertThat(r.paymentTiming() + ":" + r.paymentStatus()).isEqualTo("PREPAID:AWAITING_APPROVAL"));

        // 결제 전 공개 409
        assertThat(codeOf(() -> act(t, "PUBLISH"))).isEqualTo("TRIAL_PREPAY_REQUIRED");
        assertThat(codeOf(() -> act(t, "REQUEST_PAYMENT"))).isEqualTo("TRIAL_STATE_INVALID"); // 이미 요청했다

        // 결제 전 고치기 — 심사 대기면 그 자리에서 다시 셈(2명 = 106,000)
        edit(t, title(t), "무선 이어폰", "OPERATOR", 2, null);
        assertThat(app(appId).quote().total()).isEqualTo(116600L);
        assertThat(amount(t)).isEqualTo(106000L);
        assertThat(events(appId)).contains("TRIAL_REQUOTED:116600");
        // 금액에 닿지 않는 고치기(제목)는 견적을 건드리지 않는다
        int before = store.events(appId).size();
        edit(t, "제목만 바꿈", "무선 이어폰", "OPERATOR", 2, null);
        assertThat(store.events(appId)).hasSize(before);

        // 승인 뒤 결제 전 고치기 — 심사 대기로 되돌리고 다시 셈(4명 = 212,000)
        approveTrial(appId, "ONLINE");
        assertThat(app(appId).status()).isEqualTo(BizApplicationStatus.AWAITING_PAYMENT);
        assertThat(codeOf(() -> act(t, "PUBLISH"))).isEqualTo("TRIAL_PREPAY_REQUIRED");
        edit(t, "제목만 바꿈", "무선 이어폰", "OPERATOR", 4, null);
        a = app(appId);
        assertThat(a.status()).isEqualTo(BizApplicationStatus.SUBMITTED);
        assertThat(a.approved()).isNull();
        assertThat(a.quote().total()).isEqualTo(233200L);

        // 승인 → 온라인 결제 → 결제 끝 · 공개 가능
        approveTrial(appId, "ONLINE");
        pay(advertiserUser, appId);
        assertThat(app(appId).status()).isEqualTo(BizApplicationStatus.SCHEDULED);
        TrialViews.Payment p = payment(t);
        assertThat(p.status()).isEqualTo("PAID");
        assertThat(p.publishAllowed()).isTrue();
        assertThat(p.shippingAllowed()).isTrue();

        // 결제 뒤 — 모집 인원·제품·보내는 쪽·결제 방식은 409, 제목은 된다(신청 건은 예약 그대로)
        assertThat(codeOf(() -> edit(t, "제목만 바꿈", "무선 이어폰", "OPERATOR", 5, null))).isEqualTo("TRIAL_PREPAID_LOCKED");
        assertThat(codeOf(() -> edit(t, "제목만 바꿈", "유선 이어폰", "OPERATOR", 4, null))).isEqualTo("TRIAL_PREPAID_LOCKED");
        assertThat(codeOf(() -> edit(t, "제목만 바꿈", "무선 이어폰", "ADVERTISER", 4, null))).isEqualTo("TRIAL_PREPAID_LOCKED");
        assertThat(codeOf(() -> edit(t, "제목만 바꿈", "무선 이어폰", "OPERATOR", 4, "POSTPAID"))).isEqualTo("TRIAL_PREPAID_LOCKED");
        edit(t, "결제 뒤 제목", "무선 이어폰", "OPERATOR", 4, null);
        assertThat(app(appId).status()).isEqualTo(BizApplicationStatus.SCHEDULED);
        assertThat(amount(t)).isEqualTo(212000L);

        // 공개 → 2명만 모여 2명 뽑기 — 금액은 모집 4명 그대로(차액 환불 없음 · 뽑기 때 다시 굳히지 않는다)
        act(t, "PUBLISH");
        assertThat(trialStatus(t)).isEqualTo("RECRUITING");
        applicants(t, "OPERATOR", 2);
        int quoted = (int) store.events(appId).stream().filter(e -> e.action().startsWith("TRIAL_")).count();
        d = selectAll(t);
        assertThat(d.selectedCount()).isEqualTo(2);
        assertThat(d.amount()).isEqualTo(212000L);
        assertThat(d.payment().status()).isEqualTo("PAID");
        assertThat(app(appId).approved().total()).isEqualTo(233200L);
        assertThat(app(appId).quote().total()).isEqualTo(233200L);
        assertThat(store.events(appId).stream().filter(e -> e.action().startsWith("TRIAL_")).count()).isEqualTo(quoted);
        assertThat(lastAudit("TRIAL", Long.toString(t), "TRIAL_SELECT").get("detail").toString()).contains("PREPAID").contains("212000");

        // 뽑힌 줄 취소 — 뽑힌 수만 줄고 금액 그대로 · 신청 건은 예약 그대로
        long p1 = participant(t, 0);
        d = trials.cancelParticipant(creator, "ip", Long.toString(p1), new TrialViews.ReasonRequest("연락 두절"));
        assertThat(d.selectedCount()).isEqualTo(1);
        assertThat(d.amount()).isEqualTo(212000L);
        assertThat(app(appId).status()).isEqualTo(BizApplicationStatus.SCHEDULED);
        assertThat(app(appId).quote().total()).isEqualTo(233200L);
        assertThat(lastAudit("TRIAL", Long.toString(t), "TRIAL_PARTICIPANT_CANCEL").get("detail").toString()).containsPattern("requoted\\W{1,4}false");

        // 결제가 끝났으니 입고·발송은 막지 않는다
        trials.stock(creator, "127.0.0.1", Long.toString(t), new TrialViews.StockRequest(1, null, List.of(photo(op(), t, "STOCK"))));
        trials.ship(op(), Long.toString(participant(t, 1)), new TrialViews.ShipmentRequest("CJ", "1", null));
        assertThat(jdbc.queryForObject("SELECT shipped_at IS NOT NULL FROM trial_participants WHERE id = ?", Boolean.class, participant(t, 1))).isTrue();
    }

    @Test
    void 선결제_건당은_단가에_대신발송비_곱하기_모집인원_광고주발송은_발송비없음_0원은_결제없이_공개() {
        price(40000, "PER_RECRUIT", 3000);
        // 건당 + 광고주 발송 = 단가만
        long t1 = create("ADVERTISER", 5, "PREPAID");
        assertThat(app(bizId(t1)).quote().supply()).isEqualTo(40000L);
        assertThat(store.items(bizId(t1))).extracting(i -> i.product().name() + ":" + i.unitPrice() + "x" + i.units()).containsExactly("TRIAL:40000x1");
        // 건당 + 운영자 대신 발송 = 단가 + 3,000 × 모집 5명
        long t2 = create("OPERATOR", 5, "PREPAID");
        assertThat(app(bizId(t2)).quote().supply()).isEqualTo(55000L);
        assertThat(store.items(bizId(t2))).extracting(i -> i.product().name() + ":" + i.unitPrice() + "x" + i.units()).containsExactlyInAnyOrder("TRIAL:40000x1",
                "TRIAL_SHIPPING:3000x5");
        assertThat(codeOf(() -> act(t2, "PUBLISH"))).isEqualTo("TRIAL_PREPAY_REQUIRED");

        // 0원 — 결제 흐름 밖(심사 큐·처리 기록 없음) · 결제 없이 공개
        price(0, "PER_PERSON", 0);
        long t3 = create("OPERATOR", 3, null);
        long app3 = bizId(t3);
        assertThat(app(app3).quote().total()).isZero();
        assertThat(app(app3).userId()).isNull();
        assertThat(inAdQueue(app3)).isFalse();
        assertThat(events(app3)).noneMatch(e -> e.startsWith("TRIAL_"));
        TrialViews.Payment p = payment(t3);
        assertThat(p.status()).isEqualTo("NOT_REQUIRED");
        assertThat(p.required()).isFalse();
        assertThat(p.publishAllowed()).isTrue();
        assertThat(amount(t3)).isZero();
        act(t3, "PUBLISH");
        assertThat(trialStatus(t3)).isEqualTo("RECRUITING");
    }

    // ── 공개 전 광고 쪽 닫힘 · 결제 다시 요청 ─────────────────────────────

    @Test
    void 선결제_공개전_결제기한_만료_반려면_모집은_초안_그대로_결제다시요청은_새_신청건_예전건도_모집을_가리킨다() {
        price(30000, "PER_PERSON", 0);
        long t = create("ADVERTISER", 2, null);
        long app1 = bizId(t);
        assertThat(app(app1).quote().total()).isEqualTo(66000L);

        // 결제 기한 만료 — 모집은 초안 그대로(취소하지 않는다) · 결제 상태 EXPIRED · 다시 요청 가능 · 공개 409
        approveTrial(app1, "ONLINE");
        clock.set(app(app1).paymentDeadlineAt().plusSeconds(1));
        assertThat(schedules.expire(app1)).isTrue();
        assertThat(app(app1).status()).isEqualTo(BizApplicationStatus.EXPIRED);
        assertThat(trialStatus(t)).isEqualTo("DRAFT");
        TrialViews.Payment p = payment(t);
        assertThat(p.status()).isEqualTo("EXPIRED");
        assertThat(p.requestAllowed()).isTrue();
        assertThat(p.publishAllowed()).isFalse();
        assertThat(codeOf(() -> act(t, "PUBLISH"))).isEqualTo("TRIAL_PREPAY_REQUIRED");

        // 닫힌 건이면 고치기는 금액만 두고(새 건에 굳힌다) 닫힌 건은 그대로
        edit(t, title(t), "무선 이어폰", "ADVERTISER", 3, null);
        assertThat(amount(t)).isEqualTo(90000L);
        assertThat(app(app1).status()).isEqualTo(BizApplicationStatus.EXPIRED);

        // 결제 다시 요청 — 새 신청 건(모집 인원 3명으로 견적) · 감사 · 예전 건도 모집을 가리킨다(지금 건 아님)
        act(t, "REQUEST_PAYMENT");
        long app2 = bizId(t);
        assertThat(app2).isNotEqualTo(app1);
        assertThat(app(app2).status()).isEqualTo(BizApplicationStatus.SUBMITTED);
        assertThat(app(app2).quote().total()).isEqualTo(99000L);
        assertThat(inAdQueue(app2)).isTrue();
        assertThat(events(app2)).contains("TRIAL_QUOTED:99000");
        assertThat(payment(t).status()).isEqualTo("AWAITING_APPROVAL");
        assertThat(jdbc.queryForList("SELECT biz_application_id FROM trial_biz_applications WHERE trial_id = ? ORDER BY created_at", Long.class, t))
                .containsExactlyInAnyOrder(app1, app2);
        assertThat(payments.info(app1)).hasValueSatisfying(i -> {
            assertThat(i.trialId()).isEqualTo(t);
            assertThat(i.current()).isFalse();
            assertThat(i.paymentTiming()).isEqualTo("PREPAID");
        });
        assertThat(payments.info(app2)).hasValueSatisfying(i -> assertThat(i.current()).isTrue());
        assertThat(lastAudit("TRIAL", Long.toString(t), "TRIAL_PAYMENT_REQUEST").get("detail").toString()).contains(Long.toString(app1)).contains("EXPIRED");
        assertThat(codeOf(() -> act(t, "REQUEST_PAYMENT"))).isEqualTo("TRIAL_STATE_INVALID");

        // 반려 — 역시 초안 그대로 · REJECTED · 다시 요청 → 세 번째 건 → 결제 → 공개
        review.reject(operator, app2, "OTHER", "광고주 연락 안 됨");
        assertThat(trialStatus(t)).isEqualTo("DRAFT");
        assertThat(payment(t).status()).isEqualTo("REJECTED");
        act(t, "REQUEST_PAYMENT");
        long app3 = bizId(t);
        assertThat(app3).isNotIn(app1, app2);
        approveTrial(app3, "MANUAL");
        confirmManual(app3);
        assertThat(payment(t).status()).isEqualTo("PAID");
        act(t, "PUBLISH");
        assertThat(trialStatus(t)).isEqualTo("RECRUITING");
        assertThat(payment(t).requestAllowed()).isFalse();
    }

    // ── 결제 뒤 모집 취소 ────────────────────────────────────────────────

    @Test
    void 선결제_결제뒤_모집취소는_공개전도_공개뒤도_발송전이면_전액환불_발송이_있으면_409_광고쪽_취소면_초안은_그대로() throws Exception {
        price(50000, "PER_PERSON", 0);
        // ① 공개 전 결제 끝 → 모집 취소 = 전액 환불(일할 없음)
        long t1 = create("ADVERTISER", 1, null);
        long app1 = bizId(t1);
        approveTrial(app1, "ONLINE");
        pay(advertiserUser, app1);
        act(t1, "CANCEL");
        assertThat(trialStatus(t1)).isEqualTo("CANCELLED");
        assertThat(app(app1).status()).isEqualTo(BizApplicationStatus.CANCELED);
        assertThat(jdbc.queryForMap("SELECT type, status, approved_amount FROM biz_refunds WHERE application_id = ?", app1)).containsEntry("type", "FULL_AUTO")
                .containsEntry("status", "APPROVED").containsEntry("approved_amount", 55000L);

        // ② 공개 뒤(모집 중) 결제 끝 → 모집 취소 = 전액 환불
        long t2 = create("ADVERTISER", 2, null);
        long app2 = bizId(t2);
        approveTrial(app2, "ONLINE");
        pay(advertiserUser, app2);
        act(t2, "PUBLISH");
        act(t2, "CANCEL");
        assertThat(app(app2).status()).isEqualTo(BizApplicationStatus.CANCELED);
        assertThat(jdbc.queryForObject("SELECT approved_amount FROM biz_refunds WHERE application_id = ? AND type = 'FULL_AUTO'", Long.class, app2)).isEqualTo(110000L);

        // ③ 뽑고 보냈으면 모집 취소 409(기존 규칙 그대로)
        long t3 = create("ADVERTISER", 1, null);
        long app3 = bizId(t3);
        approveTrial(app3, "ONLINE");
        pay(advertiserUser, app3);
        act(t3, "PUBLISH");
        applicants(t3, "ADVERTISER", 1);
        selectAll(t3);
        long p = participant(t3, 0);
        trials.ship(adv(), Long.toString(p), new TrialViews.ShipmentRequest("CJ", "T" + p, List.of(photo(adv(), t3, "PRE_SHIP"))));
        assertThat(codeOf(() -> act(t3, "CANCEL"))).isEqualTo("TRIAL_SHIPPING_STARTED");
        assertThat(app(app3).status()).isEqualTo(BizApplicationStatus.SCHEDULED);

        // ④ 공개 전 결제 끝 건을 광고 쪽에서 취소(전액 환불) → 모집은 초안 그대로 · 결제 상태 CANCELED · 다시 요청 가능
        clock.set(Instant.now().truncatedTo(ChronoUnit.MICROS));
        long t4 = create("ADVERTISER", 1, null);
        long app4 = bizId(t4);
        approveTrial(app4, "ONLINE");
        pay(advertiserUser, app4);
        review.cancel(operator, app4, "광고주 요청");
        assertThat(app(app4).status()).isEqualTo(BizApplicationStatus.CANCELED);
        assertThat(trialStatus(t4)).isEqualTo("DRAFT");
        assertThat(payment(t4).status()).isEqualTo("CANCELED");
        assertThat(payment(t4).requestAllowed()).isTrue();
    }

    // ── 결제 방식 바꾸기 ─────────────────────────────────────────────────

    @Test
    void 결제방식은_초안에서만_바꾸고_후불로_바꾸면_견적을_거두고_선결제로_되돌리면_다시_굳힌다_신청자가_있으면_409_잘못된_값_400() {
        price(50000, "PER_PERSON", 0);
        long t = create("ADVERTISER", 2, null);
        long appId = bizId(t);
        assertThat(app(appId).quote().total()).isEqualTo(110000L);
        assertThat(fieldsOf(() -> edit(t, title(t), "무선 이어폰", "ADVERTISER", 2, "LATER"))).containsExactly("paymentTiming");

        // 후불로 — 견적 0(심사 큐 밖) · 금액은 뽑기 때
        approveTrial(appId, "MANUAL");
        TrialViews.TrialDetail d = edit(t, title(t), "무선 이어폰", "ADVERTISER", 2, "POSTPAID");
        assertThat(d.paymentTiming()).isEqualTo("POSTPAID");
        assertThat(d.amount()).isNull();
        assertThat(d.payment().status()).isEqualTo("BEFORE_SELECTION");
        assertThat(app(appId).status()).isEqualTo(BizApplicationStatus.SUBMITTED);
        assertThat(app(appId).quote().total()).isZero();
        assertThat(inAdQueue(appId)).isFalse();
        assertThat(events(appId)).contains("TRIAL_REQUOTED:0");

        // 선결제로 되돌림 — 다시 굳힌다
        d = edit(t, title(t), "무선 이어폰", "ADVERTISER", 2, "PREPAID");
        assertThat(d.amount()).isEqualTo(100000L);
        assertThat(app(appId).quote().total()).isEqualTo(110000L);
        assertThat(inAdQueue(appId)).isTrue();

        // 후불로 연 모집 — 초안이 아니면 결제 방식을 바꿀 수 없다 · 신청자가 있으면 409 TRIAL_APPLICANTS_LOCKED
        edit(t, title(t), "무선 이어폰", "ADVERTISER", 2, "POSTPAID");
        act(t, "PUBLISH");
        assertThat(codeOf(() -> edit(t, title(t), "무선 이어폰", "ADVERTISER", 2, "PREPAID"))).isEqualTo("TRIAL_STATE_INVALID");
        applicants(t, "ADVERTISER", 1);
        assertThat(codeOf(() -> edit(t, title(t), "무선 이어폰", "ADVERTISER", 2, "PREPAID"))).isEqualTo("TRIAL_APPLICANTS_LOCKED");
        assertThat(codeOf(() -> act(t, "REQUEST_PAYMENT"))).isEqualTo("TRIAL_STATE_INVALID"); // 후불은 결제 요청이 없다
    }
}
