package com.community.trial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.ad.repository.master.AdAdvertiserStore;
import com.community.addressbook.dto.AddressInput;
import com.community.addressbook.service.AddressBookService;
import com.community.auth.dto.AuthUser;
import com.community.board.service.BoardCatalog;
import com.community.business.service.BusinessPii;
import com.community.cache.definition.CacheNames;
import com.community.commerce.CommerceApiTestBase;
import com.community.report.dto.request.ReportRequest;
import com.community.report.service.ReportService;
import com.community.title.service.TitleReviewService;
import com.community.trial.batch.TrialDailyJob;
import com.community.trial.dto.TrialViews;
import com.community.trial.repository.master.TrialQueries;
import com.community.trial.service.PurchaseProofService;
import com.community.trial.service.SponsorReviewService;
import com.community.trial.service.TrialPii;
import com.community.trial.service.TrialService;
import com.community.user.entity.Role;
import com.community.withdrawal.WithdrawalTestSupport;
import com.community.withdrawal.dto.response.WithdrawalView;
import com.community.withdrawal.service.WithdrawalService;
import com.community.zconfiguration.crypto.DataCipher;
import com.community.zconfiguration.exception.CommonException;
import com.jayway.jsonpath.JsonPath;
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
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 체험단·칭호 점검 고치기(trial-group-contract 「점검 고치기」 — 결정 D2·D3·D5·D6·D8·D11·D13·D14) — 공개 사이트 앱 문맥 + 실제 PostgreSQL + 옮길 수 있는 시계. 관리자 HTTP(주소 보기·
 * 반납 권한·협찬 숨김 제재·신고 사건·입고 부족 알림·관리자 사진 API)는 {@code TrialAuditFixAdminHttpTest}.
 */
class TrialAuditFixTest extends CommerceApiTestBase {

    @Autowired
    private org.springframework.web.context.WebApplicationContext webContext;
    @Autowired
    private BoardCatalog catalog;
    @Autowired
    private TrialService trials;
    @Autowired
    private TrialDailyJob daily;
    @Autowired
    private TrialQueries queries;
    @Autowired
    private AddressBookService addresses;
    @Autowired
    private AdAdvertiserStore advertisers;
    @Autowired
    private DataCipher cipher;
    @Autowired
    private ReportService reports;
    @Autowired
    private SponsorReviewService sponsors;
    @Autowired
    private PurchaseProofService proofs;
    @Autowired
    private TitleReviewService titleReviews;
    @Autowired
    private WithdrawalService withdrawals;
    @Autowired
    private PasswordEncoder encoder;
    @Autowired
    private CacheManager caches;

