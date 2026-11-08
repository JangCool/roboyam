package com.community.post.service;

import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.EffectiveBoard;
import com.community.board.service.BoardCatalog;
import com.community.group.service.GroupPostGate;
import com.community.post.dto.request.PostProcessStatusRequest;
import com.community.post.dto.response.PostProcessStatusHistoryItemView;
import com.community.post.dto.response.PostProcessStatusHistoryView;
import com.community.post.dto.response.PostProcessStatusView;
import com.community.post.entity.PostEntity;
import com.community.post.entity.PostProcessStatus;
import com.community.post.exception.PostHiddenException;
import com.community.post.exception.PostNotFoundException;
import com.community.post.repository.master.PostProcessStatusHistoryRepository;
import com.community.post.repository.master.PostRepository;
import com.community.sanction.exception.BoardSanctionException;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 글의 처리 상태 — 바꾸기({@code PUT /api/posts/{id}/process-status}, V166 · 메모 V168)와 이력({@code GET /api/posts/{id}/process-status/history}, V168).
 * 규칙 몸통은 shared {@link PostProcessStatusChanger}(관리자 앱과 같다).
 *
 * <p>바꾸기 검사 순서: 로그인(401 {@code UNAUTHENTICATED}) → 글(없거나 지움 404 {@code POST_NOT_FOUND}) → 글 열기 판정({@link GroupPostGate#resolve} — 꺼진 게시판 403 ·
 * 19금·비밀글 403 · 소모임 비공개 글) → 숨긴 글 403 {@code POST_HIDDEN} → 바꿀 수 있는 사람(정지 중이 아닌 운영자 회원 계정 · 그 게시판 매니저·스텝 · 소모임 글이면 그 소모임의
 * 소모임장·글 관리 매니저 — {@link SecretPostGate#statusChanger}, 아니면 403 {@code BOARD_STAFF_FORBIDDEN}) → 값(400 {@code fields.status}) → 메모(400 {@code fields.note})
 * → 게시판이 상태를 쓰지 않음(409 {@code POST_STATUS_DISABLED}). 글 행을 잠그고 바꾼다. 바뀌면 이력 한 줄 + 글쓴이에게 회원 알림 {@code POST_STATUS_CHANGED}(메모 포함 —
 * 바꾼 사람이 글쓴이 본인이면 보내지 않는다).
 *
 * <p>이력: 그 글을 읽을 수 있는 사람(글 상세와 같은 판정 — 비밀글 403 {@code SECRET_POST}, 숨긴 글 403 {@code POST_HIDDEN}). 최신순 50줄, 바꾼 사람 이름은 싣지 않는다
 * (구분 {@code actor} 만). 상태를 쓰지 않는 게시판이면 빈 목록.
 */
@Service
public class PostProcessStatusService {

    private static final Logger log = LoggerFactory.getLogger(PostProcessStatusService.class);

    private final BoardCatalog catalog;
    private final PostQueryService queries;
    private final PostRepository posts;
    private final GroupPostGate groupGate;
    private final SecretPostGate staff;
    private final PostProcessStatusChanger changer;
    private final PostProcessStatusHistoryRepository history;

    public PostProcessStatusService(BoardCatalog catalog, PostQueryService queries, PostRepository posts, GroupPostGate groupGate, SecretPostGate staff,
                                    PostProcessStatusChanger changer, PostProcessStatusHistoryRepository history) {
        this.catalog = catalog;
        this.queries = queries;
        this.posts = posts;
        this.groupGate = groupGate;
        this.staff = staff;
        this.changer = changer;
        this.history = history;
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
        String kind = staff.statusChanger(s, post, userId);
        if (kind == null) {
            throw BoardSanctionException.forbidden();
        }
        PostProcessStatus status = PostProcessStatusChanger.parse(body == null ? null : body.status());
        String note = PostProcessStatusChanger.note(body == null ? null : body.note());
        PostEntity locked = posts.findWithLockById(post.getId()).filter(p -> !p.isGoneAt(now)).orElseThrow(PostNotFoundException::new);
        PostProcessStatusChanger.Result result = changer.change(locked, b, status, note, PostProcessStatusChanger.Changer.member(kind, userId), now);
        if (result.changed()) {
            log.info("글 처리 상태 {} → {} postId={} by userId={}({})", result.before(), result.after(), locked.getId(), userId, kind);
        }
        return new PostProcessStatusView(result.after().name(), result.at(), changer.lastNote(b, locked.getId()));
    }

    /** 이력 — 최신순 50줄(이름 없음). 읽을 수 없는 글은 글 상세와 같은 오류 */
    @Transactional(readOnly = true)
    public PostProcessStatusHistoryView history(String idText, Long userId) {
        Instant now = Instant.now();
        PostEntity post = queries.requireVisible(idText, now);
        BoardCatalogSnapshot s = catalog.snapshot();
        EffectiveBoard b = groupGate.resolve(s, post, userId).board();
        if (post.isHiddenAt(now)) {
            throw new PostHiddenException();
        }
        if (!b.statusEnabled()) {
            return new PostProcessStatusHistoryView(List.of());
        }
        return new PostProcessStatusHistoryView(history.findTop50ByPostIdOrderByIdDesc(post.getId()).stream().map(PostProcessStatusHistoryItemView::of).toList());
    }
}
