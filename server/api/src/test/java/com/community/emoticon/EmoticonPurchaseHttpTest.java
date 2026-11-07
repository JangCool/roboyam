package com.community.emoticon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.auth.dto.AuthUser;
import com.community.board.service.BoardCatalog;
import com.community.commerce.CommerceApiTestBase;
import com.community.emoticon.dto.request.EmoticonReviewDecisionRequest;
import com.community.emoticon.dto.request.EmoticonStudioSaveRequest;
import com.community.emoticon.service.EmoticonReviewService;
import com.community.emoticon.service.EmoticonStudioService;
import com.community.identity.dto.request.FakeIdentityInput;
import com.community.identity.service.IdentityVerificationService;
import com.community.payment.dto.ConfirmedVia;
import com.community.payment.gateway.fake.FakePgStore;
import com.community.payment.service.PaymentConfirmService;
import com.community.permission.entity.MemberPermission;
import com.community.role.entity.MemberRoleIds;
import com.community.testsupport.SiteOperatorFixture;
import com.community.user.entity.Role;
import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 로보티콘 S3 유료 구매(emoticon-contract S3 · commerce.md 7-3·8-2·8-6·12-1·12-4) — 앱 문맥 전체 + 실제 PostgreSQL + 가짜 PG + MockMvc.
 * 결제 준비 자격·차례 → 결제창(가짜 PG) → 공용 완료 API → 보유·구매 내역, 다시 준비·늦은 결제·도메인 거절의 자동 취소, 유료 판매 끄기, 같은 결제 동시 확정,
 * 셀프 환불(멱등 키·쓴 팩·기한·PG 취소), 「사용함」(글·댓글·소모임 글 저장 트랜잭션), 환불한 팩의 받기·다시 사기.
 */
class EmoticonPurchaseHttpTest extends CommerceApiTestBase {

    private static final int PRICE = 2500;
    /** 일반 회원 기본 권한 — 운영 시드({@code BuiltinRoleSeeder})와 같은 정본(먼저 도는 시험이 역할을 만들면 뒤의 공구 주문 시험도 그 권한을 쓴다) */
    private static final List<MemberPermission> GENERAL = MemberRoleIds.GENERAL_DEFAULT_PERMISSIONS;

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private BoardCatalog boards;
    @Autowired
    private IdentityVerificationService identity;
    @Autowired
    private EmoticonStudioService studio;
    @Autowired
    private EmoticonReviewService reviews;
    @Autowired
    private PaymentConfirmService confirm;
    @Autowired
    private com.community.emoticon.service.EmoticonPackAdminService packAdmin;
    @Autowired
    private com.community.withdrawal.service.WithdrawalService withdrawals;
    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder encoder;

