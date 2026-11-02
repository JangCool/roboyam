package com.community.bizapplication;

import com.community.bizapplication.dto.BizOperator;
import com.community.bizapplication.dto.request.BizAdApplyRequest;
import com.community.bizapplication.dto.request.BizDealApplyRequest;
import com.community.bizapplication.dto.request.BizDealInfo;
import com.community.bizapplication.entity.BizProduct;
import com.community.bizapplication.repository.master.BizApplicationStore;
import com.community.bizapplication.repository.master.BizApplicationStore.AppRow;
import com.community.bizapplication.repository.master.BizPriceRuleStore;
import com.community.bizapplication.repository.master.BizRefundStore;
import com.community.bizapplication.service.BizApplicationMemberService;
import com.community.bizapplication.service.BizApplicationReviewService;
import com.community.bizapplication.service.BizApplicationSchedules;
import com.community.bizapplication.service.BizPeriods;
import com.community.business.service.BusinessApplicationHook;
import com.community.commerce.CommerceApiTestBase;
import com.community.payment.dto.ConfirmedVia;
import com.community.payment.dto.PaymentCheckout;
import com.community.payment.gateway.fake.FakePgStore;
import com.community.payment.service.PaymentConfirmService;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 사업자 신청 건 규칙 시험의 바탕(commerce.md 18절 — 돈이 움직여 커머스 규칙 적용) — 공개 사이트 앱 문맥 전체를 커머스 시험 DB(Testcontainers PostgreSQL)에 띄운다.
 * 자격 판정은 실제 {@code BusinessEligibility}(JPA), 결제는 local 의 가짜 게이트웨이(가짜 PG 표에 「결제됨」을 쓰고 확정 코드를 부른다).
 *
 * <ul>
 *   <li>DB 를 다른 시험과 같이 쓴다 — 자리(한도)는 상품·기간 전체가 공유하므로, 승인하는 시험은 {@link #freshPeriod} 로 <b>겹치지 않는 먼 미래 기간</b>으로 옮겨 승인한다
 *       (승인은 미리 신청 일수 제한이 없다). 신청 때의 기간은 가까운 날짜(내일~)지만 신청은 자리를 차지하지 않는다.</li>
 *   <li>시계는 시험마다 지금으로 되돌린다(기반 클래스). 「진행 중」·「종료」는 시계를 기간 안·뒤로 옮겨 만든다.</li>
 * </ul>
 */
public abstract class BizApplicationApiTestBase extends CommerceApiTestBase {

    /** 먼 미래 기간의 차례(시험마다 40일씩 떨어진다) */
    private static final AtomicInteger SLOT = new AtomicInteger();
    protected static final String POLICY = "2026-11-02";
    /** 사업자 핫딜 상품 — V121 이 껐다가 V123 이 다시 열었다(사업자 핫딜 게시 2차) */
    protected static final List<BizProduct> HOTDEAL_PRODUCTS = List.of(BizProduct.HOTDEAL_POST, BizProduct.HOTDEAL_TOP, BizProduct.HOTDEAL_CATEGORY);

    @Autowired
    protected BizApplicationMemberService members;
    @Autowired
    protected BizApplicationReviewService review;
    @Autowired
    protected BizApplicationSchedules schedules;
    @Autowired
    protected BizApplicationStore store;
    @Autowired
    protected BizRefundStore refunds;
    @Autowired
    protected BusinessApplicationHook hook;
    @Autowired
    protected PaymentConfirmService confirm;
    @Autowired
    protected BizPriceRuleStore priceRules;
    @Autowired
    protected com.community.board.service.BoardCatalog boardCatalog;

    /** 이 시험의 받는 핫딜 게시판(말머리 둘 — 처음 쓸 때 만든다) */
    private Long dealBoardId;
    private Long dealPrefixId;

    protected FakePgStore fakePg;
    protected BizOperator operator;

    @BeforeEach
    void bizSetUp() {
        fakePg = new FakePgStore(dataSource);
        operator = new BizOperator(newOperator(), "127.0.0.1");
    }

    // ── 사업자 ─────────────────────────────────────────────────────────────

    /** 승인된 사업자(광고·핫딜 서비스 둘 다 승인) — 회원 ID */
    protected long approvedBusiness() {
        return business("APPROVED", false);
    }

    protected long profileOf(long userId) {
        return jdbc.queryForObject("SELECT id FROM business_profiles WHERE user_id = ?", Long.class, userId);
    }

    /** 사업자 신원 한 줄 + 서비스 두 줄 */
    protected long business(String status, boolean reReview) {
        long user = newMember();
        jdbc.update("INSERT INTO profiles (user_id, nickname) VALUES (?, ?)", user, "b" + Long.toString(user, 36).substring(Math.max(0, Long.toString(user, 36).length() - 11)));
        long profile = ids.nextId();
        String hash = String.format("%064x", profile);
        jdbc.update("INSERT INTO business_profiles (id, user_id, status, re_review, company_name, biz_no_enc, biz_no_hash, biz_no_last4, owner_name_enc, phone_enc, email_enc, "
                        + "postcode, address1_enc, terms_version, privacy_version, agreed_at, submitted_at, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, '시험상사', 'x', ?, '6789', 'x', 'x', 'x', '06236', 'x', 'v1', 'v1', now(), now(), now(), now())",
                profile, user, status.equals("SUSPENDED") ? "APPROVED" : status, reReview, hash);
        if (status.equals("SUSPENDED")) {
            jdbc.update("UPDATE business_profiles SET status = 'SUSPENDED', suspend_code = 'OTHER', suspend_text = '시험', suspended_at = now(), suspend_pending_action = 'KEEP', "
                    + "suspend_scheduled_action = 'HOLD', suspend_running_action = 'TAKEDOWN_ONLY' WHERE id = ?", profile);
        }
        String serviceStatus = status.equals("PENDING") && reReview ? "APPROVED" : status.equals("PENDING") ? "PENDING" : status;
        for (String service : new String[] {"AD", "HOTDEAL"}) {
            jdbc.update("INSERT INTO business_services (id, profile_id, service, status, updated_at) VALUES (?, ?, ?, ?, now())", ids.nextId(), profile, service, serviceStatus);
        }
        return user;
    }

    /** 회원이 올린 광고 이미지(파일 장부 한 줄 — 저장소 없이) */
    protected long adImage(long userId) {
        long id = ids.nextId();
        jdbc.update("INSERT INTO files (id, purpose, storage_key, content_type, size_bytes, width, height, status, uploaded_by_user, created_at) "
                + "VALUES (?, 'BIZ_AD_IMAGE', ?, 'image/png', 100, 10, 10, 'PENDING', ?, now())", id, "biz-ad/test/" + id + ".png", userId);
        return id;
    }

    // ── 신청 ───────────────────────────────────────────────────────────────

    protected LocalDate today() {
        return ServiceTime.today(clock);
    }

    /** 한 줄 광고 신청 — 내일 + {@code startOffset} 일부터 {@code days} 일 */
    protected long submitTextLine(long userId, int startOffset, int days, String method) {
        LocalDate start = today().plusDays(1 + startOffset);
        return members.submitAd(userId, new BizAdApplyRequest("TEXT_LINE", start.toString(), start.plusDays(days - 1L).toString(), null, "https://shop.example.com/a",
                null, "가을 특가 한 줄 광고", method, POLICY, POLICY));
    }

    /** 배너 광고 신청 */
    protected long submitBanner(long userId, String adType, int startOffset, int days, String method) {
        LocalDate start = today().plusDays(1 + startOffset);
        return members.submitAd(userId, new BizAdApplyRequest(adType, start.toString(), start.plusDays(days - 1L).toString(), Long.toString(adImage(userId)),
                "https://shop.example.com/b", "가을 특가 배너", null, method, POLICY, POLICY));
    }

    /** 받는 핫딜 게시판(말머리 둘) — 시험마다 새로 만든다 */
    protected long dealBoard() {
        if (dealBoardId == null) {
            dealBoardId = newDealBoard(true);
            dealPrefixId = jdbc.queryForObject("SELECT id FROM board_prefixes WHERE board_id = ? ORDER BY sort_order LIMIT 1", Long.class, dealBoardId);
        }
        return dealBoardId;
    }

    protected long dealPrefix() {
        dealBoard();
        return dealPrefixId;
    }

    /** 새 핫딜 게시판(켬, 쓰기 REGULAR) — {@code withPrefixes} 면 말머리 「로봇」·「AI」 */
    protected long newDealBoard(boolean withPrefixes) {
        long id = ids.nextId();
        jdbc.update("INSERT INTO boards (id, slug, name, sort_order, kind, write_min) VALUES (?, ?, ?, 950, 'DEAL', 'REGULAR')", id, "bz" + Long.toString(id, 36),
                "사업자 핫딜 " + Long.toString(id, 36).substring(Long.toString(id, 36).length() - 5));
        if (withPrefixes) {
            jdbc.update("INSERT INTO board_prefixes (id, board_id, name, sort_order) VALUES (?, ?, '로봇', 1), (?, ?, 'AI', 2)", ids.nextId(), id, ids.nextId(), id);
        }
        boardCatalog.invalidate();
        return id;
    }

    /** 핫딜 정보 — 받는 게시판·첫 말머리, 재고 IN_STOCK, 대형몰 링크(막히지 않음) */
    protected BizDealInfo deal(String placement) {
        return new BizDealInfo("https://smartstore.naver.com/roboyam/products/1", "네이버 스마트스토어", 32_000L, 15_900L, 0L, null, "IN_STOCK", "1+1 행사", placement,
                Long.toString(dealBoard()), Long.toString(dealPrefix()), null, null);
    }

    /** 사업자 핫딜 신청(V123 — 받기 켜짐, 말머리 상단은 V124 로 닫혀 시험 안에서만 잠깐 연다) */
    protected long submitDeal(long userId, String placement, LocalDate start, LocalDate end, String method) {
        return categoryOpenIf(placement, () -> members.submitDeal(userId, new BizDealApplyRequest("[시험상사] 텀블러 1+1", deal(placement),
                start == null ? null : start.toString(), end == null ? null : end.toString(), method, POLICY, POLICY)));
    }

    /** 말머리 상단({@code CATEGORY_TOP})이면 그 신청 동안만 {@code HOTDEAL_CATEGORY} 받기를 켠다(V124 닫힘 — 끝나면 다시 끔) */
    protected <T> T categoryOpenIf(String placement, Supplier<T> call) {
        if (!"CATEGORY_TOP".equals(placement)) {
            return call.get();
        }
        priceRules.setAccepting(BizProduct.HOTDEAL_CATEGORY, true);
        try {
            return call.get();
        } finally {
            priceRules.setAccepting(BizProduct.HOTDEAL_CATEGORY, false);
        }
    }

    // ── 심사 ───────────────────────────────────────────────────────────────

    /** 다른 시험과 겹치지 않는 먼 미래 기간(한국 0시 시작, {@code days} 일) */
    protected BizPeriods.Period freshPeriod(int days) {
        LocalDate start = LocalDate.of(2031, 1, 5).plusDays(40L * SLOT.incrementAndGet());
        return BizPeriods.ofDates(start, start.plusDays(days - 1L));
    }

    protected long quoteSupply(long id, BizPeriods.Period p) {
        return review.availability(id, p.start().toString(), p.end().toString()).quote().supply();
    }

    /** 요금표 금액 그대로 승인 */
    protected void approve(long id, String method, BizPeriods.Period p) {
        review.approve(operator, id, method, quoteSupply(id, p), null, clock.instant().plus(Duration.ofHours(72)).toString(), p.start().toString(), p.end().toString());
    }

    protected AppRow app(long id) {
        return store.find(id).orElseThrow();
    }

    // ── 결제 ───────────────────────────────────────────────────────────────

    /** 온라인 결제 — 결제창 → 가짜 PG 결제됨(지금) → 확정 코드 */
    protected PaymentCheckout pay(long userId, long id) {
        PaymentCheckout checkout = members.startPayment(userId, id);
        fakePg.pay(checkout.paymentId(), checkout.amount(), "KRW", clock.instant());
        confirm.confirm(checkout.paymentId(), ConfirmedVia.COMPLETE_API);
        return checkout;
    }

    /** 수동 결제 확정까지 — 계약서 PDF 한 장 + 입금 확인(확정 금액) + 예약 확정 */
    protected void confirmManual(long id) {
        review.uploadContracts(operator, id, java.util.List.of(new com.community.bizapplication.dto.BizUpload("계약서.pdf", pdf())), null);
        AppRow a = app(id);
        review.saveDeposit(operator, id, a.approved().total(), today().toString(), "시험상사", null);
        review.confirm(operator, id);
    }

    protected static byte[] pdf() {
        return "%PDF-1.4 test".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    }

    // ── 오류 ───────────────────────────────────────────────────────────────

    /** 업무 예외의 코드 */
    protected static String codeOf(Executable call) {
        try {
            call.execute();
        } catch (CommonException e) {
            return e.getErrorCode();
        } catch (Throwable e) {
            throw new AssertionError("업무 예외가 아니다: " + e, e);
        }
        throw new AssertionError("예외가 나지 않았다");
    }

    /** 400 의 칸 이름들 */
    protected static java.util.Set<String> fieldsOf(Executable call) {
        try {
            call.execute();
        } catch (CommonException e) {
            return e.getFields().keySet();
        } catch (Throwable e) {
            throw new AssertionError("업무 예외가 아니다: " + e, e);
        }
        throw new AssertionError("예외가 나지 않았다");
    }

    protected Instant at(LocalDate date) {
        return BizPeriods.startOf(date);
    }
}
