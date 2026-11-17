package com.community.trial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.ad.repository.master.AdAdvertiserStore;
import com.community.addressbook.dto.AddressInput;
import com.community.addressbook.service.AddressBookService;
import com.community.board.service.BoardCatalog;
import com.community.business.service.BusinessPii;
import com.community.commerce.Commerce04AdminTestBase;
import com.community.permission.entity.Permission;
import com.community.permission.entity.PermissionUnit;
import com.community.trial.dto.TrialViews;
import com.community.trial.service.SponsorReviewService;
import com.community.trial.service.TrialMemberService;
import com.community.trial.service.TrialService;
import com.community.zconfiguration.crypto.DataCipher;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 체험단 점검 고치기 — 관리자 HTTP(trial-group-contract 「점검 고치기」 — 결정 D2·D3·D4·D10·D12·L2·L8): 주소 가림·주소 보기(사유·열람 기록·감사·권한) · 반납 받음 권한 ·
 * 관리자 사진 API · 협찬 숨김 확인에 곁들인 제재와 신고 사건 함께 닫기(양쪽 방향) · 입고 부족 알림 · 칭호 시스템 회수 사유 문구 · 확인할 일 대기 수. 실제 필터 체인·PostgreSQL.
 */
class TrialAuditFixAdminHttpTest extends Commerce04AdminTestBase {

    @Autowired
    private BoardCatalog catalog;
    @Autowired
    private AdAdvertiserStore advertisers;
    @Autowired
    private AddressBookService addresses;
    @Autowired
    private TrialMemberService members;
    @Autowired
    private TrialService trials;
    @Autowired
    private SponsorReviewService sponsors;
    @Autowired
    private PlatformTransactionManager transactions;
    @Autowired
    private DataCipher cipher;

    private long board;
    private long advertiser;
    private long advertiserUser;
    private long creator;
    private Cookie manager;

    @BeforeEach
    void setUp() throws Exception {
        board = ids.nextId();
        String slug = "tz" + Long.toString(board, 36);
        boardFixture.board(board, slug, "점검관리 " + slug.substring(slug.length() - 6), 972);
        // SQL 로 남김: 게시판 정책 칸은 게시판 저장 API 가 바꾼다 — 이 시험은 사용기 게시판 값만 세운다
        jdbc.update("UPDATE boards SET purchase_source_mode = 'REQUIRED', trial_reviews_enabled = true WHERE id = ?", board);
        catalog.invalidate();
        creator = operatorWith();
        advertiserUser = newMember();
        long profile = ids.nextId();
        // SQL 로 남김: 사업자 신원 줄(승인)을 심사 기록 없이 바로 세운다 — 입고 부족 광고주 알림(D10)의 받는 회원
        jdbc.update("INSERT INTO business_profiles (id, user_id, status, re_review, company_name, biz_no_enc, biz_no_hash, biz_no_last4, owner_name_enc, phone_enc, email_enc, "
                        + "postcode, address1_enc, terms_version, privacy_version, agreed_at, submitted_at, created_at, updated_at) "
                        + "VALUES (?, ?, 'APPROVED', false, '점검관리상사', 'x', ?, '4321', ?, ?, ?, '06236', 'x', 'v1', 'v1', now(), now(), now(), now())",
                profile, advertiserUser, String.format("%064x", profile), cipher.encrypt(BusinessPii.OWNER, "대표"), cipher.encrypt(BusinessPii.PHONE, "0212345678"),
                cipher.encrypt(BusinessPii.EMAIL, "z@test.local"));
        advertiser = ids.nextId();
        new TransactionTemplate(transactions).executeWithoutResult(s -> advertisers.insert(new AdAdvertiserStore.NewAdvertiser(advertiser, "점검관리광고주" + advertiser % 1_000_000,
                profile, null, null, null, null, null, null, null, creator, Instant.now())));
        manager = login(operatorWith(Permission.TRIAL_MANAGE, Permission.TRIAL_REVIEW, Permission.AD_READ));
    }

