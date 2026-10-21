package com.community.mail.service;

import com.community.mail.entity.MailSettingEntity;
import com.community.mail.repository.master.MailSettingRepository;
import com.community.zconfiguration.crypto.SecretEncryptor;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * 저장된 메일 서버 설정({@code mail_settings}, 설치 마법사가 저장)으로 메일 한 통을 보낸다.
 *
 * <ul>
 *   <li>메일 서버가 설정돼 있지 않으면(행이 없거나, 비밀번호를 풀 암호화 키가 없으면) <b>보내지 않고 {@code false}</b> 를 돌려준다 — 던지지 않는다.</li>
 *   <li>설정은 보낼 때마다 읽는다. 설정을 바꾸면 다음 발송부터 새 설정으로 나간다.</li>
 *   <li>연결·인증·전송에 실패하면 던진다({@link org.springframework.mail.MailException} 등). 호출하는 쪽이 로그를 남기고 요청을 어떻게 끝낼지 정한다.</li>
 *   <li><b>트랜잭션 밖에서 부른다</b> — 메일 서버가 느려도 DB 트랜잭션을 붙잡지 않는다(접속 5초·읽기 10초 제한은 {@link MailSenderFactory}).</li>
 * </ul>
 * 수신자 주소·본문·비밀번호를 로그에 남기지 않는다.
 */
@Service
public class MailDispatcher {

    private static final Logger log = LoggerFactory.getLogger(MailDispatcher.class);

    private final MailSettingRepository settings;
    private final SecretEncryptor encryptor;
    private final MailSenderFactory factory;

    public MailDispatcher(MailSettingRepository settings, SecretEncryptor encryptor, MailSenderFactory factory) {
        this.settings = settings;
        this.encryptor = encryptor;
        this.factory = factory;
    }

    /**
     * @param html 본문(HTML)
     * @return 보냈으면 true, 메일 서버가 설정돼 있지 않아 보내지 못했으면 false
     */
    public boolean send(String to, String subject, String html) {
        Optional<MailSettingEntity> found = settings.findById(MailSettingEntity.ROW_ID);
        if (found.isEmpty()) {
            log.warn("메일 서버가 설정되지 않아 메일을 보내지 못했다 — 설치 마법사나 설정 화면에서 메일 서버를 등록해 주세요");
            return false;
        }
        MailSettingEntity setting = found.get();
        String password = null;
        if (setting.getUsername() != null) {
            if (setting.getPasswordEnc() == null || !encryptor.isConfigured()) {
                log.warn("메일 서버 비밀번호를 풀 수 없어 메일을 보내지 못했다 — 서버에 ROBOYAM_CRYPTO_KEY 가 있는지, 비밀번호가 저장돼 있는지 확인해 주세요");
                return false;
            }
            try {
                password = encryptor.decrypt(setting.getPasswordEnc());
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("메일 서버 비밀번호를 풀지 못했다 — 암호화 키가 저장 때와 다른지 확인해 주세요");
            }
        }

        JavaMailSenderImpl sender = factory.build(setting.getHost(), setting.getPort(), setting.getUsername(), password, setting.getSecurity());
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(setting.getFromAddress());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            sender.send(message);
            return true;
        } catch (MessagingException e) {
            throw new IllegalStateException("메일을 만들지 못했다"); // 수신자 주소를 담지 않는다
        }
    }
}
