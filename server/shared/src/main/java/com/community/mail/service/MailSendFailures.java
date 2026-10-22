package com.community.mail.service;

import jakarta.mail.SendFailedException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;

/**
 * {@link MailDispatcher#send} 가 던진 예외의 원인을 짧은 코드로 가른다 — 관리자 화면의 시험 발송이 쓴다.
 * <b>예외의 종류만 본다</b>(문구에는 SMTP 응답 원문·호스트 이름·받는 주소가 실릴 수 있다). 코드를 응답·감사에 실어도 되고 로그에는 {@link #causeNames} 만 남긴다.
 *
 * <ul>
 *   <li>{@code REJECTED} — 메일 서버가 보내는/받는 주소를 거절했다({@link SendFailedException})</li>
 *   <li>{@code TIMEOUT} · {@code AUTH_FAILED} · {@code TLS_FAILED} · {@code CONNECT_FAILED} — 연결 시험과 같은 분류({@link MailConnectionChecker})</li>
 *   <li>{@code FAILED} — 메일 서버와 상관없는 실패(저장된 비밀번호를 풀지 못함 등)</li>
 * </ul>
 */
public final class MailSendFailures {

    public static final String REJECTED = "REJECTED";
    public static final String FAILED = "FAILED";

    private MailSendFailures() {
    }

    public static String classify(RuntimeException error) {
        List<Throwable> roots = new ArrayList<>();
        roots.add(error);
        if (error instanceof MailSendException send) {
            roots.addAll(List.of(send.getMessageExceptions())); // 메일별 실패는 원인 사슬이 아니라 여기에 들어 있다
        }
        for (Throwable root : roots) {
            int depth = 0;
            for (Throwable t = root; t != null && depth < 12; t = t.getCause() == t ? null : t.getCause(), depth++) {
                if (t instanceof SendFailedException) {
                    return REJECTED;
                }
            }
        }
        if (!(error instanceof MailException)) {
            return FAILED;
        }
        return MailConnectionChecker.classify(roots.size() > 1 ? roots.get(1) : error).name();
    }

    /** 로그용: 예외 클래스 이름만 */
    public static String causeNames(Throwable error) {
        StringBuilder names = new StringBuilder();
        int depth = 0;
        for (Throwable t = error; t != null && depth < 12; t = t.getCause() == t ? null : t.getCause(), depth++) {
            names.append(names.isEmpty() ? "" : " <- ").append(t.getClass().getSimpleName());
        }
        return names.toString();
    }
}
