package com.community.batch.service;

import com.community.aimember.service.AiDraftGenerator;
import com.community.aimember.service.AiDraftPublisher;
import com.community.aimember.service.AiMemberProperties;
import com.community.batch.config.BatchProperties;
import com.community.batch.core.BatchLockService;
import com.community.batch.core.WorkerHeartbeats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * AI 회원 실행기의 「언제 돌릴지」(관리자 서버 쪽 — ai-members-contract 정책 11, 세션 정리 결정 2026-11-10 「배치는 admin 또는 pg_cron 에서만」). 한 바퀴의 일은 shared 의
 * {@link AiDraftPublisher}(승인 줄 등록 — 관리자 「게시」는 상태만 바꾼다)와 {@link AiDraftGenerator}(할 일 고르기 + 제공사 호출)가 한다. 쓰기는 shared {@code AiContentWriter} 가
 * 공개 사이트 쓰기 경로와 같은 판정 코드로 한다(그래서 api 서버에는 실행기가 없다).
 *
 * <ul>
 *   <li>{@code irobit.batch.enabled=false} 인 서버, 또는 {@code irobit.ai-member.runner-enabled=false}(환경 변수 {@code IROBIT_AI_MEMBER_RUNNER})인 서버는 돌지 않는다.</li>
 *   <li>한 바퀴({@code irobit.ai-member.poll-millis}, 기본 1분): ① 승인 줄 등록 ② 할 일 생성. 전체 정지 중에는 둘 다 하지 않는다(각 부품이 본다).</li>
 *   <li>관리자 서버를 여러 대(배치를 켠 서버가 둘 이상) 띄워도 DB 어드바이저리 잠금({@link BatchLockService}, 키 {@value #LOCK_KEY})으로 한 바퀴는 한 곳에서만 돈다
 *       (잡지 못한 서버는 그 바퀴를 건너뛴다).</li>
 *   <li>바퀴마다 워커 기록({@code worker_heartbeats}, 키 {@value #LOCK_KEY}) — 일이 없으면 간격마다 한 번만, 실패하면 가린 오류 한 줄. 바퀴의 실패는 로그만 남기고 다음 바퀴로 넘어간다.</li>
 *   <li>제공사 호출이 있어 한 바퀴가 길 수 있다(호출 상한 {@code calls-per-tick} × 응답 제한 시간) — {@code @Scheduled} 스레드 하나를 그동안 쓴다(스레드 풀 {@code spring.task.scheduling.pool.size}).</li>
 * </ul>
 */
@Component
public class AiMemberScheduler {

    private static final Logger log = LoggerFactory.getLogger(AiMemberScheduler.class);

    static final String LOCK_KEY = "ai-member-runner";

    private final BatchProperties batch;
    private final AiMemberProperties properties;
    private final BatchLockService locks;
    private final AiDraftPublisher publisher;
    private final AiDraftGenerator generator;
    private final WorkerHeartbeats heartbeats;

    public AiMemberScheduler(BatchProperties batch, AiMemberProperties properties, BatchLockService locks, AiDraftPublisher publisher, AiDraftGenerator generator,
                             WorkerHeartbeats heartbeats) {
        this.batch = batch;
        this.properties = properties;
        this.locks = locks;
        this.publisher = publisher;
        this.generator = generator;
        this.heartbeats = heartbeats;
    }

    /** @return 이번 바퀴에 등록·생성한 수(돌지 않았으면 -1) */
    @Scheduled(initialDelayString = "${irobit.ai-member.poll-millis:60000}", fixedDelayString = "${irobit.ai-member.poll-millis:60000}")
    public int tick() {
        if (!batch.enabled() || !properties.runnerEnabled()) {
            return -1;
        }
        try (BatchLockService.BatchLock lock = locks.tryAcquire(LOCK_KEY).orElse(null)) {
            if (lock == null) {
                return -1; // 다른 관리자 서버가 돌고 있다
            }
            int published = publisher.publishApproved();
            int generated = generator.runOnce();
            int handled = published + generated;
            if (handled > 0) {
                log.info("AI 회원 실행기 한 바퀴 published={} generated={}", published, generated);
            }
            heartbeats.success(LOCK_KEY, handled);
            return handled;
        } catch (RuntimeException e) {
            log.error("AI 회원 실행기 한 바퀴가 실패했다 원인={}", e.getClass().getSimpleName(), e);
            heartbeats.failure(LOCK_KEY, e);
            return 0;
        }
    }
}
