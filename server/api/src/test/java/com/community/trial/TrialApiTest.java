package com.community.trial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import com.community.commerce.CommerceApiTestBase;
import com.community.report.dto.request.ReportRequest;
import com.community.report.service.ReportService;
import com.community.restriction.entity.RestrictionAutoRule;
import com.community.restriction.entity.RestrictionType;
import com.community.restriction.service.MemberRestrictionService;
import com.community.trial.batch.TrialDailyJob;
import com.community.trial.dto.TrialViews;
import com.community.trial.service.SponsorReviewService;
import com.community.trial.service.TrialMemberService;
import com.community.trial.service.TrialService;
import com.community.user.entity.Role;
import com.community.zconfiguration.crypto.DataCipher;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ValidationException;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 체험단 · 사용기 구입 경로(trial-group-contract 「구현 결정」 1~14) — 공개 사이트 앱 문맥 + 실제 PostgreSQL + 옮길 수 있는 시계. 모집 만들기·뽑기·입고는 shared 서비스를 바로 부르고
 * (관리자 HTTP 는 관리자 시험), 회원·광고주·글쓰기·신고는 공개 API 로 본다. 하루 배치는 {@link TrialDailyJob#runOnce} 를 시계를 옮겨 부른다.
 * DB 를 다른 시험과 같이 쓰므로 모집·게시판·회원·광고주는 시험마다 새로 만들고, 체험단 요금표 줄을 바꾼 시험은 끝나면 되돌린다.
 */
class TrialApiTest extends CommerceApiTestBase {

    @Autowired
    private org.springframework.web.context.WebApplicationContext webContext;
    @Autowired
    private BoardCatalog catalog;
    @Autowired
    private TrialService trials;
    @Autowired
    private TrialMemberService members;
    @Autowired
    private TrialDailyJob daily;
    @Autowired
    private AddressBookService addresses;
    @Autowired
    private AdAdvertiserStore advertisers;
    @Autowired
    private DataCipher cipher;
    @Autowired
    private MemberRestrictionService restrictions;
    @Autowired
    private ReportService reports;
    @Autowired
    private SponsorReviewService sponsors;

    private MockMvc mvc;
    private long board;
    private String slug;
    private long operator;
    private long advertiser;
    private long advertiserUser;
    private Map<String, Object> savedRule;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(webContext).build();
        board = ids.nextId();
        slug = "tr" + Long.toString(board, 36);
        boardFixture.board(board, slug, "체험 " + slug.substring(slug.length() - 6), 960);
        // SQL 로 남김: 게시판 정책 칸은 관리자 저장 API 로 바뀐다 — 공개 사이트 시험은 사용기 게시판 값(꼭 골라야 함 + 체험단 사용기 받기)만 세운다
        jdbc.update("UPDATE boards SET purchase_source_mode = 'REQUIRED', trial_reviews_enabled = true WHERE id = ?", board);
        catalog.invalidate();
        operator = newOperator();
        advertiserUser = member(true);
        long profile = ids.nextId();
        // SQL 로 남김: 사업자 신원 줄의 심사 상태·암호문 칸을 심사 기록 없이 바로 세운다 — 팩터리(apply)는 신청(PENDING)만 만든다
        jdbc.update("INSERT INTO business_profiles (id, user_id, status, re_review, company_name, biz_no_enc, biz_no_hash, biz_no_last4, owner_name_enc, phone_enc, email_enc, "
                        + "postcode, address1_enc, terms_version, privacy_version, agreed_at, submitted_at, created_at, updated_at) "
                        + "VALUES (?, ?, 'APPROVED', false, ?, 'x', ?, '4321', ?, ?, ?, '06236', 'x', 'v1', 'v1', now(), now(), now(), now())",
                profile, advertiserUser, "체험상사" + profile % 100000, String.format("%064x", profile), cipher.encrypt(BusinessPii.OWNER, "대표"),
                cipher.encrypt(BusinessPii.PHONE, "0212345678"), cipher.encrypt(BusinessPii.EMAIL, "a@test.local"));
        // SQL 로 남김: 사업자 서비스 줄(광고 승인) — 광고주 화면 자격(점검 D5)은 신원 승인 + 광고 서비스 승인이다
        jdbc.update("INSERT INTO business_services (id, profile_id, service, status, updated_at) VALUES (?, ?, 'AD', 'APPROVED', now())", ids.nextId(), profile);
        advertiser = ids.nextId();
        tx.executeWithoutResult(s -> advertisers.insert(new AdAdvertiserStore.NewAdvertiser(advertiser, "광고주" + advertiser % 1_000_000, profile, null, null, null, null,
                null, null, null, operator, Instant.now())));
        savedRule = jdbc.queryForMap("SELECT unit_price, price_unit, shipping_fee_per_person FROM biz_price_rules WHERE product = 'TRIAL'");
    }

    @AfterEach
    void restore() {
        jdbc.update("UPDATE biz_price_rules SET unit_price = ?, price_unit = ?, shipping_fee_per_person = ? WHERE product = 'TRIAL'", savedRule.get("unit_price"),
                savedRule.get("price_unit"), savedRule.get("shipping_fee_per_person"));
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

    /** 정식 등급·이메일 인증 회원(본인인증 여부) */
    private long member(boolean identity) {
        long id = newMember();
        memberFixture.verifyEmail(id);
        String b36 = Long.toString(id, 36);
        memberFixture.profile(id, "t" + b36.substring(Math.max(0, b36.length() - 11)), "REGULAR");
        if (identity) {
            String hash = String.format("%064x", id);
            // SQL 로 남김: identity_verifications 는 엔티티가 없는 표다(가짜 본인인증 결과를 바로 둔다)
            jdbc.update("INSERT INTO identity_verifications (user_id, di_hash, ci_hash, birth_date, provider, provider_tx_id, verified_at) VALUES (?, ?, ?, ?, 'FAKE', ?, now())",
                    id, hash, hash.replace('0', 'c'), java.time.LocalDate.of(1990, 1, 1), "tx-" + id);
        }
        return id;
    }

    private long address(long memberId) {
        return addresses.create(memberId, new AddressInput("집", "홍길동", "01012345678", "06236", "서울 강남구 테헤란로 1", "101호", "문 앞", true)).id();
    }

    /** 리뷰어 칭호(단계 — null 이면 단계 전) */
    private void reviewer(long memberId, String tier) {
        long titleId = jdbc.queryForObject("SELECT id FROM titles WHERE title_key = 'reviewer'", Long.class);
        // SQL 로 남김: 칭호 수여·승인은 관리자 서버의 일이다 — 체험단 판정만 보려고 받은 줄을 바로 둔다
        jdbc.update("INSERT INTO member_titles (id, user_id, title_id, tier, period, source, granted_at) VALUES (?, ?, ?, ?, '', 'APPLICATION', now())", ids.nextId(),
                memberId, titleId, tier);
    }

    /** 모집 결제 때(null = 선결제 기본 — V244). 뽑기 뒤 견적을 보는 시험은 POSTPAID 로 둔다 */
    private String timing;

    private TrialViews.TrialForm form(String form, String shipper, long price, int recruit, int share, Instant start, Integer returnDays) {
        return new TrialViews.TrialForm(Long.toString(advertiser), "체험 " + start.toEpochMilli() % 100000, "무선 이어폰", price, form, Long.toString(board), recruit, share, 48,
                24, start.toString(), start.plus(Duration.ofDays(3)).toString(), 14, returnDays, shipper, "설명", null, timing, null);
    }

    /** 모집 만들기 + 열기 — 신청 시작 = 지금 + {@code startHours} */
    private long trial(String form, String shipper, int recruit, int share, long startHours) {
        Instant start = clock.instant().plus(Duration.ofHours(startHours)).truncatedTo(ChronoUnit.MINUTES);
        String id = trials.create(operator, "127.0.0.1", form(form, shipper, 30000, recruit, share, start, "LEND".equals(form) ? 10 : null)).id();
        trials.update(operator, "127.0.0.1", id, action("PUBLISH"));
        return Long.parseLong(id);
    }

    private static TrialViews.TrialForm action(String action) {
        return new TrialViews.TrialForm(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, action);
    }

    private ResultActions apply(long memberId, long trialId, long addressId, List<String> consents) throws Exception {
        String body = "{\"addressId\":\"" + addressId + "\",\"consents\":[" + String.join(",", consents.stream().map(c -> "\"" + c + "\"").toList()) + "]}";
        return mvc.perform(as(memberId, post("/api/trials/" + trialId + "/applications")).content(body));
    }

    private long applicationId(long trialId, long memberId) {
        return jdbc.queryForObject("SELECT id FROM trial_applications WHERE trial_id = ? AND user_id = ? AND status <> 'CANCELLED'", Long.class, trialId, memberId);
    }

    private long participant(long trialId, long memberId) {
        return jdbc.queryForObject("SELECT id FROM trial_participants WHERE trial_id = ? AND user_id = ?", Long.class, trialId, memberId);
    }

    /** 신청 마감 뒤로 시계를 옮기고 뽑는다 */
    private void selectAll(long trialId, List<Long> applicationIds) {
        Instant end = jdbc.queryForObject("SELECT apply_end_at FROM trials WHERE id = ?", java.sql.Timestamp.class, trialId).toInstant();
        if (clock.instant().isBefore(end)) {
            clock.set(end.plusSeconds(60));
        }
        trials.select(operator, "127.0.0.1", Long.toString(trialId), new TrialViews.SelectRequest(applicationIds.stream().map(String::valueOf).toList()));
    }

    /** 제공형 · 광고주 발송 모집에 회원 하나를 뽑아 둔다 — 뽑힌 줄 ID */
    private long selected(long memberId, String form, String shipper) throws Exception {
        long t = trial(form, shipper, 1, 0, 0);
        apply(memberId, t, address(memberId), consents(form, shipper)).andExpect(status().isCreated());
        selectAll(t, List.of(applicationId(t, memberId)));
        return participant(t, memberId);
    }

    private static List<String> consents(String form, String shipper) {
        if ("ADVERTISER".equals(shipper)) {
            return "LEND".equals(form) ? List.of("PROVIDE_CONTACT", "PRODUCT_CHARGE", "DAMAGE_CHARGE") : List.of("PROVIDE_CONTACT");
        }
        return "LEND".equals(form) ? List.of("UNRETURNED_CONTACT", "PRODUCT_CHARGE", "DAMAGE_CHARGE") : List.of();
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

    private int notices(long memberId, String type) {
        return jdbc.queryForObject("SELECT count(*) FROM member_notifications WHERE user_id = ? AND type = ?", Integer.class, memberId, type);
    }

    private static String code(Throwable e) {
        return ((CommonException) e).getErrorCode();
    }

    // ── 신청 자격 ─────────────────────────────────────────────────────────

    @Test
    void 신청은_본인인증_회원만_AI회원_운영팀_불가_신청금지면_403() throws Exception {
        long t = trial("GIVE", "ADVERTISER", 3, 60, 0);
        long unverified = member(false);
        apply(unverified, t, address(unverified), consents("GIVE", "ADVERTISER")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("IDENTITY_REQUIRED"));

        long ai = member(true);
        // SQL 로 남김: AI 회원 만들기는 관리자 API(제공사 키·모델 검사)를 거친다 — 판별만 보면 되므로 프로필 줄만 둔다
        jdbc.update("INSERT INTO ai_member_profiles (id, user_id, provider, model, gender, age_band, personality, tone, interests, morning_start, morning_end, "
                + "afternoon_start, afternoon_end, created_at, updated_at) VALUES (?, ?, 'OPENAI', 'gpt-test', 'MALE', 'THIRTIES', '시험', '시험', '시험', "
                + "time '08:00', time '11:00', time '13:00', time '18:00', now(), now())", ids.nextId(), ai);
        apply(ai, t, address(ai), consents("GIVE", "ADVERTISER")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("TRIAL_APPLICANT_EXCLUDED"));

        long banned = member(true);
        restrictions.grantAuto(banned, RestrictionType.TRIAL_BAN, Duration.ofDays(90), RestrictionAutoRule.TRIAL_REVIEW_LATE);
        apply(banned, t, address(banned), consents("GIVE", "ADVERTISER")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("MEMBER_RESTRICTED"));
        mvc.perform(as(banned, get("/api/trials/" + t))).andExpect(status().isOk()).andExpect(jsonPath("$.data.mine.blockedReason").value("RESTRICTED"))
                .andExpect(jsonPath("$.data.card.ad").value(true));
    }

    @Test
    void 리뷰어_먼저_신청_금은_48_은은_24시간_전부터_동과_일반은_시작부터_리뷰어는_리뷰어몫() throws Exception {
        long t = trial("GIVE", "ADVERTISER", 5, 60, 30); // 일반 시작 30시간 뒤
        long gold = member(true);
        reviewer(gold, "GOLD");
        long silver = member(true);
        reviewer(silver, "SILVER");
        long bronze = member(true);
        reviewer(bronze, "BRONZE");
        long plain = member(true);
        List<String> c = consents("GIVE", "ADVERTISER");

        apply(gold, t, address(gold), c).andExpect(status().isCreated()).andExpect(jsonPath("$.data.quota").value("REVIEWER"))
                .andExpect(jsonPath("$.data.reviewerTier").value("GOLD"));
        apply(silver, t, address(silver), c).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("TRIAL_APPLY_NOT_OPEN"));
        clock.advance(Duration.ofHours(7)); // 일반 시작 23시간 전 — 은 창이 열렸다
        apply(silver, t, address(silver), c).andExpect(status().isCreated());
        apply(bronze, t, address(bronze), c).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("TRIAL_APPLY_NOT_OPEN"));
        apply(plain, t, address(plain), c).andExpect(status().isConflict());
        clock.advance(Duration.ofHours(23));
        apply(bronze, t, address(bronze), c).andExpect(status().isCreated()).andExpect(jsonPath("$.data.quota").value("REVIEWER"));
        apply(plain, t, address(plain), c).andExpect(status().isCreated()).andExpect(jsonPath("$.data.quota").value("GENERAL"));
    }

    @Test
    void 신청_동의는_보내는쪽_형태로_정해지고_배송지는_암호문_사본_중복은_409_취소하면_주소_파기() throws Exception {
        long t = trial("LEND", "OPERATOR", 3, 60, 0);
        long m = member(true);
        long addr = address(m);
        apply(m, t, addr, List.of("PROVIDE_CONTACT")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.consents").exists());
        apply(m, t, 123L, consents("LEND", "OPERATOR")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.addressId").exists());
        apply(m, t, addr, consents("LEND", "OPERATOR")).andExpect(status().isCreated());
        apply(m, t, addr, consents("LEND", "OPERATOR")).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("TRIAL_ALREADY_APPLIED"));
        Map<String, Object> row = jdbc.queryForMap("SELECT recipient_enc, address1_enc, postcode FROM trial_applications WHERE trial_id = ? AND user_id = ?", t, m);
        assertThat((String) row.get("recipient_enc")).startsWith("d").doesNotContain("홍길동");
        assertThat(cipher.decrypt(com.community.trial.service.TrialPii.RECIPIENT, (String) row.get("recipient_enc"))).isEqualTo("홍길동");
        mvc.perform(as(m, get("/api/trials/" + t))).andExpect(jsonPath("$.data.consents.length()").value(3)).andExpect(jsonPath("$.data.damageChargeLimit").value(30000))
                .andExpect(jsonPath("$.data.mine.canCancel").value(true));

        mvc.perform(as(m, delete("/api/trials/" + t + "/applications/mine"))).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM trial_applications WHERE trial_id = ? AND user_id = ? AND address_purged_at IS NOT NULL AND recipient_enc IS NULL",
                Integer.class, t, m)).isEqualTo(1);
        apply(m, t, addr, consents("LEND", "OPERATOR")).andExpect(status().isCreated()); // 취소하면 다시 신청
    }

    @Test
    void 제공형은_제품값_상한까지_넘으면_대여형으로만_모집_게시판은_체험단_사용기_받기만() {
        Instant start = clock.instant().plus(Duration.ofHours(1)).truncatedTo(ChronoUnit.MINUTES);
        assertThatThrownBy(() -> trials.create(operator, "127.0.0.1", form("GIVE", "ADVERTISER", 60000, 2, 50, start, null)))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsKey("form"));
        long other = ids.nextId();
        boardFixture.board(other, "tx" + Long.toString(other, 36), "일반 " + Long.toString(other, 36).substring(6), 961);
        catalog.invalidate();
        TrialViews.TrialForm wrongBoard = new TrialViews.TrialForm(Long.toString(advertiser), "체험", "제품", 10000L, "LEND", Long.toString(other), 2, 50, 48, 24,
                start.toString(), start.plus(Duration.ofDays(2)).toString(), 14, 7, "OPERATOR", "", null, null);
        assertThatThrownBy(() -> trials.create(operator, "127.0.0.1", wrongBoard))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsKey("reviewBoardId"));
        String id = trials.create(operator, "127.0.0.1", form("LEND", "OPERATOR", 60000, 2, 50, start, 7)).id(); // 대여형은 상한 밖도 된다
        Map<String, Object> app = jdbc.queryForMap("SELECT b.kind, b.status, b.source, b.quote_total, i.product FROM trials t JOIN biz_applications b ON b.id = t.biz_application_id "
                + "JOIN biz_application_items i ON i.application_id = b.id WHERE t.id = ?", Long.parseLong(id));
        assertThat(app).containsEntry("kind", "TRIAL").containsEntry("status", "SUBMITTED").containsEntry("source", "OPERATOR").containsEntry("product", "TRIAL");
        // 초안은 공개 상세에 없다
        assertThatThrownBy(() -> members.detail(id, null)).satisfies(e -> assertThat(code(e)).isEqualTo("TRIAL_NOT_FOUND"));
    }

    // ── 뽑기 ─────────────────────────────────────────────────────────────

    @Test
    void 뽑기는_리뷰어몫부터_채우고_넘치면_일반몫_일반은_일반몫만_넘으면_400_금액은_인원당_더하기_대신발송비() throws Exception {
        jdbc.update("UPDATE biz_price_rules SET unit_price = 50000, price_unit = 'PER_PERSON', shipping_fee_per_person = 3000 WHERE product = 'TRIAL'");
        timing = "POSTPAID"; // 뽑힌 인원으로 견적(후불 — 선결제는 TrialPrepaidTest)
        long t = trial("GIVE", "OPERATOR", 2, 50, 0); // 리뷰어 몫 1 · 일반 몫 1
        long r1 = member(true);
        reviewer(r1, null);
        long r2 = member(true);
        reviewer(r2, "BRONZE");
        long g1 = member(true);
        long g2 = member(true);
        for (long m : new long[] {r1, r2, g1, g2}) {
            apply(m, t, address(m), consents("GIVE", "OPERATOR")).andExpect(status().isCreated());
        }
        long a1 = applicationId(t, r1);
        long a2 = applicationId(t, r2);
        long b1 = applicationId(t, g1);
        long b2 = applicationId(t, g2);
        clock.set(jdbc.queryForObject("SELECT apply_end_at FROM trials WHERE id = ?", java.sql.Timestamp.class, t).toInstant().plusSeconds(60));
        assertThatThrownBy(() -> trials.select(operator, "ip", Long.toString(t), new TrialViews.SelectRequest(List.of(Long.toString(b1), Long.toString(b2)))))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsKey("applicationIds")); // 일반 2명 > 일반 몫 1
        assertThatThrownBy(() -> trials.select(operator, "ip", Long.toString(t), new TrialViews.SelectRequest(List.of(Long.toString(a1), Long.toString(a2), Long.toString(b1)))))
                .isInstanceOf(ValidationException.class); // 인원 2 초과
        trials.select(operator, "ip", Long.toString(t), new TrialViews.SelectRequest(List.of(Long.toString(a1), Long.toString(a2)))); // 리뷰어 2명 — 하나는 일반 몫으로

        assertThat(jdbc.queryForList("SELECT quota FROM trial_participants WHERE trial_id = ? ORDER BY quota", String.class, t)).containsExactly("GENERAL", "REVIEWER");
        Map<String, Object> amount = jdbc.queryForMap("SELECT t.status, t.selected_count, t.amount, b.quote_supply, b.quote_vat, b.quote_total, i.units FROM trials t "
                + "JOIN biz_applications b ON b.id = t.biz_application_id JOIN biz_application_items i ON i.application_id = b.id AND i.product = 'TRIAL' WHERE t.id = ?", t);
        assertThat(amount).containsEntry("status", "RUNNING").containsEntry("selected_count", 2).containsEntry("amount", 106000L).containsEntry("quote_supply", 106000L)
                .containsEntry("quote_vat", 10600L).containsEntry("quote_total", 116600L).containsEntry("units", 2);
        // 대신 발송비는 따로 품목 줄(V242 TRIAL_SHIPPING — 3,000 × 2명) · 줄 합 = 견적 공급가
        assertThat(jdbc.queryForMap("SELECT i.unit_price, i.units, i.supply FROM trials t JOIN biz_application_items i ON i.application_id = t.biz_application_id "
                + "WHERE t.id = ? AND i.product = 'TRIAL_SHIPPING'", t)).containsEntry("unit_price", 3000L).containsEntry("units", 2).containsEntry("supply", 6000L);
        assertThat(jdbc.queryForObject("SELECT sum(i.supply) FROM trials t JOIN biz_application_items i ON i.application_id = t.biz_application_id WHERE t.id = ?", Long.class, t))
                .isEqualTo(106000L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM trial_applications WHERE trial_id = ? AND status = 'NOT_SELECTED' AND address_purged_at IS NOT NULL", Integer.class, t))
                .isEqualTo(2); // 안 뽑힌 신청의 주소는 바로 파기
        assertThat(notices(r1, "TRIAL_SELECTED")).isEqualTo(1);
        assertThat(notices(g1, "TRIAL_NOT_SELECTED")).isEqualTo(1);
        assertThat(lastAudit("TRIAL", Long.toString(t), "TRIAL_SELECT").get("detail").toString()).contains("106000");

        jdbc.update("UPDATE biz_price_rules SET price_unit = 'PER_RECRUIT' WHERE product = 'TRIAL'");
        long t2 = trial("GIVE", "ADVERTISER", 3, 0, 0);
        long m = member(true);
        apply(m, t2, address(m), consents("GIVE", "ADVERTISER")).andExpect(status().isCreated());
        selectAll(t2, List.of(applicationId(t2, m)));
        assertThat(jdbc.queryForObject("SELECT amount FROM trials WHERE id = ?", Long.class, t2)).isEqualTo(50000L); // 건당 — 인원과 상관없이, 광고주 발송이라 발송비 없음
    }

    // ── 사용기 쓰기 ────────────────────────────────────────────────────────

    @Test
    void 체험단_사용기는_연결하면_TRIAL_고정_말머리_자동_표시상자_약속지킴_세번이면_리뷰어_동() throws Exception {
        long m = member(true);
        reviewer(m, null);
        for (int i = 0; i < 2; i++) {
            long p = selected(m, "GIVE", "ADVERTISER");
            // SQL 로 남김: 앞선 두 번의 약속 지킴을 바로 둔다 — 같은 회원이 글을 잇달아 쓰면 작성 간격(60초)에 걸린다
            jdbc.update("UPDATE trial_participants SET kept = true, kept_at = now() WHERE id = ?", p);
        }
        long p = selected(m, "GIVE", "ADVERTISER");
        String body = write(m, postBody(slug, "이어폰 사용기", ",\"trialParticipantId\":\"" + p + "\"")).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString();
        long postId = Long.parseLong(JsonPath.read(body, "$.data.id"));
        Map<String, Object> post = jdbc.queryForMap("SELECT p.purchase_source, p.trial_participant_id, bp.common_key FROM posts p JOIN board_prefixes bp ON bp.id = p.prefix_id "
                + "WHERE p.id = ?", postId);
        assertThat(post).containsEntry("purchase_source", "TRIAL").containsEntry("trial_participant_id", p).containsEntry("common_key", "TRIAL");
        Map<String, Object> row = jdbc.queryForMap("SELECT review_post_id, received_by, kept FROM trial_participants WHERE id = ?", p);
        assertThat(row).containsEntry("review_post_id", postId).containsEntry("received_by", "MEMBER").containsEntry("kept", true);
        assertThat(jdbc.queryForObject("SELECT mt.tier FROM member_titles mt JOIN titles t ON t.id = mt.title_id WHERE t.title_key = 'reviewer' AND mt.user_id = ? "
                + "AND mt.revoked_at IS NULL", String.class, m)).isEqualTo("BRONZE");
        String advertiserName = jdbc.queryForObject("SELECT name FROM ad_advertisers WHERE id = ?", String.class, advertiser);
        mvc.perform(get("/api/posts/" + postId)).andExpect(status().isOk()).andExpect(jsonPath("$.data.purchase.source").value("TRIAL"))
                .andExpect(jsonPath("$.data.purchase.proof").value("NONE"))
                .andExpect(jsonPath("$.data.purchase.disclosure").value("이 글은 " + advertiserName + "로부터 제품을 무상으로 제공받아 작성한 글입니다"))
                .andExpect(jsonPath("$.data.prefix.name").value("체험단"));

        long other = member(true);
        write(other, postBody(slug, "남의 줄", ",\"trialParticipantId\":\"" + p + "\"")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.trialParticipantId").exists());
        long trialPrefix = jdbc.queryForObject("SELECT id FROM board_prefixes WHERE board_id = ? AND common_key = 'TRIAL'", Long.class, board);
        write(other, postBody(slug, "말머리만", ",\"purchaseSource\":\"SELF\",\"prefixId\":\"" + trialPrefix + "\"")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.prefixId").exists());
        long third = member(true);
        write(third, postBody(slug, "경로 없음", "")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.purchaseSource").exists());
        write(third, postBody(slug, "연결 없는 체험단", ",\"purchaseSource\":\"TRIAL\"")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.purchaseSource").exists());
        mvc.perform(as(m, get("/api/me/trials"))).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].participation.canWrite").value(false));
    }

    // ── 구매 인증 ─────────────────────────────────────────────────────────

    @Test
    void 구매인증은_SELF_글만_비공개_암호문_매니저가_승인하면_칩_보너스_알림_자기글은_불가_목록_거르기() throws Exception {
        long author = member(true);
        long self = Long.parseLong(JsonPath.read(write(author, postBody(slug, "직접 산 후기", ",\"purchaseSource\":\"SELF\"")).andExpect(status().isCreated()).andReturn()
                .getResponse().getContentAsString(), "$.data.id"));
        long borrower = member(true);
        long borrowed = Long.parseLong(JsonPath.read(write(borrower, postBody(slug, "빌린 후기", ",\"purchaseSource\":\"BORROWED\"")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.data.id"));
        MockMultipartFile file = new MockMultipartFile("file", "r.png", "image/png", png());
        mvc.perform(asFile(borrower, multipart("/api/posts/" + borrowed + "/purchase-proof").file(file))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PURCHASE_PROOF_NOT_ALLOWED"));
        mvc.perform(asFile(borrower, multipart("/api/posts/" + self + "/purchase-proof").file(file))).andExpect(status().isConflict()); // 남의 글
        String created = mvc.perform(asFile(author, multipart("/api/posts/" + self + "/purchase-proof").file(file))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING")).andReturn().getResponse().getContentAsString();
        String proofId = JsonPath.read(created, "$.data.id");
        mvc.perform(asFile(author, multipart("/api/posts/" + self + "/purchase-proof").file(file))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PURCHASE_PROOF_EXISTS"));
        byte[] stored = jdbc.queryForObject("SELECT content FROM purchase_proofs WHERE id = ?", byte[].class, Long.parseLong(proofId));
        assertThat(stored[0]).isNotEqualTo((byte) 0xFF); // JPEG 원문이 아니라 암호문

        long manager = member(true);
        // SQL 로 남김: board_staff 팩터리(byOperator)는 지정한 운영자를 요구한다 — 시험은 지정자 없는 행
        jdbc.update("INSERT INTO board_staff (board_id, user_id, role) VALUES (?, ?, 'MANAGER')", board, manager);
        long outsider = member(true);
        mvc.perform(as(outsider, post("/api/boards/" + slug + "/staff/purchase-proofs/" + proofId + "/approve"))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PURCHASE_PROOF_FORBIDDEN"));
        String staffList = mvc.perform(as(manager, get("/api/boards/" + slug + "/staff/purchase-proofs"))).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        assertThat(JsonPath.<List<String>>read(staffList, "$.data[?(@.id == '" + proofId + "')].status")).as(staffList).containsExactly("PENDING");
        // 확인 전이면 확인한 사람 칸이 없고, 매니저 응답의 회원에는 회원번호가 없다(관리자 응답에만)
        assertThat(JsonPath.<List<Object>>read(staffList, "$.data[?(@.id == '" + proofId + "')].reviewerName")).isEmpty();
        assertThat(JsonPath.<List<Object>>read(staffList, "$.data[?(@.id == '" + proofId + "')].author.memberNo")).isEmpty();
        mvc.perform(as(manager, get("/api/boards/" + slug + "/staff/purchase-proofs/" + proofId + "/photo"))).andExpect(status().isOk());
        assertThat(lastAudit("POST", Long.toString(self), "PURCHASE_PROOF_VIEW")).isNotNull();
        long xpBefore = jdbc.queryForObject("SELECT xp FROM profiles WHERE user_id = ?", Long.class, author);
        mvc.perform(as(manager, post("/api/boards/" + slug + "/staff/purchase-proofs/" + proofId + "/approve"))).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT xp FROM profiles WHERE user_id = ?", Long.class, author)).isEqualTo(xpBefore + 100); // 경험치에도 더한다
        mvc.perform(as(manager, post("/api/boards/" + slug + "/staff/purchase-proofs/" + proofId + "/approve"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PURCHASE_PROOF_CLOSED"));
        assertThat(jdbc.queryForObject("SELECT coalesce(sum(delta), 0) FROM point_ledger WHERE user_id = ? AND reason = 'PURCHASE_PROOF' AND ref_id = ?", Long.class, author, self))
                .isEqualTo(100L);
        assertThat(notices(author, "PURCHASE_PROOF_APPROVED")).isEqualTo(1);
        String doneList = mvc.perform(as(manager, get("/api/boards/" + slug + "/staff/purchase-proofs?status=DONE"))).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        assertThat(JsonPath.<List<String>>read(doneList, "$.data[?(@.id == '" + proofId + "')].reviewerName"))
                .containsExactly(jdbc.queryForObject("SELECT nickname FROM profiles WHERE user_id = ?", String.class, manager)); // 매니저 = 닉네임

        // 매니저 자기 글의 인증은 확인할 수 없다
        long mine = Long.parseLong(JsonPath.read(write(manager, postBody(slug, "매니저 후기", ",\"purchaseSource\":\"SELF\"")).andExpect(status().isCreated()).andReturn()
                .getResponse().getContentAsString(), "$.data.id"));
        String own = JsonPath.read(mvc.perform(asFile(manager, multipart("/api/posts/" + mine + "/purchase-proof").file(file))).andExpect(status().isCreated()).andReturn()
                .getResponse().getContentAsString(), "$.data.id");
        mvc.perform(as(manager, post("/api/boards/" + slug + "/staff/purchase-proofs/" + own + "/reject")).content("{\"reason\":\"자기 글\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PURCHASE_PROOF_SELF"));

        String list = mvc.perform(get("/api/boards/" + slug + "/posts")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Object>>read(list, "$.data.items[?(@.id == '" + self + "')].purchaseProofApproved")).containsExactly(true);
        assertThat(JsonPath.<List<Object>>read(list, "$.data.items[?(@.id == '" + borrowed + "')].purchaseSource")).containsExactly("BORROWED");
        String proofOnly = mvc.perform(get("/api/boards/" + slug + "/posts?purchase=PROOF")).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(proofOnly, "$.data.items[*].id")).containsExactly(Long.toString(self));
    }

    @Test
    void 체험단_빼고_거르기는_TRIAL_글을_뺀다() throws Exception {
        long m = member(true);
        long p = selected(m, "GIVE", "ADVERTISER");
        long trialPost = Long.parseLong(JsonPath.read(write(m, postBody(slug, "체험 후기", ",\"trialParticipantId\":\"" + p + "\"")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.data.id"));
        long other = member(true);
        long selfPost = Long.parseLong(JsonPath.read(write(other, postBody(slug, "산 후기", ",\"purchaseSource\":\"SELF\"")).andExpect(status().isCreated()).andReturn()
                .getResponse().getContentAsString(), "$.data.id"));
        String all = mvc.perform(get("/api/boards/" + slug + "/posts")).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(all, "$.data.items[*].id")).contains(Long.toString(trialPost), Long.toString(selfPost));
        String noTrial = mvc.perform(get("/api/boards/" + slug + "/posts?purchase=NO_TRIAL")).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(noTrial, "$.data.items[*].id")).contains(Long.toString(selfPost)).doesNotContain(Long.toString(trialPost));
    }

    // ── 협찬 숨김 ─────────────────────────────────────────────────────────

    @Test
    void 협찬숨김_신고는_구입경로_게시판_글만_확인줄_대기_자동숨김셈_제외_확인하면_SPONSORED_표시() throws Exception {
        long author = member(true);
        long postId = Long.parseLong(JsonPath.read(write(author, postBody(slug, "숨긴 협찬", ",\"purchaseSource\":\"SELF\"")).andExpect(status().isCreated()).andReturn()
                .getResponse().getContentAsString(), "$.data.id"));
        long plainBoard = ids.nextId();
        boardFixture.board(plainBoard, "tp" + Long.toString(plainBoard, 36), "일반 " + Long.toString(plainBoard, 36).substring(6), 962);
        catalog.invalidate();
        long plainPost = ids.nextId();
        boardFixture.post(plainPost, plainBoard, author, "일반 글", "본문");
        long reporter = member(true);
        assertThatThrownBy(() -> reports.report(new ReportRequest("POST", Long.toString(plainPost), "SPONSOR_HIDDEN", null), reporter))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsKey("reason"));
        reports.report(new ReportRequest("POST", Long.toString(postId), "SPONSOR_HIDDEN", null), reporter);
        assertThat(jdbc.queryForObject("SELECT status FROM trial_sponsor_reviews WHERE post_id = ?", String.class, postId)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT hidden_at IS NULL FROM posts WHERE id = ?", Boolean.class, postId)).isTrue();

        sponsors.confirm(operator, "127.0.0.1", Long.toString(postId), "업체 제공 확인");
        assertThat(jdbc.queryForObject("SELECT purchase_source FROM posts WHERE id = ?", String.class, postId)).isEqualTo("SPONSORED");
        mvc.perform(get("/api/posts/" + postId)).andExpect(jsonPath("$.data.purchase.source").value("SPONSORED"))
                .andExpect(jsonPath("$.data.purchase.disclosure").value("이 글은 업체로부터 제품을 제공받아 작성한 글입니다"));
        assertThatThrownBy(() -> sponsors.dismiss(operator, "ip", Long.toString(postId), null)).satisfies(e -> assertThat(code(e)).isEqualTo("SPONSOR_REVIEW_CLOSED"));
        assertThat(lastAudit("POST", Long.toString(postId), "SPONSOR_HIDDEN_CONFIRM")).isNotNull();
    }

    // ── 광고주 화면 ────────────────────────────────────────────────────────

    @Test
    void 광고주는_자기모집만_동의범위_주소를_보고_열람기록_발송전_사진없이_송장은_400_광고주아니면_403() throws Exception {
        long m = member(true);
        long p = selected(m, "GIVE", "ADVERTISER");
        long t = jdbc.queryForObject("SELECT trial_id FROM trial_participants WHERE id = ?", Long.class, p);
        long stranger = member(true);
        mvc.perform(as(stranger, get("/api/business/trials"))).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("TRIAL_NOT_ADVERTISER"));
        mvc.perform(as(advertiserUser, get("/api/business/trials"))).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(Long.toString(t)));
        mvc.perform(as(advertiserUser, get("/api/business/trials/" + t))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trial.participants[0].recipient").value("홍길동"))
                .andExpect(jsonPath("$.data.trial.participants[0].address1").value("서울 강남구 테헤란로 1"))
                .andExpect(jsonPath("$.data.applicants.items[0].status").value("SELECTED"))
                .andExpect(jsonPath("$.data.trial.participants[0].member.memberNo").doesNotExist()) // 회원번호는 관리자 응답에만
                .andExpect(jsonPath("$.data.applicants.items[0].member.memberNo").doesNotExist());
        assertThat(lastAudit("TRIAL", Long.toString(t), "TRIAL_PII_PROVIDE").get("detail").toString()).contains("ADVERTISER_VIEW");

        mvc.perform(as(advertiserUser, put("/api/business/trials/participants/" + p + "/shipment")).content("{\"carrier\":\"우체국\",\"trackingNo\":\"1234\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.photoIds").exists()); // 발송 전 사진 필수
        String photo = JsonPath.read(mvc.perform(asFile(advertiserUser, multipart("/api/business/trials/" + t + "/photos").file(new MockMultipartFile("file", "a.png",
                "image/png", png())).param("kind", "PRE_SHIP"))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.data.id");
        mvc.perform(as(advertiserUser, put("/api/business/trials/participants/" + p + "/shipment"))
                .content("{\"carrier\":\"우체국\",\"trackingNo\":\"1234\",\"photoIds\":[\"" + photo + "\"]}")).andExpect(status().isNoContent());
        assertThat(notices(m, "TRIAL_SHIPPED")).isEqualTo(1);
        assertThatThrownBy(() -> trials.ship(TrialService.Actor.operator(operator, "ip"), Long.toString(p), new TrialViews.ShipmentRequest("CJ", "9", null)))
                .satisfies(e -> assertThat(code(e)).isEqualTo("TRIAL_WRONG_SHIPPER")); // 광고주가 보내는 모집 — 운영자 발송 없음
        mvc.perform(as(advertiserUser, post("/api/business/trials/participants/" + p + "/returned")).content("{}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("TRIAL_NOT_LEND"));

        mvc.perform(as(m, post("/api/me/trials/participants/" + p + "/received"))).andExpect(status().isNoContent());
        mvc.perform(as(m, post("/api/me/trials/participants/" + p + "/received"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("TRIAL_ALREADY_RECEIVED"));
    }

    @Test
    void 운영자가_보내는_모집은_광고주에게_주소가_가지_않는다_입고는_사진_필수_발송은_입고_수량까지() throws Exception {
        long m1 = member(true);
        long m2 = member(true);
        long t = trial("GIVE", "OPERATOR", 2, 0, 0);
        apply(m1, t, address(m1), consents("GIVE", "OPERATOR")).andExpect(status().isCreated());
        apply(m2, t, address(m2), consents("GIVE", "OPERATOR")).andExpect(status().isCreated());
        selectAll(t, List.of(applicationId(t, m1), applicationId(t, m2)));
        mvc.perform(as(advertiserUser, get("/api/business/trials/" + t))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trial.participants[0].recipient").doesNotExist()).andExpect(jsonPath("$.data.trial.stock").doesNotExist());
        TrialService.Actor op = TrialService.Actor.operator(operator, "127.0.0.1");
        assertThatThrownBy(() -> trials.ship(op, Long.toString(participant(t, m1)), new TrialViews.ShipmentRequest("CJ", "1", null)))
                .satisfies(e -> assertThat(code(e)).isEqualTo("TRIAL_NO_STOCK"));
        assertThatThrownBy(() -> trials.stock(operator, "ip", Long.toString(t), new TrialViews.StockRequest(1, null, List.of())))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsKey("photoIds"));
        trials.stock(operator, "ip", Long.toString(t), new TrialViews.StockRequest(1, "상자 1", List.of(upload(op, t, "STOCK"))));
        trials.ship(op, Long.toString(participant(t, m1)), new TrialViews.ShipmentRequest("CJ", "1", null));
        assertThatThrownBy(() -> trials.ship(op, Long.toString(participant(t, m2)), new TrialViews.ShipmentRequest("CJ", "2", null)))
                .satisfies(e -> assertThat(code(e)).isEqualTo("TRIAL_NO_STOCK")); // 입고 1개 — 두 번째는 모자란다
        TrialViews.TrialDetail detail = trials.detail(Long.toString(t), op);
        assertThat(detail.participants().get(0).recipient()).isEqualTo("홍*동"); // 운영자는 운영자가 보내는 모집의 주소를 가린 값으로 본다(점검 D4 — 평문은 주소 보기)
        assertThat(detail.participants().get(0).addressMasked()).isTrue();
        assertThat(detail.stock().photos()).hasSize(1);
    }

    // ── 파손 ─────────────────────────────────────────────────────────────

    @Test
    void 파손은_사진_필수_이의기한_전에는_판정_불가_과실은_90일_금지와_연락처_제공_고의는_영구_금지와_리뷰어_회수() throws Exception {
        TrialService.Actor op = TrialService.Actor.operator(operator, "127.0.0.1");
        long m = member(true);
        reviewer(m, "SILVER");
        long p = selected(m, "LEND", "ADVERTISER");
        long t = jdbc.queryForObject("SELECT trial_id FROM trial_participants WHERE id = ?", Long.class, p);
        TrialService.Actor adv = TrialService.Actor.advertiser(advertiserUser, advertiser, "ip");
        trials.ship(adv, Long.toString(p), new TrialViews.ShipmentRequest("우체국", "77", List.of(upload(adv, t, "PRE_SHIP"))));
        String damagePhoto = upload(adv, t, "DAMAGE");
        assertThatThrownBy(() -> trials.reportDamage(adv, Long.toString(p), new TrialViews.DamageRequest("액정 깨짐", List.of(damagePhoto))))
                .satisfies(e -> assertThat(code(e)).isEqualTo("TRIAL_NOT_RETURNED")); // 파손 신고는 반납 받은 뒤(점검 L7)
        trials.confirmReturn(adv, Long.toString(p), new TrialViews.PhotosRequest(List.of(upload(adv, t, "RETURN"))));
        assertThatThrownBy(() -> trials.reportDamage(adv, Long.toString(p), new TrialViews.DamageRequest("액정 깨짐", List.of())))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsKey("photoIds"));
        String report = trials.reportDamage(adv, Long.toString(p), new TrialViews.DamageRequest("액정 깨짐", List.of(damagePhoto))).id();
        assertThat(notices(m, "TRIAL_DAMAGE_REPORTED")).isEqualTo(1);
        assertThatThrownBy(() -> trials.decideDamage(operator, "ip", report, new TrialViews.DecideRequest("MEMBER_FAULT", null)))
                .satisfies(e -> assertThat(code(e)).isEqualTo("TRIAL_DAMAGE_OBJECTION_OPEN"));
        mvc.perform(as(m, post("/api/me/trials/damage-reports/" + report + "/objection")).content("{\"text\":\"받을 때부터 흠집\"}")).andExpect(status().isNoContent());
        mvc.perform(as(m, post("/api/me/trials/damage-reports/" + report + "/objection")).content("{\"text\":\"다시\"}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("TRIAL_OBJECTION_CLOSED"));
        trials.decideDamage(operator, "ip", report, new TrialViews.DecideRequest("MEMBER_FAULT", "흠집 크기"));
        Map<String, Object> ban = jdbc.queryForMap("SELECT until, auto_rule FROM member_restrictions WHERE user_id = ? AND type = 'TRIAL_BAN'", m);
        assertThat(ban).containsEntry("auto_rule", "TRIAL_DAMAGE_FAULT");
        assertThat(((java.sql.Timestamp) ban.get("until")).toInstant()).isAfter(clock.instant().plus(Duration.ofDays(89)));
        assertThat(jdbc.queryForObject("SELECT contact_provided_at IS NOT NULL FROM trial_participants WHERE id = ?", Boolean.class, p)).isTrue();
        assertThat(lastAudit("TRIAL", Long.toString(t), "TRIAL_PII_PROVIDE").get("detail").toString()).contains("DAMAGE_MEMBER_FAULT");
        assertThat(notices(m, "TRIAL_DAMAGE_DECIDED")).isEqualTo(1);
        assertThatThrownBy(() -> trials.decideDamage(operator, "ip", report, new TrialViews.DecideRequest("INTENTIONAL", null)))
                .satisfies(e -> assertThat(code(e)).isEqualTo("TRIAL_DAMAGE_DECIDED"));

        String second = trials.reportDamage(op, Long.toString(p), new TrialViews.DamageRequest("숨김 반납", List.of(upload(op, t, "DAMAGE")))).id();
        // SQL 로 남김: 이의 기한(7일)이 지난 것으로 둔다 — 시계를 옮기면 앞서 건 제한 기간도 함께 지나간다
        jdbc.update("UPDATE trial_damage_reports SET objection_due_at = now() - interval '1 minute' WHERE id = ?", Long.parseLong(second));
        trials.decideDamage(operator, "ip", second, new TrialViews.DecideRequest("INTENTIONAL", null));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member_restrictions WHERE user_id = ? AND type = 'TRIAL_BAN' AND until IS NULL AND auto_rule = 'TRIAL_DAMAGE_INTENTIONAL'",
                Integer.class, m)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT revoked_reason FROM member_titles mt JOIN titles t ON t.id = mt.title_id WHERE t.title_key = 'reviewer' AND mt.user_id = ?",
                String.class, m)).isEqualTo("TRIAL_DAMAGE_INTENTIONAL");
    }

    // ── 하루 배치 ─────────────────────────────────────────────────────────

    @Test
    void 하루배치_받음자동_7일_사용기기한넘김_90일금지_반납독촉_0일_7일_미반납14일_영구금지_리뷰어회수_연락처제공_주소파기() throws Exception {
        TrialService.Actor op = TrialService.Actor.operator(operator, "127.0.0.1");
        long m = member(true);
        reviewer(m, "BRONZE");
        long t = trial("LEND", "OPERATOR", 1, 0, 0);
        apply(m, t, address(m), consents("LEND", "OPERATOR")).andExpect(status().isCreated());
        selectAll(t, List.of(applicationId(t, m)));
        long p = participant(t, m);
        trials.stock(operator, "ip", Long.toString(t), new TrialViews.StockRequest(2, null, List.of(upload(op, t, "STOCK"))));
        trials.ship(op, Long.toString(p), new TrialViews.ShipmentRequest("CJ", "100", null));
        Instant shipped = jdbc.queryForObject("SELECT shipped_at FROM trial_participants WHERE id = ?", java.sql.Timestamp.class, p).toInstant();

        clock.set(shipped.plus(Duration.ofDays(7)).plusSeconds(1));
        daily.runOnce();
        Map<String, Object> row = jdbc.queryForMap("SELECT received_by, received_at, review_due_at, return_due_at FROM trial_participants WHERE id = ?", p);
        assertThat(row).containsEntry("received_by", "AUTO");
        Instant received = ((java.sql.Timestamp) row.get("received_at")).toInstant();
        assertThat(received).isEqualTo(shipped.plus(Duration.ofDays(7)));
        Instant returnDue = ((java.sql.Timestamp) row.get("return_due_at")).toInstant();
        assertThat(returnDue).isEqualTo(received.plus(Duration.ofDays(10)));

        clock.set(returnDue.plusSeconds(1)); // 반납 기한 당일 독촉
        daily.runOnce();
        assertThat(notices(m, "TRIAL_RETURN_DUE")).isEqualTo(1);
        daily.runOnce(); // 두 번 돌아도 한 번
        assertThat(notices(m, "TRIAL_RETURN_DUE")).isEqualTo(1);

        clock.set(received.plus(Duration.ofDays(14)).plusSeconds(1)); // 사용기 기한(14일) 넘김
        daily.runOnce();
        assertThat(jdbc.queryForObject("SELECT kept FROM trial_participants WHERE id = ?", Boolean.class, p)).isFalse();
        assertThat(notices(m, "TRIAL_REVIEW_DUE")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT auto_rule FROM member_restrictions WHERE user_id = ? AND type = 'TRIAL_BAN' AND until IS NOT NULL", String.class, m))
                .isEqualTo("TRIAL_REVIEW_LATE");

        clock.set(returnDue.plus(Duration.ofDays(7)).plusSeconds(1)); // 7일 뒤 다시 독촉
        daily.runOnce();
        assertThat(notices(m, "TRIAL_RETURN_DUE")).isEqualTo(2);

        clock.set(returnDue.plus(Duration.ofDays(14)).plusSeconds(1)); // 14일 지나면 미반납 확정
        daily.runOnce();
        assertThat(jdbc.queryForObject("SELECT unreturned_at IS NOT NULL AND contact_provided_at IS NOT NULL FROM trial_participants WHERE id = ?", Boolean.class, p)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member_restrictions WHERE user_id = ? AND type = 'TRIAL_BAN' AND until IS NULL AND auto_rule = 'TRIAL_UNRETURNED'",
                Integer.class, m)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT revoked_reason FROM member_titles mt JOIN titles t ON t.id = mt.title_id WHERE t.title_key = 'reviewer' AND mt.user_id = ?",
                String.class, m)).isEqualTo("TRIAL_UNRETURNED");
        assertThat(notices(m, "TRIAL_UNRETURNED")).isEqualTo(1);
        assertThat(lastAudit("TRIAL", Long.toString(t), "TRIAL_PII_PROVIDE").get("detail").toString()).contains("UNRETURNED");
        assertThat(lastAudit("MEMBER", Long.toString(m), "TRIAL_UNRETURNED")).isNotNull();
        mvc.perform(as(advertiserUser, get("/api/business/trials/" + t))).andExpect(jsonPath("$.data.trial.participants[0].recipient").value("홍길동"))
                .andExpect(jsonPath("$.data.trial.participants[0].address1").doesNotExist()); // 운영자 발송 — 미반납이면 이름·연락처만

        clock.advance(Duration.ofDays(31)); // 제공 뒤 30일 — 주소 사본 파기 · 모든 줄 판정 → 모집 끝남
        daily.runOnce();
        assertThat(jdbc.queryForObject("SELECT address_purged_at IS NOT NULL AND recipient_enc IS NULL FROM trial_applications WHERE trial_id = ? AND user_id = ?", Boolean.class,
                t, m)).isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM trials WHERE id = ?", String.class, t)).isEqualTo("CLOSED");
    }

    @Test
    void 사용기_기한넘김_두번째면_리뷰어_칭호_정지_기한안_연결과_반납이면_약속지킴() throws Exception {
        long m = member(true);
        reviewer(m, null);
        long first = selected(m, "GIVE", "ADVERTISER");
        long second = selected(m, "GIVE", "ADVERTISER");
        TrialService.Actor adv = TrialService.Actor.advertiser(advertiserUser, advertiser, "ip");
        for (long p : new long[] {first, second}) {
            long t = jdbc.queryForObject("SELECT trial_id FROM trial_participants WHERE id = ?", Long.class, p);
            trials.ship(adv, Long.toString(p), new TrialViews.ShipmentRequest("우체국", "1", List.of(upload(adv, t, "PRE_SHIP"))));
            mvc.perform(as(m, post("/api/me/trials/participants/" + p + "/received"))).andExpect(status().isNoContent());
        }
        // 이 회원의 다른 제한이 먼저 걸리지 않게 첫 줄만 판정 — 두 줄 모두 기한을 넘긴다
        clock.advance(Duration.ofDays(15));
        daily.runOnce();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM trial_participants WHERE user_id = ? AND review_late_at IS NOT NULL", Integer.class, m)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT revoked_reason FROM member_titles mt JOIN titles t ON t.id = mt.title_id WHERE t.title_key = 'reviewer' AND mt.user_id = ?",
                String.class, m)).isEqualTo("TRIAL_REVIEW_LATE");

        // 대여형 — 기한 안 연결 사용기 + 반납까지 해야 약속 지킴
        long k = member(true);
        long lend = selected(k, "LEND", "ADVERTISER");
        long lt = jdbc.queryForObject("SELECT trial_id FROM trial_participants WHERE id = ?", Long.class, lend);
        trials.ship(adv, Long.toString(lend), new TrialViews.ShipmentRequest("우체국", "2", List.of(upload(adv, lt, "PRE_SHIP"))));
        write(k, postBody(slug, "대여 후기", ",\"trialParticipantId\":\"" + lend + "\"")).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT kept FROM trial_participants WHERE id = ?", Boolean.class, lend)).isNull(); // 반납 전
        assertThatThrownBy(() -> trials.confirmReturn(adv, Long.toString(lend), new TrialViews.PhotosRequest(List.of())))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsKey("photoIds"));
        trials.confirmReturn(adv, Long.toString(lend), new TrialViews.PhotosRequest(List.of(upload(adv, lt, "RETURN"))));
        assertThat(jdbc.queryForObject("SELECT kept FROM trial_participants WHERE id = ?", Boolean.class, lend)).isTrue();
    }

    @Test
    void 하루배치_파손이의기한_지나면_확인할일로_구매인증_사진은_확인뒤_30일_파기_붙지않은_사진_정리() throws Exception {
        TrialService.Actor adv = TrialService.Actor.advertiser(advertiserUser, advertiser, "ip");
        long m = member(true);
        long p = selected(m, "LEND", "ADVERTISER");
        long t = jdbc.queryForObject("SELECT trial_id FROM trial_participants WHERE id = ?", Long.class, p);
        trials.ship(adv, Long.toString(p), new TrialViews.ShipmentRequest("우체국", "3", List.of(upload(adv, t, "PRE_SHIP"))));
        trials.confirmReturn(adv, Long.toString(p), new TrialViews.PhotosRequest(List.of(upload(adv, t, "RETURN")))); // 파손 신고는 반납 받은 뒤(점검 L7)
        String report = trials.reportDamage(adv, Long.toString(p), new TrialViews.DamageRequest("긁힘", List.of(upload(adv, t, "DAMAGE")))).id();
        String loose = upload(adv, t, "RETURN");

        long author = member(true);
        long self = Long.parseLong(JsonPath.read(write(author, postBody(slug, "인증 후기", ",\"purchaseSource\":\"SELF\"")).andExpect(status().isCreated()).andReturn()
                .getResponse().getContentAsString(), "$.data.id"));
        String proof = JsonPath.read(mvc.perform(asFile(author, multipart("/api/posts/" + self + "/purchase-proof").file(new MockMultipartFile("file", "r.png", "image/png",
                png())))).andReturn().getResponse().getContentAsString(), "$.data.id");
        com.community.trial.service.PurchaseProofService proofs = context.getBean(com.community.trial.service.PurchaseProofService.class);
        proofs.reject(com.community.trial.service.PurchaseProofService.Reviewer.operator(operator, "ip"), proof, "흐림");

        clock.advance(Duration.ofDays(8));
        daily.runOnce();
        assertThat(jdbc.queryForObject("SELECT status FROM trial_damage_reports WHERE id = ?", String.class, Long.parseLong(report))).isEqualTo("AWAITING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM trial_photos WHERE id = ?", Integer.class, Long.parseLong(loose))).isZero();
        assertThat(jdbc.queryForObject("SELECT purged_at IS NULL FROM purchase_proofs WHERE id = ?", Boolean.class, Long.parseLong(proof))).isTrue();
        clock.advance(Duration.ofDays(23));
        daily.runOnce();
        assertThat(jdbc.queryForObject("SELECT purged_at IS NOT NULL AND content IS NULL FROM purchase_proofs WHERE id = ?", Boolean.class, Long.parseLong(proof))).isTrue();
    }

    @Test
    void 공개_목록과_상세_끝난_모집은_사용기_모아보기() throws Exception {
        long m = member(true);
        long p = selected(m, "GIVE", "ADVERTISER");
        long t = jdbc.queryForObject("SELECT trial_id FROM trial_participants WHERE id = ?", Long.class, p);
        long postId = Long.parseLong(JsonPath.read(write(m, postBody(slug, "모아보기 후기", ",\"trialParticipantId\":\"" + p + "\"")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.data.id"));
        String running = mvc.perform(get("/api/trials?status=RUNNING")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(running, "$.data[*].id")).contains(Long.toString(t));
        trials.update(operator, "ip", Long.toString(t), action("CLOSE"));
        mvc.perform(get("/api/trials/" + t)).andExpect(status().isOk()).andExpect(jsonPath("$.data.card.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.reviews[0].postId").value(Long.toString(postId))).andExpect(jsonPath("$.data.mine").doesNotExist());
        mvc.perform(get("/api/trials?status=WRONG")).andExpect(status().isBadRequest());
        mvc.perform(as(m, get("/api/me/trials/writable?board=" + slug))).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
    }
}
