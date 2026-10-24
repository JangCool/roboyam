package com.community.debate.service;

import com.community.board.service.ContentRules;
import com.community.comment.entity.CommentEntity;
import com.community.debate.dto.response.CommentDebateView;
import com.community.debate.dto.response.DebateCountsView;
import com.community.debate.dto.response.DebateSummaryView;
import com.community.debate.entity.DebateStance;
import com.community.debate.exception.DebateNotOpenException;
import com.community.debate.exception.StanceLockedException;
import com.community.debate.repository.master.DebateQueries;
import com.community.highlight.entity.HighlightEntity;
import com.community.highlight.repository.master.HighlightRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 하이라이트 토론(찬성·반대 의견)의 판정 — 글 상세·댓글 목록·댓글 쓰기가 부른다(docs/features/popular-highlight-contract.md 「서버 구현 결과」, community docs/api-debate.md).
 * <b>DB 를 바로 읽는다</b>(하이라이트 표의 글 하나 유일 키 조회 — 사본 없음): 운영자가 하이라이트를 내리면 다음 요청부터 바로 닫힌다. 트랜잭션은 부르는 쪽이 연다.
 *
 * <ul>
 *   <li><b>하이라이트 글</b>({@code highlighted}): 그 글의 하이라이트가 노출을 켰고 노출 기간 안이다(글이 보이는지는 부르는 쪽이 이미 확인했다).</li>
 *   <li><b>토론이 열려 있다</b>({@code enabled}): 하이라이트 글이면서 「찬성·반대 의견 받기」가 켜져 있다.</li>
 *   <li><b>쓰기의 입장</b>({@link #resolveForWrite}) — community {@code DebateService.resolveForWrite} 의 표 그대로.</li>
 * </ul>
 */
@Component
public class DebateReader {

    /** 입장 배지가 붙는 댓글(토론 댓글)의 글자 수 한도 — community 는 토론 2,000 · 일반 1,000 이었으나 로보얌 일반 댓글이 이미 2,000 이라 같은 값이다(따로 둔 상수) */
    public static final int OPINION_MAX = 2000;

    private final HighlightRepository highlights;
    private final DebateQueries queries;

    public DebateReader(HighlightRepository highlights, DebateQueries queries) {
        this.highlights = highlights;
        this.queries = queries;
    }

    /** 그 글의 하이라이트 상태 */
    public record Status(boolean highlighted, boolean open) {

        static final Status NONE = new Status(false, false);
    }

    /**
     * 쓰기의 입장 해석 결과.
     *
     * @param stance 이 댓글에 붙일 배지(null = 일반 댓글)
     * @param settle 이 댓글로 내 입장이 처음 정해진다 — 같은 트랜잭션에서 {@link #settle} 을 부른다
     */
    public record WriteStance(DebateStance stance, boolean settle) {

        static final WriteStance GENERAL = new WriteStance(null, false);

        /** 이 댓글의 글자 수 한도 */
        public int bodyMax() {
            return stance == null ? ContentRules.COMMENT_MAX : OPINION_MAX;
        }

        public String stanceName() {
            return stance == null ? null : stance.name();
        }
    }

    public Status status(long postId, Instant now) {
        Optional<HighlightEntity> highlight = highlights.findByPostId(postId);
        return highlight.map(h -> new Status(h.isShowingAt(now), h.isDebateOpenAt(now))).orElse(Status.NONE);
    }

    /** 내 입장(비로그인·미정 null) */
    public DebateStance myStance(long postId, Long userId) {
        return userId == null ? null : queries.stanceOf(postId, userId).orElse(null);
    }

    /** 글 상세의 토론 요약 */
    public DebateSummaryView summary(Status status, long postId, Long userId) {
        DebateStance mine = myStance(postId, userId);
        return new DebateSummaryView(status.open(), mine == null ? null : mine.name(), OPINION_MAX);
    }

    /**
     * 댓글 목록의 토론 칸.
     *
     * @param all 그 글의 댓글 전부(목록이 이미 읽은 것) — 내 의견 수({@code myOpinionCount})를 센다
     */
    public CommentDebateView forComments(long postId, Long userId, Instant now, DebateCountsView counts, List<CommentEntity> all) {
        Status status = status(postId, now);
        DebateStance mine = myStance(postId, userId);
        int myOpinions = 0;
        if (mine != null) {
            for (CommentEntity c : all) {
                if (userId.equals(c.getAuthorId()) && !c.isDeleted() && mine.name().equals(c.getStance())) {
                    myOpinions++;
                }
            }
        }
        return new CommentDebateView(status.open(), mine == null ? null : mine.name(), OPINION_MAX, counts, myOpinions);
    }

    /**
     * 쓰기의 입장을 정한다(커밋 전 판정 — 처음 정하는 경합은 {@link #settle} 이 트랜잭션 안에서 다시 본다).
     * <table>
     *   <tr><th>요청 stance</th><th>토론</th><th>내 입장</th><th>결과</th></tr>
     *   <tr><td>있음</td><td>닫힘</td><td>—</td><td>409 DEBATE_NOT_OPEN</td></tr>
     *   <tr><td>있음</td><td>열림</td><td>없음</td><td>그 입장으로 쓰고 내 입장이 정해진다</td></tr>
     *   <tr><td>있음</td><td>열림</td><td>같음</td><td>그 입장</td></tr>
     *   <tr><td>있음</td><td>열림</td><td>다름</td><td>409 STANCE_LOCKED</td></tr>
     *   <tr><td>없음</td><td>열림</td><td>있음</td><td>현재 입장을 배지로(답글 포함)</td></tr>
     *   <tr><td>없음</td><td>—</td><td>그 밖</td><td>일반 댓글</td></tr>
     * </table>
     *
     * @param userId 회원(비회원 null — 비회원은 입장을 보낼 수 없다: 부르는 쪽이 {@code fields.stance} 로 막는다)
     */
    public WriteStance resolveForWrite(long postId, Long userId, DebateStance requested, Instant now) {
        boolean open = status(postId, now).open();
        if (requested != null && !open) {
            throw new DebateNotOpenException();
        }
        if (!open || userId == null) {
            return WriteStance.GENERAL;
        }
        DebateStance current = myStance(postId, userId);
        if (requested == null) {
            return current == null ? WriteStance.GENERAL : new WriteStance(current, false);
        }
        if (current == null) {
            return new WriteStance(requested, true);
        }
        if (current != requested) {
            throw new StanceLockedException();
        }
        return new WriteStance(requested, false);
    }

    /** 처음 입장을 정한다 — <b>쓰기 트랜잭션 안에서</b>. 같은 회원의 다른 입장 쓰기가 먼저 정했으면 409(댓글도 롤백된다) */
    public void settle(long postId, long userId, WriteStance write, Instant now) {
        if (!write.settle()) {
            return;
        }
        DebateStance stored = queries.settle(postId, userId, write.stance(), now);
        if (stored != write.stance()) {
            throw new StanceLockedException();
        }
    }
}
