package com.community.poll.service;

import com.community.board.dto.BoardActor;
import com.community.board.service.BoardAccessPolicy;
import com.community.board.service.BoardActorResolver;
import com.community.board.service.BoardCatalog;
import com.community.poll.dto.PollData;
import com.community.poll.dto.PollOptionData;
import com.community.poll.dto.response.PollOptionView;
import com.community.poll.dto.response.PollResultView;
import com.community.poll.dto.response.PollView;
import com.community.poll.repository.master.PollQueries;
import com.community.post.entity.PostEntity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 글 투표의 보기({@link PollView}) — 글 상세·{@code GET /api/posts/{id}/poll}·투표 응답이 같은 모양을 쓴다. docs/features/post-poll-contract.md.
 * <b>DB 를 바로 읽는다</b>(사본 없음). 트랜잭션은 부르는 쪽이 연다.
 *
 * <ul>
 *   <li><b>결과 공개</b>: 투표한 사람은 바로, 마감되면 모두(투표 안 한 회원·비회원 포함). 그 전에는 결과·참여 수를 내지 않는다(null).</li>
 *   <li><b>투표 자격</b>({@link #memberBlocked}): 로그인 · 정지 아님 · 이메일 인증 — 회원 판정은 글쓰기·댓글 판정과 같은 {@link BoardActorResolver}·{@link BoardAccessPolicy}
 *       까닭 이름을 쓴다(게시판의 등급·잠금·역할 권한은 투표에 걸지 않는다 — 계약 「규칙」의 세 가지만).</li>
 *   <li><b>익명</b>: 내 선택({@code myOptionIds})과 집계만 — 누가 무엇을 골랐는지는 없다.</li>
 * </ul>
 */
@Component
public class PollReader {

    public static final String CLOSED = "CLOSED";

    private final PollQueries queries;
    private final BoardActorResolver actors;
    private final BoardCatalog catalog;

    public PollReader(PollQueries queries, BoardActorResolver actors, BoardCatalog catalog) {
        this.queries = queries;
        this.actors = actors;
        this.catalog = catalog;
    }

    /** 글 상세의 {@code poll} — 투표가 없으면 null */
    public PollView forPost(PostEntity post, Long userId, Instant now) {
        return queries.find(post.getId()).map(poll -> view(poll, post, userId, null, now)).orElse(null);
    }

    /**
     * @param actor 이미 판정한 보는 사람(없으면 null — 필요할 때만 읽는다)
     */
    public PollView view(PollData poll, PostEntity post, Long userId, BoardActor actor, Instant now) {
        List<PollOptionData> options = queries.options(poll.id());
        List<String> mine = userId == null ? List.of() : queries.myOptionIds(poll.id(), userId).stream().map(String::valueOf).toList();
        boolean voted = !mine.isEmpty();
        boolean closed = poll.closedAt(now);
        boolean mayVoteAgain = !voted || poll.changeAllowed();

        String blocked = null;
        if (closed) {
            blocked = CLOSED;
        } else if (mayVoteAgain) {
            blocked = memberBlocked(actor != null ? actor : actorOf(userId));
        }
        boolean canVote = !closed && mayVoteAgain && blocked == null;

        int voters = queries.voterCount(poll.id());
        boolean resultsVisible = voted || closed;
        List<PollResultView> results = null;
        if (resultsVisible) {
            Map<Long, Integer> counts = queries.votesByOption(poll.id());
            results = options.stream().map(o -> new PollResultView(Long.toString(o.id()), counts.getOrDefault(o.id(), 0))).toList();
        }
        boolean editable = userId != null && userId.equals(post.getAuthorId()) && voters == 0;
        return new PollView(Long.toString(poll.id()), poll.question(), poll.multiple(), poll.changeAllowed(), poll.closesAt(), closed,
                options.stream().map(o -> new PollOptionView(Long.toString(o.id()), o.label())).toList(), voted, mine, canVote, blocked, resultsVisible,
                resultsVisible ? voters : null, results, editable);
    }

    /**
     * 투표할 수 없는 회원 까닭(투표할 수 있으면 null) — 비회원(탈퇴 포함) {@code LOGIN_REQUIRED} → 정지·영구 정지 {@code ACCOUNT_RESTRICTED}
     * → 이메일 인증 전 {@code EMAIL_NOT_VERIFIED}({@link BoardAccessPolicy} 판정 차례와 같다).
     */
    public static String memberBlocked(BoardActor actor) {
        if (actor == null || actor.guest()) {
            return BoardAccessPolicy.LOGIN_REQUIRED;
        }
        if (actor.restricted()) {
            return BoardAccessPolicy.ACCOUNT_RESTRICTED;
        }
        if (!actor.emailVerified()) {
            return BoardAccessPolicy.EMAIL_NOT_VERIFIED;
        }
        return null;
    }

    private BoardActor actorOf(Long userId) {
        return userId == null ? BoardActor.anonymous() : actors.resolve(userId, catalog.snapshot());
    }
}
