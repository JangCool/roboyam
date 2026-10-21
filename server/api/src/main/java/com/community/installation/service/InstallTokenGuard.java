package com.community.installation.service;

import com.community.installation.config.InstallProperties;
import com.community.installation.exception.InstallationTokenLockedException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 설치 토큰을 두드려 맞히는 것을 막는다.
 *
 * <ul>
 *   <li><b>IP 당 연속 {@code maxFailures}(5)번 틀리면 {@code lockSeconds}(600초 = 10분) 잠근다.</b> 잠긴 동안은 맞는 토큰도 거절한다.
 *       성공하면 그 IP 의 실패 횟수를 지운다. 5번째 틀린 응답까지는 401, 그 뒤가 429 다.</li>
 *   <li><b>서버 전체로도 분당 {@code globalPerMinute} 번까지만</b> 받는다(성공·실패 모두 센다) — IP 를 바꿔 가며 두드리는 것을 막는 두 번째 방어선이다.</li>
 *   <li>횟수·잠금은 이 서버의 메모리에 둔다(서버를 다시 띄우면 비워진다). 설치 전에는 사용자가 한 명뿐이라 서버 간 공유는 하지 않는다.</li>
 *   <li>시간은 저장하거나 서버끼리 비교하지 않는 <b>경과 시간</b>이라 {@code System.nanoTime()} 이다.</li>
 * </ul>
 * 로그에는 IP 와 횟수만 남기고 입력한 토큰은 어디에도 남기지 않는다.
 */
@Component
public class InstallTokenGuard {

    private static final long MINUTE_NANOS = Duration.ofMinutes(1).toNanos();

    /** 추적하는 IP 의 상한 — 넘치면 오래된 항목부터 비운다 */
    private static final int MAX_TRACKED_IPS = 10_000;

    /** IP 하나의 연속 실패 횟수와 잠금 */
    private static final class Attempts {
        private int failures;
        private boolean locked;
        private long lockedUntilNanos;
        private long touchedNanos = System.nanoTime();
    }

    private final int maxFailures;
    private final long lockNanos;
    private final int globalPerMinute;
    private final ConcurrentHashMap<String, Attempts> byIp = new ConcurrentHashMap<>();

    private long windowStartNanos = System.nanoTime();
    private int windowCount;

    public InstallTokenGuard(InstallProperties props) {
        this.maxFailures = props.maxFailures();
        this.lockNanos = Duration.ofSeconds(props.lockSeconds()).toNanos();
        this.globalPerMinute = props.globalPerMinute();
    }

    /**
     * 이 IP 가 지금 토큰을 확인해 볼 수 있는가. 잠겼거나 서버 전체 상한을 넘었으면 {@code 429} 를 던진다.
     * 서버 전체 상한은 <b>시도(성공·실패 모두)</b> 를 센다.
     */
    public void checkAllowed(String ip) {
        long now = System.nanoTime();
        synchronized (this) {
            if (now - windowStartNanos >= MINUTE_NANOS) {
                windowStartNanos = now;
                windowCount = 0;
            }
            windowCount++;
            if (windowCount > globalPerMinute) {
                throw new InstallationTokenLockedException(retryAfterSeconds(windowStartNanos + MINUTE_NANOS - now));
            }
        }
        Attempts attempts = byIp.get(ip);
        if (attempts == null) {
            return;
        }
        synchronized (attempts) {
            if (attempts.locked && now - attempts.lockedUntilNanos < 0) {
                throw new InstallationTokenLockedException(retryAfterSeconds(attempts.lockedUntilNanos - now));
            }
            if (attempts.locked) { // 잠금이 풀렸다 — 처음부터 다시 센다
                attempts.locked = false;
                attempts.failures = 0;
            }
        }
    }

    /** 틀렸다. 연속 실패가 한도에 닿으면 이 IP 를 잠근다 */
    public void recordFailure(String ip) {
        if (byIp.size() >= MAX_TRACKED_IPS) {
            prune();
        }
        Attempts attempts = byIp.get(ip);
        if (attempts == null) {
            if (byIp.size() >= MAX_TRACKED_IPS) {
                return; // 비워도 자리가 없다 — 서버 전체 상한(globalPerMinute)이 막고 있다
            }
            attempts = byIp.computeIfAbsent(ip, key -> new Attempts());
        }
        synchronized (attempts) {
            attempts.touchedNanos = System.nanoTime();
            attempts.failures++;
            if (attempts.failures >= maxFailures) {
                attempts.locked = true;
                attempts.lockedUntilNanos = System.nanoTime() + lockNanos;
                attempts.failures = 0;
            }
        }
    }

    /** 맞았다. 연속 실패 횟수를 지운다 */
    public void recordSuccess(String ip) {
        byIp.remove(ip);
    }

    /** 잠금이 풀렸거나 한참 건드리지 않은 항목을 비운다 */
    private void prune() {
        long now = System.nanoTime();
        long idleNanos = Math.max(lockNanos, Duration.ofHours(1).toNanos());
        byIp.entrySet().removeIf(entry -> {
            Attempts attempts = entry.getValue();
            synchronized (attempts) {
                boolean lockOver = attempts.locked && now - attempts.lockedUntilNanos >= 0;
                return lockOver || now - attempts.touchedNanos >= idleNanos;
            }
        });
    }

    private static long retryAfterSeconds(long remainingNanos) {
        return Math.max(1, (remainingNanos + 999_999_999L) / 1_000_000_000L);
    }
}