    private String form(String shipper, int recruit) {
        Instant start = Instant.now().minus(Duration.ofMinutes(5)).truncatedTo(ChronoUnit.MINUTES);
        return "{\"advertiserId\":\"" + advertiser + "\",\"title\":\"점검 관리 체험\",\"productName\":\"청소기\",\"productPrice\":30000,\"form\":\"LEND\",\"reviewBoardId\":\""
                + board + "\",\"recruitCount\":" + recruit + ",\"reviewerShare\":0,\"applyStartAt\":\"" + start + "\",\"applyEndAt\":\"" + start.plus(Duration.ofDays(2))
                + "\",\"reviewDays\":14,\"returnDays\":10,\"shipper\":\"" + shipper + "\"}";
    }

    /** 모집 만들기 → 열기 → 신청 {@code count} 명 → 신청 마감 → 모두 뽑기. 모집 ID */
    private String running(String shipper, int count) throws Exception {
        String id = JsonPath.read(mvc.perform(withCsrf(post("/api/admin/trials"), manager).content(form(shipper, count))).andExpect(status().isCreated()).andReturn()
                .getResponse().getContentAsString(), "$.data.id");
        mvc.perform(withCsrf(put("/api/admin/trials/" + id), manager).content("{\"action\":\"PUBLISH\"}")).andExpect(status().isOk());
        List<String> consents = "ADVERTISER".equals(shipper) ? List.of("PROVIDE_CONTACT", "PRODUCT_CHARGE", "DAMAGE_CHARGE")
                : List.of("UNRETURNED_CONTACT", "PRODUCT_CHARGE", "DAMAGE_CHARGE");
        StringBuilder picked = new StringBuilder();
        for (int i = 0; i < count; i++) {
            long m = newMember();
            verified(m, LocalDate.of(1990, 1, 1), "FAKE", Instant.now());
            long addr = addresses.create(m, new AddressInput(null, "김신청", "01099998888", "06236", "서울 강남구 테헤란로 1", "101호", "문 앞", true)).id();
            members.apply(m, id, new TrialViews.ApplyRequest(Long.toString(addr), consents));
            long app = jdbc.queryForObject("SELECT id FROM trial_applications WHERE trial_id = ? AND user_id = ?", Long.class, Long.parseLong(id), m);
            picked.append(i == 0 ? "" : ",").append('"').append(app).append('"');
        }
        mvc.perform(withCsrf(put("/api/admin/trials/" + id), manager).content("{\"action\":\"CLOSE_APPLY\"}")).andExpect(status().isOk());
        mvc.perform(withCsrf(post("/api/admin/trials/" + id + "/select"), manager).content("{\"applicationIds\":[" + picked + "]}")).andExpect(status().isOk());
        return id;
    }

    private long firstParticipant(String trialId) {
        return jdbc.queryForObject("SELECT id FROM trial_participants WHERE trial_id = ? ORDER BY id LIMIT 1", Long.class, Long.parseLong(trialId));
    }

    private static byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private String upload(String trialId, String kind) throws Exception {
        return trials.uploadPhoto(TrialService.Actor.operator(creator, "ip"), trialId, kind, png()).id();
    }

    // ── D4 주소 가림·주소 보기 ──────────────────────────────────────────────

