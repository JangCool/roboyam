package com.community.auth.service;

import com.community.auth.config.AccountProperties;
import com.community.auth.dto.PasswordForgotTarget;
import com.community.auth.dto.PasswordResetTarget;
import com.community.auth.exception.AuthSamePasswordException;
import com.community.notificationtemplate.catalog.RequiredMail;
import com.community.site.dto.SiteValues;
import com.community.site.service.SiteSettingsService;
import com.community.user.service.UserPolicy;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 비밀번호 찾기·재설정 (로그인 전 공개 API — docs/features/member-account-contract.md).
 *
 * <ul>
 *   <li><b>찾기</b>({@code POST /api/auth/password/forgot}): 이메일 형식만 보고(틀리면 {@code 400 VALIDATION fields.email}) <b>늘 202</b> 다. 요청 스레드는
 *       형식 검사 → 이메일 단위 60초 제한({@link PasswordForgotThrottle}, 메모리) → 일을 메일 풀({@link AccountMailSender})에 넘기기만 하고 끝난다 —
 *       <b>계정을 조회하지도, 토큰을 만들지도, 메일 서버에 접속하지도 않으므로</b> 응답 내용·응답 시간이 계정 유무와 상관없이 같다. 계정 조회·링크 발급·발송은 풀의 작업이 한다:
 *       비밀번호 있는 회원 → 재설정 링크({@code {공개 주소}/password/reset?token=}, 60분), 소셜 전용 → 안내({@code {공개 주소}/password/social?provider=}),
 *       없는 이메일·탈퇴 → 아무것도 보내지 않는다.</li>
 *   <li><b>확인</b>({@code GET …/reset/check}): {@code { valid: true }} · {@code 400 TOKEN_INVALID} · {@code 410 TOKEN_EXPIRED}.</li>
 *   <li><b>재설정</b>({@code POST …/reset}) 검사 순서: 새 비밀번호 규칙({@code 400 VALIDATION fields.newPassword}) → 링크({@code 400 TOKEN_INVALID}·{@code 410}) →
 *       지금 비밀번호와 같음({@code 400 SAME_PASSWORD}) → 교체 + 링크 사용 처리 + <b>그 회원의 모든 세션 종료</b> + 로그인 실패 횟수·잠금 초기화(메일을 받은 본인임이 확인됐다).</li>
 * </ul>
 * 이 클래스는 트랜잭션을 열지 않는다(느린 해시가 DB 연결을 붙잡지 않게). 정지 회원도 재설정할 수 있다. 이메일·토큰·비밀번호를 로그에 남기지 않는다.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final PasswordResetTokenService tokens;
    private final PasswordForgotThrottle throttle;
    private final AccountMailSender mailSender;
    private final SiteSettingsService siteSettings;
    private final PasswordEncoder encoder;
    private final AccountProperties props;
    private final Messages messages;

    public PasswordResetService(PasswordResetTokenService tokens, PasswordForgotThrottle throttle, AccountMailSender mailSender, SiteSettingsService siteSettings,
                                PasswordEncoder encoder, AccountProperties props, Messages messages) {
        this.tokens = tokens;
        this.throttle = throttle;
        this.mailSender = mailSender;
        this.siteSettings = siteSettings;
        this.encoder = encoder;
        this.props = props;
        this.messages = messages;
    }

    /**
     * 재설정 메일 요청 — 형식이 맞으면 늘 정상 종료(202)다.
     *
     * @throws ValidationException 이메일 형식이 틀렸을 때({@code fields.email})
     */
    public void forgot(String rawEmail) {
        String email = UserPolicy.normalizeEmail(rawEmail);
        UserPolicy.emailViolation(email).ifPresent(message -> {
            throw ValidationException.of("email", message);
        });
        if (!throttle.tryAcquire(email)) {
            return; // 60초 안의 반복 — 조용히 무시한다
        }
        Locale locale = messages.locale(); // 메일 언어는 요청한 사람의 언어 — 작업 스레드에는 요청이 없어 여기서 정해 넘긴다
        mailSender.submit("PASSWORD_FORGOT", () -> dispatchForgot(email, locale));
    }

    /** 풀의 작업 — 계정에 따라 보낼 메일을 정하고 보낸다 */
    private void dispatchForgot(String email, Locale locale) {
        String baseUrl = siteSettings.current().map(SiteValues::publicUrl).filter(url -> url != null && !url.isBlank()).orElse(null);
        if (baseUrl == null) {
            log.warn("공개 주소가 설정되지 않아 비밀번호 찾기 메일을 보내지 못했다 — 설치 마법사나 설정 화면에서 공개 주소를 저장해 주세요");
            return;
        }
        PasswordForgotTarget target = tokens.prepareForgot(email);
        switch (target.kind()) {
            case RESET -> mailSender.sendNow(RequiredMail.MEMBER_PASSWORD_RESET, target.email(), locale, Map.of(
                    "recipientName", nameOf(target.nickname(), locale),
                    "nickname", nameOf(target.nickname(), locale),
                    "resetLink", baseUrl + "/password/reset?token=" + URLEncoder.encode(target.token(), StandardCharsets.UTF_8),
                    "validMinutes", props.resetTokenMinutes()), target.userId());
            case SOCIAL -> mailSender.sendNow(RequiredMail.MEMBER_PASSWORD_SOCIAL, target.email(), locale, Map.of(
                    "recipientName", nameOf(target.nickname(), locale),
                    "nickname", nameOf(target.nickname(), locale),
                    "providerName", messages.get(new MessageRef(target.provider().displayNameKey()), locale),
                    "socialLink", baseUrl + "/password/social?provider=" + target.provider().id()), target.userId());
            case NONE -> {
                // 보내지 않는다 — 로그에도 계정 유무를 남기지 않는다
            }
        }
    }

    /**
     * 재설정 링크를 아직 쓸 수 있는가.
     *
     * @throws com.community.auth.exception.AuthTokenInvalidException 400
     * @throws com.community.auth.exception.AuthTokenExpiredException 410
     */
    public void check(String rawToken) {
        tokens.check(rawToken);
    }

    /**
     * 새 비밀번호로 바꾸고 그 회원의 모든 세션을 끝낸다.
     *
     * @throws ValidationException        새 비밀번호 규칙 위반({@code fields.newPassword})
     * @throws AuthSamePasswordException  지금 비밀번호와 같다(400)
     */
    public void reset(String rawToken, String newPassword) {
        SignupService.passwordViolation(newPassword).ifPresent(message -> {
            throw ValidationException.of("newPassword", message);
        });
        PasswordResetTarget target = tokens.check(rawToken);
        if (PasswordMatching.matches(encoder, newPassword, target.passwordHash())) {
            throw new AuthSamePasswordException();
        }
        tokens.applyReset(rawToken, encoder.encode(newPassword), target.passwordHash());
    }

    private String nameOf(String nickname, Locale locale) {
        return nickname != null ? nickname : messages.get(new MessageRef("mail.verify.defaultName"), locale);
    }
}
