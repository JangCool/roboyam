package com.community.notice.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.board.dto.ContentBody;
import com.community.board.dto.EffectiveBoard;
import com.community.board.repository.master.BoardRepository;
import com.community.board.service.BoardCatalog;
import com.community.board.service.ContentRules;
import com.community.media.service.PostImageUploads;
import com.community.notice.dto.request.NoticeSaveRequest;
import com.community.notice.dto.response.NoticeCreatedView;
import com.community.notice.dto.response.NoticeImageView;
import com.community.notice.entity.NoticeGlobalPinEntity;
import com.community.notice.exception.NoticeBoardInactiveException;
import com.community.notice.exception.NoticeGlobalLimitException;
import com.community.notice.exception.NoticeWriterNotLinkedException;
import com.community.notice.repository.master.NoticeGlobalPinQueries;
import com.community.notice.repository.master.NoticeGlobalPinRepository;
import com.community.operator.repository.master.OperatorMemberAccountStore.Link;
import com.community.operator.service.OperatorMemberAccountService;
import com.community.permission.dto.AdminActor;
import com.community.post.entity.PostEntity;
import com.community.post.entity.PostModerationEventEntity;
import com.community.post.exception.AdminPostNotFoundException;
import com.community.post.exception.ImageUploadNotAllowedException;
import com.community.post.exception.PinLimitException;
import com.community.post.exception.PostStateInvalidException;
import com.community.post.repository.master.AdminPostRepositoryQDSL;
import com.community.post.repository.master.PostRepository;
import com.community.post.service.AdminPostQueryService;
import com.community.post.service.PostContentFields;
import com.community.post.service.PostImages;
import com.community.post.service.PostModerationActions;
import com.community.post.service.PostModerationLog;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 공지 쓰기 · 고치기 · 고정 · 고정 해제 · 삭제 · 본문 이미지 올리기({@code post.manage}). docs/features/notice-admin-contract.md.
 *
 * <ul>
 *   <li><b>작성자</b> = 로그인한 운영자의 운영자 회원 계정(힘이 살아 있을 때 — {@link OperatorMemberAccountService}). 연결이 없거나 멈췄으면 새 글 · 수정 · 이미지 올리기 모두
 *       409 NOTICE_WRITER_NOT_LINKED. 고쳐도 작성자는 바뀌지 않는다(다른 운영자가 쓴 공지도 고칠 수 있다).</li>
 *   <li><b>게시판</b>: 없으면 404 NOTICE_BOARD_MISSING, 꺼져 있으면 쓰기 · 고치기 · 고정 409 NOTICE_BOARD_INACTIVE(고정 해제 · 삭제는 된다).</li>
 *   <li><b>칸</b>: 공개 사이트 글쓰기와 같은 규칙({@link PostContentFields} — 정화 · 길이 · 말머리 · 링크 · 이미지 수, 글쓴이는 회원 · 운영진 전용 말머리 허용). 칸 경로는
 *       {@code title} · {@code body}(본문 · 본문 형식) · {@code prefixId}(말머리 · 성인 전용 말머리)로 옮겨 400 VALIDATION. 본문 이미지는 공개 글쓰기와 같은
 *       {@link PostImages}(내가 올린 아직 안 쓴 이미지 또는 이 글이 쓰던 이미지 — 아니면 {@code fields.body}), 이미지 금지 게시판의 새 이미지 422 IMAGE_UPLOAD_NOT_ALLOWED.
 *       금지어 · 작성 간격 · 하루 한도 · 같은 글 반복 · 자동입력 방지는 보지 않는다(운영자 공지). 포인트는 주지 않는다.</li>
 *   <li><b>고정</b>: 게시글 관리와 같은 한도(그 게시판의 지우지 않은 고정 글 수, 정책 {@code pinnedMax} — 넘으면 409 PIN_LIMIT), 공개 글만(409 POST_STATE_INVALID).</li>
 *   <li><b>전체 공지</b>(V174): 고정할 때 범위 {@code scope} NOTICE_ONLY(기본) · ALL. ALL 이면 뺄 게시판 · 소모임 게시판 전체 빼기 · 기간을 함께 저장한다. 사이트 전체에서
 *       동시에 살아 있는 전체 공지는 {@value NoticeGlobalPinEntity#MAX}개까지(이 글 말고, 공지 게시판 행을 잠그고 센다 — 넘으면 409 NOTICE_GLOBAL_LIMIT, 이미 지난 기간이면 세지 않는다).
 *       고정을 끄거나 범위를 NOTICE_ONLY 로 바꾸거나 고정 해제 · 삭제하면 줄을 지운다. 목록 ⋯ 「고정」({@link #pin})은 공지 게시판에만이다.</li>
 *   <li><b>삭제</b>: 지우지 않은 글 → 게시글 관리 삭제와 같은 「지운 글」(고정 해제) — 사유 · 작성자 알림 · 포인트 회수 없음. 복구 · 조치 기록은 게시글 관리에서.</li>
 *   <li><b>기록</b>: 감사 {@code NOTICE_*}(대상 POST), 삭제 · 고정 · 해제는 조치 기록에도 기존 종류(DELETED · PINNED · UNPINNED, 덧붙임 {@code via NOTICE_ADMIN}, 사유 없음).
 *       전체 공지는 감사 {@code global} 칸(범위 · 뺀 게시판 · 소모임 전체 빼기 · 기간), 고정 해제 · 삭제로 내려가면 {@code globalRemoved}.</li>
 * </ul>
 */
@Service
public class NoticeWriteService {

    private static final Logger log = LoggerFactory.getLogger(NoticeWriteService.class);

    static final String TARGET = "POST";
    static final String VIA = "NOTICE_ADMIN";
    /** 공지 몸의 본문 칸 경로 */
    static final String BODY = "body";
    static final String SCOPE_NOTICE_ONLY = "NOTICE_ONLY";
    static final String SCOPE_ALL = "ALL";
    /** 뺄 게시판 ID 최대 수 */
    static final int EXCLUDED_MAX = 1000;
    /** 기간 끝 최대 — 지금부터 */
    static final int EXPIRES_MAX_DAYS = 366;

    private final NoticeQueryService notices;
    private final BoardCatalog catalog;
    private final PostRepository posts;
    private final AdminPostRepositoryQDSL queries;
    private final OperatorMemberAccountService accounts;
    private final PostImages postImages;
    private final PostImageUploads uploads;
    private final PostModerationLog moderation;
    private final AuditLogService audit;
    private final SnowflakeIdGenerator ids;
    private final NoticeGlobalPinRepository globalPins;
    private final NoticeGlobalPinQueries globalQueries;
    private final BoardRepository boards;

    public NoticeWriteService(NoticeQueryService notices, BoardCatalog catalog, PostRepository posts, AdminPostRepositoryQDSL queries,
                              OperatorMemberAccountService accounts, PostImages postImages, PostImageUploads uploads, PostModerationLog moderation,
                              AuditLogService audit, SnowflakeIdGenerator ids, NoticeGlobalPinRepository globalPins, NoticeGlobalPinQueries globalQueries,
                              BoardRepository boards) {
        this.notices = notices;
        this.catalog = catalog;
        this.posts = posts;
        this.queries = queries;
        this.accounts = accounts;
        this.postImages = postImages;
        this.uploads = uploads;
        this.moderation = moderation;
        this.audit = audit;
        this.ids = ids;
        this.globalPins = globalPins;
        this.globalQueries = globalQueries;
        this.boards = boards;
    }

    // ── 쓰기 · 고치기 ───────────────────────────────────────────────────────

    /** 새 공지 — 검사 차례: 게시판(404 · 409) → 작성 계정(409) → 칸(400) → 이미지(400 · 422) → 고정 한도(409) → 전체 공지 한도(409) */
    @Transactional
    public NoticeCreatedView create(AdminActor actor, NoticeSaveRequest body) {
        EffectiveBoard board = writableBoard();
        long writer = writerOf(actor);
        Instant now = Instant.now();
        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        GlobalInput global = globalInput(board, body, null, now, invalid);
        PostContentFields.Cleaned c = fields(board, null, body, invalid);
        ContentBody content = c.body();
        if (!content.imageIds().isEmpty()) {
            postImages.check(content.imageIds(), writer, null, BODY); // 없거나 남의 이미지 — 400 fields.body
            if (!board.imageUploadAllowed()) {
                throw new ImageUploadNotAllowedException(); // 새 글의 이미지는 모두 새 이미지다
            }
        }
        long id = ids.nextId();
        boolean pin = Boolean.TRUE.equals(body == null ? null : body.pinned());
        if (pin) {
            requirePinRoom(board, id, now);
            requireGlobalRoom(board, id, global, now);
        }
        String stored = content.isHtml() ? postImages.bind(content.content(), content.imageIds(), writer, id, false, now, BODY) : content.content();
        PostEntity post = PostEntity.create(id, board.id(), c.prefixId(), writer, null, null, actor.ip(), c.title(), content.format(), stored, content.plain(),
                c.adult(), c.hasImage(), now);
        if (pin) {
            post.pin(now);
        }
        posts.persist(post);
        if (pin) {
            moderation.operator(id, PostModerationEventEntity.PINNED, actor.operatorId(), null, via(), now);
            saveGlobal(actor, post, null, global, now);
        }
        AuditEvent event = event(actor, AuditActions.NOTICE_CREATE, id).with("boardId", Long.toString(board.id())).with("authorUserId", Long.toString(writer))
                .with("title", c.title()).with("pinned", pin);
        if (pin && global.all()) {
            event = event.with("global", global.summary());
        }
        audit.record(c.prefixId() == null ? event : event.with("prefixId", Long.toString(c.prefixId())));
        log.info("공지 쓰기 postId={} by operatorId={} authorUserId={} pinned={} global={}", id, actor.operatorId(), writer, pin, pin && global.all());
        return new NoticeCreatedView(Long.toString(id));
    }

    /**
     * 공지 고치기 — 작성자는 그대로. 검사 차례: 게시판(404 · 409) → 작성 계정(409) → 글(공지 게시판 글이 아니면 404 POST_NOT_FOUND) → 지운 글 409 POST_STATE_INVALID
     * → 칸(400) → 이미지(400 · 422) → 고정(새로 고정이면 공개 글만 409 · 한도 409) → 전체 공지 한도(409). {@code pinned false} 면 고정을 풀고 전체 공지에서도 내린다.
     */
    @Transactional
    public void update(AdminActor actor, String idText, NoticeSaveRequest body) {
        EffectiveBoard board = writableBoard();
        long editor = writerOf(actor);
        PostEntity post = lock(board, idText);
        Instant now = Instant.now();
        if (post.isGoneAt(now)) {
            throw new PostStateInvalidException();
        }
        NoticeGlobalPinEntity row = globalPins.findById(post.getId()).orElse(null);
        NoticeGlobalPinEntity current = live(row, post);
        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        GlobalInput global = globalInput(board, body, current, now, invalid);
        PostContentFields.Cleaned c = fields(board, post.getPrefixId(), body, invalid);
        ContentBody content = c.body();
        List<Long> imageIds = content.imageIds();
        if (!imageIds.isEmpty()) {
            postImages.check(imageIds, editor, post.getId(), BODY); // 고치는 운영자가 올린 새 이미지 또는 이 글이 쓰던 이미지만
            Set<Long> existing = postImages.attachedTo(post.getId());
            if (!board.imageUploadAllowed() && imageIds.stream().anyMatch(i -> !existing.contains(i))) {
                throw new ImageUploadNotAllowedException(); // 이미 있던 이미지는 둘 수 있고 새 이미지만 막는다
            }
        }
        boolean wantPin = Boolean.TRUE.equals(body == null ? null : body.pinned());
        boolean wasPinned = post.getPinnedAt() != null;
        if (wantPin && !wasPinned) {
            PostModerationActions.requirePublic(post, now);
            requirePinRoom(board, post.getId(), now);
        }
        if (wantPin) {
            requireGlobalRoom(board, post.getId(), global, now);
        }

        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        if (!Objects.equals(post.getPrefixId(), c.prefixId())) {
            before.put("prefixId", idString(post.getPrefixId()));
            after.put("prefixId", idString(c.prefixId()));
        }
        if (!Objects.equals(post.getTitle(), c.title())) {
            before.put("title", post.getTitle());
            after.put("title", c.title());
        }
        if (wantPin != wasPinned) {
            before.put("pinned", wasPinned);
            after.put("pinned", wantPin);
        }
        Map<String, Object> globalBefore = current == null ? null : summary(current.getExcludedBoardIds(), current.isExcludeGroupBoards(), current.getExpiresAt());
        Map<String, Object> globalAfter = wantPin && global.all() ? global.summary() : null;
        if (!Objects.equals(globalBefore, globalAfter)) {
            before.put("global", globalBefore);
            after.put("global", globalAfter);
        }
        String oldContent = post.getContent();

        String stored;
        if (content.isHtml()) {
            stored = postImages.bind(content.content(), imageIds, editor, post.getId(), true, now, BODY);
        } else {
            postImages.releaseAll(post.getId());
            stored = content.content();
        }
        post.edit(c.prefixId(), c.title(), content.format(), stored, content.plain(), c.adult(), c.hasImage(), now);
        if (wantPin && !wasPinned) {
            post.pin(now);
            moderation.operator(post.getId(), PostModerationEventEntity.PINNED, actor.operatorId(), null, via(), now);
        } else if (!wantPin && wasPinned) {
            post.unpin(now);
            moderation.operator(post.getId(), PostModerationEventEntity.UNPINNED, actor.operatorId(), null, via(), now);
        }
        if (wantPin) {
            saveGlobal(actor, post, row, global, now);
        } else {
            dropGlobal(row);
        }
        AuditEvent event = event(actor, AuditActions.NOTICE_UPDATE, post.getId()).with("bodyChanged", !Objects.equals(oldContent, stored));
        if (!before.isEmpty()) {
            event = event.with("before", before).with("after", after);
        }
        audit.record(event);
        log.info("공지 고치기 postId={} by operatorId={}", post.getId(), actor.operatorId());
    }

    // ── 고정 · 해제 · 삭제 ──────────────────────────────────────────────────

    /** 고정(공지 게시판에만) — 게시판이 꺼져 있으면 409 NOTICE_BOARD_INACTIVE, 공개 글 · 고정 아님(409 POST_STATE_INVALID), 한도(409 PIN_LIMIT) */
    @Transactional
    public void pin(AdminActor actor, String idText) {
        EffectiveBoard board = writableBoard();
        PostEntity post = lock(board, idText);
        Instant now = Instant.now();
        PostModerationActions.requirePublic(post, now);
        if (post.getPinnedAt() != null) {
            throw new PostStateInvalidException();
        }
        requirePinRoom(board, post.getId(), now);
        post.pin(now);
        moderation.operator(post.getId(), PostModerationEventEntity.PINNED, actor.operatorId(), null, via(), now);
        audit.record(event(actor, AuditActions.NOTICE_PIN, post.getId()));
        log.info("공지 고정 postId={} by operatorId={}", post.getId(), actor.operatorId());
    }

    /** 고정 해제 — 고정 글만(아니면 409 POST_STATE_INVALID), 상태와 무관. 전체 공지에서도 내린다 */
    @Transactional
    public void unpin(AdminActor actor, String idText) {
        PostEntity post = lock(notices.requireBoard(), idText);
        Instant now = Instant.now();
        if (post.getPinnedAt() == null) {
            throw new PostStateInvalidException();
        }
        NoticeGlobalPinEntity row = globalPins.findById(post.getId()).orElse(null);
        boolean wasGlobal = live(row, post) != null;
        post.unpin(now);
        dropGlobal(row);
        moderation.operator(post.getId(), PostModerationEventEntity.UNPINNED, actor.operatorId(), null, via(), now);
        AuditEvent event = event(actor, AuditActions.NOTICE_UNPIN, post.getId());
        audit.record(wasGlobal ? event.with("globalRemoved", true) : event);
        log.info("공지 고정 해제 postId={} by operatorId={}", post.getId(), actor.operatorId());
    }

    /** 삭제 — 지우지 않은 글만(아니면 409 POST_STATE_INVALID). 고정을 풀고 전체 공지에서도 내린다. 사유 · 작성자 알림 · 포인트 회수 없음 */
    @Transactional
    public void delete(AdminActor actor, String idText) {
        PostEntity post = lock(notices.requireBoard(), idText);
        Instant now = Instant.now();
        if (post.isGoneAt(now)) {
            throw new PostStateInvalidException();
        }
        boolean wasPinned = post.getPinnedAt() != null;
        NoticeGlobalPinEntity row = globalPins.findById(post.getId()).orElse(null);
        boolean wasGlobal = live(row, post) != null;
        post.staffDelete(now);
        dropGlobal(row);
        Map<String, Object> detail = new LinkedHashMap<>(via());
        if (wasPinned) {
            detail.put("unpinned", true);
        }
        moderation.operator(post.getId(), PostModerationEventEntity.DELETED, actor.operatorId(), null, detail, now);
        AuditEvent event = event(actor, AuditActions.NOTICE_DELETE, post.getId());
        if (wasPinned) {
            event = event.with("unpinned", true);
        }
        audit.record(wasGlobal ? event.with("globalRemoved", true) : event);
        log.info("공지 삭제 postId={} by operatorId={}", post.getId(), actor.operatorId());
    }

    // ── 본문 이미지 ─────────────────────────────────────────────────────────

    /**
     * 본문 이미지 올리기 — 작성 계정(409 NOTICE_WRITER_NOT_LINKED) → 파일 칸(400 {@code fields.file}) → 공개 사이트 글 이미지와 같은 검사 · 저장({@link PostImageUploads},
     * 올린 회원 = 내 운영자 회원 계정). 공개 쪽의 1분 · 하루 올리기 한도는 두지 않는다(운영자).
     */
    public NoticeImageView uploadImage(AdminActor actor, MultipartFile file) {
        long writer = writerOf(actor);
        if (file == null || file.isEmpty()) {
            throw ValidationException.of("file", new MessageRef(file == null ? "file.required" : "site.image.empty"));
        }
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw ValidationException.of("file", new MessageRef("site.image.corrupt"));
        }
        PostImageUploads.Stored stored = uploads.store(writer, data);
        return new NoticeImageView(Long.toString(stored.id()), stored.url(), stored.width(), stored.height());
    }

    // ── 전체 공지 ─────────────────────────────────────────────────────────

    /**
     * 검사한 전체 공지 칸.
     *
     * @param all       범위가 ALL 이고 고정한다
     * @param excluded  뺄 게시판(차례 그대로, 같은 ID 는 한 번 — 공지 게시판 자신 · 형식이 맞지만 지금 없는 게시판 ID 는 뺀다)
     * @param expiresAt 기간 끝(null = 풀 때까지)
     */
    private record GlobalInput(boolean all, List<Long> excluded, boolean excludeGroups, Instant expiresAt) {

        static final GlobalInput NONE = new GlobalInput(false, List.of(), false, null);

        boolean expiredAt(Instant now) {
            return expiresAt != null && !expiresAt.isAfter(now);
        }

        Map<String, Object> summary() {
            return NoticeWriteService.summary(excluded, excludeGroups, expiresAt);
        }
    }

    /**
     * 전체 공지 칸 검사 — 고정하지 않으면 모두 무시한다. 칸 경로: {@code scope}(NOTICE_ONLY · ALL 밖) · {@code excludedBoardIds}(형식이 틀린 ID · {@value #EXCLUDED_MAX}개 넘음)
     * · {@code expiresAt}(형식 · 지난 시각 — 이미 저장된 값 그대로면 둔다 · {@value #EXPIRES_MAX_DAYS}일 넘음). 틀린 칸은 {@code invalid} 에 모은다.
     */
    private GlobalInput globalInput(EffectiveBoard board, NoticeSaveRequest body, NoticeGlobalPinEntity current, Instant now, Map<String, MessageRef> invalid) {
        if (body == null || !Boolean.TRUE.equals(body.pinned())) {
            return GlobalInput.NONE;
        }
        String scope = body.scope() == null || body.scope().isBlank() ? SCOPE_NOTICE_ONLY : body.scope().strip().toUpperCase(Locale.ROOT);
        if (!SCOPE_NOTICE_ONLY.equals(scope) && !SCOPE_ALL.equals(scope)) {
            invalid.put("scope", new MessageRef("notice.global.scope.invalid"));
            return GlobalInput.NONE;
        }
        if (SCOPE_NOTICE_ONLY.equals(scope)) {
            return GlobalInput.NONE;
        }
        List<String> raw = body.excludedBoardIds() == null ? List.of() : body.excludedBoardIds();
        Set<Long> excluded = new LinkedHashSet<>();
        if (raw.size() > EXCLUDED_MAX) {
            invalid.put("excludedBoardIds", new MessageRef("notice.global.excluded.tooMany", String.valueOf(EXCLUDED_MAX)));
        } else {
            var snapshot = catalog.snapshot();
            for (String text : raw) {
                Long id = AdminPostQueryService.parseId(text);
                if (id == null) {
                    invalid.put("excludedBoardIds", new MessageRef("notice.global.excluded.invalid"));
                    break;
                }
                if (id != board.id() && snapshot.board(id).isPresent()) {
                    excluded.add(id); // 그사이 지운 게시판은 조용히 뺀다
                }
            }
        }
        Instant expiresAt = null;
        if (body.expiresAt() != null && !body.expiresAt().isBlank()) {
            try {
                expiresAt = OffsetDateTime.parse(body.expiresAt().strip()).toInstant();
            } catch (DateTimeParseException e) {
                invalid.put("expiresAt", new MessageRef("notice.global.expiresAt.invalid"));
            }
            if (expiresAt != null) {
                boolean unchanged = current != null && expiresAt.equals(current.getExpiresAt());
                if (!unchanged && !expiresAt.isAfter(now)) {
                    invalid.put("expiresAt", new MessageRef("notice.global.expiresAt.past"));
                } else if (expiresAt.isAfter(now.plus(Duration.ofDays(EXPIRES_MAX_DAYS)))) {
                    invalid.put("expiresAt", new MessageRef("notice.global.expiresAt.tooFar", String.valueOf(EXPIRES_MAX_DAYS)));
                }
            }
        }
        boolean excludeGroups = Boolean.TRUE.equals(body.excludeAllGroupBoards());
        return new GlobalInput(true, List.copyOf(excluded), excludeGroups, expiresAt);
    }

    /**
     * 전체 공지 한도 — 범위 ALL 이고 기간이 남았으면, 공지 게시판 행을 잠근 뒤(동시에 거는 요청을 줄 세운다) 이 글 말고 살아 있는 전체 공지가
     * {@value NoticeGlobalPinEntity#MAX}개면 409 NOTICE_GLOBAL_LIMIT.
     */
    private void requireGlobalRoom(EffectiveBoard board, long postId, GlobalInput global, Instant now) {
        if (!global.all() || global.expiredAt(now)) {
            return;
        }
        boards.findWithLockById(board.id());
        if (globalQueries.liveCount(board.id(), postId, now) >= NoticeGlobalPinEntity.MAX) {
            throw new NoticeGlobalLimitException(NoticeGlobalPinEntity.MAX);
        }
    }

    /** 고정한 글의 전체 공지 줄을 맞춘다 — ALL 이면 만들거나 고치고(글의 지금 고정 시각으로), 아니면 지운다 */
    private void saveGlobal(AdminActor actor, PostEntity post, NoticeGlobalPinEntity row, GlobalInput global, Instant now) {
        if (!global.all()) {
            dropGlobal(row);
            return;
        }
        if (row == null) {
            globalPins.persist(NoticeGlobalPinEntity.create(post.getId(), post.getPinnedAt(), global.excluded(), global.excludeGroups(), global.expiresAt(),
                    actor.operatorId(), now));
        } else {
            row.change(post.getPinnedAt(), global.excluded(), global.excludeGroups(), global.expiresAt(), actor.operatorId(), now);
        }
    }

    private void dropGlobal(NoticeGlobalPinEntity row) {
        if (row != null) {
            globalPins.delete(row);
        }
    }

    /** 힘이 있는 줄 — 글의 고정 시각이 줄의 사본과 같을 때만(공지 관리 밖에서 고정이 풀렸거나 다시 고정됐으면 null) */
    private static NoticeGlobalPinEntity live(NoticeGlobalPinEntity row, PostEntity post) {
        return row != null && post.getPinnedAt() != null && post.getPinnedAt().equals(row.getPinnedAt()) ? row : null;
    }

    /** 감사용 전체 공지 요약 */
    static Map<String, Object> summary(List<Long> excluded, boolean excludeGroups, Instant expiresAt) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("scope", SCOPE_ALL);
        out.put("excludedBoardIds", excluded.stream().map(String::valueOf).toList());
        out.put("excludeAllGroupBoards", excludeGroups);
        out.put("expiresAt", expiresAt == null ? null : expiresAt.toString());
        return out;
    }

    // ── 안쪽 ──────────────────────────────────────────────────────────────

    /** 공지 게시판 — 없으면 404, 꺼져 있으면 409 NOTICE_BOARD_INACTIVE */
    private EffectiveBoard writableBoard() {
        EffectiveBoard board = notices.requireBoard();
        if (!board.active()) {
            throw new NoticeBoardInactiveException();
        }
        return board;
    }

    /** 힘이 살아 있는 내 운영자 회원 계정의 회원 ID — 없거나 멈췄으면 409 NOTICE_WRITER_NOT_LINKED */
    private long writerOf(AdminActor actor) {
        Optional<Link> link = accounts.linkOfOperator(actor.operatorId()).filter(Link::active);
        return link.map(Link::userId).orElseThrow(NoticeWriterNotLinkedException::new);
    }

    /** 공지 게시판 글 행 잠금 — 없는 글 · 다른 게시판 글 · 소모임 글 404 POST_NOT_FOUND */
    private PostEntity lock(EffectiveBoard board, String idText) {
        Long id = AdminPostQueryService.parseId(idText);
        return (id == null ? Optional.<PostEntity>empty() : posts.findWithLockById(id))
                .filter(p -> Objects.equals(p.getBoardId(), board.id()) && !p.isGroupPost())
                .orElseThrow(AdminPostNotFoundException::new);
    }

    /**
     * 칸 검사(공개 글쓰기와 같은 규칙) → 칸 경로를 공지 몸으로 옮겨 400(전체 공지 칸 {@code extra} 와 함께). 글쓴이는 회원(새 회원 규칙 없음) · 운영진(운영진 전용 말머리 허용),
     * 본문 형식은 늘 HTML, 19금은 고르지 않는다(성인 전용 말머리면 그 규칙대로).
     */
    private PostContentFields.Cleaned fields(EffectiveBoard board, Long currentPrefixId, NoticeSaveRequest body, Map<String, MessageRef> extra) {
        Map<String, MessageRef> raw = new LinkedHashMap<>();
        PostContentFields.Cleaned c = PostContentFields.check(board, true, ContentRules.Writer.MEMBER, currentPrefixId, body == null ? null : body.prefixId(),
                body == null ? null : body.title(), "HTML", body == null ? null : body.body(), null, catalog.snapshot().security(), raw);
        if (raw.isEmpty() && extra.isEmpty()) {
            return c;
        }
        Map<String, MessageRef> out = new LinkedHashMap<>();
        raw.forEach((key, message) -> out.putIfAbsent(switch (key) {
            case "content", "contentFormat" -> BODY;
            case "adult" -> "prefixId";
            default -> key;
        }, message));
        extra.forEach(out::putIfAbsent);
        throw new ValidationException(out);
    }

    /** 고정 한도 — 그 게시판에 이 글 말고 보이는 고정 글이 한도만큼 있으면(한도 0 포함) 409 PIN_LIMIT(게시글 관리와 같은 셈) */
    private void requirePinRoom(EffectiveBoard board, long postId, Instant now) {
        int max = board.pinnedMax();
        if (max <= 0 || queries.pinnedCount(board.id(), postId, now) >= max) {
            throw new PinLimitException(Math.max(0, max));
        }
    }

    private static Map<String, Object> via() {
        return Map.of("via", VIA);
    }

    private static AuditEvent event(AdminActor actor, String action, long postId) {
        return AuditEvent.byOperator(actor.operatorId(), actor.ip(), action, AuditResult.SUCCESS).target(TARGET, Long.toString(postId));
    }

    private static String idString(Long id) {
        return id == null ? null : Long.toString(id);
    }
}