    @Test
    void D4_운영자_상세는_가린_주소_주소보기는_trial_address_reveal_사유_5자_이상_열람기록_감사_뒤_평문_광고주발송_모집은_409() throws Exception {
        String id = running("OPERATOR", 1);
        long p = firstParticipant(id);
        mvc.perform(get("/api/admin/trials/" + id).cookie(manager)).andExpect(status().isOk()).andExpect(jsonPath("$.data.participants[0].recipient").value("김*청"))
                .andExpect(jsonPath("$.data.participants[0].phone").value("010-****-8888")).andExpect(jsonPath("$.data.participants[0].address1").value("서울 강남구 …"))
                .andExpect(jsonPath("$.data.participants[0].address2").doesNotExist()).andExpect(jsonPath("$.data.participants[0].addressMasked").value(true));

        mvc.perform(withCsrf(post("/api/admin/trials/participants/" + p + "/address/reveal"), manager).content("{\"reason\":\"송장 입력 위해\"}"))
                .andExpect(status().isForbidden()); // trial.manage 만으로는 못 본다
        long revealerId = operatorWith(Permission.TRIAL_ADDRESS_REVEAL);
        Cookie revealer = login(revealerId);
        mvc.perform(withCsrf(post("/api/admin/trials/participants/" + p + "/address/reveal"), revealer).content("{\"reason\":\"짧음\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.reason").exists());
        mvc.perform(withCsrf(post("/api/admin/trials/participants/" + p + "/address/reveal"), revealer).content("{\"reason\":\"송장 입력을 위해 확인\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.recipient").value("김신청")).andExpect(jsonPath("$.data.address2").value("101호"))
                .andExpect(jsonPath("$.data.memo").value("문 앞")).andExpect(jsonPath("$.data.hideAt").exists())
                .andExpect(header().string("Cache-Control", Matchers.containsString("no-store")));
        assertThat(jdbc.queryForMap("SELECT operator_id, reason FROM trial_address_views WHERE participant_id = ?", p)).containsEntry("operator_id", revealerId)
                .containsEntry("reason", "송장 입력을 위해 확인");
        String detail = auditLogs.where().targetType("TRIAL").target(id).action("TRIAL_ADDRESS_REVEAL").lastById().orElseThrow().detail();
        assertThat(detail).contains("송장 입력을 위해 확인").doesNotContain("김신청").doesNotContain("테헤란로");

        String advertiserShips = running("ADVERTISER", 1);
        mvc.perform(withCsrf(post("/api/admin/trials/participants/" + firstParticipant(advertiserShips) + "/address/reveal"), revealer)
                .content("{\"reason\":\"송장 입력을 위해 확인\"}")).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("TRIAL_WRONG_SHIPPER"));

