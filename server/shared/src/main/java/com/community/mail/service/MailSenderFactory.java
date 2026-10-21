package com.community.mail.service;

import com.community.mail.config.MailSendProperties;
import com.community.mail.entity.MailSecurity;
import java.util.Properties;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

/**
 * 입력한(또는 저장한) 메일 서버 값으로 발송기를 만든다.
 *
 * <ul>
 *   <li>사용자 이름이 있을 때만 인증한다(없으면 인증 없이 보낸다).</li>
 *   <li>{@code STARTTLS} 는 서버가 지원하지 않으면 <b>평문으로 물러서지 않고 실패</b>한다({@code starttls.required}). {@code SSL}·{@code STARTTLS} 는 서버
 *       인증서의 호스트 이름도 확인한다({@code ssl.checkserveridentity}).</li>
 *   <li>접속·읽기·쓰기 시간 제한은 {@link MailSendProperties} 값이다 — 응답 없는 서버 때문에 요청이 오래 묶이지 않는다.</li>
 * </ul>
 */
@Component
public class MailSenderFactory {

    private final MailSendProperties props;

    public MailSenderFactory(MailSendProperties props) {
        this.props = props;
    }

    /**
     * @param username 비어 있으면(null) 인증 없이
     * @param password 사용자 이름이 있을 때만 쓴다
     */
    public JavaMailSenderImpl build(String host, int port, String username, String password, MailSecurity security) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setDefaultEncoding("UTF-8");
        Properties mail = sender.getJavaMailProperties();
        mail.put("mail.smtp.connectiontimeout", String.valueOf(props.connectTimeoutMillis()));
        mail.put("mail.smtp.timeout", String.valueOf(props.ioTimeoutMillis()));
        mail.put("mail.smtp.writetimeout", String.valueOf(props.ioTimeoutMillis()));
        if (username != null) {
            sender.setUsername(username);
            sender.setPassword(password);
            mail.put("mail.smtp.auth", "true");
        }
        switch (security) {
            case STARTTLS -> {
                mail.put("mail.smtp.starttls.enable", "true");
                mail.put("mail.smtp.starttls.required", "true");
                mail.put("mail.smtp.ssl.checkserveridentity", "true");
            }
            case SSL -> {
                mail.put("mail.smtp.ssl.enable", "true");
                mail.put("mail.smtp.ssl.checkserveridentity", "true");
            }
            case NONE -> {
                // 암호화하지 않는다
            }
        }
        return sender;
    }
}
