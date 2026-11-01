package com.community.hotdeal.service;

import com.community.hotdeal.repository.master.DealEndRequestStore;
import com.community.hotdeal.repository.master.DealQueries;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴 때의 핫딜(서버 단계 고정 규칙 6) — <b>공개 탈퇴 API 가 아직 없어 부르는 곳이 없다</b>({@code BusinessWithdrawal} 과 같은 자리). 탈퇴 API(docs/domain/commerce.md 17절)가 생기면
 * 탈퇴 트랜잭션 안에서 {@link #withdraw} 를 부른다.
 *
 * <ul>
 *   <li>탈퇴 작성자의 진행 중 딜(저장 상태 ACTIVE — 의심·기간 지남 포함) = <b>작성자 종료</b>(의심이었으면 의심 구간 요청자 보상 — 종료 경로가 같다).</li>
 *   <li>탈퇴 회원이 보낸 종료 요청 줄은 지운다 — <b>이미 의심이 된 딜의 상태는 그대로</b>(의심 시각·keep 은 딜 행에 있다).</li>
 *   <li>정지 회원의 딜은 그대로다(정지는 이 길을 부르지 않는다).</li>
 * </ul>
 */
@Service
public class DealWithdrawal {

    private final DealQueries queries;
    private final DealEndRequestStore requests;
    private final DealEndService ends;

    public DealWithdrawal(DealQueries queries, DealEndRequestStore requests, DealEndService ends) {
        this.queries = queries;
        this.requests = requests;
        this.ends = ends;
    }

    /** 결과 — 작성자 종료한 딜 수 · 지운 요청 줄 수 */
    public record Result(int endedDeals, int removedRequests) {
    }

    @Transactional
    public Result withdraw(long userId) {
        Instant now = ends.now();
        int ended = 0;
        for (Long postId : queries.activeByAuthor(userId)) {
            ended += ends.lock(postId).map(d -> ends.endByAuthor(d, userId, now) ? 1 : 0).orElse(0);
        }
        return new Result(ended, requests.deleteForMember(userId));
    }
}
