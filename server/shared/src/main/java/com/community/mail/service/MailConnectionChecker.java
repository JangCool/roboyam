package com.community.mail.service;

import com.community.mail.dto.MailCheckReason;
import com.community.mail.dto.MailCheckResult;
import com.community.mail.entity.MailSecurity;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import java.net.SocketTimeoutException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.net.ssl.SSLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

/**
 * 메일 서버에 <b>접속하고 인증까지만</b> 해 본다 — <b>메일은 보내지 않는다</b>. 설치 마법사의 "연결 확인" 버튼이 쓴다.
 *
 * <p>실패해도 던지지 않고 원인({@link MailCheckReason})으로 답한다. <b>서버 응답 원문·자격 증명·예외 문구를 응답에 싣지 않는다</b> —
 * 원인을 거슬러 올라가며 아는 것에 이름만 붙이고, 로그에는 예외 <b>클래스 이름</b>만 남긴다.
 */
@Component
public class MailConnectionChecker {

    private static final Logger log = LoggerFactory.getLogger(MailConnectionChecker.class);

    private final MailSenderFactory factory;

    public MailConnectionChecker(MailSenderFactory factory) {
        this.factory = factory;
    }

    /** 접속·인증을 시도한다. 사용자 이름이 null 이면 인증 없이 접속만 본다 */
    public MailCheckResult check(String host, int port, String username, String password, MailSecurity security) {
        JavaMailSenderImpl sender = factory.build(host, port, username, password, security);
        try {
            sender.testConnection();
            return MailCheckResult.success();
        } catch (MessagingException | RuntimeException e) {
            MailCheckReason reason = classify(e);
            log.info("메일 서버 연결 시험 실패 host={} port={} security={} reason={} cause={}", host, port, security, reason, causeNames(e));
            return MailCheckResult.failure(reason);
        }
    }

    /**
     * 실패 원인을 가른다. 라이브러리 예외는 껍데기 안에 진짜 원인이 들어 있으므로 <b>원인 사슬 전체</b>를 본다.
     * 시간 초과 → 인증 거절 → TLS 문제 → 그 밖(접속 못 함) 순이다 — 접속 시간 초과는 접속 예외 안에 들어 있어 먼저 봐야 한다.
     */
    static MailCheckReason classify(Throwable error) {
        List<Throwable> chain = chain(error);
        if (chain.stream().anyMatch(SocketTimeoutException.class::isInstance)) {
            return MailCheckReason.TIMEOUT;
        }
        if (chain.stream().anyMatch(AuthenticationFailedException.class::isInstance)) {
            return MailCheckReason.AUTH_FAILED;
        }
        if (chain.stream().anyMatch(t -> t instanceof SSLException || t instanceof CertificateException
                || (t instanceof MessagingException && mentionsStartTls(t)))) {
            return MailCheckReason.TLS_FAILED;
        }
        return MailCheckReason.CONNECT_FAILED;
    }

    /** STARTTLS 를 요구했는데 서버가 지원하지 않을 때 JavaMail 이 던지는 예외는 전용 타입이 없다 — 문구에 STARTTLS 가 있는지만 본다(분류에만 쓰고 내보내지 않는다) */
    private static boolean mentionsStartTls(Throwable t) {
        String message = t.getMessage();
        return message != null && message.toUpperCase(Locale.ROOT).contains("STARTTLS");
    }

    private static List<Throwable> chain(Throwable error) {
        List<Throwable> chain = new ArrayList<>();
        for (Throwable t = error; t != null && chain.size() < 12; t = t.getCause()) {
            chain.add(t);
            if (t.getCause() == t) {
                break;
            }
        }
        return chain;
    }

    /** 로그용: 예외 클래스 이름만(메시지에는 서버 응답·호스트 이름이 실릴 수 있다) */
    private static String causeNames(Throwable error) {
        StringBuilder names = new StringBuilder();
        for (Throwable t : chain(error)) {
            names.append(names.isEmpty() ? "" : " <- ").append(t.getClass().getSimpleName());
        }
        return names.toString();
    }
}
