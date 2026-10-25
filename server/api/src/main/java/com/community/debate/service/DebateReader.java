package com.community.debate.service;

import com.community.board.dto.EffectiveBoard;
import com.community.board.service.ContentRules;
import com.community.comment.entity.CommentEntity;
import com.community.debate.dto.DebateVotesView;
import com.community.debate.dto.response.CommentDebateView;
import com.community.debate.dto.response.DebateCountsView;
import com.community.debate.dto.response.DebateSummaryView;
import com.community.debate.entity.DebateStance;
import com.community.debate.entity.PostDebateEntity;
import com.community.debate.exception.DebateNotOpenException;
import com.community.debate.exception.StanceLockedException;
import com.community.debate.exception.StanceRequiredException;
import com.community.debate.repository.master.DebateQueries;
import com.community.debate.repository.master.PostDebateRepository;
import com.community.highlight.repository.master.HighlightRepository;
import com.community.post.entity.PostEntity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 글의 찬반 토론(찬성·반대·중립 의견)의 판정 — 글 상세·댓글 목록·댓글 쓰기·입장 고르기·토론 설정이 부른다(docs/features/post-debate-contract.md,
 * 토론 안의 규칙은 popular-highlight-contract.md 「서버 구현 결과」). <b>DB 를 바로 읽는다</b>(글 하나 유일 키 조회 — 사본 없음): 닫으면 다음 요청부터 바로 닫힌다.
 * 트랜잭션은 부르는 쪽이 연다.
 *
 * <ul>
 *   <li><b>하이라이트 글</b>({@code highlighted}): 그 글의 하이라이트가 노출을 켰고 노출 기간 안이다(글이 보이는지는 부르는 쪽이 이미 확인했다). 토론과 무관하다.</li>
 *   <li><b>토론이 열려 있다</b>({@code enabled}): 그 글의 {@code post_debates} 행이 있다(하이라이트·노출과 무관).</li>
 *   <li><b>토론 설정</b>({@code canManage}): 그 게시판 매니저, 또는 글쓴이(회원 글 + 게시판 정책 {@code debateEnabled} + 토론이 없거나 글쓴이가 연 토론).
 *       닫기({@code canClose})는 열려 있을 때 — 글쓴이는 투표가 없을 때만.</li>
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
    private final PostDebateRepository debates;
    private final DebateQueries queries;
    private final PinReviewers pinReviewers;

    public DebateReader(HighlightRepository highlights, PostDebateRepository debates, DebateQueries queries, PinReviewers pinReviewers) {
        this.highlights = highlights;
        this.debates = debates;
        this.queries = queries;
        this.pinReviewers = pinReviewers;
    }

    /**
     * 그 글의 하이라이트·토론 상태.
     *
     * @param highlighted 노출 중인 하이라이트 글(토론과 무관)
     * @param debate      그 글의 토론({@code post_debates} — 닫혀 있으면 null)
     */
    public record Status(boolean highlighted, PostDebateEntity debate) {

        /** 토론이 열려 있다 */
        public boolean open() {
            return debate != null;
        }

        /** 「중립도 받기」 — 닫혀 있으면 true(기본값) */
        public boolean neutralEnabled() {
            return debate == null || debate.isNeutralEnabled();
        }

        /** 「입장 바꾸기 허용」 — 닫혀 있으면 true(기본값 — 닫혀 있어 어차피 고를 수 없다) */
        public boolean stanceChangeAllowed() {
            return debate == null || debate.isStanceChangeAllowed();
        }

        /** 연 사람 MEMBER · OPERATOR(매니저가 연 것 포함) — 닫혀 있으면 null */
        public String openedBy() {
            return debate == null ? null : debate.openedByView();
        }
    }

    /**
     * 지금 보는 사람의 토론 설정 자격({@code PUT /api/posts/{id}/debate}).
     *
     * @param manager 그 게시판 매니저(어느 글이든 열고 바꾸고 닫는다 — 투표가 있어도 닫는다)
     * @param author  글쓴이 규칙을 채운다(회원 글 + 게시판 정책 {@code debateEnabled} + 토론이 없거나 글쓴이가 연 토론)
     */
    public record Manage(boolean manager, boolean author) {

        static final Manage NONE = new Manage(false, false);

        public boolean canManage() {
            return manager || author;
        }
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
        boolean highlighted = highlights.findByPostId(postId).map(h -> h.isShowingAt(now)).orElse(false);
        return new Status(highlighted, debates.findById(postId).orElse(null));
    }

    /** 이 사람이 이 글의 토론 설정을 바꿀 수 있는가 — 비로그인은 없음. 매니저 판정은 고정 요청 확인과 같은 코드({@link PinReviewers#isBoardManager}) */
    public Manage manage(Status status, PostEntity post, EffectiveBoard b, Long userId) {
        if (userId == null || b == null) {
            return Manage.NONE;
        }
        boolean manager = pinReviewers.isBoardManager(b.id(), userId);
        boolean author = userId.equals(post.getAuthorId()) && b.debateEnabled() && (status.debate() == null || status.debate().openedByMember());
        return new Manage(manager, author);
    }

    /** 닫을 수 있는가 — 열려 있고, 매니저이거나 글쓴이면 투표가 없을 때 */
    private static boolean canClose(Status status, Manage m, DebateVotesView votes) {
        return status.open() && (m.manager() || (m.author() && votes.total() == 0));
    }

    /** 내 입장(비로그인·미정 null) */
    public DebateStance myStance(long postId, Long userId) {
        return userId == null ? null : queries.stanceOf(postId, userId).orElse(null);
    }

    /** 글 상세의 토론 요약 */
    public DebateSummaryView summary(Status status, PostEntity post, EffectiveBoard b, Long userId, Instant now) {
        DebateStance mine = myStance(post.getId(), userId);
        DebateVotesView votes = queries.votesOf(post.getId());
        Integer pending = status.open() || votes.total() > 0 ? pinReviewers.pendingFor(b, post, userId, now, null) : null;
        Manage m = manage(status, post, b, userId);
        return new DebateSummaryView(status.open(), mine == null ? null : mine.name(), OPINION_MAX, status.neutralEnabled(), status.stanceChangeAllowed(),
                votes, pending, pinRequestEnabled(b), status.openedBy(), m.canManage(), canClose(status, m, votes));
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
        Integer pending = status.open() || votes.total() > 0 ? pinReviewers.pendingFor(b, post, userId, now, byId) : null;
        Manage m = manage(status, post, b, userId);
        return new CommentDebateView(status.open(), mine == null ? null : mine.name(), OPINION_MAX, status.neutralEnabled(), status.stanceChangeAllowed(), votes, counts,
                myOpinions, pending, pinRequestEnabled(b), status.openedBy(), m.canManage(), canClose(status, m, votes));
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
