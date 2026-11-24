package com.community.operator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.adminauth.service.AdminActorService;
import com.community.auth.service.AccountLinkMails;
import com.community.commerce.Commerce04AdminTestBase;
import com.community.mail.outbox.dto.OutboxDelivery;
import com.community.mail.outbox.service.RequiredMailOutboxSender;
import com.community.mail.service.MailDispatcher;
import com.community.membernotification.repository.master.MemberNotificationMailQueries;
import com.community.notificationtemplate.engine.NotificationMessageRenderer;
import com.community.operator.dto.request.OperatorMemberCreateRequest;
import com.community.operator.exception.OperatorMemberAccountException;
import com.community.operator.service.OperatorMemberCreateService;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.crypto.Digests;
import com.community.zconfiguration.exception.CommonException;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 운영자 회원 계정 「새 계정 만들기」(docs/features/operator-member-account-contract.md 「새 계정 만들기(2026-11-24)」)를 실제 PostgreSQL 에서 확인한다 —
 * HTTP(보안 필터·권한·CSRF) → 회원·프로필(비공개)·연결·감사·메일 대기열이 한 트랜잭션, 오류 판정 순서, 대기열 메일이 보낼 때 링크를 만드는 것.
 */
class OperatorMemberCreateAdminTest extends Commerce04AdminTestBase {

