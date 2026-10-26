package com.community.mail.outbox.dto;

import java.time.Duration;

/**
 * 메일 대기열 워커의 재시도 규칙(배포 설정 {@code roboyam.mail-outbox.*} — 운영 정책이 아니라 발송 장치의 값이라 프로퍼티).
 *
 * @param maxAttempts 이만큼 시도해도 못 보내면 DEAD(기본 12 — 1·2·4·…분, 최대 2시간 간격으로 약 10시간)
 * @param batchSize   한 바퀴에 집는 줄 수(기본 20)
 * @param staleAfter  SENDING 으로 이만큼 머문 줄은 워커가 죽은 것으로 보고 다시 PENDING(기본 10분 — 다시 갈 수 있다)
 * @param baseBackoff 첫 재시도 간격(기본 1분, 시도마다 두 배)
 * @param maxBackoff  재시도 간격 상한(기본 2시간)
 */
public record MailOutboxPolicy(int maxAttempts, int batchSize, Duration staleAfter, Duration baseBackoff, Duration maxBackoff) {

    public static final MailOutboxPolicy DEFAULTS = new MailOutboxPolicy(12, 20, Duration.ofMinutes(10), Duration.ofMinutes(1), Duration.ofHours(2));

    public MailOutboxPolicy {
        if (maxAttempts < 1 || batchSize < 1 || staleAfter.isNegative() || staleAfter.isZero() || baseBackoff.isNegative() || maxBackoff.compareTo(baseBackoff) < 0) {
            throw new IllegalArgumentException("메일 대기열 설정이 범위를 벗어났다");
        }
    }

    /** {@code attempts} 번째 시도가 실패한 뒤 다음 시도까지 — base × 2^(attempts-1), 상한 maxBackoff */
    public Duration backoffAfter(int attempts) {
        int shift = Math.max(0, Math.min(attempts - 1, 30));
        Duration next = baseBackoff.multipliedBy(1L << shift);
        return next.compareTo(maxBackoff) > 0 ? maxBackoff : next;
    }
}
