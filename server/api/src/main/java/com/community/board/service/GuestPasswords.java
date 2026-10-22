package com.community.board.service;

import com.community.board.exception.PasswordMismatchException;
import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 비회원 글·댓글 비밀번호 — 해시 만들기(bcrypt, 공용 {@link PasswordEncoder})와 확인(시도 제한 {@link GuestPasswordGuard} 포함).
 * bcrypt 는 느리므로 <b>트랜잭션 밖에서</b> 부른다. 비밀번호는 로그·예외 문구에 싣지 않는다.
 */
@Component
public class GuestPasswords {

    private final PasswordEncoder encoder;
    private final GuestPasswordGuard guard;

    public GuestPasswords(PasswordEncoder encoder, GuestPasswordGuard guard) {
        this.encoder = encoder;
        this.guard = guard;
    }

    /** 검사를 마친 비밀번호의 해시 */
    public String hash(String password) {
        return encoder.encode(password);
    }

    /**
     * 비회원 글·댓글의 비밀번호가 맞는지 본다 — 막혔으면 429, 틀렸으면 403 {@code PASSWORD_MISMATCH}(비회원 글이 아니거나 비밀번호가 없을 때도 같다).
     *
     * @param storedHash 저장된 해시(회원 글이면 null)
     * @param target     시도 제한의 대상 열쇠(예: {@code post:<id>})
     */
    public void require(String storedHash, String password, String ip, String target) {
        guard.checkAllowed(ip, target);
        boolean ok = storedHash != null && password != null && !password.isEmpty()
                && password.getBytes(StandardCharsets.UTF_8).length <= ContentRules.PASSWORD_MAX_BYTES && encoder.matches(password, storedHash);
        if (!ok) {
            guard.recordFailure(ip, target);
            throw new PasswordMismatchException();
        }
        guard.recordSuccess(ip, target);
    }
}
