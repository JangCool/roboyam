package com.community.debate.service;

import com.community.debate.entity.DebateStance;
import com.community.debate.exception.DebateNotOpenException;
import com.community.debate.exception.StanceLockedException;
import com.community.debate.exception.StanceRequiredException;
import com.community.debate.repository.master.DebateQueries;
import com.community.debate.repository.master.PostDebateRepository;
import org.springframework.stereotype.Component;

/**
 * 댓글을 쓸 때의 입장 판정(하이라이트 토론 — docs/features/popular-highlight-contract.md). 의견 쓰기 = 원댓글은 요청의 의견 표시({@code opinion: true} 또는 {@code stance}),
 * 답글은 원댓글이 의견일 때. 입장은 쓰기로 정해지지 않는다(투표로만 — 사용자 결정 2026-10-24). 공개 사이트(api {@code DebateReader#resolveForWrite})와 관리자 서버에서 도는
 * AI 회원 쓰기({@code AiContentWriter})가 같은 판정을 쓰도록 shared 에 둔다(2026-11-10).
 */
@Component
public class DebateWriteStance {

    /** 입장 의견의 글자 수 한도 — community 는 토론 2,000 · 일반 1,000 이었으나 로보얌 일반 댓글이 이미 2,000 이라 같은 값이다(따로 둔 상수) */
    public static final int OPINION_MAX = 2000;

    private final PostDebateRepository debates;
    private final DebateQueries queries;

    public DebateWriteStance(PostDebateRepository debates, DebateQueries queries) {
        this.debates = debates;
        this.queries = queries;
    }

    /**
     * 의견 쓰기가 아니면 null(일반 댓글). 의견 쓰기면 토론이 열려 있어야 하고(409 {@code DEBATE_NOT_OPEN}) 투표한 입장이 있어야 한다(409 {@code STANCE_REQUIRED}) —
     * 요청의 {@code stance} 를 보냈으면 지금 입장과 같아야 한다(409 {@code STANCE_LOCKED}). 비회원의 의견 쓰기는 부르는 쪽이 {@code fields.stance} 로 막는다.
     *
     * @param opinion   의견 쓰기인가
     * @param requested 요청의 {@code stance}(선택)
     * @return 이 댓글에 붙는 입장(일반 댓글이면 null)
     */
    public DebateStance resolve(long postId, long userId, boolean opinion, DebateStance requested) {
        if (!opinion) {
            return null;
        }
        if (debates.findById(postId).isEmpty()) {
            throw new DebateNotOpenException(); // 토론이 열려 있다 = 그 글의 post_debates 행이 있다
        }
        DebateStance current = queries.stanceOf(postId, userId).orElse(null);
        if (current == null) {
            throw new StanceRequiredException();
        }
        if (requested != null && requested != current) {
            throw new StanceLockedException();
        }
        return current;
    }
}
