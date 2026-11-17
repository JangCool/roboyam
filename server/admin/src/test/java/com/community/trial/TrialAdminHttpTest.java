package com.community.trial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.ad.repository.master.AdAdvertiserStore;
import com.community.addressbook.dto.AddressInput;
import com.community.addressbook.service.AddressBookService;
import com.community.board.service.BoardCatalog;
import com.community.commerce.Commerce04AdminTestBase;
import com.community.menu.service.AdminScreenRegistry;
import com.community.permission.entity.Permission;
import com.community.trial.dto.TrialViews;
import com.community.trial.service.PurchaseProofService;
import com.community.trial.service.SponsorReviewService;
import com.community.trial.service.TrialMemberService;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 체험단 관리자 API(trial-group-contract 「구현 결정」 7·10) — 실제 필터 체인·권한·PostgreSQL. 규칙 자체(신청 창·뽑기 몫·파손·배치)는 공개 사이트 시험({@code TrialApiTest})이 shared 서비스로
 * 본다 — 여기서는 권한·HTTP 모양·광고 심사 큐와 섞이지 않음·확인할 일 목록·설정·요금표 체험단 칸을 본다.
 */
class TrialAdminHttpTest extends Commerce04AdminTestBase {

    @Autowired
    private BoardCatalog catalog;
    @Autowired
    private AdAdvertiserStore advertisers;
    @Autowired
    private AddressBookService addresses;
    @Autowired
    private TrialMemberService members;
    @Autowired
    private SponsorReviewService sponsors;
    @Autowired
    private PlatformTransactionManager transactions;
    @Autowired
    private PurchaseProofService proofService;

    private long board;
    private String slug;
    private long advertiser;
    private Cookie manager;

    @BeforeEach
    void setUp() throws Exception {
        board = ids.nextId();
        slug = "ta" + Long.toString(board, 36);
        boardFixture.board(board, slug, "체험관리 " + slug.substring(slug.length() - 6), 970);
        // SQL 로 남김: 게시판 정책 칸은 게시판 저장 API 가 바꾼다 — 이 시험은 사용기 게시판 값만 세운다
        jdbc.update("UPDATE boards SET purchase_source_mode = 'REQUIRED', trial_reviews_enabled = true WHERE id = ?", board);
        catalog.invalidate();
        long creator = operatorWith();
        advertiser = ids.nextId();
        new TransactionTemplate(transactions).executeWithoutResult(s -> advertisers.insert(new AdAdvertiserStore.NewAdvertiser(advertiser, "체험광고주" + advertiser % 1_000_000,
                null, null, null, null, null, null, null, null, creator, Instant.now())));
        manager = login(operatorWith(Permission.TRIAL_MANAGE, Permission.TRIAL_REVIEW, Permission.TRIAL_SETTINGS, Permission.AD_READ));
    }

    private String form(String form, long price, Instant start) {
        return "{\"advertiserId\":\"" + advertiser + "\",\"title\":\"관리 체험\",\"productName\":\"청소기\",\"productPrice\":" + price + ",\"form\":\"" + form
                + "\",\"reviewBoardId\":\"" + board + "\",\"recruitCount\":2,\"reviewerShare\":50,\"applyStartAt\":\"" + start + "\",\"applyEndAt\":\""
                + start.plus(Duration.ofDays(2)) + "\",\"reviewDays\":14" + ("LEND".equals(form) ? ",\"returnDays\":10" : "") + ",\"shipper\":\"OPERATOR\"}";
    }

