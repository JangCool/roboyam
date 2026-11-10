package com.community.aimember.service;

import com.community.batch.core.BatchLockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * AI 회원 실행기의 「언제 돌릴지」(ai-members-contract 정책 11 — 서버 단계 결정 2026-11-10). <b>api 서버</b>에서 돈다: 글쓰기 경로({@code PostWriteService}·
 * {@code CommentService}·{@code GroupPostService})가 api 에 있고 api ↔ admin 은 서로 의존하지 않으므로, 쓰기 핵심을 shared 로 옮기지 않고 등록을 api 가 맡는다.
 * api 서버가 2~3대여도 DB 어드바이저리 잠금({@link BatchLockService}, 키 {@value #LOCK_KEY})으로 한 바퀴는 한 곳에서만 돈다(잡지 못한 서버는 그 바퀴를 건너뛴다).
 *
 * <p>한 바퀴({@code roboyam.ai-member.poll-millis}, 기본 1분): ① 승인 줄 등록({@link AiDraftPublisher} — 관리자 「게시」는 상태만 바꾼다) ② 할 일 생성({@link AiDraftGenerator}).
 * {@code roboyam.ai-member.runner-enabled=false} 인 서버는 돌지 않는다. 바퀴의 실패는 로그만 남기고 다음 바퀴로 넘어간다.
 */
@Component
public class AiMemberScheduler {

    private static final Logger log = LoggerFactory.getLogger(AiMemberScheduler.class);

    static final String LOCK_KEY = "ai-member-runner";

    private final AiMemberProperties properties;
    private final BatchLockService locks;
    private final AiDraftPublisher publisher;
    private final AiDraftGenerator generator;

    public AiMemberScheduler(AiMemberProperties properties, BatchLockService locks, AiDraftPublisher publisher, AiDraftGenerator generator) {
        this.properties = properties;
        this.locks = locks;
        this.publisher = publisher;
        this.generator = generator;
    }

    /** @return 이번 바퀴에 등록·생성한 수(돌지 않았으면 -1) */
    @Scheduled(initialDelayString = "${roboyam.ai-member.poll-millis:60000}", fixedDelayString = "${roboyam.ai-member.poll-millis:60000}")
    public int tick() {
        if (!properties.runnerEnabled()) {
            return -1;
        }
        try (BatchLockService.BatchLock lock = locks.tryAcquire(LOCK_KEY).orElse(null)) {
            if (lock == null) {
                return -1; // 다른 서버가 돌고 있다
            }
            int published = publisher.publishApproved();
            int generated = generator.runOnce();
            if (published + generated > 0) {
                log.info("AI 회원 실행기 한 바퀴 published={} generated={}", published, generated);
            }
            return published + generated;
        } catch (RuntimeException e) {
            log.error("AI 회원 실행기 한 바퀴가 실패했다 원인={}", e.getClass().getSimpleName(), e);
            return 0;
        }
    }
}
