package com.community.popular.service;

import com.community.batch.core.BatchCatalog;
import com.community.batch.core.BatchRequestQueue;
import org.springframework.stereotype.Component;

/**
 * 인기글 순위를 「바로 다시 계산」 — 관리자의 설정 저장·빼기·다시 넣기가 같은 트랜잭션 안에서 부른다. DB 배치 {@code popular-rank} 의 「지금 실행」 요청 한 줄을
 * 요청 표({@code batch_run_requests})에 넣고, 배치를 켠 서버가 집어 저장 프로시저를 {@code CALL} 한다(docs/features/batch-contract.md 「0-2 보강」 — pg_cron 이 없어도 돈다).
 * community 의 「커밋 뒤 이 서버가 즉시 계산」(30초 Java 스케줄러)은 옮기지 않았다 — 응답 시점에 순위가 이미 바뀌어 있다는 보장은 없고 보통 몇 초 안에 바뀐다.
 *
 * <p>끝나지 않은 요청이 이미 있으면 넣지 않는다 — 대기 중인 요청은 커밋된 새 값으로 계산한다. 실행 중이던 요청·정기 실행은 옛 값으로 돌았을 수 있어 다음 정기 실행(기본 5분)이 따라온다.
 */
@Component
public class PopularRecompute {

    private final BatchRequestQueue queue;

    public PopularRecompute(BatchRequestQueue queue) {
        this.queue = queue;
    }

    /** @return 새로 넣었으면 true(이미 끝나지 않은 요청이 있으면 false) */
    public boolean request(long operatorId) {
        return queue.enqueueIfIdle(BatchCatalog.POPULAR_RANK.key(), operatorId).isPresent();
    }
}
