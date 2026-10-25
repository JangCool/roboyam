package com.community.account.service;

import com.community.account.dto.IssuedEmailChange;
import com.community.account.dto.request.EmailChangeRequest;
import com.community.account.dto.request.PasswordChangeRequest;
import com.community.account.dto.response.AccountResponse;
import com.community.account.exception.AccountEmailChangeNotAllowedException;
import com.community.account.exception.AccountPasswordNotSetException;
import com.community.auth.config.AccountProperties;
import com.community.auth.exception.AuthSamePasswordException;
import com.community.auth.repository.master.AuthTokenRepositoryQDSL;
import com.community.auth.service.AccountMailSender;
import com.community.auth.service.EmailMasks;
import com.community.notificationtemplate.catalog.RequiredMail;
import com.community.site.dto.SiteValues;
import com.community.site.service.SiteSettingsService;
import com.community.sociallogin.dto.response.MeSocialResponse;
import com.community.sociallogin.service.MeSocialService;
import com.community.user.entity.UserEntity;
import com.community.user.exception.UserEmailTakenException;
import com.community.user.repository.master.UserRepository;
import com.community.user.service.UserPolicy;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 내 계정 — 조회·비밀번호 변경·이메일 변경 요청 (docs/features/member-account-contract.md, community {@code api-account.md}). 대상은 늘 로그인한 본인이다.
 * <b>정지(SUSPENDED·BANNED) 회원도 쓸 수 있다</b>(개인 설정 — 계약 「결정 2차」). 탈퇴·지운 회원의 낡은 세션은 401 {@code UNAUTHENTICATED}.
 *
 * <ul>
 *   <li><b>비밀번호 변경 검사 순서</b>: 비밀번호 없는 계정 {@code 409 PASSWORD_NOT_SET} → 입력({@code fields.currentPassword}·{@code fields.newPassword} 한꺼번에,
 *       {@code 400 VALIDATION}) → 잠금 {@code 429 LOGIN_LOCKED} → 현재 비밀번호 {@code 401 CURRENT_PASSWORD_WRONG}(로그인 실패와 같은 횟수) →
 *       현재와 같음 {@code 400 SAME_PASSWORD} → 교체 + 이 세션만 남기고 다른 세션 종료 + 실패 횟수 초기화(세션 ID 교체는 컨트롤러).</li>
 *   <li><b>이메일 변경 검사 순서</b>: 비밀번호 없는 계정 {@code 409 EMAIL_CHANGE_NOT_ALLOWED} → 입력({@code fields.newEmail}·{@code fields.password}) →
 *       잠금 {@code 429 LOGIN_LOCKED} → 현재 비밀번호 {@code 401 CURRENT_PASSWORD_WRONG} → 지금 주소와 같거나 이미 쓰는 주소 {@code 409 EMAIL_TAKEN} →
 *       60초 제한 {@code 429 RESEND_COOLDOWN} → 새 링크(이전 변경 링크 무효) → 메일 두 통(새 주소에 링크 24시간, 지금 주소에 알림 — 메일 풀이 보낸다).</li>
 * </ul>
 * 이 클래스는 트랜잭션을 열지 않는다(느린 해시가 DB 연결을 붙잡지 않게) — DB 쓰기는 {@link AccountCredentialWriter}. 비밀번호·토큰·이메일을 로그에 남기지 않는다.
 */
@Service
public class MeAccountService {

    private static final Logger log = LoggerFactory.getLogger(MeAccountService.class);

    private final UserRepository users;
    private final AuthTokenRepositoryQDSL tokenQueries;
    private final MeSocialService meSocial;
    private final CurrentPasswordVerifier verifier;
    private final AccountCredentialWriter writer;
    private final PasswordEncoder encoder;
    private final AccountMailSender mailSender;
    private final SiteSettingsService siteSettings;
    private final AccountProperties props;
    private final Messages messages;

    public MeAccountService(UserRepository users, AuthTokenRepositoryQDSL tokenQueries, MeSocialService meSocial, CurrentPasswordVerifier verifier,
                            AccountCredentialWriter writer, PasswordEncoder encoder, AccountMailSender mailSender, SiteSettingsService siteSettings,
                            AccountProperties props, Messages messages) {
        this.users = users;
        this.tokenQueries = tokenQueries;
        this.meSocial = meSocial;
        this.verifier = verifier;
        this.writer = writer;
        this.encoder = encoder;
        this.mailSender = mailSender;
        this.siteSettings = siteSettings;
        this.props = props;
        this.messages = messages;
    }

