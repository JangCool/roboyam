package com.community.trial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.ad.repository.master.AdAdvertiserStore;
import com.community.addressbook.dto.AddressInput;
import com.community.addressbook.service.AddressBookService;
import com.community.bizapplication.dto.BizOperator;
import com.community.bizapplication.dto.BizUpload;
import com.community.bizapplication.service.BizApplicationReviewService;
import com.community.board.service.BoardCatalog;
import com.community.commerce.Commerce04AdminTestBase;
import com.community.permission.entity.Permission;
import com.community.trial.dto.TrialViews;
import com.community.trial.service.TrialMemberService;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 유료 체험단 결제 연결 — 관리자 HTTP(trial-group-contract 「유료 결제 연결」). 광고 심사 큐에 나오는 조건 · 신청 건 상세의 체험단 칸 · 승인 본문(견적 그대로 · 회원 없는 광고주는 수동만) ·
 * 모집 상세 결제 칸 · 결제 전 입고 409 · 뽑힌 줄 취소 API. 규칙 자체(결제·환불·만료·훅·자동 끝남)는 공개 사이트 시험 {@code TrialPaymentTest}.
 */
class TrialPaymentAdminHttpTest extends Commerce04AdminTestBase {

    @Autowired
    private BoardCatalog catalog;
    @Autowired
    private AdAdvertiserStore advertisers;
    @Autowired
    private AddressBookService addresses;
    @Autowired
    private TrialMemberService members;
    @Autowired
    private BizApplicationReviewService review;
    @Autowired
    private PlatformTransactionManager transactions;

    private long board;
    private long advertiser;
    private Cookie manager;
    private Cookie adReviewer;
    private long adReviewerId;
    private Map<String, Object> savedRule;

    @BeforeEach
    void setUp() throws Exception {
        board = ids.nextId();
        String slug = "tq" + Long.toString(board, 36);
        boardFixture.board(board, slug, "체험결제관리 " + slug.substring(slug.length() - 6), 972);
        // SQL 로 남김: 게시판 정책 칸은 게시판 저장 API 가 바꾼다 — 이 시험은 사용기 게시판 값만 세운다
        jdbc.update("UPDATE boards SET purchase_source_mode = 'REQUIRED', trial_reviews_enabled = true WHERE id = ?", board);
        catalog.invalidate();
        long creator = operatorWith();
        advertiser = ids.nextId();
        // 사업자 신원에 이어지지 않은 광고주 — 신청 건 회원이 없어 수동 결제만
        new TransactionTemplate(transactions).executeWithoutResult(s -> advertisers.insert(new AdAdvertiserStore.NewAdvertiser(advertiser,
                "결제관리광고주" + advertiser % 1_000_000, null, null, null, null, null, null, null, null, creator, Instant.now())));
        manager = login(operatorWith(Permission.TRIAL_MANAGE, Permission.TRIAL_REVIEW, Permission.AD_READ));
        adReviewerId = operatorWith(Permission.AD_READ, Permission.AD_MANAGE);
        adReviewer = login(adReviewerId);
        savedRule = jdbc.queryForMap("SELECT unit_price, price_unit, shipping_fee_per_person FROM biz_price_rules WHERE product = 'TRIAL'");
        // 인원당 40,000 + 운영자 대신 발송 1명당 2,000
        jdbc.update("UPDATE biz_price_rules SET unit_price = 40000, price_unit = 'PER_PERSON', shipping_fee_per_person = 2000 WHERE product = 'TRIAL'");
    }

    @AfterEach
    void restoreRule() {
        jdbc.update("UPDATE biz_price_rules SET unit_price = ?, price_unit = ?, shipping_fee_per_person = ? WHERE product = 'TRIAL'", savedRule.get("unit_price"),
                savedRule.get("price_unit"), savedRule.get("shipping_fee_per_person"));
    }

