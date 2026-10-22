package com.community.auth.service;

import com.community.auth.config.AuthProperties;
import com.community.auth.dto.IssuedVerification;
import com.community.mail.service.MailDispatcher;
import com.community.site.dto.SiteValues;
import com.community.site.service.SiteSettingsService;
import com.community.zconfiguration.message.Messages;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
 *   <li>인증 링크를 받아 처리하는 일({@code POST /api/auth/email/verify}, {@code /verify-email} 화면)은 이번 범위가 아니다.</li>
 * </ul>
 * 메일 본문은 요청한 회원의 언어(요청의 {@code Accept-Language}, ko|en)로 만든다 — 발송은 요청 스레드 안에서 이뤄진다.
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

    public EmailVerificationService(VerificationTokenService tokenService, SiteSettingsService siteSettings, MailDispatcher mail,
                                    AuthProperties props, Messages messages) {
        this.tokenService = tokenService;
        this.siteSettings = siteSettings;
        this.mail = mail;
        this.props = props;
        this.messages = messages;
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
        AuthMailTemplate.Mail content = AuthMailTemplate.verification(messages, messages.locale(), site.map(SiteValues::name).orElse(null), issued.nickname(), link,
                props.verifyTokenHours());
        try {
            if (mail.sendTagged(issued.email(), content.subject(), content.untaggedSubject(), content.html())) {
                log.info("인증 메일을 보냈다 userId={}", userId);
            }
        } catch (RuntimeException e) {
            // 사용자는 60초 뒤 다시 보내기로 재시도한다. 예외 문구에 수신자 주소·서버 응답이 실릴 수 있어 클래스 이름만 남긴다
            log.error("인증 메일 발송에 실패했다 userId={} 원인={}", userId, e.getClass().getSimpleName());
        }
    }
}
