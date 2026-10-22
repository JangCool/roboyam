package com.community.board.repository.master;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.OptionalLong;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비회원 비밀번호 시도 기록({@code guest_password_attempts}) — 막힘 확인·실패 더하기·성공 시 지우기.
 *
 * <p>Native SQL 인 이유: 실패 더하기는 「없으면 만들고, 기간이 지났으면 1부터, 아니면 1 더한다」를 한 문장({@code INSERT … ON CONFLICT DO UPDATE})으로 해야
 * 서버 여러 대가 동시에 틀려도 횟수가 겹치지 않는다. 기간 판정은 DB 의 {@code now()} 로만 한다(서버마다 시계가 달라도 같다).
 *
 * <p>모든 메서드는 <b>자기 트랜잭션</b>({@code REQUIRES_NEW})으로 돈다 — 비밀번호가 틀리면 부르는 쪽이 예외를 던지므로,
 * 바깥 트랜잭션에 묶이면 실패 기록이 함께 되돌려진다.
 */
@Repository
public class GuestPasswordAttemptQueries {

    private final EntityManager entityManager;

    public GuestPasswordAttemptQueries(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /** 기간 안에서 {@code maxFailures} 번 이상 틀렸으면 막힘이 풀리기까지 남은 초(1 이상), 아니면 비어 있다 */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public OptionalLong blockedSeconds(String key, int maxFailures, int windowSeconds) {
        @SuppressWarnings("unchecked")
        List<Number> rows = entityManager.createNativeQuery("""
                        select ceil(extract(epoch from (window_started_at + make_interval(secs => ?3) - now())))
                          from guest_password_attempts
                         where attempt_key = ?1 and failures >= ?2 and window_started_at > now() - make_interval(secs => ?3)
                        """)
                .setParameter(1, key)
                .setParameter(2, maxFailures)
                .setParameter(3, windowSeconds)
                .getResultList();
        return rows.isEmpty() ? OptionalLong.empty() : OptionalLong.of(Math.max(1, rows.getFirst().longValue()));
    }

    /** 실패를 하나 더한다 — 기간이 지난 줄은 지금부터 새로 센다 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String key, int windowSeconds) {
        entityManager.createNativeQuery("""
                        insert into guest_password_attempts as a (attempt_key, failures, window_started_at, updated_at)
                        values (?1, 1, now(), now())
                        on conflict (attempt_key) do update set
                            failures = case when a.window_started_at <= now() - make_interval(secs => ?2) then 1 else a.failures + 1 end,
                            window_started_at = case when a.window_started_at <= now() - make_interval(secs => ?2) then now() else a.window_started_at end,
                            updated_at = now()
                        """)
                .setParameter(1, key)
                .setParameter(2, windowSeconds)
                .executeUpdate();
    }

    /** 맞혔으면 그 IP·대상의 기록을 지운다 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void clear(String key) {
        entityManager.createNativeQuery("delete from guest_password_attempts where attempt_key = ?1")
                .setParameter(1, key)
                .executeUpdate();
    }
}
