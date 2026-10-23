package com.community.auth.service;

import com.community.auth.config.AuthProperties;
import com.community.auth.dto.IssuedVerification;
import com.community.mail.service.MailDispatcher;
import com.community.notificationtemplate.catalog.MessageCondition;
import com.community.notificationtemplate.catalog.RequiredMail;
import com.community.notificationtemplate.engine.MessageRenderRequest;
import com.community.notificationtemplate.engine.NotificationMessageRenderer;
import com.community.notificationtemplate.engine.RenderedEmail;
import com.community.site.dto.SiteValues;
import com.community.site.service.SiteSettingsService;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 이메일 인증 메일 다시 보내기. 링크를 만드는 DB 일은 {@link VerificationTokenService}(짧은 트랜잭션)가 하고, <b>메일 발송은 그 트랜잭션이 끝난 뒤</b>
 * 이 클래스가 한다 — 메일 서버가 느려도 DB 트랜잭션을 붙잡지 않는다(접속 5초·읽기 10초 제한).
 *
 * <ul>
 *   <li>링크는 {@code {공개 주소}/verify-email?token=…} 이다. 공개 주소는 사이트 설정({@code site_settings})의 값이고, <b>보낼 때마다</b> 읽는다.</li>
 *   <li>메일 서버가 설정돼 있지 않거나 공개 주소가 없거나 발송에 실패하면 <b>보내지 못한 채 로그만 남기고 정상 종료</b>한다(요청은 202). 새 링크는 이미 만들어졌고
 *       60초 제한도 시작됐다.</li>
 *   <li>제목·본문은 알림 문구 엔진({@link NotificationMessageRenderer}, 필수 안내 메일 {@link RequiredMail#MEMBER_EMAIL_VERIFY})이 그린다 — 운영자가 고른 문구·메일 템플릿,
 *       없으면 기본 문구. 비밀번호 없이 소셜로만 가입한 회원이면 조건 {@code signupMethod=SOCIAL}, 아니면 {@code EMAIL}.</li>
 * </ul>
 * 메일 언어는 요청한 회원의 언어(요청의 {@code Accept-Language}, ko|en)다 — 발송은 요청 스레드 안에서 이뤄진다.
 * 이메일 주소·토큰을 로그에 남기지 않는다(예외도 클래스 이름만).
 */
@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

    private final VerificationTokenService tokenService;
    private final SiteSettingsService siteSettings;
    private final MailDispatcher mail;
    private final AuthProperties props;
    private final Messages messages;
    private final NotificationMessageRenderer renderer;

    public EmailVerificationService(VerificationTokenService tokenService, SiteSettingsService siteSettings, MailDispatcher mail,
                                    AuthProperties props, Messages messages, NotificationMessageRenderer renderer) {
        this.tokenService = tokenService;
        this.siteSettings = siteSettings;
        this.mail = mail;
        this.props = props;
        this.messages = messages;
        this.renderer = renderer;
    }

    /**
     * 로그인한 회원에게 인증 메일을 다시 보낸다.
     *
     * @throws com.community.auth.exception.AuthAlreadyVerifiedException 이미 인증됐을 때(409)
     * @throws com.community.auth.exception.AuthResendCooldownException  60초가 안 지났을 때(429)
     * @throws com.community.zconfiguration.exception.CommonException     낡은 세션일 때(401)
     */
    public void resend(long userId) {
        IssuedVerification issued = tokenService.issue(userId);

        Optional<SiteValues> site = siteSettings.current();
        String baseUrl = site.map(SiteValues::publicUrl).orElse(null);
        if (baseUrl == null || baseUrl.isBlank()) {
            log.warn("공개 주소가 설정되지 않아 인증 메일을 보내지 못했다 — 설치 마법사나 설정 화면에서 공개 주소를 저장해 주세요 userId={}", userId);
            return;
        }

        String link = baseUrl + "/verify-email?token=" + URLEncoder.encode(issued.token(), StandardCharsets.UTF_8);
        try {
            Locale locale = messages.locale();
            String nickname = issued.nickname() != null ? issued.nickname() : messages.get(new MessageRef("mail.verify.defaultName"), locale);
            RenderedEmail content = renderer.renderEmail(MessageRenderRequest.requiredMail(RequiredMail.MEMBER_EMAIL_VERIFY)
                    .locale(locale)
                    .variable("recipientName", nickname)
                    .variable("nickname", nickname)
                    .variable("verifyLink", link)
                    .variable("validHours", props.verifyTokenHours())
                    .condition(MessageCondition.SIGNUP_METHOD, issued.social() ? MessageCondition.SOCIAL : MessageCondition.EMAIL)
                    .build());
            if (mail.sendTagged(issued.email(), content.subject(), content.untaggedSubject(), content.html())) {
                log.info("인증 메일을 보냈다 userId={} 문구={}", userId, content.messageId());
            }
        } catch (RuntimeException e) {
            // 사용자는 60초 뒤 다시 보내기로 재시도한다. 예외 문구에 수신자 주소·서버 응답이 실릴 수 있어 클래스 이름만 남긴다
            log.error("인증 메일 발송에 실패했다 userId={} 원인={}", userId, e.getClass().getSimpleName());
        }
    }
}