    private String create() throws Exception {
        Instant start = Instant.now().minus(Duration.ofMinutes(5)).truncatedTo(ChronoUnit.MINUTES);
        String form = "{\"advertiserId\":\"" + advertiser + "\",\"title\":\"결제 관리 체험\",\"productName\":\"청소기\",\"productPrice\":30000,\"form\":\"LEND\""
                + ",\"reviewBoardId\":\"" + board + "\",\"recruitCount\":2,\"reviewerShare\":0,\"applyStartAt\":\"" + start + "\",\"applyEndAt\":\""
                + start.plus(Duration.ofDays(2)) + "\",\"reviewDays\":14,\"returnDays\":10,\"shipper\":\"OPERATOR\",\"paymentTiming\":\"POSTPAID\"}";
        String body = mvc.perform(withCsrf(post("/api/admin/trials"), manager).header("Accept-Language", "ko").content(form)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.data.id");
        mvc.perform(withCsrf(put("/api/admin/trials/" + id), manager).content("{\"action\":\"PUBLISH\"}")).andExpect(status().isOk());
        return id;
    }

    private long applicant(String trialId) {
        long m = newMember();
        verified(m, LocalDate.of(1990, 1, 1), "FAKE", Instant.now());
        long addr = addresses.create(m, new AddressInput(null, "김신청", "01099998888", "06236", "서울 강남구 1", null, null, true)).id();
        members.apply(m, trialId, new TrialViews.ApplyRequest(Long.toString(addr), List.of("UNRETURNED_CONTACT", "PRODUCT_CHARGE", "DAMAGE_CHARGE")));
        return jdbc.queryForObject("SELECT id FROM trial_applications WHERE trial_id = ? AND user_id = ?", Long.class, Long.parseLong(trialId), m);
    }

    @Test
    void 유료_체험단은_뽑기_뒤_광고_심사_큐에_나오고_상세는_체험단_칸_승인은_견적_그대로_회원없으면_수동만_결제전_입고_409_뽑힌줄_취소는_다시셈() throws Exception {
        String id = create();
        long a = applicant(id);
        long b = applicant(id);
        long app = jdbc.queryForObject("SELECT biz_application_id FROM trials WHERE id = ?", Long.class, Long.parseLong(id));
        // 뽑기 전(견적 0)은 큐 밖 · 상세 404
        mvc.perform(get("/api/admin/biz-applications/" + app).cookie(adReviewer)).andExpect(status().isNotFound());
        mvc.perform(withCsrf(put("/api/admin/trials/" + id), manager).content("{\"action\":\"CLOSE_APPLY\"}")).andExpect(status().isOk());
        mvc.perform(withCsrf(post("/api/admin/trials/" + id + "/select"), manager).content("{\"applicationIds\":[\"" + a + "\",\"" + b + "\"]}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(84000)).andExpect(jsonPath("$.data.payment.status").value("AWAITING_APPROVAL"))
                .andExpect(jsonPath("$.data.payment.required").value(true)).andExpect(jsonPath("$.data.payment.shippingAllowed").value(false))
                .andExpect(jsonPath("$.data.payment.total").value(92400)).andExpect(jsonPath("$.data.payment.bizApplicationId").value(Long.toString(app)));

        // 광고 심사 큐 「심사 대기」 · 종류 TRIAL 거르기
        String queue = mvc.perform(get("/api/admin/biz-applications?domain=AD&queue=REVIEW").cookie(adReviewer)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        assertThat(JsonPath.<List<String>>read(queue, "$.data.items[?(@.id == '" + app + "')].kind")).containsExactly("TRIAL");
        assertThat(JsonPath.<List<Integer>>read(queue, "$.data.items[?(@.id == '" + app + "')].total")).containsExactly(92400);
        String onlyTrials = mvc.perform(get("/api/admin/biz-applications?domain=AD&kind=TRIAL&queue=REVIEW").cookie(adReviewer)).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(onlyTrials, "$.data.items[*].kind")).isNotEmpty().allMatch("TRIAL"::equals);
        assertThat(JsonPath.<List<String>>read(onlyTrials, "$.data.items[*].id")).contains(Long.toString(app));

        // 상세 — 체험단 칸 · 줄 단위(인원당 = PERSON · 발송비 = PERSON)
        mvc.perform(get("/api/admin/biz-applications/" + app).cookie(adReviewer)).andExpect(status().isOk()).andExpect(jsonPath("$.data.kind").value("TRIAL"))
                .andExpect(jsonPath("$.data.content.type").value("TRIAL")).andExpect(jsonPath("$.data.content.trialId").value(id))
                .andExpect(jsonPath("$.data.content.selectedCount").value(2)).andExpect(jsonPath("$.data.content.priceUnit").value("PER_PERSON"))
                .andExpect(jsonPath("$.data.content.unitPrice").value(40000)).andExpect(jsonPath("$.data.content.shippingFeePerPerson").value(2000))
                .andExpect(jsonPath("$.data.content.shipper").value("OPERATOR")).andExpect(jsonPath("$.data.quote.total").value(92400))
                .andExpect(jsonPath("$.data.quote.lines.length()").value(2)).andExpect(jsonPath("$.data.quote.lines[?(@.product == 'TRIAL_SHIPPING')].units").value(2))
                .andExpect(jsonPath("$.data.quote.lines[?(@.product == 'TRIAL')].unit").value("PERSON"));

        // 승인 — 회원 없는 광고주는 온라인 400 fields.method · 다른 금액 400 fields.supply · 수동은 기한을 비워도(승인 + 7일)
        mvc.perform(withCsrf(post("/api/admin/biz-applications/" + app + "/approve"), adReviewer).content("{\"method\":\"ONLINE\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.method").exists());
        mvc.perform(withCsrf(post("/api/admin/biz-applications/" + app + "/approve"), adReviewer).content("{\"method\":\"MANUAL\",\"supply\":1000}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.supply").exists());
        mvc.perform(withCsrf(post("/api/admin/biz-applications/" + app + "/approve"), adReviewer).content("{\"method\":\"MANUAL\",\"supply\":84000}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("AWAITING_DEPOSIT")).andExpect(jsonPath("$.data.approval.amount.total").value(92400));
        Instant depositDeadline = jdbc.queryForObject("SELECT deposit_deadline_at FROM biz_applications WHERE id = ?", java.sql.Timestamp.class, app).toInstant();
        assertThat(depositDeadline).isBetween(Instant.now().plus(Duration.ofDays(7)).minus(Duration.ofMinutes(5)), Instant.now().plus(Duration.ofDays(7)));

        // 모집 상세·목록의 결제 칸 · 결제 전 입고 409
        mvc.perform(get("/api/admin/trials/" + id).cookie(manager)).andExpect(status().isOk()).andExpect(jsonPath("$.data.payment.status").value("AWAITING_DEPOSIT"))
                .andExpect(jsonPath("$.data.payment.method").value("MANUAL")).andExpect(jsonPath("$.data.payment.deadlineAt").exists());
        String list = mvc.perform(get("/api/admin/trials").cookie(manager)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(list, "$.data[?(@.id == '" + id + "')].paymentStatus")).containsExactly("AWAITING_DEPOSIT");
        mvc.perform(withCsrf(post("/api/admin/trials/" + id + "/stock"), manager).content("{\"quantity\":2,\"photoIds\":[]}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("TRIAL_PAYMENT_PENDING"));

        // 뽑힌 줄 취소 — 사유 필수 · 권한(trial.manage·ad.manage) · 결제 전이면 1명으로 다시 셈 + 심사 대기로 되돌림
        long pa = jdbc.queryForObject("SELECT id FROM trial_participants WHERE application_id = ?", Long.class, a);
        mvc.perform(withCsrf(post("/api/admin/trials/participants/" + pa + "/cancel"), login(operatorWith(Permission.TRIAL_REVIEW))).content("{\"reason\":\"취소\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(withCsrf(post("/api/admin/trials/participants/" + pa + "/cancel"), manager).content("{}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.reason").exists());
        mvc.perform(withCsrf(post("/api/admin/trials/participants/" + pa + "/cancel"), manager).content("{\"reason\":\"연락 두절\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.selectedCount").value(1)).andExpect(jsonPath("$.data.amount").value(42000))
                .andExpect(jsonPath("$.data.counts.cancelled").value(1)).andExpect(jsonPath("$.data.payment.status").value("AWAITING_APPROVAL"))
                .andExpect(jsonPath("$.data.payment.total").value(46200))
                .andExpect(jsonPath("$.data.participants[?(@.id == '" + pa + "')].cancelReason").value("연락 두절"));
        assertThat(auditLogs.where().targetType("TRIAL").target(id).action("TRIAL_PARTICIPANT_CANCEL").count()).isEqualTo(1);

        // 다시 승인 → 수동 결제 확정(계약서 + 입금) → 결제 끝 · 입고·발송 열림
        mvc.perform(withCsrf(post("/api/admin/biz-applications/" + app + "/approve"), adReviewer).content("{\"method\":\"MANUAL\"}")).andExpect(status().isOk());
        BizOperator op = new BizOperator(adReviewerId, "127.0.0.1");
        review.uploadContracts(op, app, List.of(new BizUpload("계약서.pdf", "%PDF-1.4 test".getBytes(java.nio.charset.StandardCharsets.US_ASCII))), null);
        review.saveDeposit(op, app, 46200L, LocalDate.now(ZoneId.of("Asia/Seoul")).toString(), "광고주", null);
        review.confirm(op, app);
        mvc.perform(get("/api/admin/trials/" + id).cookie(manager)).andExpect(status().isOk()).andExpect(jsonPath("$.data.payment.status").value("PAID"))
                .andExpect(jsonPath("$.data.payment.shippingAllowed").value(true));
        mvc.perform(get("/api/admin/biz-applications/" + app).cookie(adReviewer)).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("RUNNING"));
        mvc.perform(withCsrf(post("/api/admin/trials/" + id + "/stock"), manager).content("{\"quantity\":2,\"photoIds\":[]}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.photoIds").exists()); // 결제 뒤에는 결제 검사를 지나 사진 검사로
    }

    /** 선결제 모집 본문(결제 때 칸 없음 = 선결제 기본) — 모집 인원 {@code recruit} */
    private String prepaidForm(int recruit, String extra) {
        Instant start = Instant.now().minus(Duration.ofMinutes(5)).truncatedTo(ChronoUnit.MINUTES);
        return "{\"advertiserId\":\"" + advertiser + "\",\"title\":\"선결제 관리 체험\",\"productName\":\"청소기\",\"productPrice\":30000,\"form\":\"LEND\""
                + ",\"reviewBoardId\":\"" + board + "\",\"recruitCount\":" + recruit + ",\"reviewerShare\":0,\"applyStartAt\":\"" + start + "\",\"applyEndAt\":\""
                + start.plus(Duration.ofDays(2)) + "\",\"reviewDays\":14,\"returnDays\":10,\"shipper\":\"OPERATOR\"" + extra + "}";
    }

    @Test
    void 선결제_모집은_만들때_모집인원_견적으로_심사큐에_나오고_결제전_공개_409_결제뒤_금액칸_409_목록_상세_신청건에_결제때_칸() throws Exception {
        mvc.perform(withCsrf(post("/api/admin/trials"), manager).content(prepaidForm(2, ",\"paymentTiming\":\"LATER\""))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.paymentTiming").exists());
        // 만들기 — 모집 2명 × 40,000 + 대신 발송 2명 × 2,000 = 84,000(공급가) · 92,400(합계)
        String body = mvc.perform(withCsrf(post("/api/admin/trials"), manager).content(prepaidForm(2, ""))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.paymentTiming").value("PREPAID")).andExpect(jsonPath("$.data.amount").value(84000))
                .andExpect(jsonPath("$.data.payment.timing").value("PREPAID")).andExpect(jsonPath("$.data.payment.status").value("AWAITING_APPROVAL"))
                .andExpect(jsonPath("$.data.payment.publishAllowed").value(false)).andExpect(jsonPath("$.data.payment.requestAllowed").value(false))
                .andExpect(jsonPath("$.data.payment.total").value(92400)).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.data.id");
        long app = jdbc.queryForObject("SELECT biz_application_id FROM trials WHERE id = ?", Long.class, Long.parseLong(id));

        // 결제 전 공개 409
        mvc.perform(withCsrf(put("/api/admin/trials/" + id), manager).content("{\"action\":\"PUBLISH\"}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("TRIAL_PREPAY_REQUIRED"));
        // 목록 줄 · 광고 심사 큐 · 신청 건 상세의 체험단 칸
        String list = mvc.perform(get("/api/admin/trials").cookie(manager)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(list, "$.data[?(@.id == '" + id + "')].paymentTiming")).containsExactly("PREPAID");
        assertThat(JsonPath.<List<String>>read(list, "$.data[?(@.id == '" + id + "')].paymentStatus")).containsExactly("AWAITING_APPROVAL");
        String queue = mvc.perform(get("/api/admin/biz-applications?domain=AD&kind=TRIAL&queue=REVIEW").cookie(adReviewer)).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(queue, "$.data.items[*].id")).contains(Long.toString(app));
        mvc.perform(get("/api/admin/biz-applications/" + app).cookie(adReviewer)).andExpect(status().isOk()).andExpect(jsonPath("$.data.content.paymentTiming").value("PREPAID"))
                .andExpect(jsonPath("$.data.content.recruitCount").value(2)).andExpect(jsonPath("$.data.content.currentPayment").value(true))
                .andExpect(jsonPath("$.data.content.amount").value(84000)).andExpect(jsonPath("$.data.quote.lines[?(@.product == 'TRIAL_SHIPPING')].units").value(2));

        // 승인(수동 — 회원 없는 광고주) → 계약서 + 입금 + 확정 → 결제 끝
        mvc.perform(withCsrf(post("/api/admin/biz-applications/" + app + "/approve"), adReviewer).content("{\"method\":\"MANUAL\"}")).andExpect(status().isOk());
        BizOperator op = new BizOperator(adReviewerId, "127.0.0.1");
        review.uploadContracts(op, app, List.of(new BizUpload("계약서.pdf", "%PDF-1.4 test".getBytes(java.nio.charset.StandardCharsets.US_ASCII))), null);
        review.saveDeposit(op, app, 92400L, LocalDate.now(ZoneId.of("Asia/Seoul")).toString(), "광고주", null);
        review.confirm(op, app);
        mvc.perform(get("/api/admin/trials/" + id).cookie(manager)).andExpect(status().isOk()).andExpect(jsonPath("$.data.payment.status").value("PAID"))
                .andExpect(jsonPath("$.data.payment.publishAllowed").value(true));

        // 결제 뒤 금액에 닿는 칸 409 · 공개 200
        mvc.perform(withCsrf(put("/api/admin/trials/" + id), manager).content(prepaidForm(3, ""))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("TRIAL_PREPAID_LOCKED"));
        mvc.perform(withCsrf(put("/api/admin/trials/" + id), manager).content("{\"action\":\"PUBLISH\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RECRUITING")).andExpect(jsonPath("$.data.amount").value(84000));
    }
}