    /** 내 계정 — {@code hasPassword}·{@code providers} 는 {@code GET /api/me/social} 과 같은 줄이다 */
    public AccountResponse view(long userId) {
        UserEntity user = member(userId);
        MeSocialResponse social = meSocial.view(userId);
        String pending = user.hasPassword() ? tokenQueries.findPendingNewEmail(userId, Instant.now()).map(EmailMasks::mask).orElse(null) : null;
        return new AccountResponse(user.getEmail(), user.isEmailVerified(), social.hasPassword(), pending, social.providers());
    }

    /**
     * 비밀번호를 바꾼다.
     *
     * @param keepSessionId 지금 요청의 세션 ID — 이것만 남기고 그 회원의 다른 세션을 끝낸다
     */
    public void changePassword(long userId, String keepSessionId, PasswordChangeRequest body) {
        UserEntity user = member(userId);
        if (!user.hasPassword()) {
            throw new AccountPasswordNotSetException();
        }
        String current = body == null ? null : body.currentPassword();
        String next = body == null ? null : body.newPassword();
        Map<String, MessageRef> errors = new LinkedHashMap<>();
        if (current == null || current.isEmpty()) {
            errors.put("currentPassword", new MessageRef("user.currentPassword.required"));
        }
        newPasswordViolation(next).ifPresent(message -> errors.put("newPassword", message));
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        verifier.verify(user, current);
        if (current.equals(next)) {
            throw new AuthSamePasswordException();
        }
        writer.changePassword(userId, user.getPasswordHash(), encoder.encode(next), keepSessionId);
    }

    /** 이메일 변경 링크를 새 주소로 보낸다(지금 주소에는 알림) */
    public void requestEmailChange(long userId, EmailChangeRequest body) {
        UserEntity user = member(userId);
        if (!user.hasPassword() || user.getEmail() == null) {
            throw new AccountEmailChangeNotAllowedException();
        }
        String newEmail = UserPolicy.normalizeEmail(body == null ? null : body.newEmail());
        String password = body == null ? null : body.password();
        Map<String, MessageRef> errors = new LinkedHashMap<>();
        UserPolicy.emailViolation(newEmail).ifPresent(message -> errors.put("newEmail", message));
        if (password == null || password.isEmpty()) {
            errors.put("password", new MessageRef("user.currentPassword.required"));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        verifier.verify(user, password);
        if (newEmail.equals(user.getEmail()) || users.existsByEmail(newEmail)) {
            throw new UserEmailTakenException();
        }
        IssuedEmailChange issued = writer.issueEmailChange(userId, newEmail);
        sendEmailChangeMails(issued, messages.locale());
    }

    private void sendEmailChangeMails(IssuedEmailChange issued, Locale locale) {
        String baseUrl = siteSettings.current().map(SiteValues::publicUrl).filter(url -> url != null && !url.isBlank()).orElse(null);
        if (baseUrl == null) {
            log.warn("공개 주소가 설정되지 않아 이메일 변경 메일을 보내지 못했다 — 설치 마법사나 설정 화면에서 공개 주소를 저장해 주세요 userId={}", issued.userId());
            return;
        }
        String name = issued.nickname() != null ? issued.nickname() : messages.get(new MessageRef("mail.verify.defaultName"), locale);
        mailSender.sendLater(RequiredMail.MEMBER_EMAIL_CHANGE, issued.newEmail(), locale, Map.of(
                "recipientName", name,
                "nickname", name,
                "changeLink", baseUrl + "/verify-email?token=" + URLEncoder.encode(issued.token(), StandardCharsets.UTF_8),
                "validHours", props.emailChangeTokenHours()), issued.userId());
        String masked = EmailMasks.mask(issued.newEmail());
        mailSender.sendLater(RequiredMail.MEMBER_EMAIL_CHANGE_NOTICE, issued.currentEmail(), locale, Map.of(
                "recipientName", name,
                "nickname", name,
                "newEmail", masked == null ? "" : masked), issued.userId());
    }

    /** 새 비밀번호 — 비었으면 「입력해 주세요」, 그 밖에는 가입 규칙(72바이트 포함) */
    private static Optional<MessageRef> newPasswordViolation(String password) {
        if (password == null || password.isEmpty()) {
            return Optional.of(new MessageRef("user.password.required"));
        }
        return UserPolicy.passwordViolation(password);
    }

    /** 낡은 세션(회원이 없거나 탈퇴)은 401 */
    private UserEntity member(long userId) {
        return users.findById(userId).filter(u -> !u.isGone()).orElseThrow(() -> new CommonException(ErrorCode.UNAUTHENTICATED));
    }
}
