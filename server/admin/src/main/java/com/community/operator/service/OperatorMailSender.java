package com.community.operator.service;

import com.community.adminauth.config.AdminAuthProperties;
import com.community.mail.service.MailDispatcher;
import com.community.operator.dto.OperatorCredentialsIssued;
import com.community.operator.dto.OperatorInvitationIssued;
import com.community.site.dto.SiteValues;
import com.community.site.service.SiteSettingsService;
import com.community.zconfiguration.message.Messages;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 운영자 메일(계정 안내·초대 링크)을 <b>업무 트랜잭션이 커밋된 뒤에</b> 보낸다 — 롤백되면 알림이 오지 않아 메일도 나가지 않고, 메일 서버가 느려도 DB 연결을 붙잡지 않는다
 * (접속 5초·읽기 10초 제한은 {@code MailSenderFactory}). 발송은 요청 스레드 안에서 이뤄지므로 요청의 {@code Accept-Language} 가 메일 언어다.
 *
 * <ul>
 *   <li>메일 서버가 설정돼 있지 않거나 발송에 실패하면 <b>보내지 못한 채 로그만 남기고 정상 종료</b>한다(요청 결과는 그대로) — 공개 사이트의 인증 메일과 같다.</li>
 *   <li>초대 링크는 {@code {관리자 앱 주소}/invitation?token=…} 이다. 관리자 앱 주소({@code ADMIN_URL})가 없으면 링크를 만들 수 없어 보내지 못한다.
 *       링크를 받아 처리하는 화면·API 는 이번 단계 밖이다(경로는 가정).</li>
 * </ul>
 * 이메일 주소·토큰·임시 비밀번호를 로그에 남기지 않는다(예외도 클래스 이름만).
 */
@Component
public class OperatorMailSender {

    private static final Logger log = LoggerFactory.getLogger(OperatorMailSender.class);

    /** 초대 링크가 가리키는 관리자 앱의 경로(가정 — 수락 화면은 후속 단계) */
    static final String INVITATION_PATH = "/invitation";

    private final MailDispatcher mail;
    private final SiteSettingsService siteSettings;
    private final AdminAuthProperties props;
    private final Messages messages;
    private final String adminUrl;

    public OperatorMailSender(MailDispatcher mail, SiteSettingsService siteSettings, AdminAuthProperties props, Messages messages,
                              @Value("${roboyam.admin-url:}") String adminUrl) {
        this.mail = mail;
        this.siteSettings = siteSettings;
        this.props = props;
        this.messages = messages;
        this.adminUrl = normalizeAdminUrl(adminUrl);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCredentialsIssued(OperatorCredentialsIssued event) {
        Locale locale = messages.locale();
        OperatorMailTemplate.Mail content = OperatorMailTemplate.credentials(messages, locale, siteName(), event.operatorName(), event.temporaryPassword(), adminUrl);
        send(event.email(), content, "운영자 계정 안내");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInvitationIssued(OperatorInvitationIssued event) {
        if (adminUrl == null) {
            log.warn("관리자 앱 주소(ADMIN_URL)가 설정되지 않아 운영자 초대 메일을 보내지 못했다 — 서버에 ADMIN_URL 을 등록해 주세요");
            return;
        }
        String link = adminUrl + INVITATION_PATH + "?token=" + URLEncoder.encode(event.token(), StandardCharsets.UTF_8);
        OperatorMailTemplate.Mail content = OperatorMailTemplate.invitation(messages, messages.locale(), siteName(), link, props.invitationValidHours());
        send(event.email(), content, "운영자 초대");
    }

    private void send(String to, OperatorMailTemplate.Mail content, String purpose) {
        try {
            if (mail.send(to, content.subject(), content.html())) {
                log.info("{} 메일을 보냈다", purpose);
            }
        } catch (RuntimeException e) {
            // 예외 문구에 수신자 주소·서버 응답이 실릴 수 있어 클래스 이름만 남긴다
            log.error("{} 메일 발송에 실패했다 원인={}", purpose, e.getClass().getSimpleName());
        }
    }

    private String siteName() {
        return siteSettings.current().map(SiteValues::name).orElse(null);
    }

    /** 앞뒤 공백을 자른다. {@code http(s)://} 로 시작하지 않으면(비었거나 잘못된 값) 없는 것으로 본다. 끝의 {@code /} 는 뗀다 */
    private static String normalizeAdminUrl(String value) {
        String url = value == null ? "" : value.strip();
        if (!(url.startsWith("http://") || url.startsWith("https://"))) {
            return null;
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
