package com.community.business.service;

import com.community.business.exception.BusinessException;
import com.community.business.nts.NtsProperties;
import com.community.business.repository.master.BusinessNtsDailyCountQueries;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 사업자 신청·수정의 국세청 진위확인 <b>회원별 하루 횟수 제한</b>(business-contract 「국세청 진위확인 · 공공데이터 설정」, 사용자 결정 2026-11-02).
 * 불일치는 저장하지 않으므로, 막지 않으면 같은 회원이 남의 번호에 상호·대표자명·개업일을 바꿔 가며 국세청을 불러 맞는지 알아낼 수 있다.
 *
 * <ul>
 *   <li>회원 × 한국 날짜(KST 0시 기준)로 센다 — 신청·수정 합산. 국세청을 실제로 부르게 되는 시도만 부르기 바로 앞에서 센다(결과와 무관).</li>
 *   <li>{@code INSERT … ON CONFLICT … DO UPDATE … WHERE count < 한도 RETURNING count} 한 문장을 <b>업무 트랜잭션과 따로</b>(새 트랜잭션) 먼저 커밋한다 —
 *       업무가 실패해도 시도 수는 남는다. 한도에 닿은 줄은 더 올리지 않고(돌려받는 줄이 없다) 429 {@code BUSINESS_NTS_DAILY_LIMIT} + 다음 한국 0시까지의 초.</li>
 *   <li>한도({@code roboyam.business.nts.daily-validate-limit}, 기본 5)가 <b>0 이하면 제한 없음</b> — 세지도 않는다. 운영자 [다시 조회](admin)는 이 길을 거치지 않는다.</li>
 *   <li>지난 날짜 줄은 DB 배치 {@code business-nts-count-cleanup}(R__batch_34)이 지운다.</li>
 * </ul>
 * 세는 문장(native — {@code INSERT … ON CONFLICT … RETURNING})은 저장소 {@link BusinessNtsDailyCountQueries} 다.
 */
@Component
public class BusinessNtsDailyLimiter {

    private static final Logger log = LoggerFactory.getLogger(BusinessNtsDailyLimiter.class);

    private final BusinessNtsDailyCountQueries counts;
    private final TransactionTemplate separate;
    private final int limit;
    private final Clock clock;

    @Autowired
    public BusinessNtsDailyLimiter(BusinessNtsDailyCountQueries counts, PlatformTransactionManager transactions, NtsProperties properties, ObjectProvider<Clock> clocks) {
        this(counts, transactions, properties.dailyValidateLimit(), clocks.getIfUnique(Clock::systemUTC));
    }

    public BusinessNtsDailyLimiter(BusinessNtsDailyCountQueries counts, PlatformTransactionManager transactions, int limit, Clock clock) {
        this.counts = counts;
        this.separate = new TransactionTemplate(transactions);
        this.separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.limit = limit;
        this.clock = clock;
    }

    /**
     * 진위확인 한 번을 센다(국세청을 부르기 바로 앞에서). 오늘 한도를 이미 다 썼으면 세지 않고 429 를 던진다.
     *
     * @return 오늘 센 횟수(이번 포함 — 제한 없음이면 세지 않고 0)
     */
    public int acquire(long userId) {
        if (limit <= 0) {
            return 0;
        }
        Instant now = clock.instant();
        LocalDate day = ServiceTime.today(clock);
        Optional<Integer> counted = separate.execute(status -> counts.incrementBelow(userId, day, limit));
        if (counted == null || counted.isEmpty()) {
            long retryAfter = retryAfterSeconds(now, day);
            log.info("사업자 국세청 진위확인 하루 한도에 걸렸다 userId={} day={} limit={} retryAfter={}s", userId, day, limit, retryAfter);
            throw BusinessException.ntsDailyLimit(retryAfter);
        }
        return counted.get();
    }

    /** 다음 한국 0시까지의 초(올림, 최소 1) */
    static long retryAfterSeconds(Instant now, LocalDate today) {
        Instant next = today.plusDays(1).atStartOfDay(ServiceTime.ZONE).toInstant();
        long millis = Duration.between(now, next).toMillis();
        return Math.max(1, (millis + 999) / 1000);
    }
}
