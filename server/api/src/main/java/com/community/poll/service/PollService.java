package com.community.poll.service;

import com.community.board.dto.BoardActor;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.exception.NotAuthorException;
import com.community.board.service.BoardActorResolver;
import com.community.board.service.BoardCatalog;
import com.community.board.service.PublicBoardService;
import com.community.poll.dto.PollData;
import com.community.poll.dto.PollOptionData;
import com.community.poll.dto.request.PollVoteRequest;
import com.community.poll.dto.response.PollView;
import com.community.poll.exception.AlreadyVotedException;
import com.community.poll.exception.PollClosedException;
import com.community.poll.exception.PollLockedException;
import com.community.poll.exception.PollNotFoundException;
import com.community.poll.repository.master.PollQueries;
import com.community.post.entity.PostEntity;
import com.community.post.exception.PostHiddenException;
import com.community.post.service.PostQueryService;
import com.community.post.service.PostWriteService;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 글 투표의 공개 API — 투표만 읽기 · 투표하기 · 글쓴이의 투표 지우기. 붙이기·고치기는 글쓰기·고치기({@link PollWriter}). docs/features/post-poll-contract.md.
 *
 * <ul>
 *   <li><b>읽기</b>({@code GET /api/posts/{id}/poll}): 글 읽기 규칙(404 POST_NOT_FOUND · 403 BOARD_INACTIVE · 403 POST_HIDDEN) → 투표 없음 404 POLL_NOT_FOUND.
 *       조회수를 올리지 않는다.</li>
 *   <li><b>투표하기</b>({@code POST /api/posts/{id}/poll/votes}): 로그인(401) → 글 읽기 규칙 → 자격(정지 403 ACCOUNT_RESTRICTED · 이메일 인증 전 403 EMAIL_NOT_VERIFIED)
 *       → 투표 행 {@code FOR SHARE}(없으면 404 POLL_NOT_FOUND) → 마감 409 POLL_CLOSED → 고른 선택지(단일 = 정확히 1개, 복수 = 1개 이상, 중복·남의 선택지 400 {@code fields.optionIds})
 *       → 같은 사람 요청을 줄 세우고: 처음이면 넣기 · 같은 선택이면 그대로(200, 멱등) · 다른 선택이면 「다시 투표 허용」일 때 바꾸기, 아니면 409 ALREADY_VOTED.
 *       글쓴이도 투표할 수 있다. 응답은 투표 뒤의 {@link PollView}. 감사·알림 없음(회원 행위).</li>
 *   <li><b>지우기</b>({@code DELETE /api/posts/{id}/poll}): 로그인(401) → 글 읽기 규칙 → 투표 행 {@code FOR UPDATE}(없으면 404 POLL_NOT_FOUND) → 글쓴이만(아니면 403 FORBIDDEN)
 *       → 표가 있으면 409 POLL_LOCKED. 운영자는 관리자 앱에서 표가 있어도 지운다.</li>
 * </ul>
 */
@Service
public class PollService {

    private static final Logger log = LoggerFactory.getLogger(PollService.class);

    private final PollQueries queries;
    private final PollReader reader;
    private final PostQueryService posts;
    private final BoardCatalog catalog;
    private final BoardActorResolver actors;

    public PollService(PollQueries queries, PollReader reader, PostQueryService posts, BoardCatalog catalog, BoardActorResolver actors) {
        this.queries = queries;
        this.reader = reader;
        this.posts = posts;
        this.catalog = catalog;
        this.actors = actors;
    }

    @Transactional(readOnly = true)
    public PollView read(String postIdText, Long userId) {
        Instant now = Instant.now();
        PostEntity post = readable(postIdText, catalog.snapshot(), now);
        PollData poll = queries.find(post.getId()).orElseThrow(PollNotFoundException::new);
        return reader.view(poll, post, userId, null, now);
    }

    @Transactional
    public PollView vote(String postIdText, PollVoteRequest body, Long userId) {
        if (userId == null) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        Instant now = Instant.now();
        BoardCatalogSnapshot s = catalog.snapshot();
        PostEntity post = readable(postIdText, s, now);
        BoardActor actor = actors.resolve(userId, s);
        PostWriteService.requireWritable(PollReader.memberBlocked(actor)); // 탈퇴 등 비회원으로 보이면 401, 나머지 403 까닭 코드

        PollData poll = queries.findForShare(post.getId()).orElseThrow(PollNotFoundException::new);
        if (poll.closedAt(now)) {
            throw new PollClosedException();
        }
        Set<Long> chosen = chosen(body == null ? null : body.optionIds(), poll);
        queries.lockVoter(poll.id(), userId);
        Set<Long> existing = new LinkedHashSet<>(queries.myOptionIds(poll.id(), userId));
        if (existing.isEmpty()) {
            queries.replaceVotes(poll.id(), userId, chosen, now);
        } else if (!existing.equals(chosen)) {
            if (!poll.changeAllowed()) {
                throw new AlreadyVotedException();
            }
            queries.replaceVotes(poll.id(), userId, chosen, now); // 다시 투표 — 취소는 없고 다른 선택으로 바꾸기만
        }
        return reader.view(poll, post, userId, actor, now);
    }

    @Transactional
    public void delete(String postIdText, Long userId) {
        if (userId == null) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        Instant now = Instant.now();
        PostEntity post = readable(postIdText, catalog.snapshot(), now);
        PollData poll = queries.findForUpdate(post.getId()).orElseThrow(PollNotFoundException::new);
        if (!userId.equals(post.getAuthorId())) {
            throw new NotAuthorException();
        }
        if (queries.hasVotes(poll.id())) {
            throw new PollLockedException();
        }
        queries.delete(poll.id());
        log.info("글 투표 지우기(글쓴이) postId={} pollId={} by userId={}", post.getId(), poll.id(), userId);
    }

    /** 글 읽기 규칙 — 없거나 지운 글 404, 꺼진 게시판 403 BOARD_INACTIVE, 숨긴 글 403 POST_HIDDEN */
    private PostEntity readable(String postIdText, BoardCatalogSnapshot s, Instant now) {
        PostEntity post = posts.requireVisible(postIdText, now);
        PublicBoardService.requireActive(s, post.getBoardId());
        if (post.isHiddenAt(now)) {
            throw new PostHiddenException();
        }
        return post;
    }

    /** 고른 선택지 — 비었거나 · 중복 · 이 투표의 선택지가 아니거나 · 단일 선택에 둘 이상이면 400 {@code fields.optionIds} */
    private Set<Long> chosen(List<String> raw, PollData poll) {
        Set<Long> chosen = new LinkedHashSet<>();
        for (String id : raw == null ? List.<String>of() : raw) {
            Long parsed = PostQueryService.parseId(id);
            if (parsed == null || !chosen.add(parsed)) {
                throw ValidationException.of("optionIds", new MessageRef("poll.optionIds.invalid"));
            }
        }
        if (chosen.isEmpty()) {
            throw ValidationException.of("optionIds", new MessageRef("poll.optionIds.required"));
        }
        if (!poll.multiple() && chosen.size() != 1) {
            throw ValidationException.of("optionIds", new MessageRef("poll.optionIds.single"));
        }
        Set<Long> valid = queries.options(poll.id()).stream().map(PollOptionData::id).collect(Collectors.toSet());
        if (!valid.containsAll(chosen)) {
            throw ValidationException.of("optionIds", new MessageRef("poll.optionIds.invalid"));
        }
        return chosen;
    }
}