    private MockMvc mvc;
    private FakePgStore fakePg;
    private Boolean paidWas;
    private String slug;
    private long board;
    private long operator;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        fakePg = new FakePgStore(dataSource);
        operator = newOperator();
        paidWas = jdbc.queryForObject("SELECT paid_sales_enabled FROM emoticon_settings WHERE id = 1", Boolean.class);
        jdbc.update("UPDATE emoticon_settings SET paid_sales_enabled = true WHERE id = 1");
        SiteOperatorFixture.fillBusiness(jdbc); // 유료 판매·구매는 운영 주체가 개인사업자 이상 + 통신판매업 신고번호(사용자 결정 2026-11-07)
        for (MemberPermission p : MemberPermission.values()) {
            // SQL 로 남김: ON CONFLICT upsert — 권한·내장 역할 사본을 없을 때만 넣는다
            jdbc.update("INSERT INTO permissions (code, axis, type_id, category, feature_ready) VALUES (?, 'MEMBER', ?, ?, ?) ON CONFLICT (code) DO NOTHING", p.code(),
                    p.typeId(), p.group().name(), p.featureReady());
        }
        // SQL 로 남김: ON CONFLICT upsert — 권한·내장 역할 사본을 없을 때만 넣는다
        if (jdbc.update("INSERT INTO roles (role_id, role_type_id, axis, role_name, scope, grant_mode, builtin) VALUES (?, 'memberKind', 'MEMBER', ?, 'SITE', 'AUTO', true) "
                + "ON CONFLICT (role_id) DO NOTHING", MemberRoleIds.GENERAL, MemberRoleIds.GENERAL) == 1) {
            for (MemberPermission p : GENERAL) {
                // SQL 로 남김: ON CONFLICT upsert — 권한·내장 역할 사본을 없을 때만 넣는다
                jdbc.update("INSERT INTO role_permissions (id, role_id, permission_code) VALUES (?, ?, ?) ON CONFLICT DO NOTHING", ids.nextId(), MemberRoleIds.GENERAL, p.code());
            }
        }
        board = ids.nextId();
        slug = "emp" + Long.toString(board, 36);
        boardFixture.board(board, slug, "로보티콘 구매 " + slug.substring(slug.length() - 5), 951);
        boards.invalidate();
    }

    @AfterEach
    void restore() {
        jdbc.update("UPDATE emoticon_packs SET price = 0 WHERE price > 0");
        jdbc.update("UPDATE emoticon_settings SET paid_sales_enabled = ? WHERE id = 1", paidWas);
        SiteOperatorFixture.clear(jdbc);
    }

    // ── 도우미 ──────────────────────────────────────────────────────────────

    private long member() {
        long id = newMember();
        memberFixture.verifyEmail(id);
        String base36 = Long.toString(id, 36);
        memberFixture.profile(id, "b" + base36.substring(Math.max(0, base36.length() - 11)), "REGULAR");
        return id;
    }

    /** 본인인증한 성인 회원 */
    private long buyer() {
        long id = member();
        identity.confirm(id, identity.start(id).verificationId(), new FakeIdentityInput("1990-05-14", newPerson()));
        return id;
    }

    private static <B extends org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder<B>> B as(long memberId, B request) {
        AuthUser user = new AuthUser(memberId, Role.USER);
        return request.principal(new UsernamePasswordAuthenticationToken(user, null, user.authorities())).header("Accept-Language", "ko");
    }

    private long image(long creator, String kind, String format, int frames) {
        long id = ids.nextId();
        jdbc.update("INSERT INTO emoticon_images (id, kind, format, width, height, bytes, frames, loop_forever, content_hash, static_path, uploaded_by_user, created_at) "
                + "VALUES (?, ?, ?, 200, 200, 1000, ?, true, lpad(to_hex(?::bigint), 64, '0'), '/emoticons/test.gif', ?, now())", id, kind, format, frames, id, creator);
        return id;
    }

    /** 작가 팩을 승인(무료)한 뒤 운영자가 유료(2,500원)로 바꾼 상태 — 팩 ID */
    private long paidPack() {
        long creator = member();
        List<EmoticonStudioSaveRequest.Item> items = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            items.add(new EmoticonStudioSaveRequest.Item(Long.toString(image(creator, "EMOTICON", "GIF", 2)), "표" + i));
        }
        EmoticonStudioSaveRequest body = new EmoticonStudioSaveRequest("유료 시험 팩", "설명", List.of("시험"), Long.toString(image(creator, "COVER", "PNG", 1)), items, "FREE",
                null, true, true, true, null);
        long packId = Long.parseLong(tx.execute(s -> studio.submit(creator, null, body)).id());
        String review = jdbc.queryForObject("SELECT id FROM emoticon_reviews WHERE pack_id = ?", Long.class, packId).toString();
        tx.executeWithoutResult(s -> reviews.approve(operator, "127.0.0.1", review, new EmoticonReviewDecisionRequest(0, null)));
        jdbc.update("UPDATE emoticon_packs SET price = ? WHERE id = ?", PRICE, packId);
        return packId;
    }

    private List<Long> emoticons(long pack) {
        return jdbc.queryForList("SELECT i.emoticon_id FROM emoticon_packs p JOIN emoticon_revision_items i ON i.revision_id = p.live_revision_id WHERE p.id = ? ORDER BY i.position",
                Long.class, pack);
    }

    private ResultActions prepare(long member, long pack) throws Exception {
        return mvc.perform(as(member, post("/api/emoticons/packs/" + pack + "/purchase")).contentType(MediaType.APPLICATION_JSON).content("{\"agreeRefundNotice\":true}"));
    }

    /** 결제 준비 → {paymentId, orderRefId} */
    private String[] start(long member, long pack) throws Exception {
        String body = prepare(member, pack).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new String[] {JsonPath.read(body, "$.data.checkout.paymentId"), JsonPath.read(body, "$.data.orderRefId")};
    }

    private ResultActions complete(long member, String[] started) throws Exception {
        return mvc.perform(as(member, post("/api/payments/" + started[0] + "/complete")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderType\":\"ROBOTIKON_PACK\",\"orderRefId\":\"" + started[1] + "\"}"));
    }

    /** 결제 준비 → 가짜 PG 결제 → 완료 — 구매 ID */
    private long buy(long member, long pack) throws Exception {
        String[] s = start(member, pack);
        fakePg.pay(s[0], PRICE, "KRW", clock.instant());
        complete(member, s).andExpect(status().isOk()).andExpect(jsonPath("$.data.accepted").value(true));
        return Long.parseLong(s[1]);
    }

    private ResultActions refund(long member, long purchase, String key) throws Exception {
        return mvc.perform(as(member, post("/api/me/emoticon-purchases/" + purchase + "/refund")).header("Idempotency-Key", key));
    }

    private String purchaseStatus(long purchase) {
        return jdbc.queryForObject("SELECT status FROM emoticon_purchases WHERE id = ?", String.class, purchase);
    }

    private Timestamp usedAt(long purchase) {
        return jdbc.queryForObject("SELECT used_at FROM emoticon_purchases WHERE id = ?", Timestamp.class, purchase);
    }

    private int acquireCount(long pack) {
        return jdbc.queryForObject("SELECT acquire_count FROM emoticon_packs WHERE id = ?", Integer.class, pack);
    }

    private static String token(long id) {
        return ":emo-" + id + ":";
    }

    private static String json(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** 그 회원의 구매 알림 종류(오래된 것 먼저) */
    private List<String> purchaseNotices(long member) {
        return jdbc.queryForList("SELECT payload->>'kind' FROM member_notifications WHERE user_id = ? AND type = 'EMOTICON_PURCHASE' ORDER BY id", String.class, member);
    }

    // ── 후속 결정(2026-11-05) — 작가 자기 팩 · 구매 알림 ─────────────────────

    @Test
    void 작가는_자기_유료_팩을_사지_못하고_사지_않아도_가지며_내_로보티콘에_mine_으로_나온다() throws Exception {
        long pack = paidPack();
        long creator = jdbc.queryForObject("SELECT creator_id FROM emoticon_packs WHERE id = ?", Long.class, pack);
        identity.confirm(creator, identity.start(creator).verificationId(), new FakeIdentityInput("1990-05-14", newPerson()));
        prepare(creator, pack).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("EMOTICON_PACK_OWNED"))
                .andExpect(jsonPath("$.error.reason").value("CREATOR")).andExpect(jsonPath("$.error.message").value("내가 만든 팩이라 사지 않아도 쓸 수 있어요."));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM emoticon_purchases WHERE user_id = ?", Integer.class, creator)).isZero();
        mvc.perform(as(creator, get("/api/emoticons/packs/" + pack))).andExpect(jsonPath("$.data.acquired").value(true));
        mvc.perform(as(creator, get("/api/me/emoticons"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.packs[0].id").value(Long.toString(pack)))
                .andExpect(jsonPath("$.data.packs[0].mine").value(true)).andExpect(jsonPath("$.data.packs[0].price").value(0))
                .andExpect(jsonPath("$.data.packs[0].purchaseId").doesNotExist());
        mvc.perform(as(creator, delete("/api/me/emoticons/" + pack))).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("EMOTICON_PACK_OWNED"))
                .andExpect(jsonPath("$.error.reason").value("CREATOR"));
        // 글에 자기 팩 로보티콘을 쓴다(사용함 기록은 없다 — 구매가 없다)
        mvc.perform(as(creator, post("/api/posts").contentType(MediaType.APPLICATION_JSON).content("{\"boardSlug\":\"" + slug + "\",\"title\":\"작가 글\","
                + "\"contentFormat\":\"TEXT\",\"content\":" + json("내 팩 " + token(emoticons(pack).get(0))) + "}"))).andExpect(status().isCreated());
        // 다른 회원이 산 것은 작가 팩 판매로 남는다(작가 본인 구매는 없다)
        long buyer = buyer();
        buy(buyer, pack);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM emoticon_purchases WHERE pack_id = ? AND status = 'PAID'", Integer.class, pack)).isEqualTo(1);
        mvc.perform(as(buyer, get("/api/me/emoticons"))).andExpect(jsonPath("$.data.packs[0].mine").value(false));
    }

    @Test
    void 구매_완료와_셀프_환불은_회원_알림을_한_번씩_내고_알림함에_그려진다() throws Exception {
        long pack = paidPack();
        long buyer = buyer();
        String[] s = start(buyer, pack);
        fakePg.pay(s[0], PRICE, "KRW", clock.instant());
        complete(buyer, s).andExpect(status().isOk()).andExpect(jsonPath("$.data.accepted").value(true));
        complete(buyer, s).andExpect(status().isOk());
        assertThat(confirm.confirm(s[0], ConfirmedVia.WEBHOOK).outcome()).isEqualTo(PaymentConfirmService.Outcome.ALREADY_DONE);
        assertThat(purchaseNotices(buyer)).containsExactly("PAID");
        long purchase = Long.parseLong(s[1]);
        String key = "notice-refund-" + purchase;
        refund(buyer, purchase, key).andExpect(status().isOk());
        refund(buyer, purchase, key).andExpect(status().isOk()).andExpect(header().string("Idempotent-Replayed", "true"));
        assertThat(purchaseNotices(buyer)).containsExactly("PAID", "REFUNDED_SELF");
        // 알림함 — 제목 = 종류별 한 줄(금액), 본문 = 팩 이름, 링크 = 내 로보티콘
        mvc.perform(as(buyer, get("/api/notifications"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].type").value("EMOTICON_PURCHASE"))
                .andExpect(jsonPath("$.data.items[0].title").value("환불을 접수했어요. 2,500원이 결제한 수단으로 돌아가요."))
                .andExpect(jsonPath("$.data.items[0].body").value("로보티콘 팩 「유료 시험 팩」")).andExpect(jsonPath("$.data.items[0].link").value("/me/robotikon"))
                .andExpect(jsonPath("$.data.items[1].title").value("로보티콘 팩을 샀어요(2,500원). 내 로보티콘에서 바로 쓸 수 있어요."));
    }

    @Test
    void 자동_취소는_PG_취소가_끝나면_한_번_알리고_그_팩으로_잇는다() throws Exception {
        long pack = paidPack();
        long buyer = buyer();
        String[] s = start(buyer, pack);
        fakePg.pay(s[0], PRICE - 500, "KRW", clock.instant()); // PG 금액 불일치 — 결제 모듈이 처리기 앞에서 거르고 전액 자동 취소
        complete(buyer, s).andExpect(status().isOk()).andExpect(jsonPath("$.data.accepted").value(false));
        Instant until = Instant.now().plusSeconds(10);
        while (Instant.now().isBefore(until) && purchaseNotices(buyer).isEmpty()) {
            Thread.onSpinWait();
        }
        assertThat(purchaseNotices(buyer)).containsExactly("AUTO_CANCELED");
        assertThat(jdbc.queryForObject("SELECT payload->>'path' FROM member_notifications WHERE user_id = ? AND type = 'EMOTICON_PURCHASE'", String.class, buyer))
                .isEqualTo("/robotikon/" + pack);
        assertThat(jdbc.queryForObject("SELECT (payload->>'amount')::int FROM member_notifications WHERE user_id = ? AND type = 'EMOTICON_PURCHASE'", Integer.class, buyer))
                .isEqualTo(PRICE - 500);
    }

    // ── 후속 결정 6(2026-11-05) — 탈퇴 작가의 팩은 새 판매만 멈춘다 ─────────

    @Test
    void 작가가_탈퇴하면_같은_트랜잭션에서_팩_판매가_멈추고_새_구매는_409_결제창_주문은_자동_취소_가진_회원은_계속_쓰고_셀프_환불한다() throws Exception {
        long pack = paidPack();
        long creator = jdbc.queryForObject("SELECT creator_id FROM emoticon_packs WHERE id = ?", Long.class, pack);
        long otherPack = paidPack();
        long user = buyer();
        buy(user, pack);                       // 글에 쓸 회원
        long refunder = buyer();
        long refundable = buy(refunder, pack); // 셀프 환불할 회원(쓰지 않았다)
        long waiting = buyer();
        String[] window = start(waiting, pack); // 탈퇴 순간 결제창에 있다

        com.community.withdrawal.WithdrawalTestSupport.withPassword(jdbc, encoder, creator, clock.instant());
        // 판매 수익이 남은 작가 — 정산 정보가 있어야 포기 동의 없이 탈퇴한다(V148 — 없으면 409 WITHDRAWAL_EMOTICON_EARNINGS_UNPAID, api WithdrawalEmoticonEarningsHttpTest)
        jdbc.update("INSERT INTO emoticon_payout_accounts (user_id, payee_type, bank_code, account_number_enc, account_last4, holder_name_enc, holder_masked, created_at, "
                + "updated_at) VALUES (?, 'INDIVIDUAL', 'KB', 'd1:AAAA', '1234', 'd1:AAAA', '홍*동', now(), now())", creator);
        assertThat(withdrawals.request(creator, com.community.withdrawal.WithdrawalTestSupport.plain(), "127.0.0.1").state()).isEqualTo("COMPLETED");

        // 탈퇴 완료 트랜잭션 안에서 — 작가 탈퇴 판매 중단 · 감사(시스템) · 탈퇴 감사에 멈춘 수
        Map<String, Object> row = jdbc.queryForMap("SELECT status, suspend_kind, suspended_by FROM emoticon_packs WHERE id = ?", pack);
        assertThat(row).containsEntry("status", "SUSPENDED").containsEntry("suspend_kind", "CREATOR_WITHDRAWN");
        assertThat(row.get("suspended_by")).isNull();
        Map<String, Object> audit = lastAudit("EMOTICON_PACK", Long.toString(pack), "EMOTICON_PACK_CREATOR_WITHDRAWN");
        assertThat(audit).isNotNull().containsEntry("actor_type", "SYSTEM");
        assertThat(audit.get("actor_id")).isNull();
        assertThat((Integer) JsonPath.read((String) lastAudit("MEMBER", Long.toString(creator), "MEMBER_WITHDRAW").get("detail"), "$.stoppedEmoticonPacks")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM emoticon_packs WHERE id = ?", String.class, otherPack)).isEqualTo("APPROVED");

        // 새 구매 409(판매 중단과 같은 코드) · 다른 작가의 팩은 그대로 산다
        prepare(buyer(), pack).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("EMOTICON_PACK_SUSPENDED"));
        prepare(buyer(), otherPack).andExpect(status().isOk());

        // 결제창 주문 — 완료는 「판매 중 아님」 거절 → 자동 취소(구매 CLOSED · 보유 없음 · 자동 취소 알림)
        fakePg.pay(window[0], PRICE, "KRW", clock.instant());
        complete(waiting, window).andExpect(status().isOk()).andExpect(jsonPath("$.data.accepted").value(false));
        assertThat(purchaseStatus(Long.parseLong(window[1]))).isEqualTo("CLOSED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM emoticon_acquisitions WHERE user_id = ? AND pack_id = ?", Integer.class, waiting, pack)).isZero();
        Instant until = Instant.now().plusSeconds(10);
        while (Instant.now().isBefore(until) && purchaseNotices(waiting).isEmpty()) {
            Thread.onSpinWait();
        }
        assertThat(purchaseNotices(waiting)).containsExactly("AUTO_CANCELED");

        // 가진 회원 — 글에 계속 쓰고, 상세는 판매 중단(작가 탈퇴)으로 보인다 · 가지지 않은 회원에게는 없다
        mvc.perform(as(user, post("/api/posts").contentType(MediaType.APPLICATION_JSON).content("{\"boardSlug\":\"" + slug + "\",\"title\":\"탈퇴 작가 팩\","
                + "\"contentFormat\":\"TEXT\",\"content\":" + json("계속 써요 " + token(emoticons(pack).get(0))) + "}"))).andExpect(status().isCreated());
        mvc.perform(as(user, get("/api/emoticons/packs/" + pack))).andExpect(status().isOk()).andExpect(jsonPath("$.data.saleState").value("SUSPENDED"))
                .andExpect(jsonPath("$.data.suspendKind").value("CREATOR_WITHDRAWN")).andExpect(jsonPath("$.data.acquired").value(true));
        mvc.perform(as(buyer(), get("/api/emoticons/packs/" + pack))).andExpect(status().isNotFound());
        mvc.perform(as(user, get("/api/me/emoticons"))).andExpect(jsonPath("$.data.packs[0].id").value(Long.toString(pack)))
                .andExpect(jsonPath("$.data.packs[0].suspended").value(true));
        // 셀프 환불 규칙은 그대로(쓰지 않았고 기한 안)
        refund(refunder, refundable, "withdrawn-creator-" + refundable).andExpect(status().isOk());
        assertThat(purchaseStatus(refundable)).isEqualTo("REFUNDED");
    }

    // ── 결제 준비 ───────────────────────────────────────────────────────────

    @Test
    void 결제_준비는_로그인_이메일_본인인증_만_19세_동의_차례이고_금액은_서버가_팩에서_읽는다() throws Exception {
        long pack = paidPack();
        mvc.perform(post("/api/emoticons/packs/" + pack + "/purchase").contentType(MediaType.APPLICATION_JSON).content("{\"agreeRefundNotice\":true}"))
                .andExpect(status().isUnauthorized());
        long unverifiedEmail = member();
        // SQL 로 남김: 회원 상태(정지·차단·탈퇴)·이메일·인증 칸을 그대로 두는 도메인 메서드가 없다
        jdbc.update("UPDATE users SET email_verified_at = NULL WHERE id = ?", unverifiedEmail);
        prepare(unverifiedEmail, pack).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("EMAIL_NOT_VERIFIED"));
        long noIdentity = member();
        prepare(noIdentity, pack).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("IDENTITY_REQUIRED"));
        long minor = member();
        identity.confirm(minor, identity.start(minor).verificationId(), new FakeIdentityInput("2015-03-01", newPerson()));
        prepare(minor, pack).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("IDENTITY_UNDERAGE"));
        long buyer = buyer();
        mvc.perform(as(buyer, post("/api/emoticons/packs/" + pack + "/purchase")).contentType(MediaType.APPLICATION_JSON).content("{\"agreeRefundNotice\":false}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.agreeRefundNotice").exists());
        prepare(buyer, 999L).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("EMOTICON_PACK_NOT_FOUND"));
        long robot = jdbc.queryForObject("SELECT id FROM emoticon_packs WHERE source = 'OFFICIAL' ORDER BY id LIMIT 1", Long.class);
        prepare(buyer, robot).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("EMOTICON_PACK_FREE"));
        prepare(buyer, pack).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.orderType").value("ROBOTIKON_PACK")).andExpect(jsonPath("$.data.packId").value(Long.toString(pack)))
                .andExpect(jsonPath("$.data.checkout.gateway").value("FAKE")).andExpect(jsonPath("$.data.checkout.amount").value(PRICE))
                .andExpect(jsonPath("$.data.checkout.orderName").value("로보티콘 「유료 시험 팩」")).andExpect(jsonPath("$.data.customer").doesNotExist());
        Map<String, Object> row = jdbc.queryForMap("SELECT status, amount, pack_name, refund_notice_agreed_at FROM emoticon_purchases WHERE user_id = ? AND pack_id = ?", buyer,
                pack);
        assertThat(row).containsEntry("status", "PENDING").containsEntry("amount", PRICE).containsEntry("pack_name", "유료 시험 팩");
        assertThat(row.get("refund_notice_agreed_at")).isNotNull();
        assertThat(jdbc.queryForObject("SELECT order_type FROM payment_orders WHERE user_id = ?", String.class, buyer)).isEqualTo("ROBOTIKON_PACK");
        // 판매 중단 팩은 사지 못한다
        tx.executeWithoutResult(st -> packAdmin.suspend(operator, "127.0.0.1", Long.toString(pack), "판매를 멈춰 봅니다"));
        prepare(buyer(), pack).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("EMOTICON_PACK_SUSPENDED"));
    }

    // ── 결제 완료 ───────────────────────────────────────────────────────────

    @Test
    void 결제가_완료되면_보유_구매_내역이_생기고_같은_결제를_다시_확인해도_한_번만_처리한다() throws Exception {
        long pack = paidPack();
        long buyer = buyer();
        int count = acquireCount(pack);
        String[] s = start(buyer, pack);
        complete(buyer, s).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("PAYMENT_NOT_PAID")); // 결제창에서 아직 결제하지 않았다
        fakePg.pay(s[0], PRICE, "KRW", clock.instant());
        complete(buyer, s).andExpect(status().isOk()).andExpect(jsonPath("$.data.accepted").value(true)).andExpect(jsonPath("$.data.status").value("PAID"));
        complete(buyer, s).andExpect(status().isOk()).andExpect(jsonPath("$.data.accepted").value(true));
        assertThat(confirm.confirm(s[0], ConfirmedVia.WEBHOOK).outcome()).isEqualTo(PaymentConfirmService.Outcome.ALREADY_DONE);
        assertThat(acquireCount(pack)).isEqualTo(count + 1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM emoticon_acquisitions WHERE user_id = ? AND pack_id = ? AND kind = 'PURCHASE' AND removed_at IS NULL",
                Integer.class, buyer, pack)).isEqualTo(1);
        mvc.perform(as(buyer, get("/api/me/emoticons"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.packs[0].id").value(Long.toString(pack)))
                .andExpect(jsonPath("$.data.packs[0].price").value(PRICE)).andExpect(jsonPath("$.data.packs[0].purchaseId").value(s[1]));
        mvc.perform(as(buyer, get("/api/me/emoticon-purchases"))).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.items[0].id").value(s[1])).andExpect(jsonPath("$.data.items[0].status").value("PAID"))
                .andExpect(jsonPath("$.data.items[0].amount").value(PRICE)).andExpect(jsonPath("$.data.items[0].packName").value("유료 시험 팩"))
                .andExpect(jsonPath("$.data.items[0].refundable").value(true)).andExpect(jsonPath("$.data.items[0].usedAt").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].refundUntil").isNotEmpty());
        mvc.perform(as(buyer, get("/api/emoticons/packs/" + pack))).andExpect(jsonPath("$.data.acquired").value(true));
        // 이미 가진 팩은 다시 사지 않는다 · 산 팩은 목록에서 빼지 않는다
        prepare(buyer, pack).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("EMOTICON_PACK_OWNED"));
        mvc.perform(as(buyer, delete("/api/me/emoticons/" + pack))).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("EMOTICON_PACK_PAID"));
        // 남의 결제로 완료를 부르면 찾지 못한다
        complete(buyer(), s).andExpect(status().isNotFound());
    }

    @Test
    void 다시_준비하면_앞_결제는_닫히고_그_결제가_늦게_와도_자동_취소되며_뒤_결제만_받는다() throws Exception {
        long pack = paidPack();
        long buyer = buyer();
        String[] first = start(buyer, pack);
        String[] second = start(buyer, pack);
        assertThat(purchaseStatus(Long.parseLong(first[1]))).isEqualTo("CLOSED");
        fakePg.pay(first[0], PRICE, "KRW", clock.instant());
        complete(buyer, first).andExpect(status().isOk()).andExpect(jsonPath("$.data.accepted").value(false)).andExpect(jsonPath("$.data.anomaly").value("LATE_PAYMENT"));
        fakePg.pay(second[0], PRICE, "KRW", clock.instant());
        complete(buyer, second).andExpect(status().isOk()).andExpect(jsonPath("$.data.accepted").value(true));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_cancels c JOIN payment_orders o ON o.id = c.payment_order_id WHERE o.payment_id = ? AND c.kind = 'AUTO_REJECT'",
                Integer.class, first[0])).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM emoticon_purchases WHERE user_id = ? AND pack_id = ? AND status = 'PAID'", Integer.class, buyer, pack)).isEqualTo(1);
    }

    @Test
    void 결제창에_있는_동안_팩이_무료가_되어_받았으면_도메인_거절로_자동_취소된다() throws Exception {
        long pack = paidPack();
        long buyer = buyer();
        String[] s = start(buyer, pack);
        jdbc.update("UPDATE emoticon_packs SET price = 0 WHERE id = ?", pack);
        mvc.perform(as(buyer, post("/api/emoticons/packs/" + pack + "/acquire"))).andExpect(status().isOk());
        fakePg.pay(s[0], PRICE, "KRW", clock.instant());
        complete(buyer, s).andExpect(status().isOk()).andExpect(jsonPath("$.data.accepted").value(false)).andExpect(jsonPath("$.data.anomaly").value("DOMAIN_REJECTED"))
                .andExpect(jsonPath("$.data.cancel.amount").value(PRICE));
        assertThat(purchaseStatus(Long.parseLong(s[1]))).isEqualTo("CLOSED");
        assertThat(jdbc.queryForObject("SELECT kind FROM emoticon_acquisitions WHERE user_id = ? AND pack_id = ?", String.class, buyer, pack)).isEqualTo("FREE");
    }

    @Test
    void PG_금액이_다르면_금액_불일치로_자동_취소하고_보유를_만들지_않는다() throws Exception {
        long pack = paidPack();
        long buyer = buyer();
        String[] s = start(buyer, pack);
        fakePg.pay(s[0], PRICE - 1000, "KRW", clock.instant());
        complete(buyer, s).andExpect(status().isOk()).andExpect(jsonPath("$.data.accepted").value(false)).andExpect(jsonPath("$.data.anomaly").value("AMOUNT_MISMATCH"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM emoticon_acquisitions WHERE user_id = ? AND pack_id = ?", Integer.class, buyer, pack)).isZero();
    }

    @Test
    void 유료_판매를_끄면_새_결제_준비는_막고_이미_결제창에_들어간_주문의_완료는_받는다() throws Exception {
        long pack = paidPack();
        long buyer = buyer();
        String[] s = start(buyer, pack);
        jdbc.update("UPDATE emoticon_settings SET paid_sales_enabled = false WHERE id = 1");
        prepare(buyer(), pack).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("EMOTICON_PAID_SALES_CLOSED"));
        fakePg.pay(s[0], PRICE, "KRW", clock.instant());
        complete(buyer, s).andExpect(status().isOk()).andExpect(jsonPath("$.data.accepted").value(true));
    }

    /** 사용자 결정 2026-11-07 — 돈을 받는 기능은 운영 주체가 개인사업자 이상 + 통신판매업 신고번호일 때만(방어선: 스위치가 켜져 있어도) */
    @Test
    void 운영_주체가_개인_단계거나_신고번호가_없으면_스위치가_켜져_있어도_결제_준비는_409_공개_규칙은_꺼짐_이미_결제창의_주문은_받는다() throws Exception {
        long pack = paidPack();
        long buyer = buyer();
        String[] s = start(buyer, pack);
        mvc.perform(get("/api/emoticons/rules")).andExpect(status().isOk()).andExpect(jsonPath("$.data.paidSalesEnabled").value(true));

        SiteOperatorFixture.clear(jdbc); // 개인 단계(스위치는 켜진 채 — 옮기기 전 데이터 등)
        prepare(buyer(), pack).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("EMOTICON_PAID_SALES_CLOSED"));
        mvc.perform(get("/api/emoticons/rules")).andExpect(status().isOk()).andExpect(jsonPath("$.data.paidSalesEnabled").value(false));
        fakePg.pay(s[0], PRICE, "KRW", clock.instant());
        complete(buyer, s).andExpect(status().isOk()).andExpect(jsonPath("$.data.accepted").value(true));

        // 개인사업자여도 통신판매업 신고번호가 없으면 같은 409
        SiteOperatorFixture.fillBusiness(jdbc);
        jdbc.update("UPDATE site_operator SET mail_order_number = '  ' WHERE id = 1");
        prepare(buyer(), pack).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("EMOTICON_PAID_SALES_CLOSED"));

        // 개인사업자 + 신고번호면 다시 받는다
        SiteOperatorFixture.fillBusiness(jdbc);
        prepare(buyer(), pack).andExpect(status().isOk());
    }

    @Test
    void 같은_결제를_완료_API_웹훅이_동시에_여러_번_확인해도_보유와_받은_수는_한_번() throws Exception {
        long pack = paidPack();
        long buyer = buyer();
        int count = acquireCount(pack);
        String[] s = start(buyer, pack);
        fakePg.pay(s[0], PRICE, "KRW", clock.instant());
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<PaymentConfirmService.Outcome>> futures = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                ConfirmedVia via = i % 2 == 0 ? ConfirmedVia.WEBHOOK : ConfirmedVia.COMPLETE_API;
                Callable<PaymentConfirmService.Outcome> job = () -> {
                    go.await();
                    return confirm.confirm(s[0], via).outcome();
                };
                futures.add(pool.submit(job));
            }
            go.countDown();
            List<PaymentConfirmService.Outcome> outcomes = new ArrayList<>();
            for (Future<PaymentConfirmService.Outcome> f : futures) {
                outcomes.add(f.get(60, TimeUnit.SECONDS));
            }
            assertThat(outcomes).containsOnlyOnce(PaymentConfirmService.Outcome.ACCEPTED).doesNotContain(PaymentConfirmService.Outcome.REJECTED);
        } finally {
            pool.shutdownNow();
        }
        assertThat(acquireCount(pack)).isEqualTo(count + 1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM emoticon_acquisitions WHERE user_id = ? AND pack_id = ?", Integer.class, buyer, pack)).isEqualTo(1);
        assertThat(purchaseStatus(Long.parseLong(s[1]))).isEqualTo("PAID");
    }

    // ── 셀프 환불 ───────────────────────────────────────────────────────────

    @Test
    void 셀프_환불은_멱등_키가_있어야_하고_보유를_빼고_PG_취소를_넣으며_같은_키는_처음_응답() throws Exception {
        long pack = paidPack();
        long buyer = buyer();
        long purchase = buy(buyer, pack);
        mvc.perform(as(buyer, post("/api/me/emoticon-purchases/" + purchase + "/refund"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REQUIRED"));
        refund(buyer(), purchase, "key-other-" + purchase).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("EMOTICON_PURCHASE_NOT_FOUND"));
        String key = "refund-" + purchase;
        String first = refund(buyer, purchase, key).andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(Long.toString(purchase)))
                .andExpect(jsonPath("$.data.status").value("REFUNDED")).andExpect(jsonPath("$.data.canceledAmount").value(PRICE))
                .andExpect(jsonPath("$.data.refundedAt").isNotEmpty()).andReturn().getResponse().getContentAsString();
        refund(buyer, purchase, key).andExpect(status().isOk()).andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.data.refundedAt").value((String) JsonPath.read(first, "$.data.refundedAt")));
        refund(buyer, purchase, "refund-again-" + purchase).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("EMOTICON_ALREADY_REFUNDED"));
        assertThat(purchaseStatus(purchase)).isEqualTo("REFUNDED");
        assertThat(jdbc.queryForObject("SELECT refund_kind FROM emoticon_purchases WHERE id = ?", String.class, purchase)).isEqualTo("SELF");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM emoticon_acquisitions WHERE user_id = ? AND pack_id = ? AND removed_at IS NULL", Integer.class, buyer, pack))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_cancels WHERE request_key = ? AND kind = 'REFUND' AND amount = ?", Integer.class,
                "EMOTICON_REFUND:" + purchase, PRICE)).isEqualTo(1);
        // 가짜 PG 취소는 커밋 뒤 그 자리에서 시도된다 — 끝나면 처리기가 완료 시각을 찍는다
        Instant until = Instant.now().plusSeconds(10);
        while (Instant.now().isBefore(until) && jdbc.queryForObject("SELECT refund_completed_at FROM emoticon_purchases WHERE id = ?", Timestamp.class, purchase) == null) {
            Thread.onSpinWait();
        }
        assertThat(jdbc.queryForObject("SELECT refund_completed_at FROM emoticon_purchases WHERE id = ?", Timestamp.class, purchase)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT o.status FROM payment_orders o JOIN emoticon_purchases p ON p.payment_order_id = o.id WHERE p.id = ?", String.class, purchase))
                .isEqualTo("CANCELED");
        mvc.perform(as(buyer, get("/api/me/emoticons"))).andExpect(jsonPath("$.data.packs.length()").value(0));
        mvc.perform(as(buyer, get("/api/me/emoticon-purchases"))).andExpect(jsonPath("$.data.items[0].status").value("REFUNDED"))
                .andExpect(jsonPath("$.data.items[0].refundable").value(false)).andExpect(jsonPath("$.data.items[0].refundedAt").isNotEmpty());
        // 환불한 유료 팩은 받기로 가질 수 없고, 다시 살 수 있다(보유 줄은 그대로 하나 · 받은 수는 사람마다 한 번)
        mvc.perform(as(buyer, post("/api/emoticons/packs/" + pack + "/acquire"))).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("EMOTICON_PACK_PAID"));
        int count = acquireCount(pack);
        long again = buy(buyer, pack);
        assertThat(jdbc.queryForObject("SELECT purchase_id FROM emoticon_acquisitions WHERE user_id = ? AND pack_id = ? AND removed_at IS NULL", Long.class, buyer, pack))
                .isEqualTo(again);
        assertThat(acquireCount(pack)).isEqualTo(count);
        // 같은 키를 다른 구매에 쓰면 409(환불 신청 요청 제한 1분 3번 — 이 시험이 센 줄을 비운다. 넷째 요청은 429 인 것도 본다)
        refund(buyer, again, "fourth-" + again).andExpect(status().isTooManyRequests());
        jdbc.update("DELETE FROM request_rate_counters WHERE user_id = ?", buyer);
        refund(buyer, again, key).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void 한_번이라도_쓴_팩과_환불_기간이_지난_팩은_셀프_환불할_수_없다() throws Exception {
        long buyer = buyer();
        long used = buy(buyer, paidPack());
        long usedPack = jdbc.queryForObject("SELECT pack_id FROM emoticon_purchases WHERE id = ?", Long.class, used);
        mvc.perform(as(buyer, post("/api/posts").contentType(MediaType.APPLICATION_JSON).content("{\"boardSlug\":\"" + slug + "\",\"title\":\"산 팩을 써요\","
                + "\"contentFormat\":\"TEXT\",\"content\":" + json("좋아요 " + token(emoticons(usedPack).get(0))) + "}"))).andExpect(status().isCreated());
        refund(buyer, used, "used-" + used).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("EMOTICON_REFUND_NOT_ALLOWED"))
                .andExpect(jsonPath("$.error.blockedBy").value("USED"));
        long late = buy(buyer, paidPack());
        clock.set(clock.instant().plus(Duration.ofDays(7)));
        refund(buyer, late, "late-" + late).andExpect(status().isConflict()).andExpect(jsonPath("$.error.blockedBy").value("EXPIRED"));
        mvc.perform(as(buyer, get("/api/me/emoticon-purchases"))).andExpect(jsonPath("$.data.items[?(@.id == '" + late + "')].refundable").value(false));
    }

    // ── 사용함 ──────────────────────────────────────────────────────────────

    @Test
    void 사용함은_글_쓰기_고치기_댓글_저장이_성공한_트랜잭션에서만_처음_한_번_찍힌다() throws Exception {
        long buyer = buyer();
        long postPack = paidPack();
        long commentPack = paidPack();
        long groupPack = paidPack();
        long forPost = buy(buyer, postPack);
        long forComment = buy(buyer, commentPack);
        long forGroup = buy(buyer, groupPack);
        // 저장이 실패하면(제목 없음 400) 찍히지 않는다 — 고르기 창에서 넣기만 한 것과 같다
        mvc.perform(as(buyer, post("/api/posts").contentType(MediaType.APPLICATION_JSON).content("{\"boardSlug\":\"" + slug + "\",\"title\":\"\",\"contentFormat\":\"TEXT\","
                + "\"content\":" + json("글 " + token(emoticons(postPack).get(0))) + "}"))).andExpect(status().isBadRequest());
        assertThat(usedAt(forPost)).isNull();
        String created = mvc.perform(as(buyer, post("/api/posts").contentType(MediaType.APPLICATION_JSON).content("{\"boardSlug\":\"" + slug + "\",\"title\":\"산 팩 글\","
                + "\"contentFormat\":\"TEXT\",\"content\":" + json("글 " + token(emoticons(postPack).get(0))) + "}"))).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString();
        Timestamp firstUse = usedAt(forPost);
        assertThat(firstUse).isNotNull();
        assertThat(usedAt(forComment)).isNull();
        long postId = Long.parseLong(JsonPath.read(created, "$.data.id"));
        // 글 고치기 — 이미 찍힌 시각은 그대로, 새로 넣은 팩도 찍힌다
        clock.set(clock.instant().plus(Duration.ofMinutes(5)));
        mvc.perform(as(buyer, put("/api/posts/" + postId).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"고친 글\",\"contentFormat\":\"TEXT\",\"content\":"
                + json("고침 " + token(emoticons(postPack).get(1)) + token(emoticons(commentPack).get(0))) + "}"))).andExpect(status().isOk());
        assertThat(usedAt(forPost)).isEqualTo(firstUse);
        assertThat(usedAt(forComment)).isNotNull();
        // 댓글 쓰기(다른 회원 글) — 댓글 저장 트랜잭션에서
        long other = member();
        long otherPost = ids.nextId();
        boardFixture.post(otherPost, board, other, "남의 글", "본문이에요");
        mvc.perform(as(buyer, post("/api/posts/" + otherPost + "/comments").contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentFormat\":\"TEXT\",\"content\":" + json("댓글 " + token(emoticons(groupPack).get(0))) + "}"))).andExpect(status().isCreated());
        assertThat(usedAt(forGroup)).isNotNull();
    }

    @Test
    void 소모임_글_저장도_사용함을_찍는다() throws Exception {
        long buyer = buyer();
        long pack = paidPack();
        long purchase = buy(buyer, pack);
        long groupId = ids.nextId();
        jdbc.update("INSERT INTO groups (id, name, name_key, description, board_id, join_type, capacity, status, leader_id, creator_id, member_count, requested_at, opened_at) "
                + "VALUES (?, ?, ?, '시험', ?, 'OPEN', 50, 'ACTIVE', ?, ?, 1, now(), now())", groupId, "q" + Long.toString(groupId, 36), "q" + Long.toString(groupId, 36), board,
                buyer, buyer);
        jdbc.update("INSERT INTO group_members (group_id, user_id, role, status, joined_at) VALUES (?, ?, 'LEADER', 'ACTIVE', now())", groupId, buyer);
        mvc.perform(as(buyer, post("/api/groups/" + groupId + "/posts").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"소모임 글\",\"contentFormat\":\"TEXT\","
                + "\"content\":" + json("모임 " + token(emoticons(pack).get(0))) + "}"))).andExpect(status().is2xxSuccessful());
        assertThat(usedAt(purchase)).isNotNull();
    }

    @Test
    void 스튜디오_판매_요약은_이번_달_판매와_환불의_실제_값() throws Exception {
        long pack = paidPack();
        long creator = jdbc.queryForObject("SELECT creator_id FROM emoticon_packs WHERE id = ?", Long.class, pack);
        long a = buyer();
        long b = buyer();
        buy(a, pack);
        long refunded = buy(b, pack);
        refund(b, refunded, "studio-" + refunded).andExpect(status().isOk());
        mvc.perform(as(creator, get("/api/me/emoticon-studio"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.sales.salesCount").value(2))
                .andExpect(jsonPath("$.data.sales.salesAmount").value(2 * PRICE)).andExpect(jsonPath("$.data.sales.refundAmount").value(PRICE))
                .andExpect(jsonPath("$.data.sales.baseAmount").value(2272)).andExpect(jsonPath("$.data.sales.vatAmount").value(228))
                .andExpect(jsonPath("$.data.sales.feeAmount").value(682)).andExpect(jsonPath("$.data.sales.expectedPayout").value(1590))
                .andExpect(jsonPath("$.data.packs[0].salesTotal").value(PRICE));
    }

    /** S4 — 작가 정산 내역(user-web {@code EmoticonSettlementList}): 이번 달 예정 줄 · 최소 지급액 미만이면 넘김(지급일 없음) · 판매가 없으면 빈 목록 */
    @Test
    void 스튜디오_정산_내역은_이번_달_예정_줄을_목업_모양으로_준다() throws Exception {
        long pack = paidPack();
        long creator = jdbc.queryForObject("SELECT creator_id FROM emoticon_packs WHERE id = ?", Long.class, pack);
        buy(buyer(), pack);
        long b = buyer();
        long refunded = buy(b, pack);
        refund(b, refunded, "settle-" + refunded).andExpect(status().isOk());
        String month = java.time.YearMonth.now(java.time.ZoneId.of("Asia/Seoul")).toString();
        mvc.perform(as(creator, get("/api/me/emoticon-studio/settlements"))).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.items.length()").value(1)).andExpect(jsonPath("$.data.items[0].id").value(month.replace("-", "") + "-" + creator))
                .andExpect(jsonPath("$.data.items[0].month").value(month)).andExpect(jsonPath("$.data.items[0].status").value("ESTIMATED"))
                .andExpect(jsonPath("$.data.items[0].salesCount").value(2)).andExpect(jsonPath("$.data.items[0].salesAmount").value(2 * PRICE))
                .andExpect(jsonPath("$.data.items[0].refundAmount").value(PRICE)).andExpect(jsonPath("$.data.items[0].vatAmount").value(228))
                .andExpect(jsonPath("$.data.items[0].baseAmount").value(2272)).andExpect(jsonPath("$.data.items[0].feeAmount").value(682))
                .andExpect(jsonPath("$.data.items[0].shareAmount").value(1590)).andExpect(jsonPath("$.data.items[0].withholdingAmount").value(0))
                .andExpect(jsonPath("$.data.items[0].payoutAmount").value(0)).andExpect(jsonPath("$.data.items[0].carriedIn").value(0))
                .andExpect(jsonPath("$.data.items[0].carriedOver").value(true)).andExpect(jsonPath("$.data.items[0].carriedOut").value(1590))
                .andExpect(jsonPath("$.data.items[0].payDate").doesNotExist()).andExpect(jsonPath("$.data.items[0].holdReason").doesNotExist());
        mvc.perform(as(buyer(), get("/api/me/emoticon-studio/settlements"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(0));
    }
}
