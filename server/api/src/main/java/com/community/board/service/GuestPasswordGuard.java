package com.community.board.service;

import com.community.board.exception.PasswordAttemptsExceededException;
import com.community.board.repository.master.GuestPasswordAttemptQueries;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * 비회원 글·댓글 비밀번호를 두드려 맞히는 것을 막는다 — <b>같은 IP 가 같은 글(또는 댓글)에 대해 {@value #MAX_FAILURES}번 틀리면 처음 틀린 때부터 10분 동안</b>
 * 그 대상의 비밀번호 확인·고치기·지우기를 {@code 429 PASSWORD_ATTEMPTS_EXCEEDED} 로 막는다(가정 — 계약 「서버 구현 결과」). 맞히면 그 IP·대상의 기록을 지운다.
 *
 * <p>기록은 DB({@code guest_password_attempts}, {@link GuestPasswordAttemptQueries})에 둔다 — 서버가 여러 대여도 한곳에서 세고, 서버를 다시 띄워도 이어진다.
 * 열쇠는 sha256("IP|대상") 이라 IP 원문을 남기지 않는다. 비밀번호는 어디에도 남기지 않는다. 오래된 줄은 DB 배치 {@code board-post-upkeep} 이 지운다.
 */
@Component
public class GuestPasswordGuard {

    static final int MAX_FAILURES = 5;
    private static final int WINDOW_SECONDS = (int) Duration.ofMinutes(10).toSeconds();

    private final GuestPasswordAttemptQueries attempts;

    public GuestPasswordGuard(GuestPasswordAttemptQueries attempts) {
        this.attempts = attempts;
    }

    /** 지금 확인해 볼 수 있는가 — 막혔으면 남은 초와 함께 429 */
    public void checkAllowed(String ip, String target) {
        attempts.blockedSeconds(key(ip, target), MAX_FAILURES, WINDOW_SECONDS).ifPresent(seconds -> {
            throw new PasswordAttemptsExceededException(seconds);
        });
    }

    public void recordFailure(String ip, String target) {
        attempts.recordFailure(key(ip, target), WINDOW_SECONDS);
    }

    public void recordSuccess(String ip, String target) {
        attempts.clear(key(ip, target));
    }

    private static String key(String ip, String target) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest((ip + '|' + target).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 을 쓸 수 없다", e);
        }
    }
}
