package com.community.debate.service;

import com.community.board.dto.EffectiveBoard;
import com.community.board.service.ContentRules;
import com.community.comment.entity.CommentEntity;
import com.community.debate.dto.DebateVotesView;
import com.community.debate.dto.response.CommentDebateView;
import com.community.debate.dto.response.DebateCountsView;
import com.community.debate.dto.response.DebateSummaryView;
import com.community.debate.entity.DebateStance;
import com.community.debate.exception.DebateNotOpenException;
import com.community.debate.exception.StanceLockedException;
import com.community.debate.exception.StanceRequiredException;
import com.community.debate.repository.master.DebateQueries;
import com.community.highlight.entity.HighlightEntity;
import com.community.highlight.repository.master.HighlightRepository;
import com.community.post.entity.PostEntity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 하이라이트 토론(찬성·반대·중립 의견)의 판정 — 글 상세·댓글 목록·댓글 쓰기·입장 고르기가 부른다(docs/features/popular-highlight-contract.md 「서버 구현 결과」).
 * <b>DB 를 바로 읽는다</b>(하이라이트 표의 글 하나 유일 키 조회 — 사본 없음): 운영자가 하이라이트를 내리면 다음 요청부터 바로 닫힌다. 트랜잭션은 부르는 쪽이 연다.
 *
 * <ul>
 *   <li><b>하이라이트 글</b>({@code highlighted}): 그 글의 하이라이트가 노출을 켰고 노출 기간 안이다(글이 보이는지는 부르는 쪽이 이미 확인했다).</li>
 *   <li><b>토론이 열려 있다</b>({@code enabled}): 하이라이트 글이면서 「찬성·반대 의견 받기」가 켜져 있다.</li>
 *   <li><b>입장 = 투표</b>(사용자 결정 2026-10-24): 입장은 {@code PUT /api/posts/{id}/stance} 로만 고르고 바꾼다. 댓글 쓰기는 입장을 정하지 않는다.</li>
 *   <li><b>의견과 일반 댓글은 따로</b>: 의견 표시({@code opinion: true} 또는 {@code stance})가 있는 원댓글과 의견에 단 답글만 입장 의견이다({@link #resolveForWrite}).
 *       그 밖은 입장을 가진 회원이 써도 일반 댓글(배지 없음).</li>
 * </ul>
 */
@Component
public class DebateReader {

    /** 입장 의견의 글자 수 한도 — community 는 토론 2,000 · 일반 1,000 이었으나 로보얌 일반 댓글이 이미 2,000 이라 같은 값이다(따로 둔 상수) */
    public static final int OPINION_MAX = 2000;

    private final HighlightRepository highlights;
    private final DebateQueries queries;
    private final PinReviewers pinReviewers;

    public DebateReader(HighlightRepository highlights, DebateQueries queries, PinReviewers pinReviewers) {
        this.highlights = highlights;
        this.queries = queries;
        this.pinReviewers = pinReviewers;
    }

    /**
     * 그 글의 하이라이트 상태.
     *
     * @param neutralEnabled 하이라이트의 「중립도 받기」 — 하이라이트가 없으면 false
     * @param exists         그 글의 하이라이트 행이 있다(노출과 무관 — 고정 요청 수를 셀지)
     */
    public record Status(boolean highlighted, boolean open, boolean neutralEnabled, boolean exists) {

        static final Status NONE = new Status(false, false, false, false);
    }

    /**
     * 쓰기의 입장 해석 결과.
     *
     * @param stance 이 댓글에 붙일 배지(null = 일반 댓글)
     */
    public record WriteStance(DebateStance stance) {

        public static final WriteStance GENERAL = new WriteStance(null);

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
        return highlight.map(h -> new Status(h.isShowingAt(now), h.isDebateOpenAt(now), h.isNeutralEnabled(), true)).orElse(Status.NONE);
    }

    /** 내 입장(비로그인·미정 null) */
    public DebateStance myStance(long postId, Long userId) {
        return userId == null ? null : queries.stanceOf(postId, userId).orElse(null);
    }

    /** 글 상세의 토론 요약 */
    public DebateSummaryView summary(Status status, PostEntity post, EffectiveBoard b, Long userId, Instant now) {
        DebateStance mine = myStance(post.getId(), userId);
        Integer pending = status.exists() ? pinReviewers.pendingFor(b, post, userId, now, null) : null;
        return new DebateSummaryView(status.open(), mine == null ? null : mine.name(), OPINION_MAX, status.neutralEnabled(), queries.votesOf(post.getId()), pending,
                pinRequestEnabled(b));
    }

    /**
     * 댓글 목록의 토론 칸.
     *
     * @param all  그 글의 댓글 전부(목록이 이미 읽은 것) — 내 의견 수({@code myOpinionCount})를 센다
     * @param byId 같은 댓글을 ID 로
     */
    public CommentDebateView forComments(PostEntity post, EffectiveBoard b, Long userId, Instant now, DebateCountsView counts, List<CommentEntity> all,
                                         Map<Long, CommentEntity> byId) {
        long postId = post.getId();
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
        DebateVotesView votes = queries.votesOf(postId);
        Integer pending = status.exists() ? pinReviewers.pendingFor(b, post, userId, now, byId) : null;
        return new CommentDebateView(status.open(), mine == null ? null : mine.name(), OPINION_MAX, status.neutralEnabled(), votes, counts, myOpinions, pending,
                pinRequestEnabled(b));
    }

    /** 이 게시판이 의견 고정 요청을 받는가 — 유효 정책 {@code pinRequestReviewers} 가 비어 있지 않다({@code PIN_REQUEST_OFF} 판정과 같은 기준) */
    private static boolean pinRequestEnabled(EffectiveBoard b) {
        return b != null && !b.pinRequestReviewers().isEmpty();
    }

    /**
     * 쓰기의 입장을 정한다(커밋 전 판정).
     * <table>
     *   <tr><th>의견 쓰기?</th><th>토론</th><th>내 입장</th><th>결과</th></tr>
     *   <tr><td>아니오</td><td>—</td><td>—</td><td>일반 댓글(입장이 있어도 배지 없음)</td></tr>
     *   <tr><td>예</td><td>닫힘</td><td>—</td><td>409 DEBATE_NOT_OPEN</td></tr>
     *   <tr><td>예</td><td>열림</td><td>없음</td><td>409 STANCE_REQUIRED(먼저 투표)</td></tr>
     *   <tr><td>예</td><td>열림</td><td>있음, 요청 stance 가 다름</td><td>409 STANCE_LOCKED</td></tr>
     *   <tr><td>예</td><td>열림</td><td>있음</td><td>내 지금 입장을 배지로</td></tr>
     * </table>
     * 의견 쓰기 = 원댓글은 요청의 의견 표시({@code opinion: true} 또는 {@code stance}), 답글은 원댓글이 의견일 때. 비회원의 의견 쓰기는 부르는 쪽이 {@code fields.stance} 로 막는다.
     *
     * @param opinion   의견 쓰기인가
     * @param requested 요청의 {@code stance}(선택 — 보냈으면 지금 입장과 같아야 한다)
     */
    public WriteStance resolveForWrite(long postId, long userId, boolean opinion, DebateStance requested, Instant now) {
        if (!opinion) {
            return WriteStance.GENERAL;
        }
        if (!status(postId, now).open()) {
            throw new DebateNotOpenException();
        }
        DebateStance current = myStance(postId, userId);
        if (current == null) {
            throw new StanceRequiredException();
        }
        if (requested != null && requested != current) {
            throw new StanceLockedException();
        }
        return new WriteStance(current);
    }
}
