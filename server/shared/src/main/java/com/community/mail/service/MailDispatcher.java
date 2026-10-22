package com.community.mail.service;

import com.community.mail.entity.MailSettingEntity;
import com.community.mail.repository.master.MailSettingRepository;
import com.community.zconfiguration.crypto.SecretEncryptor;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * 저장된 메일 서버 설정({@code mail_settings}, 설치 마법사·관리자 화면이 저장)으로 메일 한 통을 보낸다. 로보얌의 모든 메일(회원 인증, 운영자 초대·자격 증명·
 * 비밀번호 재설정 승인번호, 관리자 알림, 시험 메일)이 이 길로 나간다.
 *
 * <ul>
 *   <li>메일 서버가 설정돼 있지 않으면(행이 없거나, 비밀번호를 풀 암호화 키가 없으면) <b>보내지 않고 {@code false}</b> 를 돌려준다 — 던지지 않는다.</li>
 *   <li>설정은 보낼 때마다 읽는다. 설정을 바꾸면 다음 발송부터 새 설정으로 나간다.</li>
 *   <li><b>보내는 이름</b>({@code sender_name})이 있으면 From 은 {@code "보내는 이름" <주소>}(RFC 5322 · RFC 2047 UTF-8 인코딩), 없으면 주소만.</li>
 *   <li><b>제목 머리말</b>({@code subject_prefix})이 있으면 제목은 {@code "{머리말} {제목}"} — 템플릿마다 고치지 않고 여기서 한 번에 붙인다.
 *       제목에 자기 {@code [사이트 이름]} 을 붙이는 메일(관리자 알림)은 {@link #sendTagged} 로 그것을 뺀 제목도 넘겨 머리말과 겹치지 않게 한다.</li>
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
     * @param subject 제목 — 머리말이 설정돼 있으면 앞에 붙는다
     * @param html    본문(HTML)
     * @return 보냈으면 true, 메일 서버가 설정돼 있지 않아 보내지 못했으면 false
     */
    public boolean send(String to, String subject, String html) {
        return sendTagged(to, subject, subject, html);
    }

    /**
     * 제목에 자기 {@code [사이트 이름]} 을 붙이는 메일용 — 머리말이 없으면 {@code taggedSubject} 를 그대로, 있으면 {@code "{머리말} {untaggedSubject}"} 를 쓴다.
     *
     * @param taggedSubject   머리말이 없을 때의 제목(예: {@code [사이트 이름] 제목})
     * @param untaggedSubject 머리말이 있을 때 머리말 뒤에 붙일 제목(자체 {@code [사이트 이름]} 을 뺀 것)
     * @return 보냈으면 true, 메일 서버가 설정돼 있지 않아 보내지 못했으면 false
     */
    public boolean sendTagged(String to, String taggedSubject, String untaggedSubject, String html) {
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
            String senderName = headerText(setting.getSenderName());
            if (senderName == null) {
                helper.setFrom(setting.getFromAddress());
            } else {
                helper.setFrom(new InternetAddress(setting.getFromAddress(), senderName, StandardCharsets.UTF_8.name()));
            }
            helper.setTo(to);
            helper.setSubject(subject(headerText(setting.getSubjectPrefix()), taggedSubject, untaggedSubject));
            helper.setText(html, true);
            sender.send(message);
            return true;
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new IllegalStateException("메일을 만들지 못했다"); // 수신자 주소를 담지 않는다
        }
    }

    /** 머리말이 없으면 원래 제목, 있으면 {@code "{머리말} {자체 태그를 뺀 제목}"} */
    static String subject(String prefix, String taggedSubject, String untaggedSubject) {
        if (prefix == null) {
            return taggedSubject;
        }
        String rest = untaggedSubject == null ? taggedSubject : untaggedSubject;
        return rest == null || rest.isEmpty() ? prefix : prefix + " " + rest;
    }

    /**
     * 저장된 보내는 이름·머리말을 헤더에 넣을 모양으로 — 저장 때 이미 막지만(줄바꿈·제어 문자), DB 를 직접 고친 값이 와도 헤더가 갈라지지 않게 공백으로 바꾼다. 비면 null.
     */
    private static String headerText(String value) {
        if (value == null) {
            return null;
        }
        StringBuilder out = new StringBuilder(value.length());
        value.codePoints().forEach(c -> {
            int type = Character.getType(c);
            out.appendCodePoint(Character.isISOControl(c) || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR ? ' ' : c);
        });
        String text = out.toString().strip();
        return text.isEmpty() ? null : text;
    }
}
