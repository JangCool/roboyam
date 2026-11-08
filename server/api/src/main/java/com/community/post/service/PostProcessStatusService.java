package com.community.post.service;

import com.community.board.dto.BoardActor;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.EffectiveBoard;
import com.community.board.service.BoardActorResolver;
import com.community.board.service.BoardCatalog;
import com.community.group.service.GroupPostGate;
import com.community.post.dto.request.PostProcessStatusRequest;
import com.community.post.dto.response.PostProcessStatusView;
import com.community.post.entity.PostEntity;
import com.community.post.entity.PostProcessStatus;
import com.community.post.exception.PostHiddenException;
import com.community.post.exception.PostNotFoundException;
import com.community.post.repository.master.PostRepository;
import com.community.sanction.exception.BoardSanctionException;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 글의 처리 상태 바꾸기(공개 — {@code PUT /api/posts/{id}/process-status}, V166 운영 게시판). 규칙 몸통은 shared {@link PostProcessStatusChanger}(관리자 앱과 같다).
 *
 * <p>검사 순서: 로그인(401 {@code UNAUTHENTICATED}) → 글(없거나 지움 404 {@code POST_NOT_FOUND}) → 글 열기 판정({@link GroupPostGate#resolve} — 꺼진 게시판 403 ·
 * 19금·비밀글 403) → 숨긴 글 403 {@code POST_HIDDEN} → 그 게시판 매니저·스텝·운영자 회원 계정(정지 중이 아님)만(아니면 403 {@code BOARD_STAFF_FORBIDDEN} — 게시판 스텝 처리와 같은 코드)
 * → 값(400 {@code fields.status}) → 게시판이 상태를 쓰지 않음(409 {@code POST_STATUS_DISABLED}). 글 행을 잠그고 바꾼다. 바뀌면 글쓴이에게 회원 알림 {@code POST_STATUS_CHANGED}
 * (바꾼 사람이 글쓴이 본인이면 보내지 않는다).
 */
@Service
public class PostProcessStatusService {

    private static final Logger log = LoggerFactory.getLogger(PostProcessStatusService.class);

    private final BoardCatalog catalog;
    private final BoardActorResolver actors;
    private final PostQueryService queries;
    private final PostRepository posts;
    private final GroupPostGate groupGate;
    private final PostProcessStatusChanger changer;

    public PostProcessStatusService(BoardCatalog catalog, BoardActorResolver actors, PostQueryService queries, PostRepository posts, GroupPostGate groupGate,
                                    PostProcessStatusChanger changer) {
        this.catalog = catalog;
        this.actors = actors;
        this.queries = queries;
        this.posts = posts;
        this.groupGate = groupGate;
        this.changer = changer;
    }

    @Transactional
    public PostProcessStatusView change(String idText, PostProcessStatusRequest body, Long userId) {
        if (userId == null) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        Instant now = Instant.now();
        PostEntity post = queries.requireVisible(idText, now);
        BoardCatalogSnapshot s = catalog.snapshot();
        EffectiveBoard b = groupGate.resolve(s, post, userId).board();
        if (post.isHiddenAt(now)) {
            throw new PostHiddenException();
        }
        BoardActor actor = actors.resolve(userId, s);
        if (actor.restricted() || !actor.staffOf(b.id())) {
            throw BoardSanctionException.forbidden();
        }
        PostProcessStatus status = PostProcessStatusChanger.parse(body == null ? null : body.status());
        PostEntity locked = posts.findWithLockById(post.getId()).filter(p -> !p.isGoneAt(now)).orElseThrow(PostNotFoundException::new);
        PostProcessStatusChanger.Result result = changer.change(locked, b, status, userId, now);
        if (result.changed()) {
            log.info("글 처리 상태 {} → {} postId={} by userId={}", result.before(), result.after(), locked.getId(), userId);
        }
        return new PostProcessStatusView(result.after().name(), result.at());
    }
}
