package com.community.ad.service;

import com.community.ad.repository.master.AdAdvertiserStore;
import com.community.bizapplication.repository.master.BizApplicationStore.AppRow;
import com.community.business.service.BusinessPii;
import com.community.mail.service.MailDispatcher;
import com.community.zconfiguration.crypto.DataCipher;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * 긴급 내림 광고주 메일(ad-contract — 「광고주 메일 선택」). <b>트랜잭션 밖에서</b> 부른다(메일 서버가 느려도 DB 를 붙잡지 않는다).
 * 받는 주소 = 계정 없는 광고주의 이메일(암호문을 풀어서), 없으면 연결된 사업자 신원의 이메일. 결과 코드 {@code SENT}·{@code FAILED}(메일 서버 없음·전송 실패)·{@code NO_ADDRESS}.
 * 본문은 광고 이름과 운영자가 적은 사유만 싣는다. 주소·본문은 로그에 남기지 않는다.
 */
@Component
public class AdTakedownMailer {

    public static final String SENT = "SENT";
    public static final String FAILED = "FAILED";
    public static final String NO_ADDRESS = "NO_ADDRESS";

    private static final Logger log = LoggerFactory.getLogger(AdTakedownMailer.class);

    private final MailDispatcher dispatcher;
    private final AdAdvertiserStore advertisers;
    private final DataCipher cipher;
    private final Messages messages;

    public AdTakedownMailer(MailDispatcher dispatcher, AdAdvertiserStore advertisers, DataCipher cipher, Messages messages) {
        this.dispatcher = dispatcher;
        this.advertisers = advertisers;
        this.cipher = cipher;
        this.messages = messages;
    }

    public String send(AppRow app, String reason) {
        Optional<String> to = address(app);
        if (to.isEmpty()) {
            return NO_ADDRESS;
        }
        String subject = messages.get(new MessageRef("ad.takedown.mail.subject", app.title()));
        String html = "<p>" + HtmlUtils.htmlEscape(messages.get(new MessageRef("ad.takedown.mail.body", app.title(), app.no()))) + "</p><p>"
                + HtmlUtils.htmlEscape(messages.get(new MessageRef("ad.takedown.mail.reason", reason))) + "</p>";
        try {
            return dispatcher.send(to.get(), subject, html) ? SENT : FAILED;
        } catch (RuntimeException e) {
            log.warn("긴급 내림 메일을 보내지 못했다 applicationId={} error={}", app.id(), e.getClass().getSimpleName());
            return FAILED;
        }
    }

    private Optional<String> address(AppRow app) {
        try {
            if (app.advertiserId() != null) {
                Optional<String> enc = advertisers.secret(app.advertiserId()).map(AdAdvertiserStore.Secret::emailEnc);
                if (enc.isPresent()) {
                    return enc.map(e -> cipher.decrypt(AdAdvertiserService.EMAIL, e));
                }
            }
            if (app.businessProfileId() != null) {
                return advertisers.businessEmailEnc(app.businessProfileId()).map(e -> cipher.decrypt(BusinessPii.EMAIL, e));
            }
        } catch (RuntimeException e) {
            log.warn("긴급 내림 메일 주소를 풀지 못했다 applicationId={} error={}", app.id(), e.getClass().getSimpleName());
        }
        return Optional.empty();
    }
}
