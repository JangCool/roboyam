package com.community.mail.outbox.service;

import com.community.mail.outbox.dto.OutboxDelivery;
import com.community.mail.service.MailDispatcher;
import com.community.membernotification.repository.master.MemberNotificationMailQueries;
import com.community.membernotification.repository.master.MemberNotificationMailQueries.Recipient;
import com.community.notificationtemplate.catalog.MessageLocales;
import com.community.notificationtemplate.catalog.RequiredMail;
import com.community.notificationtemplate.engine.MessageRenderRequest;
import com.community.notificationtemplate.engine.NotificationMessageRenderer;
import com.community.notificationtemplate.engine.RenderedEmail;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 대기열 메일의 기본 발송 — 문구는 알림 문구 엔진의 <b>필수 안내 메일</b>과 같은 해석(그 종류의 쓸 문구·규칙·메일 템플릿, 없으면 기본 문구 — 결정 11),
 * 보내기는 {@link MailDispatcher#sendTagged}(보내는 이름·제목 머리말 규칙 그대로).
 *
 * <ul>
 *   <li><b>받는 사람</b>: 그 회원의 지금 {@code users.email}. 회원이 없거나 지워졌으면·탈퇴했으면·주소가 없으면·<b>이메일 인증을 마치지 않았으면</b> 보내지 않는다(CANCELED —
 *       인증하지 않은 주소는 남의 것일 수 있어 개인 거래 내용을 보내지 않는다). 정지 회원도 받는다(돈 관련 안내라 계정 상태와 무관하다).</li>
 *   <li><b>언어</b>: 회원 표시 설정의 언어, 없으면 사이트 표시 기본 언어. {@code recipientName} = 회원 닉네임.</li>
 *   <li>메일 서버가 설정돼 있지 않아 못 보냈으면 RETRY(서버를 고치면 다음 시도에 나간다 — P27). 모르는 종류 코드는 CANCELED.</li>
 *   <li>주소·본문·변수는 로그에 남기지 않는다. 남기는 것은 가린 주소({@code ab***@example.com}, 관리자 {@code MemberMasking.email} 과 같은 규칙)뿐이다.</li>
 * </ul>
 */
@Component
public class RequiredMailOutboxSender implements OutboxMailSender {

    private final MemberNotificationMailQueries recipients;
    private final NotificationMessageRenderer renderer;
    private final MailDispatcher mail;

    public RequiredMailOutboxSender(MemberNotificationMailQueries recipients, NotificationMessageRenderer renderer, MailDispatcher mail) {
        this.recipients = recipients;
        this.renderer = renderer;
        this.mail = mail;
    }

    @Override
    public OutboxDelivery send(long userId, String kind, Map<String, Object> variables) {
        Optional<RequiredMail> mailKind = RequiredMail.ofCode(kind);
        if (mailKind.isEmpty()) {
            return OutboxDelivery.canceled("UNKNOWN_KIND");
        }
        Optional<Recipient> found = recipients.recipient(userId);
        if (found.isEmpty()) {
            return OutboxDelivery.canceled("NO_RECIPIENT");
        }
        Recipient recipient = found.get();
        String refusal = refusal(recipient);
        if (refusal != null) {
            return OutboxDelivery.canceled(refusal);
        }
        RenderedEmail content = renderer.renderEmail(MessageRenderRequest.requiredMail(mailKind.get()).locale(localeOf(recipient.language()))
                .variables(variables).variable("recipientName", recipient.nickname()).build());
        if (!mail.sendTagged(recipient.email(), content.subject(), content.untaggedSubject(), content.html())) {
            return OutboxDelivery.retry("MAIL_NOT_CONFIGURED");
        }
        return OutboxDelivery.sent(mask(recipient.email()));
    }

    /** 받을 수 없는 까닭(받을 수 있으면 null) */
    static String refusal(Recipient recipient) {
        if ("WITHDRAWN".equals(recipient.status())) {
            return "WITHDRAWN";
        }
        if (recipient.email() == null || recipient.email().isBlank()) {
            return "NO_EMAIL";
        }
        if (!recipient.verified()) {
            return "EMAIL_NOT_VERIFIED";
        }
        return null;
    }

    private Locale localeOf(String language) {
        String own = MessageLocales.of(language);
        if (own != null) {
            return MessageLocales.locale(own);
        }
        String site = MessageLocales.of(renderer.site().language());
        return MessageLocales.locale(site != null ? site : MessageLocales.KO);
    }

    /** {@code kim123@gmail.com} → {@code ki***@gmail.com}(로컬 부분이 두 글자 이하면 한 글자) */
    static String mask(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = email.substring(0, at);
        int keep = local.length() <= 2 ? 1 : 2;
        return local.substring(0, keep) + "***" + email.substring(at);
    }
}
