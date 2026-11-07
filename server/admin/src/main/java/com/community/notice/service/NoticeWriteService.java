package com.community.notice.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.board.dto.ContentBody;
import com.community.board.dto.EffectiveBoard;
import com.community.board.service.BoardCatalog;
import com.community.board.service.ContentRules;
import com.community.media.service.PostImageUploads;
import com.community.notice.dto.request.NoticeSaveRequest;
import com.community.notice.dto.response.NoticeCreatedView;
import com.community.notice.dto.response.NoticeImageView;
import com.community.notice.exception.NoticeBoardInactiveException;
import com.community.notice.exception.NoticeWriterNotLinkedException;
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
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
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
 *   <li><b>삭제</b>: 지우지 않은 글 → 게시글 관리 삭제와 같은 「지운 글」(고정 해제) — 사유 · 작성자 알림 · 포인트 회수 없음. 복구 · 조치 기록은 게시글 관리에서.</li>
 *   <li><b>기록</b>: 감사 {@code NOTICE_*}(대상 POST), 삭제 · 고정 · 해제는 조치 기록에도 기존 종류(DELETED · PINNED · UNPINNED, 덧붙임 {@code via NOTICE_ADMIN}, 사유 없음).</li>
 * </ul>
 */
@Service
public class NoticeWriteService {

    private static final Logger log = LoggerFactory.getLogger(NoticeWriteService.class);

    static final String TARGET = "POST";
    static final String VIA = "NOTICE_ADMIN";
    /** 공지 몸의 본문 칸 경로 */
    static final String BODY = "body";

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

    public NoticeWriteService(NoticeQueryService notices, BoardCatalog catalog, PostRepository posts, AdminPostRepositoryQDSL queries,
                              OperatorMemberAccountService accounts, PostImages postImages, PostImageUploads uploads, PostModerationLog moderation,
                              AuditLogService audit, SnowflakeIdGenerator ids) {
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
    }

    // ── 쓰기 · 고치기 ───────────────────────────────────────────────────────

    /** 새 공지 — 검사 차례: 게시판(404 · 409) → 작성 계정(409) → 칸(400) → 이미지(400 · 422) → 고정 한도(409) */
    @Transactional
    public NoticeCreatedView create(AdminActor actor, NoticeSaveRequest body) {
        EffectiveBoard board = writableBoard();
        long writer = writerOf(actor);
        PostContentFields.Cleaned c = fields(board, null, body);
        ContentBody content = c.body();
        if (!content.imageIds().isEmpty()) {
            postImages.check(content.imageIds(), writer, null, BODY); // 없거나 남의 이미지 — 400 fields.body
            if (!board.imageUploadAllowed()) {
                throw new ImageUploadNotAllowedException(); // 새 글의 이미지는 모두 새 이미지다
            }
        }
        long id = ids.nextId();
        Instant now = Instant.now();
        boolean pin = Boolean.TRUE.equals(body == null ? null : body.pinned());
        if (pin) {
            requirePinRoom(board, id, now);
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
        }
        AuditEvent event = event(actor, AuditActions.NOTICE_CREATE, id).with("boardId", Long.toString(board.id())).with("authorUserId", Long.toString(writer))
                .with("title", c.title()).with("pinned", pin);
        audit.record(c.prefixId() == null ? event : event.with("prefixId", Long.toString(c.prefixId())));
        log.info("공지 쓰기 postId={} by operatorId={} authorUserId={} pinned={}", id, actor.operatorId(), writer, pin);
        return new NoticeCreatedView(Long.toString(id));
    }

    /**
     * 공지 고치기 — 작성자는 그대로. 검사 차례: 게시판(404 · 409) → 작성 계정(409) → 글(공지 게시판 글이 아니면 404 POST_NOT_FOUND) → 지운 글 409 POST_STATE_INVALID
     * → 칸(400) → 이미지(400 · 422) → 고정(새로 고정이면 공개 글만 409 · 한도 409). {@code pinned false} 면 고정을 푼다.
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
        PostContentFields.Cleaned c = fields(board, post.getPrefixId(), body);
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
        AuditEvent event = event(actor, AuditActions.NOTICE_UPDATE, post.getId()).with("bodyChanged", !Objects.equals(oldContent, stored));
        if (!before.isEmpty()) {
            event = event.with("before", before).with("after", after);
        }
        audit.record(event);
        log.info("공지 고치기 postId={} by operatorId={}", post.getId(), actor.operatorId());
    }

    // ── 고정 · 해제 · 삭제 ──────────────────────────────────────────────────

    /** 고정 — 게시판이 꺼져 있으면 409 NOTICE_BOARD_INACTIVE, 공개 글 · 고정 아님(409 POST_STATE_INVALID), 한도(409 PIN_LIMIT) */
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

    /** 고정 해제 — 고정 글만(아니면 409 POST_STATE_INVALID), 상태와 무관 */
    @Transactional
    public void unpin(AdminActor actor, String idText) {
        PostEntity post = lock(notices.requireBoard(), idText);
        Instant now = Instant.now();
        if (post.getPinnedAt() == null) {
            throw new PostStateInvalidException();
        }
        post.unpin(now);
        moderation.operator(post.getId(), PostModerationEventEntity.UNPINNED, actor.operatorId(), null, via(), now);
        audit.record(event(actor, AuditActions.NOTICE_UNPIN, post.getId()));
        log.info("공지 고정 해제 postId={} by operatorId={}", post.getId(), actor.operatorId());
    }

    /** 삭제 — 지우지 않은 글만(아니면 409 POST_STATE_INVALID). 고정을 푼다. 사유 · 작성자 알림 · 포인트 회수 없음 */
    @Transactional
    public void delete(AdminActor actor, String idText) {
        PostEntity post = lock(notices.requireBoard(), idText);
        Instant now = Instant.now();
        if (post.isGoneAt(now)) {
            throw new PostStateInvalidException();
        }
        boolean wasPinned = post.getPinnedAt() != null;
        post.staffDelete(now);
        Map<String, Object> detail = new LinkedHashMap<>(via());
        if (wasPinned) {
            detail.put("unpinned", true);
        }
        moderation.operator(post.getId(), PostModerationEventEntity.DELETED, actor.operatorId(), null, detail, now);
        AuditEvent event = event(actor, AuditActions.NOTICE_DELETE, post.getId());
        audit.record(wasPinned ? event.with("unpinned", true) : event);
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
     * 칸 검사(공개 글쓰기와 같은 규칙) → 칸 경로를 공지 몸으로 옮겨 400. 글쓴이는 회원(새 회원 규칙 없음) · 운영진(운영진 전용 말머리 허용), 본문 형식은 늘 HTML,
     * 19금은 고르지 않는다(성인 전용 말머리면 그 규칙대로).
     */
    private PostContentFields.Cleaned fields(EffectiveBoard board, Long currentPrefixId, NoticeSaveRequest body) {
        Map<String, MessageRef> raw = new LinkedHashMap<>();
        PostContentFields.Cleaned c = PostContentFields.check(board, true, ContentRules.Writer.MEMBER, currentPrefixId, body == null ? null : body.prefixId(),
                body == null ? null : body.title(), "HTML", body == null ? null : body.body(), null, catalog.snapshot().security(), raw);
        if (raw.isEmpty()) {
            return c;
        }
        Map<String, MessageRef> out = new LinkedHashMap<>();
        raw.forEach((key, message) -> out.putIfAbsent(switch (key) {
            case "content", "contentFormat" -> BODY;
            case "adult" -> "prefixId";
            default -> key;
        }, message));
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