    private String create(Cookie who, String form, long price, Instant start) throws Exception {
        String body = mvc.perform(withCsrf(post("/api/admin/trials"), who).header("Accept-Language", "ko").content(form(form, price, start))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.id");
    }

    private long applicant(String trialId) {
        long m = newMember();
        verified(m, LocalDate.of(1990, 1, 1), "FAKE", Instant.now());
        long addr = addresses.create(m, new AddressInput(null, "김신청", "01099998888", "06236", "서울 강남구 1", null, null, true)).id();
        members.apply(m, trialId, new TrialViews.ApplyRequest(Long.toString(addr), List.of("UNRETURNED_CONTACT", "PRODUCT_CHARGE", "DAMAGE_CHARGE")));
        return jdbc.queryForObject("SELECT id FROM trial_applications WHERE trial_id = ? AND user_id = ?", Long.class, Long.parseLong(trialId), m);
    }

    @Test
    void 권한은_trial_manage_또는_ad_manage_읽기만이면_403_모집은_광고_심사_큐에_섞이지_않는다() throws Exception {
        Instant start = Instant.now().plus(Duration.ofHours(1)).truncatedTo(ChronoUnit.MINUTES);
        Cookie reader = login(operatorWith(Permission.AD_READ));
        mvc.perform(get("/api/admin/trials").cookie(reader)).andExpect(status().isForbidden());
        mvc.perform(withCsrf(post("/api/admin/trials"), reader).content(form("LEND", 30000, start))).andExpect(status().isForbidden());
        Cookie adManager = login(operatorWith(Permission.AD_MANAGE, Permission.AD_READ));
        String id = create(adManager, "LEND", 30000, start);
        mvc.perform(get("/api/admin/trials/" + id).cookie(manager)).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.priceUnit").value("PER_PERSON")).andExpect(jsonPath("$.data.reviewerSlots").value(1));
        long app = jdbc.queryForObject("SELECT biz_application_id FROM trials WHERE id = ?", Long.class, Long.parseLong(id));
        String queue = mvc.perform(get("/api/admin/biz-applications?domain=AD&queue=REVIEW").cookie(adManager)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        assertThat(queue).doesNotContain(Long.toString(app));
        mvc.perform(get("/api/admin/biz-applications/" + app).cookie(adManager)).andExpect(status().isNotFound());

        mvc.perform(withCsrf(post("/api/admin/trials"), manager).content(form("GIVE", 60000, start))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.form").exists()); // 제공형 상한
        mvc.perform(withCsrf(put("/api/admin/trials/" + id), manager).content("{\"action\":\"WRONG\"}")).andExpect(status().isBadRequest());
        mvc.perform(withCsrf(put("/api/admin/trials/" + id), manager).content("{\"action\":\"PUBLISH\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RECRUITING"));
        mvc.perform(withCsrf(put("/api/admin/trials/" + id), manager).content("{\"action\":\"CLOSE\"}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("TRIAL_STATE_INVALID"));
        assertThat(auditLogs.where().targetType("TRIAL").target(id).action("TRIAL_CREATE").count()).isEqualTo(1);
        assertThat(auditLogs.where().targetType("TRIAL").target(id).action("TRIAL_UPDATE").count()).isEqualTo(1);
    }

    @Test
    void 뽑기_HTTP_인원넘으면_400_입고는_사진없으면_400_파손판정은_trial_review_만() throws Exception {
        Instant start = Instant.now().minus(Duration.ofMinutes(5)).truncatedTo(ChronoUnit.MINUTES);
        String id = create(manager, "LEND", 30000, start);
        mvc.perform(withCsrf(put("/api/admin/trials/" + id), manager).content("{\"action\":\"PUBLISH\"}")).andExpect(status().isOk());
        long a = applicant(id);
        long b = applicant(id);
        long c = applicant(id);
        mvc.perform(withCsrf(post("/api/admin/trials/" + id + "/select"), manager).content("{\"applicationIds\":[\"" + a + "\"]}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("TRIAL_STATE_INVALID")); // 신청 마감 전
        mvc.perform(withCsrf(put("/api/admin/trials/" + id), manager).content("{\"action\":\"CLOSE_APPLY\"}")).andExpect(status().isOk());
        String applicants = mvc.perform(get("/api/admin/trials/" + id + "/applications").cookie(manager)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(3)).andExpect(jsonPath("$.data.generalSlots").value(1)).andReturn().getResponse().getContentAsString();
        // 관리자 응답의 회원은 회원번호(회원 상세 링크 — 회원 ID 와 같다)를 싣는다
        assertThat(JsonPath.<List<String>>read(applicants, "$.data.items[*].member.memberNo"))
                .containsExactlyElementsOf(JsonPath.<List<String>>read(applicants, "$.data.items[*].member.id"));
        mvc.perform(withCsrf(post("/api/admin/trials/" + id + "/select"), manager).content("{\"applicationIds\":[\"" + a + "\",\"" + b + "\"]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.applicationIds").exists()); // 일반 신청 2명 > 일반 몫 1
        mvc.perform(withCsrf(post("/api/admin/trials/applications/" + c + "/recommend"), manager).content("{\"recommended\":true}")).andExpect(status().isNoContent());
        mvc.perform(withCsrf(post("/api/admin/trials/" + id + "/select"), manager).content("{\"applicationIds\":[\"" + c + "\"]}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RUNNING")).andExpect(jsonPath("$.data.selectedCount").value(1))
                .andExpect(jsonPath("$.data.participants[0].member.memberNo").value(Long.toString(
                        jdbc.queryForObject("SELECT user_id FROM trial_applications WHERE id = ?", Long.class, c))));
        mvc.perform(withCsrf(post("/api/admin/trials/" + id + "/stock"), manager).content("{\"quantity\":1,\"photoIds\":[]}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.photoIds").exists());
        mvc.perform(withCsrf(post("/api/admin/trials/" + id + "/return-shipment"), manager).content("{\"carrier\":\"CJ\",\"trackingNo\":\"1\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("TRIAL_NO_STOCK"));
        Cookie noReview = login(operatorWith(Permission.TRIAL_MANAGE));
        mvc.perform(withCsrf(post("/api/admin/trials/damage-reports/1/decide"), noReview).content("{\"verdict\":\"NORMAL_WEAR\"}")).andExpect(status().isForbidden());
        mvc.perform(withCsrf(post("/api/admin/trials/damage-reports/1/decide"), manager).content("{\"verdict\":\"NORMAL_WEAR\"}")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TRIAL_DAMAGE_NOT_FOUND"));
    }

    @Test
    void 확인할일_목록은_권한별_종류_협찬숨김_확인하면_SPONSORED_구매인증_승인_보너스() throws Exception {
        long author = newMember();
        long postId = ids.nextId();
        boardFixture.post(postId, board, author, "숨긴 협찬", "본문");
        sponsors.reported(postId);
        long proofPost = ids.nextId();
        boardFixture.post(proofPost, board, author, "인증 글", "본문");
        long proof = ids.nextId();
        // SQL 로 남김: 구매 인증 올리기는 공개 사이트(회원 글쓴이) API 다 — 확인할 일 목록과 운영자 확인만 보려고 대기 줄(사진 자리 바이트)을 바로 둔다
        jdbc.update("INSERT INTO purchase_proofs (id, post_id, board_id, user_id, content, content_type, status, created_at) VALUES (?, ?, ?, ?, ?, 'image/jpeg', 'PENDING', now())",
                proof, proofPost, board, author, new byte[] {1, 2, 3});

        Cookie titleReader = login(operatorWith(Permission.TITLE_READ));
        mvc.perform(get("/api/admin/review-queue").cookie(titleReader)).andExpect(status().isOk()).andExpect(jsonPath("$.data.pending.TITLE_APPLICATION").exists())
                .andExpect(jsonPath("$.data.pending.SPONSOR_HIDDEN").doesNotExist());
        mvc.perform(get("/api/admin/review-queue?type=SPONSOR_HIDDEN").cookie(titleReader)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/review-queue").cookie(login(operatorWith(Permission.AD_READ)))).andExpect(status().isForbidden());

        String pending = mvc.perform(get("/api/admin/review-queue?type=SPONSOR_HIDDEN").cookie(manager)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        assertThat(JsonPath.<List<String>>read(pending, "$.data.items[?(@.id == '" + postId + "')].status")).containsExactly("PENDING");
        String proofs = mvc.perform(get("/api/admin/review-queue?type=PURCHASE_PROOF").cookie(manager)).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(proofs, "$.data.items[?(@.id == '" + proof + "')].refs.postId")).containsExactly(Long.toString(proofPost));
        assertThat(JsonPath.<List<String>>read(proofs, "$.data.items[?(@.id == '" + proof + "')].member.memberNo")).containsExactly(Long.toString(author));

        mvc.perform(withCsrf(post("/api/admin/review-queue/sponsor-hidden/" + postId + "/confirm"), manager).content("{\"note\":\"업체 제공\"}"))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT purchase_source FROM posts WHERE id = ?", String.class, postId)).isEqualTo("SPONSORED");
        String done = mvc.perform(get("/api/admin/review-queue?type=SPONSOR_HIDDEN&status=DONE").cookie(manager)).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(done, "$.data.items[?(@.id == '" + postId + "')].status")).containsExactly("CONFIRMED");

        mvc.perform(withCsrf(post("/api/admin/purchase-proofs/" + proof + "/reject"), manager).content("{}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.reason").exists());
        mvc.perform(withCsrf(post("/api/admin/purchase-proofs/" + proof + "/approve"), manager)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT status FROM purchase_proofs WHERE id = ?", String.class, proof)).isEqualTo("APPROVED");
        // 구매 인증 응답의 확인한 사람 — 운영자가 확인했으면 관리자 표시 이름
        String operatorName = jdbc.queryForObject("SELECT o.operator_name FROM operators o JOIN purchase_proofs p ON p.reviewed_by = o.id WHERE p.id = ?", String.class, proof);
        assertThat(proofService.list("DONE", board).stream().filter(p -> p.id().equals(Long.toString(proof))).map(TrialViews.Proof::reviewerName))
                .containsExactly(operatorName);
        assertThat(jdbc.queryForObject("SELECT coalesce(sum(delta), 0) FROM point_ledger WHERE user_id = ? AND reason = 'PURCHASE_PROOF'", Long.class, author)).isEqualTo(100L);
    }

    @Test
    void 게시판_목록_줄에_체험단_사용기_받기_실효값_켠_게시판_true_비운_게시판은_기본값_false() throws Exception {
        long other = ids.nextId();
        String otherSlug = "tb" + Long.toString(other, 36);
        boardFixture.board(other, otherSlug, "체험없음 " + otherSlug.substring(otherSlug.length() - 6), 971);
        String body = mvc.perform(get("/api/admin/boards").cookie(login(operatorWith(Permission.BOARD_READ)))).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        assertThat(JsonPath.<List<Boolean>>read(body, "$.data.boards[?(@.id == '" + board + "')].trialReviewsEnabled")).containsExactly(true);
        assertThat(JsonPath.<List<Boolean>>read(body, "$.data.boards[?(@.id == '" + other + "')].trialReviewsEnabled")).containsExactly(false);
    }

    @Test
    void 설정은_범위를_보고_바뀐_칸만_감사_요금표_체험단은_단위_발송비_예약_다른_상품은_400() throws Exception {
        Map<String, Object> saved = jdbc.queryForMap("SELECT give_price_limit, proof_bonus_points FROM trial_settings WHERE id = 1");
        try {
            mvc.perform(get("/api/admin/trials/settings").cookie(manager)).andExpect(status().isOk()).andExpect(jsonPath("$.data.givePriceLimit").exists());
            mvc.perform(withCsrf(put("/api/admin/trials/settings"), manager).content("{\"givePriceLimit\":-1}")).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.fields.givePriceLimit").exists());
            mvc.perform(withCsrf(put("/api/admin/trials/settings"), manager).content("{\"proofBonusPoints\":150}")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.proofBonusPoints").value(150));
            assertThat(auditLogs.where().targetType("TRIAL_SETTINGS").action("TRIAL_SETTINGS_UPDATE").lastById().orElseThrow().detail()).contains("proofBonusPoints")
                    .doesNotContain("givePriceLimit");
        } finally {
            jdbc.update("UPDATE trial_settings SET give_price_limit = ?, proof_bonus_points = ? WHERE id = 1", saved.get("give_price_limit"), saved.get("proof_bonus_points"));
        }

        Cookie pricing = login(operatorWith(Permission.AD_READ, Permission.AD_SETTINGS));
        String at = Instant.now().plus(Duration.ofDays(400 + ids.nextId() % 300)).truncatedTo(ChronoUnit.HOURS).toString();
        mvc.perform(withCsrf(post("/api/admin/ads/price-rules/WIDE_BANNER/schedules"), pricing).content("{\"effectiveAt\":\"" + at
                + "\",\"unitPrice\":1000,\"capacity\":1,\"maxDays\":1,\"maxLeadDays\":1,\"priceUnit\":\"PER_RECRUIT\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.priceUnit").exists());
        String view = mvc.perform(withCsrf(post("/api/admin/ads/price-rules/TRIAL/schedules"), pricing).content("{\"effectiveAt\":\"" + at
                + "\",\"unitPrice\":300000,\"maxDays\":1,\"maxLeadDays\":365,\"priceUnit\":\"PER_RECRUIT\",\"shippingFeePerPerson\":2500}")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> scheduleIds = JsonPath.read(view, "$.data.rules[?(@.product == 'TRIAL')].schedules[?(@.priceUnit == 'PER_RECRUIT')].id");
        assertThat(scheduleIds).hasSize(1);
        assertThat(JsonPath.<List<Integer>>read(view, "$.data.rules[?(@.product == 'TRIAL')].schedules[?(@.priceUnit == 'PER_RECRUIT')].shippingFeePerPerson"))
                .containsExactly(2500);
        mvc.perform(withCsrf(delete("/api/admin/ads/price-rules/TRIAL/schedules/" + scheduleIds.get(0)), pricing)).andExpect(status().isOk());
    }

    @Test
    void 관리자_메뉴_광고_묶음에_체험단_두_화면() {
        assertThat(AdminScreenRegistry.screen("adTrials")).hasValueSatisfying(s -> assertThat(s.path()).isEqualTo("/ads/trials"));
        assertThat(AdminScreenRegistry.screen("adTrialSettings")).hasValueSatisfying(s -> assertThat(s.permission()).isEqualTo(Permission.TRIAL_SETTINGS));
    }
}
