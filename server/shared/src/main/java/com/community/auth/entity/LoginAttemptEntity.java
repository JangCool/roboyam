package com.community.auth.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 이메일 단위 로그인 실패 횟수와 잠금 — V2__auth.sql 의 {@code login_attempts}. 계정이 없는 이메일도 같은 방식으로 센다(계정 존재를 알리지 않기 위해).
 * 키는 정규화한 이메일의 SHA-256 16진 64자 — 이메일 원문을 저장하지 않는다. 행은 {@code LoginAttemptRepositoryQDSL.insertIfAbsent} 가 만든다.
 *
 * <p>상태 변경(실패 더하기·잠금·초기화)은 행을 쓰기 잠금으로 읽은 트랜잭션 안에서 이 메서드들로만 한다.
 */
@Entity
@Table(name = "login_attempts")
public class LoginAttemptEntity {

    @Id
    private String emailKey;

    private int failCount;
    private Instant lockedUntil;
    private Instant updatedAt;

    protected LoginAttemptEntity() {
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** 잠금이 풀렸으면 횟수를 처음부터 다시 센다 */
    public void clearIfLockExpired(Instant now) {
        if (lockedUntil != null && !lockedUntil.isAfter(now)) {
            failCount = 0;
            lockedUntil = null;
            updatedAt = now;
        }
    }

    /** 실패를 하나 더하고, 한도에 닿으면 그 순간부터 잠근다. 이번 실패로 잠겼으면 true */
    public boolean fail(Instant now, int maxFailures, long lockSeconds) {
        failCount++;
        updatedAt = now;
        if (failCount >= maxFailures) {
            lockedUntil = now.plusSeconds(lockSeconds);
            return true;
        }
        return false;
    }

    /** 로그인에 성공했다 — 횟수와 잠금을 지운다 */
    public void reset(Instant now) {
        failCount = 0;
        lockedUntil = null;
        updatedAt = now;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }
}