        // 취소한 줄(주소 파기) — 410
        mvc.perform(withCsrf(post("/api/admin/trials/participants/" + p + "/cancel"), manager).content("{\"reason\":\"취소\"}")).andExpect(status().isOk());
        mvc.perform(withCsrf(post("/api/admin/trials/participants/" + p + "/address/reveal"), revealer).content("{\"reason\":\"송장 입력을 위해 확인\"}"))
                .andExpect(status().isGone()).andExpect(jsonPath("$.error.code").value("TRIAL_ADDRESS_PURGED"));
    }

    @Test
    void D4_권한_trial_address_reveal_은_광고_유형의_민감_조치이고_V243_권한_행이_있다() {
        Permission permission = Permission.ofCode("trial.address.reveal").orElseThrow();
        assertThat(permission).isEqualTo(Permission.TRIAL_ADDRESS_REVEAL);
        assertThat(permission.unit()).isEqualTo(PermissionUnit.SENSITIVE);
        assertThat(permission.type()).isEqualTo(Permission.TRIAL_MANAGE.type());
        assertThat(permission.ordinal()).isEqualTo(Permission.TRIAL_REVIEW.ordinal() + 1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM permissions WHERE code = 'trial.address.reveal' AND feature_ready", Integer.class)).isEqualTo(1);
        // V243 — commerce.address.reveal 또는 trial.manage 를 가진(마이그레이션 때 있던) 운영자 역할은 함께 받는다(trial.review 만 가진 역할은 받지 않는다)
        assertThat(jdbc.queryForObject("SELECT count(*) FROM role_permissions x WHERE x.permission_code = 'trial.address.reveal' AND NOT EXISTS (SELECT 1 FROM role_permissions y "
                + "WHERE y.role_id = x.role_id AND y.permission_code IN ('commerce.address.reveal', 'trial.manage'))", Integer.class)).isZero();
    }

    // ── D12 반납 받음 권한 · D3 관리자 사진 API ──────────────────────────────

    @Test
    void D12_반납받음은_trial_review_만이면_403_D3_관리자_사진은_trial_review_도_보고_광고_읽기만이면_403() throws Exception {
        String id = running("OPERATOR", 1);
        long p = firstParticipant(id);
        mvc.perform(withCsrf(post("/api/admin/trials/" + id + "/stock"), manager).content("{\"quantity\":1,\"photoIds\":[\"" + upload(id, "STOCK") + "\"]}"))
                .andExpect(status().isOk());
        mvc.perform(withCsrf(put("/api/admin/trials/participants/" + p + "/shipment"), manager).content("{\"carrier\":\"CJ\",\"trackingNo\":\"1\"}"))
                .andExpect(status().isNoContent());
        String returnPhoto = upload(id, "RETURN");
        Cookie reviewOnly = login(operatorWith(Permission.TRIAL_REVIEW));
        mvc.perform(withCsrf(post("/api/admin/trials/participants/" + p + "/returned"), reviewOnly).content("{\"photoIds\":[\"" + returnPhoto + "\"]}"))
                .andExpect(status().isForbidden());
        mvc.perform(withCsrf(post("/api/admin/trials/participants/" + p + "/returned"), manager).content("{\"photoIds\":[\"" + returnPhoto + "\"]}"))
                .andExpect(status().isNoContent());

        String body = mvc.perform(get("/api/admin/trials/" + id).cookie(reviewOnly)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> urls = JsonPath.read(body, "$.data.participants[0].photos[*].url");
        assertThat(urls).containsExactly("/api/admin/trials/photos/" + returnPhoto);
        assertThat(JsonPath.<List<String>>read(body, "$.data.stock.photos[*].url")).allMatch(u -> u.startsWith("/api/admin/trials/photos/"));
        mvc.perform(get(urls.get(0)).cookie(reviewOnly)).andExpect(status().isOk()).andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("Cache-Control", Matchers.containsString("no-store")));
        mvc.perform(get(urls.get(0)).cookie(login(operatorWith(Permission.AD_READ)))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/trials/photos/1").cookie(manager)).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("TRIAL_PHOTO_NOT_FOUND"));
    }

    // ── D10 입고 부족 ───────────────────────────────────────────────────────

    @Test
    void D10_입고_수량이_뽑힌_인원보다_적으면_광고주_회원_알림과_운영자_알림() throws Exception {
        String id = running("OPERATOR", 2);
        long watcher = operatorWith(Permission.TRIAL_MANAGE);
        mvc.perform(withCsrf(post("/api/admin/trials/" + id + "/stock"), manager).content("{\"quantity\":1,\"photoIds\":[\"" + upload(id, "STOCK") + "\"]}"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member_notifications WHERE user_id = ? AND type = 'TRIAL_STOCK_SHORT'", Integer.class, advertiserUser))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admin_notifications WHERE operator_id = ? AND event_code = 'TRIAL_STOCK_SHORTAGE'", Integer.class, watcher))
                .isEqualTo(1);
        mvc.perform(withCsrf(post("/api/admin/trials/" + id + "/stock"), manager).content("{\"quantity\":2,\"photoIds\":[\"" + upload(id, "STOCK") + "\"]}"))
                .andExpect(status().isOk()); // 다 채우면 알리지 않는다
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member_notifications WHERE user_id = ? AND type = 'TRIAL_STOCK_SHORT'", Integer.class, advertiserUser))
                .isEqualTo(1);
    }

    // ── D2 협찬 숨김 확인 + 제재 + 신고 사건 ─────────────────────────────────

    /** 협찬 숨김 신고 한 건이 든 열린 사건(공개 서버 신고 접수가 만드는 모양) — 사건 ID */
    private long sponsorCase(long postId, long authorId, String... extraReasons) {
        long caseId = ids.nextId();
        // SQL 로 남김: 신고 접수는 공개 서버 API 다 — 관리자 처리만 보려고 사건·신고 줄을 바로 둔다
        jdbc.update("INSERT INTO report_cases (id, target_type, target_id, post_id, board_id, target_author_id, status, report_count, first_reported_at, last_reported_at) "
                + "VALUES (?, 'POST', ?, ?, ?, ?, 'PENDING', ?, now(), now())", caseId, postId, postId, board, authorId, 1 + extraReasons.length);
        List<String> reasons = new java.util.ArrayList<>(List.of("SPONSOR_HIDDEN"));
        reasons.addAll(List.of(extraReasons));
        for (String reason : reasons) {
            jdbc.update("INSERT INTO reports (id, case_id, target_type, target_id, post_id, board_id, reporter_id, reason, counted) VALUES (?, ?, 'POST', ?, ?, ?, ?, ?, true)",
                    ids.nextId(), caseId, postId, postId, board, newMember(), reason);
        }
        sponsors.reported(postId);
        return caseId;
    }

    @Test
    void D2_협찬숨김_확인에_제재를_곁들이면_같은_트랜잭션에서_제재_사건도_처리완료_무시하면_사건도_문제없음() throws Exception {
        long author = newMember();
        long postId = ids.nextId();
        boardFixture.post(postId, board, author, "숨긴 협찬", "본문");
        long caseId = sponsorCase(postId, author);
        Cookie sanctioner = login(operatorWith(Permission.TRIAL_REVIEW, Permission.SANCTION_MANAGE));

        // 제재 권한 없이 제재를 곁들이면 403 — 확인도 롤백
        mvc.perform(withCsrf(post("/api/admin/review-queue/sponsor-hidden/" + postId + "/confirm"), manager)
                .content("{\"note\":\"업체 제공\",\"sanction\":{\"kind\":\"WARNING\",\"reason\":\"협찬 표시 위반\"}}")).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT status FROM trial_sponsor_reviews WHERE post_id = ?", String.class, postId)).isEqualTo("PENDING");

        mvc.perform(withCsrf(post("/api/admin/review-queue/sponsor-hidden/" + postId + "/confirm"), sanctioner)
                .content("{\"note\":\"업체 제공 확인\",\"sanction\":{\"kind\":\"WARNING\",\"reason\":\"협찬 표시 위반\"}}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("WARNING"));
        assertThat(jdbc.queryForObject("SELECT purchase_source FROM posts WHERE id = ?", String.class, postId)).isEqualTo("SPONSORED");
        assertThat(jdbc.queryForMap("SELECT status, action, reason_code FROM report_cases WHERE id = ?", caseId)).containsEntry("status", "RESOLVED")
                .containsEntry("action", "SPONSOR_LABEL").containsEntry("reason_code", "SPONSOR_HIDDEN");
        assertThat(jdbc.queryForList("SELECT DISTINCT status FROM reports WHERE case_id = ?", String.class, caseId)).containsExactly("ACCEPTED");
        assertThat(jdbc.queryForMap("SELECT kind, report_case_id FROM member_sanctions WHERE user_id = ? ORDER BY created_at DESC LIMIT 1", author))
                .containsEntry("kind", "WARNING").containsEntry("report_case_id", caseId);

        // 무시 — 사건은 문제 없음(DISMISSED)
        long other = ids.nextId();
        boardFixture.post(other, board, author, "숨김 아님", "본문");
        long otherCase = sponsorCase(other, author);
        mvc.perform(withCsrf(post("/api/admin/review-queue/sponsor-hidden/" + other + "/dismiss"), manager).content("{\"note\":\"직접 구매 확인\"}"))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForMap("SELECT status, action FROM report_cases WHERE id = ?", otherCase)).containsEntry("status", "DISMISSED").containsEntry("action", "DISMISS");

        // 다른 사유가 섞인 사건은 그대로 둔다(신고 관리에서 따로)
        long mixed = ids.nextId();
        boardFixture.post(mixed, board, author, "섞인 신고", "본문");
        long mixedCase = sponsorCase(mixed, author, "ABUSE");
        mvc.perform(withCsrf(post("/api/admin/review-queue/sponsor-hidden/" + mixed + "/confirm"), manager).content("{}")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT status FROM report_cases WHERE id = ?", String.class, mixedCase)).isEqualTo("PENDING");
    }

    @Test
    void D2_익명글_협찬숨김_줄은_회원없이_anonymousPostId_로_제재미리보기_확인에_곁들인_제재는_봉인() throws Exception {
        long author = newMember();
        long postId = ids.nextId();
        boardFixture.post(postId, board, author, "익명 협찬 숨김", "본문");
        // SQL 로 남김: 익명 글은 익명 게시판 글쓰기가 정한다 — 이 시험은 확인할 일 처리만 보려고 글 한 줄의 익명 칸만 세운다
        jdbc.update("UPDATE posts SET anonymous = true WHERE id = ?", postId);
        long caseId = sponsorCase(postId, author);
        Cookie sanctioner = login(operatorWith(Permission.TRIAL_REVIEW, Permission.SANCTION_READ, Permission.SANCTION_MANAGE));

        // 확인할 일 줄 — 회원 없음(봉인) · anonymous true · sanctionable true · anonymousPostId = 글 ID
        String body = mvc.perform(get("/api/admin/review-queue?type=SPONSOR_HIDDEN").cookie(sanctioner)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        List<Map<String, Object>> rows = JsonPath.read(body, "$.data.items[?(@.id == '" + postId + "')]");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("member")).isNull();
        @SuppressWarnings("unchecked")
        Map<String, String> refs = (Map<String, String>) rows.get(0).get("refs");
        assertThat(refs).containsEntry("anonymous", "true").containsEntry("sanctionable", "true").containsEntry("anonymousPostId", Long.toString(postId));

        // 그 값으로 제재 미리보기 — 대상 봉인
        mvc.perform(get("/api/admin/sanctions/preview?anonymousPostId=" + refs.get("anonymousPostId")).cookie(sanctioner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.sealed").value(true));

        // 확인 + 제재(닉네임 확인 없이) → 봉인 제재 · 사건 처리 완료
        mvc.perform(withCsrf(post("/api/admin/review-queue/sponsor-hidden/" + postId + "/confirm"), sanctioner)
                .content("{\"note\":\"업체 제공 확인\",\"sanction\":{\"kind\":\"WARNING\",\"reason\":\"협찬 표시 위반\"}}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("WARNING"));
        assertThat(jdbc.queryForMap("SELECT kind, sealed, report_case_id FROM member_sanctions WHERE user_id = ? ORDER BY created_at DESC LIMIT 1", author))
                .containsEntry("kind", "WARNING").containsEntry("sealed", true).containsEntry("report_case_id", caseId);
        assertThat(jdbc.queryForObject("SELECT action FROM report_cases WHERE id = ?", String.class, caseId)).isEqualTo("SPONSOR_LABEL");

        // 회원 글 줄은 anonymous false · anonymousPostId 없음 · member 로 미리보기
        long plain = ids.nextId();
        boardFixture.post(plain, board, author, "회원 협찬 숨김", "본문");
        sponsors.reported(plain);
        body = mvc.perform(get("/api/admin/review-queue?type=SPONSOR_HIDDEN").cookie(sanctioner)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        rows = JsonPath.read(body, "$.data.items[?(@.id == '" + plain + "')]");
        @SuppressWarnings("unchecked")
        Map<String, String> plainRefs = (Map<String, String>) rows.get(0).get("refs");
        assertThat(plainRefs).containsEntry("anonymous", "false").containsEntry("sanctionable", "true").doesNotContainKey("anonymousPostId");
        assertThat(rows.get(0).get("member")).isNotNull();
    }

    @Test
    void D2_신고관리에서_사건을_닫으면_확인할일_줄도_닫힌다_협찬숨김_사유로_조치면_협찬_고정_무시면_무시() throws Exception {
        long author = newMember();
        Cookie reporter = login(operatorWith(Permission.REPORT_READ, Permission.REPORT_MANAGE));
        long postId = ids.nextId();
        boardFixture.post(postId, board, author, "신고 관리 처리", "본문");
        long caseId = sponsorCase(postId, author);
        mvc.perform(withCsrf(post("/api/admin/reports/" + caseId + "/resolve"), reporter)
                .content("{\"action\":\"HIDE\",\"reasonCode\":\"SPONSOR_HIDDEN\",\"note\":\"협찬을 숨겼어요\"}")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT status FROM trial_sponsor_reviews WHERE post_id = ?", String.class, postId)).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("SELECT purchase_source FROM posts WHERE id = ?", String.class, postId)).isEqualTo("SPONSORED");

        long other = ids.nextId();
        boardFixture.post(other, board, author, "신고 관리 무시", "본문");
        long otherCase = sponsorCase(other, author);
        mvc.perform(withCsrf(post("/api/admin/reports/" + otherCase + "/dismiss"), reporter).content("{\"note\":\"문제 없음\"}")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT status FROM trial_sponsor_reviews WHERE post_id = ?", String.class, other)).isEqualTo("DISMISSED");
        assertThat(jdbc.queryForObject("SELECT purchase_source IS NULL FROM posts WHERE id = ?", Boolean.class, other)).isTrue();
    }

    // ── L8 확인할 일 대기 수 · L2 시스템 회수 사유 ─────────────────────────────

    @Test
    void L8_확인할일_대기수는_종류마다_센_값과_목록이_같다() throws Exception {
        long author = newMember();
        long postId = ids.nextId();
        boardFixture.post(postId, board, author, "대기 수", "본문");
        sponsors.reported(postId);
        String body = mvc.perform(get("/api/admin/review-queue?type=SPONSOR_HIDDEN").cookie(manager)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        int pending = JsonPath.read(body, "$.data.pending.SPONSOR_HIDDEN");
        int items = JsonPath.<List<Object>>read(body, "$.data.items").size();
        assertThat(pending).isEqualTo(jdbc.queryForObject("SELECT count(*) FROM trial_sponsor_reviews WHERE status = 'PENDING'", Integer.class));
        assertThat(items).isEqualTo(Math.min(pending, 200));
    }

    @Test
    void L2_시스템_회수_사유는_코드_대신_번들_문구로_응답한다() throws Exception {
        long m = newMember();
        long titleId = jdbc.queryForObject("SELECT id FROM titles WHERE title_key = 'reviewer'", Long.class);
        // SQL 로 남김: 체험단 미반납 확정이 남기는 시스템 회수 줄(운영자 없음 · 사유 코드)을 바로 둔다
        jdbc.update("INSERT INTO member_titles (id, user_id, title_id, period, source, granted_at, revoked_at, revoked_reason) VALUES (?, ?, ?, '', 'APPLICATION', now(), now(), "
                + "'TRIAL_UNRETURNED')", ids.nextId(), m, titleId);
        Cookie reader = login(operatorWith(Permission.TITLE_READ));
        mvc.perform(get("/api/admin/members/" + m + "/titles").cookie(reader).header("Accept-Language", "ko")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].revokeReason").value("체험단 제품 미반납으로 자동 회수"));
    }

    @Test
    void 설정_사진_보관_일수는_1_365() throws Exception {
        Cookie settings = login(operatorWith(Permission.TRIAL_SETTINGS));
        mvc.perform(withCsrf(put("/api/admin/trials/settings"), settings).content("{\"photoKeepDays\":0}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.photoKeepDays").exists());
        mvc.perform(get("/api/admin/trials/settings").cookie(settings)).andExpect(status().isOk()).andExpect(jsonPath("$.data.photoKeepDays").isNumber());
        Map<String, Object> row = jdbc.queryForMap("SELECT photo_keep_days FROM trial_settings WHERE id = 1");
        assertThat(row.get("photo_keep_days")).isInstanceOf(Integer.class);
    }
}