    private MockMvc mvc;
    private long board;
    private String slug;
    private long operator;
    private long advertiser;
    private long advertiserUser;
    private long profile;
    private Map<String, Object> savedSettings;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(webContext).build();
        board = ids.nextId();
        slug = "tf" + Long.toString(board, 36);
        boardFixture.board(board, slug, "점검 " + slug.substring(slug.length() - 6), 963);
        // SQL 로 남김: 게시판 정책 칸은 관리자 저장 API 로 바뀐다 — 사용기 게시판 값(꼭 골라야 함 + 체험단 사용기 받기)만 세운다
        jdbc.update("UPDATE boards SET purchase_source_mode = 'REQUIRED', trial_reviews_enabled = true WHERE id = ?", board);
        catalog.invalidate();
        operator = newOperator();
        advertiserUser = member(true);
        profile = ids.nextId();
        // SQL 로 남김: 사업자 신원 줄의 심사 상태·암호문 칸을 심사 기록 없이 바로 세운다 — 팩터리(apply)는 신청(PENDING)만 만든다
        jdbc.update("INSERT INTO business_profiles (id, user_id, status, re_review, company_name, biz_no_enc, biz_no_hash, biz_no_last4, owner_name_enc, phone_enc, email_enc, "
                        + "postcode, address1_enc, terms_version, privacy_version, agreed_at, submitted_at, created_at, updated_at) "
                        + "VALUES (?, ?, 'APPROVED', false, ?, 'x', ?, '4321', ?, ?, ?, '06236', 'x', 'v1', 'v1', now(), now(), now(), now())",
                profile, advertiserUser, "점검상사" + profile % 100000, String.format("%064x", profile), cipher.encrypt(BusinessPii.OWNER, "대표"),
                cipher.encrypt(BusinessPii.PHONE, "0212345678"), cipher.encrypt(BusinessPii.EMAIL, "a@test.local"));
        // SQL 로 남김: 사업자 서비스 줄(광고 승인) — 광고주 화면 자격(D5)
        jdbc.update("INSERT INTO business_services (id, profile_id, service, status, updated_at) VALUES (?, ?, 'AD', 'APPROVED', now())", ids.nextId(), profile);
        advertiser = ids.nextId();
        tx.executeWithoutResult(s -> advertisers.insert(new AdAdvertiserStore.NewAdvertiser(advertiser, "점검광고주" + advertiser % 1_000_000, profile, null, null, null, null,
                null, null, null, operator, Instant.now())));
        savedSettings = jdbc.queryForMap("SELECT photo_keep_days FROM trial_settings WHERE id = 1");
    }

    @AfterEach
    void restore() {
        jdbc.update("UPDATE trial_settings SET photo_keep_days = ? WHERE id = 1", savedSettings.get("photo_keep_days"));
    }

    // ── 준비 ───────────────────────────────────────────────────────────

    private static MockHttpServletRequestBuilder as(long memberId, MockHttpServletRequestBuilder request) {
        AuthUser user = new AuthUser(memberId, Role.USER);
        return request.principal(new UsernamePasswordAuthenticationToken(user, null, user.authorities())).header("Accept-Language", "ko")
                .contentType(MediaType.APPLICATION_JSON);
    }

    private static org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder asFile(long memberId,
            org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder request) {
        AuthUser user = new AuthUser(memberId, Role.USER);
        request.principal(new UsernamePasswordAuthenticationToken(user, null, user.authorities())).header("Accept-Language", "ko");
        return request;
    }

    private long member(boolean identity) {
        long id = newMember();
        memberFixture.verifyEmail(id);
        String b36 = Long.toString(id, 36);
        memberFixture.profile(id, "f" + b36.substring(Math.max(0, b36.length() - 11)), "REGULAR");
        if (identity) {
            String hash = String.format("%064x", id);
            // SQL 로 남김: identity_verifications 는 엔티티가 없는 표다(가짜 본인인증 결과를 바로 둔다)
            jdbc.update("INSERT INTO identity_verifications (user_id, di_hash, ci_hash, birth_date, provider, provider_tx_id, verified_at) VALUES (?, ?, ?, ?, 'FAKE', ?, now())",
                    id, hash, String.format("f%063x", id), java.time.LocalDate.of(1990, 1, 1), "tx-" + id);
        }
        return id;
    }

    private long address(long memberId) {
        return addresses.create(memberId, new AddressInput("집", "홍길동", "01012345678", "06236", "서울 강남구 테헤란로 1", "101호", "문 앞", true)).id();
    }

    private void reviewer(long memberId, String tier) {
        long titleId = jdbc.queryForObject("SELECT id FROM titles WHERE title_key = 'reviewer'", Long.class);
        // SQL 로 남김: 칭호 수여·승인은 관리자 서버의 일이다 — 체험단 판정만 보려고 받은 줄을 바로 둔다
        jdbc.update("INSERT INTO member_titles (id, user_id, title_id, tier, period, source, granted_at) VALUES (?, ?, ?, ?, '', 'APPLICATION', now())", ids.nextId(),
                memberId, titleId, tier);
    }

    private long trial(String form, String shipper, int recruit, long price) {
        Instant start = clock.instant().truncatedTo(ChronoUnit.MINUTES);
        TrialViews.TrialForm f = new TrialViews.TrialForm(Long.toString(advertiser), "점검 체험 " + ids.nextId() % 100000, "무선 이어폰", price, form, Long.toString(board), recruit,
                0, 48, 24, start.toString(), start.plus(Duration.ofDays(3)).toString(), 14, "LEND".equals(form) ? 10 : null, shipper, "설명", null, null);
        String id = trials.create(operator, "127.0.0.1", f).id();
        trials.update(operator, "127.0.0.1", id, action("PUBLISH"));
        return Long.parseLong(id);
    }

    private static TrialViews.TrialForm action(String action) {
        return new TrialViews.TrialForm(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, action);
    }

    private TrialViews.TrialForm edit(long trialId, long advertiserId, long price) {
        Map<String, Object> t = jdbc.queryForMap("SELECT title, form, shipper, recruit_count, apply_start_at, apply_end_at, return_days FROM trials WHERE id = ?", trialId);
        return new TrialViews.TrialForm(Long.toString(advertiserId), (String) t.get("title"), "무선 이어폰", price, (String) t.get("form"), Long.toString(board),
                (Integer) t.get("recruit_count"), 0, 48, 24, ((java.sql.Timestamp) t.get("apply_start_at")).toInstant().toString(),
                ((java.sql.Timestamp) t.get("apply_end_at")).toInstant().toString(), 14, (Integer) t.get("return_days"), (String) t.get("shipper"), "설명", null, null);
    }

    private ResultActions apply(long memberId, long trialId, List<String> consents) throws Exception {
        String body = "{\"addressId\":\"" + address(memberId) + "\",\"consents\":[" + String.join(",", consents.stream().map(c -> "\"" + c + "\"").toList()) + "]}";
        return mvc.perform(as(memberId, post("/api/trials/" + trialId + "/applications")).content(body));
    }

    private static List<String> consents(String form, String shipper) {
        if ("ADVERTISER".equals(shipper)) {
            return "LEND".equals(form) ? List.of("PROVIDE_CONTACT", "PRODUCT_CHARGE", "DAMAGE_CHARGE") : List.of("PROVIDE_CONTACT");
        }
        return "LEND".equals(form) ? List.of("UNRETURNED_CONTACT", "PRODUCT_CHARGE", "DAMAGE_CHARGE") : List.of();
    }

    private long applicationId(long trialId, long memberId) {
        return jdbc.queryForObject("SELECT id FROM trial_applications WHERE trial_id = ? AND user_id = ? AND status <> 'CANCELLED'", Long.class, trialId, memberId);
    }

    private long participant(long trialId, long memberId) {
        return jdbc.queryForObject("SELECT id FROM trial_participants WHERE trial_id = ? AND user_id = ?", Long.class, trialId, memberId);
    }

    private void selectAll(long trialId, List<Long> applicationIds) {
        Instant end = jdbc.queryForObject("SELECT apply_end_at FROM trials WHERE id = ?", java.sql.Timestamp.class, trialId).toInstant();
        if (clock.instant().isBefore(end)) {
            clock.set(end.plusSeconds(60));
        }
        trials.select(operator, "127.0.0.1", Long.toString(trialId), new TrialViews.SelectRequest(applicationIds.stream().map(String::valueOf).toList()));
    }

    /** 회원 하나를 뽑아 둔 모집의 뽑힌 줄 */
    private long selected(long memberId, String form, String shipper) throws Exception {
        long t = trial(form, shipper, 1, 30000);
        apply(memberId, t, consents(form, shipper)).andExpect(status().isCreated());
        selectAll(t, List.of(applicationId(t, memberId)));
        return participant(t, memberId);
    }

    private long trialOf(long participantId) {
        return jdbc.queryForObject("SELECT trial_id FROM trial_participants WHERE id = ?", Long.class, participantId);
    }

    private static byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
        image.setRGB(3, 3, 0xff0000);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private String upload(TrialService.Actor actor, long trialId, String kind) throws Exception {
        return trials.uploadPhoto(actor, Long.toString(trialId), kind, png()).id();
    }

    private ResultActions write(long memberId, String body) throws Exception {
        return mvc.perform(as(memberId, post("/api/posts")).content(body));
    }

    private static String postBody(String slug, String title, String extra) {
        return "{\"boardSlug\":\"" + slug + "\",\"title\":\"" + title + "\",\"contentFormat\":\"TEXT\",\"content\":\"사용기 본문 열 글자 넘게 씁니다 " + title + "\"" + extra + "}";
    }

    private long postIdOf(ResultActions created) throws Exception {
        return Long.parseLong(JsonPath.read(created.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.data.id"));
    }

    private String uploadProof(long author, long postId) throws Exception {
        return JsonPath.read(mvc.perform(asFile(author, multipart("/api/posts/" + postId + "/purchase-proof").file(new MockMultipartFile("file", "r.png", "image/png", png()))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.data.id");
    }

    private static String code(Throwable e) {
        return ((CommonException) e).getErrorCode();
    }

    private String reviewerTier(long memberId) {
        return jdbc.queryForObject("SELECT mt.tier FROM member_titles mt JOIN titles t ON t.id = mt.title_id WHERE t.title_key = 'reviewer' AND mt.user_id = ? "
                + "AND mt.revoked_at IS NULL", String.class, memberId);
    }

    // ── D2 협찬 고정 ─────────────────────────────────────────────────────────

    @Test
    void D2_협찬숨김_확인된_글은_글쓴이가_구입경로를_바꿀_수_없다_400_같은_값은_된다_응답에_locked() throws Exception {
        long author = member(true);
        long postId = postIdOf(write(author, postBody(slug, "협찬 숨긴 글", ",\"purchaseSource\":\"SELF\"")));
        mvc.perform(get("/api/posts/" + postId)).andExpect(jsonPath("$.data.purchase.locked").value(false));
        reports.report(new ReportRequest("POST", Long.toString(postId), "SPONSOR_HIDDEN", null), member(true));
        sponsors.confirm(operator, "127.0.0.1", Long.toString(postId), "업체 제공 확인");

        String self = postBody(slug, "협찬 숨긴 글 고침", ",\"purchaseSource\":\"SELF\"").replace("\"boardSlug\":\"" + slug + "\",", "");
        mvc.perform(as(author, put("/api/posts/" + postId)).content(self)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.purchaseSource").exists());
        assertThat(jdbc.queryForObject("SELECT purchase_source FROM posts WHERE id = ?", String.class, postId)).isEqualTo("SPONSORED");
        String same = postBody(slug, "협찬 숨긴 글 고침", ",\"purchaseSource\":\"SPONSORED\"").replace("\"boardSlug\":\"" + slug + "\",", "");
        mvc.perform(as(author, put("/api/posts/" + postId)).content(same)).andExpect(status().isOk());
        String keep = postBody(slug, "구입 경로 칸 없이", "").replace("\"boardSlug\":\"" + slug + "\",", "");
        mvc.perform(as(author, put("/api/posts/" + postId)).content(keep)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT purchase_source FROM posts WHERE id = ?", String.class, postId)).isEqualTo("SPONSORED");
        mvc.perform(get("/api/posts/" + postId)).andExpect(jsonPath("$.data.purchase.source").value("SPONSORED"))
                .andExpect(jsonPath("$.data.purchase.locked").value(true));
    }

    // ── D3 사진 비공개 ───────────────────────────────────────────────────────

    @Test
    void D3_사진은_암호문으로만_두고_주소는_권한_API_광고주는_자기모집만_회원은_자기_파손사진만_보관기한_뒤_파기되면_410() throws Exception {
        TrialService.Actor adv = TrialService.Actor.advertiser(advertiserUser, advertiser, "ip");
        long m = member(true);
        long p = selected(m, "LEND", "ADVERTISER");
        long t = trialOf(p);
        TrialViews.Uploaded up = trials.uploadPhoto(adv, Long.toString(t), "PRE_SHIP", png());
        assertThat(up.key()).isNull();
        assertThat(up.url()).isEqualTo("/api/business/trials/photos/" + up.id());
        Map<String, Object> row = jdbc.queryForMap("SELECT content, content_type, purged_at FROM trial_photos WHERE id = ?", Long.parseLong(up.id()));
        byte[] stored = (byte[]) row.get("content");
        assertThat(stored[0]).isNotEqualTo((byte) 0xFF); // JPEG 원문이 아니라 암호문
        assertThat(cipher.decryptBytes(TrialPii.PHOTO, stored)).startsWith((byte) 0xFF, (byte) 0xD8); // 다시 인코딩한 JPEG
        assertThat(row).containsEntry("content_type", "image/jpeg");

        // 광고주 — 자기가 올린 붙기 전 사진은 볼 수 있다, 다른 광고주는 404
        mvc.perform(as(advertiserUser, get(up.url()))).andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                .string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
        trials.ship(adv, Long.toString(p), new TrialViews.ShipmentRequest("우체국", "1", List.of(up.id())));
        trials.confirmReturn(adv, Long.toString(p), new TrialViews.PhotosRequest(List.of(upload(adv, t, "RETURN"))));
        String damagePhoto = upload(adv, t, "DAMAGE");
        TrialViews.DamageReport report = trials.reportDamage(adv, Long.toString(p), new TrialViews.DamageRequest("흠집", List.of(damagePhoto)));
        assertThat(report.photos()).extracting(TrialViews.Photo::url).containsExactly("/api/business/trials/photos/" + damagePhoto);

        // 회원 — 내 파손 신고 사진만(발송 전 사진·남의 파손 사진은 404)
        mvc.perform(as(m, get("/api/me/trials/photos/" + damagePhoto))).andExpect(status().isOk());
        mvc.perform(as(m, get("/api/me/trials/photos/" + up.id()))).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("TRIAL_PHOTO_NOT_FOUND"));
        mvc.perform(as(member(true), get("/api/me/trials/photos/" + damagePhoto))).andExpect(status().isNotFound());
        String mine = mvc.perform(as(m, get("/api/me/trials"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(mine, "$.data[0].participation.damageReports[0].photos[*].url")).containsExactly("/api/me/trials/photos/" + damagePhoto);

        // 다른 광고주(자기 사업자·광고주) — 404
        long otherUser = member(true);
        long otherProfile = ids.nextId();
        // SQL 로 남김: 두 번째 사업자 신원(승인) — 팩터리는 신청만 만든다
        jdbc.update("INSERT INTO business_profiles (id, user_id, status, re_review, company_name, biz_no_enc, biz_no_hash, biz_no_last4, owner_name_enc, phone_enc, email_enc, "
                        + "postcode, address1_enc, terms_version, privacy_version, agreed_at, submitted_at, created_at, updated_at) "
                        + "VALUES (?, ?, 'APPROVED', false, '다른상사', 'x', ?, '1111', 'x', 'x', 'x', '06236', 'x', 'v1', 'v1', now(), now(), now(), now())",
                otherProfile, otherUser, String.format("%064x", otherProfile));
        jdbc.update("INSERT INTO business_services (id, profile_id, service, status, updated_at) VALUES (?, ?, 'AD', 'APPROVED', now())", ids.nextId(), otherProfile);
        long otherAdvertiser = ids.nextId();
        tx.executeWithoutResult(s -> advertisers.insert(new AdAdvertiserStore.NewAdvertiser(otherAdvertiser, "다른광고주" + otherAdvertiser % 1_000_000, otherProfile, null, null,
                null, null, null, null, null, operator, Instant.now())));
        mvc.perform(as(otherUser, get("/api/business/trials/photos/" + damagePhoto))).andExpect(status().isNotFound());

        // 보관 기한 — 모집이 끝나도 판정 안 된 파손 신고가 있으면 남고, 판정 뒤 설정 일수가 지나면 파기(줄은 남는다) → 410
        jdbc.update("UPDATE trial_settings SET photo_keep_days = 30 WHERE id = 1");
        // SQL 로 남김: 이의 기한(7일)이 지난 것으로 둔다 — 시계를 옮기면 판정 시각 기준도 함께 옮겨 보기 어렵다
        jdbc.update("UPDATE trial_damage_reports SET objection_due_at = now() - interval '1 minute' WHERE id = ?", Long.parseLong(report.id()));
        jdbc.update("UPDATE trial_participants SET kept = false, kept_at = now() WHERE id = ?", p); // 자동 끝남 판정을 위해 판정해 둔다
        trials.update(operator, "ip", Long.toString(t), action("CLOSE"));
        clock.advance(Duration.ofDays(31));
        daily.runOnce();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM trial_photos WHERE trial_id = ? AND purged_at IS NOT NULL", Integer.class, t)).isZero(); // 열린 파손 신고
        trials.decideDamage(operator, "ip", report.id(), new TrialViews.DecideRequest("NORMAL_WEAR", null));
        daily.runOnce();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM trial_photos WHERE trial_id = ? AND purged_at IS NOT NULL", Integer.class, t)).isZero(); // 판정 뒤 30일 전
        clock.advance(Duration.ofDays(31));
        daily.runOnce();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM trial_photos WHERE trial_id = ? AND (purged_at IS NULL OR content IS NOT NULL)", Integer.class, t)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM trial_photos WHERE trial_id = ?", Integer.class, t)).isEqualTo(3);
        mvc.perform(as(m, get("/api/me/trials/photos/" + damagePhoto))).andExpect(status().isGone()).andExpect(jsonPath("$.error.code").value("TRIAL_PHOTO_PURGED"));
    }

    @Test
    void D3_보관기한_후보는_끝나거나_취소된_모집만_진행중_모집_사진은_남는다() throws Exception {
        TrialService.Actor op = TrialService.Actor.operator(operator, "ip");
        long t = trial("GIVE", "OPERATOR", 1, 30000);
        long m = member(true);
        apply(m, t, consents("GIVE", "OPERATOR")).andExpect(status().isCreated());
        selectAll(t, List.of(applicationId(t, m)));
        trials.stock(operator, "ip", Long.toString(t), new TrialViews.StockRequest(1, null, List.of(upload(op, t, "STOCK"))));
        clock.advance(Duration.ofDays(400));
        long photo = jdbc.queryForObject("SELECT id FROM trial_photos WHERE trial_id = ?", Long.class, t);
        assertThat(candidates(clock.instant())).doesNotContain(photo); // 진행 중
        trials.update(operator, "ip", Long.toString(t), action("CLOSE"));
        assertThat(candidates(clock.instant().plus(Duration.ofDays(1)))).contains(photo);
        assertThat(candidates(clock.instant().minus(Duration.ofDays(1)))).doesNotContain(photo); // 끝난 때가 기준보다 뒤
    }

    /** 보관 기한 후보 전부(같은 DB 를 쓰는 다른 시험의 줄도 섞인다 — 커서로 끝까지) */
    private List<Long> candidates(Instant cutoff) {
        List<Long> out = new java.util.ArrayList<>();
        long cursor = 0;
        for (List<Long> page = queries.photoPurgeCandidates(cutoff, cursor, 500); !page.isEmpty(); page = queries.photoPurgeCandidates(cutoff, cursor, 500)) {
            out.addAll(page);
            cursor = page.get(page.size() - 1);
        }
        return out;
    }

    // ── D5 광고주 자격 ───────────────────────────────────────────────────────

    @Test
    void D5_광고주_화면은_광고서비스_정지_사업자_정지_광고주_보관이면_403_재심사중이면_허용() throws Exception {
        mvc.perform(as(advertiserUser, get("/api/business/trials"))).andExpect(status().isOk());
        // SQL 로 남김: 서비스·신원 상태 칸을 심사 기록 없이 바로 바꾼다(전이 메서드는 운영자·사유를 함께 남긴다)
        jdbc.update("UPDATE business_services SET status = 'SUSPENDED' WHERE profile_id = ? AND service = 'AD'", profile);
        mvc.perform(as(advertiserUser, get("/api/business/trials"))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("TRIAL_ADVERTISER_BLOCKED"));
        jdbc.update("UPDATE business_services SET status = 'APPROVED' WHERE profile_id = ? AND service = 'AD'", profile);
        jdbc.update("UPDATE business_profiles SET status = 'PENDING', re_review = true WHERE id = ?", profile);
        mvc.perform(as(advertiserUser, get("/api/business/trials"))).andExpect(status().isOk()); // 재심사 중은 진행 중 건이 그대로 돈다
        jdbc.update("UPDATE business_profiles SET status = 'SUSPENDED', re_review = false, suspend_code = 'OTHER', suspend_text = '시험', suspended_at = now(), "
                + "suspend_pending_action = 'KEEP', suspend_scheduled_action = 'HOLD', suspend_running_action = 'TAKEDOWN_ONLY' WHERE id = ?", profile);
        mvc.perform(as(advertiserUser, get("/api/business/trials"))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("TRIAL_ADVERTISER_BLOCKED"));
        jdbc.update("UPDATE business_profiles SET status = 'APPROVED', suspend_code = NULL, suspend_text = NULL, suspended_at = NULL, suspend_pending_action = NULL, "
                + "suspend_scheduled_action = NULL, suspend_running_action = NULL WHERE id = ?",
                profile);
        mvc.perform(as(advertiserUser, get("/api/business/trials"))).andExpect(status().isOk());
        // 사업자 신원에 이어진 광고주는 보관할 수 없다(V131 ck_aa_archive_direct) — 보관 검사는 서비스에 두되 시험은 신원·서비스 상태로 본다
        jdbc.update("UPDATE business_profiles SET status = 'WITHDRAWN' WHERE id = ?", profile);
        mvc.perform(as(advertiserUser, get("/api/business/trials"))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("TRIAL_ADVERTISER_BLOCKED"));
    }

    // ── D6 신청자 있으면 광고주·제품값 ─────────────────────────────────────────

    @Test
    void D6_신청자가_있으면_광고주_바꾸기_409_제품값_올리기_409_내리기는_된다_신청자_없으면_된다() throws Exception {
        long other = ids.nextId();
        tx.executeWithoutResult(s -> advertisers.insert(new AdAdvertiserStore.NewAdvertiser(other, "바꿀광고주" + other % 1_000_000, null, null, null, null, null, null,
                null, null, operator, Instant.now())));
        long t = trial("LEND", "OPERATOR", 2, 30000);
        trials.update(operator, "ip", Long.toString(t), edit(t, advertiser, 40000)); // 신청자 없으면 올려도 된다
        long m = member(true);
        apply(m, t, consents("LEND", "OPERATOR")).andExpect(status().isCreated());
        assertThatThrownBy(() -> trials.update(operator, "ip", Long.toString(t), edit(t, other, 40000)))
                .satisfies(e -> assertThat(code(e)).isEqualTo("TRIAL_APPLICANTS_LOCKED"));
        assertThatThrownBy(() -> trials.update(operator, "ip", Long.toString(t), edit(t, advertiser, 40001)))
                .satisfies(e -> assertThat(code(e)).isEqualTo("TRIAL_APPLICANTS_LOCKED"));
        trials.update(operator, "ip", Long.toString(t), edit(t, advertiser, 35000));
        assertThat(jdbc.queryForObject("SELECT product_price FROM trials WHERE id = ?", Long.class, t)).isEqualTo(35000L);
    }

    // ── D8 탈퇴 ──────────────────────────────────────────────────────────────

    @Test
    void D8_대여형_반납_전이면_탈퇴는_남은일_TRIAL_RETURN_으로_기다리고_반납되면_풀린다_완료하면_신청_주소와_구매인증_사진_파기() throws Exception {
        TrialService.Actor adv = TrialService.Actor.advertiser(advertiserUser, advertiser, "ip");
        long m = member(true);
        WithdrawalTestSupport.withPassword(jdbc, encoder, m, clock.instant());
        long p = selected(m, "LEND", "ADVERTISER");
        long t = trialOf(p);
        WithdrawalView before = withdrawals.view(m);
        assertThat(before.pending()).isEmpty(); // 보내기 전에는 막지 않는다
        trials.ship(adv, Long.toString(p), new TrialViews.ShipmentRequest("우체국", "1", List.of(upload(adv, t, "PRE_SHIP"))));
        WithdrawalView shipped = withdrawals.view(m);
        assertThat(shipped.pending()).extracting(WithdrawalView.PendingItem::code).containsExactly("TRIAL_RETURN");
        assertThat(shipped.pending().get(0).link()).isEqualTo("/me/trials");
        trials.confirmReturn(adv, Long.toString(p), new TrialViews.PhotosRequest(List.of(upload(adv, t, "RETURN"))));
        assertThat(withdrawals.view(m).pending()).isEmpty();

        long postId = postIdOf(write(m, postBody(slug, "직접 산 후기", ",\"purchaseSource\":\"SELF\"")));
        String proof = uploadProof(m, postId);
        assertThat(jdbc.queryForObject("SELECT address_purged_at IS NULL FROM trial_applications WHERE trial_id = ? AND user_id = ?", Boolean.class, t, m)).isTrue();
        assertThat(withdrawals.request(m, WithdrawalTestSupport.plain(), "127.0.0.1").state()).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT address_purged_at IS NOT NULL AND recipient_enc IS NULL AND address1_enc IS NULL FROM trial_applications WHERE trial_id = ? "
                + "AND user_id = ?", Boolean.class, t, m)).isTrue();
        assertThat(jdbc.queryForMap("SELECT status, content, purged_at IS NOT NULL AS purged FROM purchase_proofs WHERE id = ?", Long.parseLong(proof)))
                .containsEntry("status", "REJECTED").containsEntry("purged", true).containsEntry("content", null);
    }

    // ── D11 섞인 목록 칸 ─────────────────────────────────────────────────────

    @Test
    void D11_최신글_섞인_목록_줄에도_구입경로와_구매인증_칩() throws Exception {
        long author = member(true);
        long postId = postIdOf(write(author, postBody(slug, "섞인 목록 후기", ",\"purchaseSource\":\"SELF\"")));
        String proof = uploadProof(author, postId);
        proofs.approve(PurchaseProofService.Reviewer.operator(operator, "ip"), proof);
        caches.getCache(CacheNames.HOME_LATEST).clear();
        String latest = mvc.perform(get("/api/home/latest?size=50")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(latest, "$.data.items[?(@.id == '" + postId + "')].purchaseSource")).containsExactly("SELF");
        assertThat(JsonPath.<List<Boolean>>read(latest, "$.data.items[?(@.id == '" + postId + "')].purchaseProofApproved")).containsExactly(true);
    }

    // ── D13 작은 것 ─────────────────────────────────────────────────────────

    @Test
    void L1_리뷰어_신청_승인은_약속_지킨_횟수의_단계로_준다() throws Exception {
        long m = member(true);
        for (int i = 0; i < 3; i++) {
            long p = selected(m, "GIVE", "ADVERTISER");
            // SQL 로 남김: 앞선 약속 지킴을 바로 둔다 — 글을 잇달아 쓰면 작성 간격(60초)에 걸린다
            jdbc.update("UPDATE trial_participants SET kept = true, kept_at = now() WHERE id = ?", p);
        }
        long titleId = jdbc.queryForObject("SELECT id FROM titles WHERE title_key = 'reviewer'", Long.class);
        long app = ids.nextId();
        // SQL 로 남김: 신청 조건(사용기 3편·구매 인증)을 채우려면 글 여러 편이 필요하다 — 승인 판정만 보려고 대기 신청 줄을 바로 둔다
        jdbc.update("INSERT INTO title_applications (id, user_id, title_id, status, created_at) VALUES (?, ?, ?, 'PENDING', now())", app, m, titleId);
        titleReviews.approve(app, TitleReviewService.Reviewer.operator(operator, "ip"), null);
        assertThat(reviewerTier(m)).isEqualTo("BRONZE");
    }

    @Test
    void L3_구입경로가_SELF_가_아니게_되면_구매인증_칩을_숨기고_지운_글의_대기_인증은_자동_반려_승인은_409() throws Exception {
        long author = member(true);
        long postId = postIdOf(write(author, postBody(slug, "인증 후기", ",\"purchaseSource\":\"SELF\"")));
        String proof = uploadProof(author, postId);
        proofs.approve(PurchaseProofService.Reviewer.operator(operator, "ip"), proof);
        long points = jdbc.queryForObject("SELECT coalesce(sum(delta), 0) FROM point_ledger WHERE user_id = ? AND reason = 'PURCHASE_PROOF'", Long.class, author);
        String borrowed = postBody(slug, "인증 후기", ",\"purchaseSource\":\"BORROWED\"").replace("\"boardSlug\":\"" + slug + "\",", "");
        mvc.perform(as(author, put("/api/posts/" + postId)).content(borrowed)).andExpect(status().isOk());
        mvc.perform(get("/api/posts/" + postId)).andExpect(jsonPath("$.data.purchase.proof").value("NONE"));
        String list = mvc.perform(get("/api/boards/" + slug + "/posts")).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Boolean>>read(list, "$.data.items[?(@.id == '" + postId + "')].purchaseProofApproved")).containsExactly(false);
        String proofOnly = mvc.perform(get("/api/boards/" + slug + "/posts?purchase=PROOF")).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(proofOnly, "$.data.items[*].id")).doesNotContain(Long.toString(postId));
        assertThat(jdbc.queryForObject("SELECT coalesce(sum(delta), 0) FROM point_ledger WHERE user_id = ? AND reason = 'PURCHASE_PROOF'", Long.class, author))
                .isEqualTo(points); // 포인트는 회수하지 않는다

        // 글쓴이가 글을 지우면 대기 인증은 자동 반려·사진 파기
        long other = member(true);
        long second = postIdOf(write(other, postBody(slug, "지울 후기", ",\"purchaseSource\":\"SELF\"")));
        String pending = uploadProof(other, second);
        mvc.perform(as(other, post("/api/posts/" + second + "/delete"))).andExpect(status().isOk());
        assertThat(jdbc.queryForMap("SELECT status, purged_at IS NOT NULL AS purged, reject_reason FROM purchase_proofs WHERE id = ?", Long.parseLong(pending)))
                .containsEntry("status", "REJECTED").containsEntry("purged", true).containsEntry("reject_reason", "글이 지워져 구매 인증을 반려했어요.");

        // 운영자가 지운 글(작성자 삭제 길을 거치지 않음) — 승인은 409, 하루 배치가 자동 반려
        long third = member(true);
        long staffDeleted = postIdOf(write(third, postBody(slug, "운영자가 지울 후기", ",\"purchaseSource\":\"SELF\"")));
        String waiting = uploadProof(third, staffDeleted);
        // SQL 로 남김: 운영자 삭제는 관리자 서버의 일이다 — 지운 글 상태만 둔다
        jdbc.update("UPDATE posts SET deleted_at = now() WHERE id = ?", staffDeleted);
        assertThatThrownBy(() -> proofs.approve(PurchaseProofService.Reviewer.operator(operator, "ip"), waiting))
                .satisfies(e -> assertThat(code(e)).isEqualTo("PURCHASE_PROOF_POST_DELETED"));
        daily.runOnce();
        assertThat(jdbc.queryForObject("SELECT status FROM purchase_proofs WHERE id = ?", String.class, Long.parseLong(waiting))).isEqualTo("REJECTED");
    }

    @Test
    void L4_체험단_사용기를_지우면_지킴_판정을_되돌리고_리뷰어_단계를_다시_정한다() throws Exception {
        long m = member(true);
        reviewer(m, null);
        for (int i = 0; i < 2; i++) {
            long p = selected(m, "GIVE", "ADVERTISER");
            // SQL 로 남김: 앞선 두 번의 약속 지킴을 바로 둔다 — 같은 회원이 글을 잇달아 쓰면 작성 간격(60초)에 걸린다
            jdbc.update("UPDATE trial_participants SET kept = true, kept_at = now() WHERE id = ?", p);
        }
        long p = selected(m, "GIVE", "ADVERTISER");
        long postId = postIdOf(write(m, postBody(slug, "지울 체험 후기", ",\"trialParticipantId\":\"" + p + "\"")));
        assertThat(reviewerTier(m)).isEqualTo("BRONZE");
        mvc.perform(as(m, post("/api/posts/" + postId + "/delete"))).andExpect(status().isOk());
        Map<String, Object> row = jdbc.queryForMap("SELECT review_post_id, kept FROM trial_participants WHERE id = ?", p);
        assertThat(row.get("review_post_id")).isNull();
        assertThat(row.get("kept")).isNull();
        assertThat(reviewerTier(m)).isNull(); // 지킴 2번 — 동(3) 아래
        mvc.perform(as(m, get("/api/me/trials/writable?board=" + slug))).andExpect(jsonPath("$.data[0].participantId").value(Long.toString(p))); // 다시 쓸 수 있다
    }

    @Test
    void L7_반송_송장은_진행중_끝난_모집만() throws Exception {
        TrialService.Actor op = TrialService.Actor.operator(operator, "ip");
        long t = trial("GIVE", "OPERATOR", 1, 30000);
        long m = member(true);
        apply(m, t, consents("GIVE", "OPERATOR")).andExpect(status().isCreated());
        selectAll(t, List.of(applicationId(t, m)));
        trials.stock(operator, "ip", Long.toString(t), new TrialViews.StockRequest(1, null, List.of(upload(op, t, "STOCK"))));
        trials.returnShipment(operator, "ip", Long.toString(t), new TrialViews.ShipmentRequest("CJ", "9", null)); // 진행 중 — 된다
        trials.update(operator, "ip", Long.toString(t), action("CANCEL"));
        assertThatThrownBy(() -> trials.returnShipment(operator, "ip", Long.toString(t), new TrialViews.ShipmentRequest("CJ", "10", null)))
                .satisfies(e -> assertThat(code(e)).isEqualTo("TRIAL_STATE_INVALID"));
    }

    @Test
    void M4_반납_독촉_후보는_때가_된_줄만_ID_커서로_끝까지() throws Exception {
        TrialService.Actor adv = TrialService.Actor.advertiser(advertiserUser, advertiser, "ip");
        long early = member(true);
        long pEarly = selected(early, "LEND", "ADVERTISER");
        long late = member(true);
        long pLate = selected(late, "LEND", "ADVERTISER");
        for (long p : new long[] {pEarly, pLate}) {
            trials.ship(adv, Long.toString(p), new TrialViews.ShipmentRequest("우체국", "1", List.of(upload(adv, trialOf(p), "PRE_SHIP"))));
        }
        Instant now = clock.instant();
        // SQL 로 남김: 받은 때·반납 기한을 바로 둔다 — 앞 ID 줄은 아직 기한 전, 뒤 ID 줄은 기한이 지났다
        jdbc.update("UPDATE trial_participants SET received_at = now(), received_by = 'MEMBER', return_due_at = ? WHERE id = ?", ts(now.plus(Duration.ofDays(5))),
                Math.min(pEarly, pLate));
        jdbc.update("UPDATE trial_participants SET received_at = now(), received_by = 'MEMBER', return_due_at = ? WHERE id = ?", ts(now.minus(Duration.ofDays(1))),
                Math.max(pEarly, pLate));
        List<Long> due = queries.returnOpen(now, 0, 7, 14, 0, 100_000).stream().map(com.community.trial.entity.TrialParticipantEntity::getId).toList();
        assertThat(due).contains(Math.max(pEarly, pLate)).doesNotContain(Math.min(pEarly, pLate)); // 기한 전 줄이 앞을 막지 않는다
        assertThat(queries.returnOpen(now, 0, 7, 14, Math.max(pEarly, pLate), 500).stream().map(com.community.trial.entity.TrialParticipantEntity::getId))
                .doesNotContain(Math.max(pEarly, pLate), Math.min(pEarly, pLate)); // 커서 뒤
        List<Long> page = queries.purgeCandidates(0, 2);
        assertThat(page).hasSizeLessThanOrEqualTo(2);
        if (page.size() == 2) {
            assertThat(queries.purgeCandidates(page.get(1), 2)).allMatch(id -> id > page.get(1));
        }
    }

    // ── D14 칭호 ──────────────────────────────────────────────────────────────

    @Test
    void D14_unseen_API_는_보지_않은_받은_칭호만_닫으면_빈_목록_칭호_알림은_영어_이름도_남겨_언어별로() throws Exception {
        long m = member(true);
        reviewer(m, null);
        mvc.perform(as(m, get("/api/me/titles/unseen"))).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].key").value("reviewer"))
                .andExpect(jsonPath("$.data[0].labelEn").exists()).andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(as(m, post("/api/me/titles/seen"))).andExpect(status().isNoContent());
        mvc.perform(as(m, get("/api/me/titles/unseen"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(get("/api/me/titles/unseen")).andExpect(status().isUnauthorized());

        // 단계가 오르면 알림 — 덧붙임에 영어 이름, 영어로 보면 영어 이름
        for (int i = 0; i < 3; i++) {
            long p = selected(m, "GIVE", "ADVERTISER");
            jdbc.update("UPDATE trial_participants SET kept = true, kept_at = now() WHERE id = ?", p);
        }
        tx.executeWithoutResult(s -> context.getBean(com.community.trial.service.TrialPromiseService.class).raiseReviewer(m));
        String payload = jdbc.queryForObject("SELECT payload::text FROM member_notifications WHERE user_id = ? AND type = 'TITLE_GRANTED' ORDER BY id DESC LIMIT 1",
                String.class, m);
        String labelEn = jdbc.queryForObject("SELECT name_en FROM titles WHERE title_key = 'reviewer'", String.class);
        if (labelEn != null) {
            assertThat(payload.replace(" ", "")).contains("\"labelEn\":\"" + labelEn + "\"");
            AuthUser user = new AuthUser(m, Role.USER);
            String en = mvc.perform(get("/api/notifications").principal(new UsernamePasswordAuthenticationToken(user, null, user.authorities())).header("Accept-Language", "en"))
                    .andExpect(status().isOk()).andReturn().getResponse()
                    .getContentAsString();
            assertThat(en).contains(labelEn);
        }
        String applications = mvc.perform(as(m, get("/api/me/titles"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Object>>read(applications, "$.data.unseen[*].key")).containsExactly("reviewer");
    }
}