    private static final String IP = "127.0.0.1";
    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_\\-]+)");

    @Autowired
    private OperatorMemberCreateService creator;
    @Autowired
    private AdminActorService actors;
    @Autowired
    private PasswordEncoder encoder;
    @Autowired
    private MemberNotificationMailQueries recipients;
    @Autowired
    private NotificationMessageRenderer renderer;
    @Autowired
    private AccountLinkMails links;

    private FakeDispatcher dispatcher;
    private RequiredMailOutboxSender sender;

    /** 보낸 메일을 모으는 가짜 메일 서버 */
    static final class FakeDispatcher extends MailDispatcher {
        final List<String[]> sent = new ArrayList<>();

        FakeDispatcher() {
            super(null, null, null, null);
        }

        @Override
        public boolean sendTagged(String to, String taggedSubject, String untaggedSubject, String html) {
            sent.add(new String[] {to, untaggedSubject, html});
            return true;
        }
    }

    @BeforeEach
    void setUp() {
        dispatcher = new FakeDispatcher();
        sender = new RequiredMailOutboxSender(recipients, renderer, dispatcher, links);
        // 링크의 공개 주소 — 이미 있으면 그대로 둔다(다른 시험과 같은 DB)
        jdbc.update("INSERT INTO site_settings (id, site_name, public_url, updated_by) VALUES (1, '시험', 'http://localhost', ?) "
                + "ON CONFLICT (id) DO UPDATE SET public_url = COALESCE(site_settings.public_url, EXCLUDED.public_url)", operatorWith());
    }

    // ── 재료 ───────────────────────────────────────────────────────────────

    private AdminActor manager() {
        return actors.load(operatorWith(Permission.OPERATOR_READ, Permission.OPERATOR_MANAGE), IP).orElseThrow();
    }

    /** 겹치지 않는 닉네임 — 예약어(운영자)가 들어 있다(이 길은 예약어를 보지 않는다) */
    private String reservedNickname() {
        String base36 = Long.toString(ids.nextId(), 36);
        return "운영자" + base36.substring(base36.length() - 8);
    }

    private String freshEmail() {
        return "OPC-" + ids.nextId() + "@Test.Local";
    }

    private static String body(String email, String nickname, String mode, String password) {
        return "{\"email\":" + json(email) + ",\"nickname\":" + json(nickname) + ",\"passwordMode\":" + json(mode) + ",\"password\":" + json(password) + "}";
    }

    private static String json(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private String code(Runnable call) {
        Throwable thrown = catchThrowableOfType(RuntimeException.class, call::run);
        assertThat(thrown).isInstanceOf(CommonException.class);
        return ((CommonException) thrown).getErrorCode();
    }

    private Map<String, Object> outboxOf(long userId) {
        return jdbc.queryForMap("SELECT dedupe_key, mail_kind, variables::text AS variables, status FROM mail_outbox WHERE user_id = ?", userId);
    }

    private static String tokenIn(String html) {
        Matcher m = TOKEN.matcher(html);
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    // ── 만들기(HTTP) ───────────────────────────────────────────────────────

    @Test
    void DIRECT_로_만들면_201_이고_회원_비공개_프로필_연결_감사_인증메일_대기열이_한번에_남는다() throws Exception {
        long target = operatorWith(Permission.POST_MANAGE);
        long actorId = operatorWith(Permission.OPERATOR_READ, Permission.OPERATOR_MANAGE);
        Cookie cookie = login(actorId);
        String email = freshEmail();
        String nickname = reservedNickname();
        String password = "Direct1pass";

        String response = mvc.perform(withCsrf(post("/api/admin/operators/" + target + "/member-account"), cookie).content(body(email, nickname, "DIRECT", password)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value(email.toLowerCase()))
                .andExpect(jsonPath("$.data.nickname").value(nickname))
                .andExpect(jsonPath("$.data.passwordMode").value("DIRECT"))
                .andExpect(jsonPath("$.data.mailsSent[0]").value("EMAIL_VERIFICATION"))
                .andExpect(jsonPath("$.data.mailsSent.length()").value(1))
                .andExpect(jsonPath("$.data.account.candidate").doesNotExist())
                .andExpect(jsonPath("$.data.account.linked.nickname").value(nickname))
                .andExpect(jsonPath("$.data.account.linked.active").value(true))
                .andExpect(jsonPath("$.data.account.linked.linkedBy.operatorId").value(Long.toString(actorId)))
                .andExpect(jsonPath("$.data.account.powers.items[2].key").value("POST_MODERATE"))
                .andExpect(jsonPath("$.data.account.powers.items[2].granted").value(true))
                .andReturn().getResponse().getContentAsString();
        long userId = Long.parseLong(com.jayway.jsonpath.JsonPath.read(response, "$.data.userId"));
        assertThat(com.jayway.jsonpath.JsonPath.<String>read(response, "$.data.account.linked.userId")).isEqualTo(Long.toString(userId));

        // 회원 — 이메일 인증 전, 입력한 비밀번호, 공개 가입과 같은 약관 칸 · 프로필 비공개 · 연결
        Map<String, Object> user = jdbc.queryForMap("SELECT email, email_verified_at, password_hash, status, terms_version FROM users WHERE id = ?", userId);
        assertThat(user.get("email")).isEqualTo(email.toLowerCase());
        assertThat(user.get("email_verified_at")).isNull();
        assertThat(user.get("status")).isEqualTo("ACTIVE");
        assertThat(user.get("terms_version")).asString().startsWith("T");
        assertThat(encoder.matches(password, (String) user.get("password_hash"))).isTrue();
        assertThat(jdbc.queryForObject("SELECT profile_visibility FROM profiles WHERE user_id = ?", String.class, userId)).isEqualTo("PRIVATE");
        assertThat(jdbc.queryForObject("SELECT user_id FROM operator_member_accounts WHERE operator_id = ?", Long.class, target)).isEqualTo(userId);

        // 감사 — 새 행위 하나(연결 감사는 따로 남기지 않는다), 비밀번호 없음
        Map<String, Object> audit = auditLogs.where().action("OPERATOR_MEMBER_CREATE").target(Long.toString(target)).lastById().orElseThrow().asMap();
        assertThat(audit).containsEntry("actor_type", "OPERATOR").containsEntry("actor_id", actorId).containsEntry("target_type", "OPERATOR");
        assertThat((String) audit.get("detail")).contains("\"userId\":\"" + userId + "\"").contains("\"passwordMode\":\"DIRECT\"").doesNotContain(password);
        assertThat(auditLogs.where().action("OPERATOR_MEMBER_LINK").target(Long.toString(target)).lastById()).isEmpty();

        // 메일 대기열 — 인증 메일 한 통, 토큰 원문은 넣지 않는다
        Map<String, Object> outbox = outboxOf(userId);
        assertThat(outbox).containsEntry("mail_kind", "MEMBER_EMAIL_VERIFY").containsEntry("status", "PENDING")
                .containsEntry("dedupe_key", "OPERATOR_MEMBER_CREATE:" + userId);
        assertThat((String) outbox.get("variables")).isEqualTo("{}");

        // 보낼 때 링크를 만든다 — 인증 전 주소로 가고, 링크의 토큰은 해시로만 저장된다
        OutboxDelivery delivery = sender.send(userId, "MEMBER_EMAIL_VERIFY", Map.of());
        assertThat(delivery.outcome()).isEqualTo(OutboxDelivery.Outcome.SENT);
        assertThat(dispatcher.sent).hasSize(1);
        assertThat(dispatcher.sent.get(0)[0]).isEqualTo(email.toLowerCase());
        String html = dispatcher.sent.get(0)[2];
        assertThat(html).contains("/verify-email?token=").contains(nickname);
        String token = tokenIn(html);
        assertThat(jdbc.queryForObject("SELECT purpose FROM auth_tokens WHERE token_hash = ? AND user_id = ? AND consumed_at IS NULL", String.class,
                Digests.sha256Hex(token), userId)).isEqualTo("VERIFY_EMAIL");

        // 다시 보내면(재시도) 새 링크 — 앞의 링크는 무효
        sender.send(userId, "MEMBER_EMAIL_VERIFY", Map.of());
        assertThat(jdbc.queryForObject("SELECT consumed_at IS NOT NULL FROM auth_tokens WHERE token_hash = ?", Boolean.class, Digests.sha256Hex(token))).isTrue();
        // 이미 인증했으면 보내지 않는다
        jdbc.update("UPDATE users SET email_verified_at = now() WHERE id = ?", userId);
        assertThat(sender.send(userId, "MEMBER_EMAIL_VERIFY", Map.of()).reason()).isEqualTo("ALREADY_VERIFIED");
    }

    @Test
    void MAIL_로_만들면_비밀번호_정하기_메일_한_통이고_링크는_SETUP_PASSWORD_토큰이다() throws Exception {
        long target = operatorWith();
        Cookie cookie = login(operatorWith(Permission.OPERATOR_READ, Permission.OPERATOR_MANAGE));
        String email = freshEmail();

        String response = mvc.perform(withCsrf(post("/api/admin/operators/" + target + "/member-account"), cookie).content(body(email, reservedNickname(), "MAIL", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.passwordMode").value("MAIL"))
                .andExpect(jsonPath("$.data.mailsSent[0]").value("PASSWORD_SETUP"))
                .andExpect(jsonPath("$.data.mailsSent.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        long userId = Long.parseLong(com.jayway.jsonpath.JsonPath.read(response, "$.data.userId"));

        // 아무도 모르는 무작위 비밀번호(로그인 불가 — 링크에서 정한다) · 인증 전
        Map<String, Object> user = jdbc.queryForMap("SELECT email_verified_at, password_hash FROM users WHERE id = ?", userId);
        assertThat(user.get("email_verified_at")).isNull();
        assertThat((String) user.get("password_hash")).isNotBlank();
        assertThat((String) auditLogs.where().action("OPERATOR_MEMBER_CREATE").target(Long.toString(target)).lastById().orElseThrow().asMap().get("detail"))
                .contains("\"passwordMode\":\"MAIL\"");
        assertThat(outboxOf(userId)).containsEntry("mail_kind", "MEMBER_PASSWORD_SETUP");

        OutboxDelivery delivery = sender.send(userId, "MEMBER_PASSWORD_SETUP", Map.of());
        assertThat(delivery.outcome()).isEqualTo(OutboxDelivery.Outcome.SENT);
        String html = dispatcher.sent.get(0)[2];
        assertThat(html).contains("/password/reset?token=");
        assertThat(jdbc.queryForObject("SELECT purpose FROM auth_tokens WHERE token_hash = ? AND user_id = ?", String.class, Digests.sha256Hex(tokenIn(html)), userId))
                .isEqualTo("SETUP_PASSWORD");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_tokens WHERE user_id = ? AND purpose = 'VERIFY_EMAIL'", Long.class, userId)).isZero();
    }

    // ── 오류 ───────────────────────────────────────────────────────────────

    @Test
    void 형식_오류는_칸별_400_이고_운영자_존재보다_먼저다_권한이_없으면_403() throws Exception {
        Cookie cookie = login(operatorWith(Permission.OPERATOR_READ, Permission.OPERATOR_MANAGE));
        String missing = "/api/admin/operators/" + ids.nextId() + "/member-account";
        mvc.perform(withCsrf(post(missing), cookie).content(body("not-an-email", "a", "DIRECT", "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION"))
                .andExpect(jsonPath("$.error.fields.email").exists())
                .andExpect(jsonPath("$.error.fields.nickname").exists())
                .andExpect(jsonPath("$.error.fields.password").exists());
        // DIRECT 인데 비밀번호가 없다 · 방식 값이 틀렸다
        mvc.perform(withCsrf(post(missing), cookie).content(body(freshEmail(), reservedNickname(), "DIRECT", null)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.password").exists());
        mvc.perform(withCsrf(post(missing), cookie).content(body(freshEmail(), reservedNickname(), "SMS", null)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.passwordMode").exists())
                .andExpect(jsonPath("$.error.fields.password").doesNotExist());
        // 형식이 맞으면 그다음이 운영자 없음
        mvc.perform(withCsrf(post(missing), cookie).content(body(freshEmail(), reservedNickname(), "MAIL", null)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("OPERATOR_NOT_FOUND"));

        // operator.manage 가 없으면 403
        Cookie reader = login(operatorWith(Permission.OPERATOR_READ));
        mvc.perform(withCsrf(post("/api/admin/operators/" + operatorWith() + "/member-account"), reader).content(body(freshEmail(), reservedNickname(), "MAIL", null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void 루트_보호_다음_이미_이어짐_다음_이메일_다음_닉네임_차례이고_실패하면_아무것도_남지_않는다() throws Exception {
        long root = operatorFixture.rootOrCreate(id -> "opc-root-" + id + "@test.local", id -> "root" + (id % 1_000_000_000L), "x");
        long existing = newMember();
        String takenEmail = jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, existing);
        String takenNickname = jdbc.queryForObject("SELECT nickname FROM profiles WHERE user_id = ?", String.class, existing);
        AdminActor manager = manager();

        // ③ 루트는 루트 본인만 — 이메일이 겹쳐도 이 판정이 먼저
        assertThat(code(() -> creator.create(manager, Long.toString(root), new OperatorMemberCreateRequest(takenEmail, takenNickname, "MAIL", null))))
                .isEqualTo(OperatorMemberAccountException.ROOT_PROTECTED);

        // ⑤ 이메일 중복(대소문자 무시) — 닉네임 중복보다 먼저
        long target = operatorWith();
        assertThat(code(() -> creator.create(manager, Long.toString(target), new OperatorMemberCreateRequest(takenEmail.toUpperCase(), takenNickname, "MAIL", null))))
                .isEqualTo("EMAIL_TAKEN");
        // ⑥ 닉네임 중복(대소문자 무시)
        assertThat(code(() -> creator.create(manager, Long.toString(target), new OperatorMemberCreateRequest(freshEmail(), takenNickname.toUpperCase(), "MAIL", null))))
                .isEqualTo("NICKNAME_TAKEN");
        // 실패한 요청은 연결·감사를 남기지 않는다
        assertThat(jdbc.queryForObject("SELECT count(*) FROM operator_member_accounts WHERE operator_id = ?", Long.class, target)).isZero();
        assertThat(auditLogs.where().action("OPERATOR_MEMBER_CREATE").target(Long.toString(target)).lastById()).isEmpty();

        // ④ 이미 이어져 있으면 — 이메일이 겹쳐도 이 판정이 먼저
        creator.create(manager, Long.toString(target), new OperatorMemberCreateRequest(freshEmail(), reservedNickname(), "DIRECT", "Second2pass"));
        assertThat(code(() -> creator.create(manager, Long.toString(target), new OperatorMemberCreateRequest(takenEmail, takenNickname, "MAIL", null))))
                .isEqualTo(OperatorMemberAccountException.ALREADY_LINKED);
    }
}
