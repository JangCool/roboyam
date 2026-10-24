package com.community.highlight.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.EffectiveBoard;
import com.community.board.service.BoardCatalog;
import com.community.highlight.dto.request.HighlightOrderRequest;
import com.community.highlight.dto.request.HighlightSaveRequest;
import com.community.highlight.dto.request.HighlightVisibleRequest;
import com.community.highlight.dto.response.HighlightCandidatesView;
import com.community.highlight.dto.response.HighlightCommentCountsView;
import com.community.highlight.dto.response.HighlightItemView;
import com.community.highlight.dto.response.HighlightListView;
import com.community.highlight.dto.response.HighlightOperatorView;
import com.community.highlight.entity.HighlightEntity;
import com.community.highlight.exception.HighlightDuplicateException;
import com.community.highlight.exception.HighlightNotFoundException;
import com.community.highlight.exception.HighlightPostInvalidException;
import com.community.highlight.repository.master.HighlightCommentRepositoryQDSL;
import com.community.highlight.repository.master.HighlightRepository;
import com.community.media.entity.FileEntity;
import com.community.media.entity.FilePurpose;
import com.community.media.entity.FileStatus;
import com.community.media.repository.master.FileRepository;
import com.community.media.service.ImageStorage;
import com.community.operator.repository.master.OperatorRepository;
import com.community.permission.dto.AdminActor;
import com.community.post.dto.AdminPostRow;
import com.community.post.dto.response.AdminPostRefView;
import com.community.post.repository.master.AdminPostRepositoryQDSL;
import com.community.post.service.AdminPostSummaries;
import com.community.site.service.SiteImageFiles;
import com.community.user.entity.ProfileEntity;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 하이라이트 관리 — 목록·글 찾기·후보는 {@code highlight.read}, 만들기·고치기·순서·노출·지우기는 {@code highlight.manage}(컨트롤러가 선언).
 * docs/features/popular-highlight-contract.md 「서버 구현 결과」(community docs/api-content.md 1절을 로보얌에 맞춘 것). 공개 홈 표시는 아직 없다.
 *
 * <ul>
 *   <li><b>지정할 수 있는 글</b>: 보이는 글(지움·임시저장·예약 삭제 시각 지남 아님) · 숨김 아님 · 19금 아님 · 켜진 게시판. 아니면 {@code 400 HIGHLIGHT_POST_INVALID}
 *       ({@code fields.postId} 까닭 NOT_FOUND · NOT_PUBLIC · ADULT · BOARD_OFF). 같은 글은 한 번만({@code 409 HIGHLIGHT_DUPLICATE}).</li>
 *   <li><b>칸 검사</b>(모아서 {@code 400 VALIDATION}): {@code postId}(만들 때 필수·숫자) · {@code title} 40자 · {@code summary} 100자 · {@code showUntil} 이 {@code showFrom} 보다 늦어야
 *       ({@code fields.showUntil}) · {@code imageFileId}(올려 둔 HIGHLIGHT_IMAGE 파일이거나 지금 이미지).</li>
 *   <li><b>대표 이미지</b>: {@code POST /api/admin/files}(purpose HIGHLIGHT_IMAGE)로 올린 파일을 지정하면 그 파일이 쓰는 중(IN_USE)이 되고, 바꾸거나 지우면 예전 파일의 장부 행을 지우고
 *       저장소 파일은 커밋 뒤에 지운다(하이라이트를 지울 때도).</li>
 *   <li><b>순서</b>: 새 하이라이트는 맨 끝. 순서 바꾸기는 전체 ID 를 보낸다(집합이 다르면 {@code fields.ids}). 만들기·순서는 하이라이트 전체를 잠가 줄 세운다.</li>
 *   <li>모든 변경은 감사(HIGHLIGHT_CREATE · UPDATE · DELETE · ORDER · VISIBLE)에 남는다.</li>
 * </ul>
 */
@Service
public class HighlightAdminService {

    private static final Logger log = LoggerFactory.getLogger(HighlightAdminService.class);

    static final int TITLE_MAX = 40;
    static final int SUMMARY_MAX = 100;
    static final int SEARCH_MIN = 2;
    static final int SEARCH_MAX = 50;
    static final int SEARCH_LIMIT = 10;
    /** 글 번호로 찾는 최소 자릿수(Snowflake ID 는 15자리 이상) */
    static final int ID_DIGITS_MIN = 15;
    static final int CANDIDATE_LIMIT = 30;
    static final int DAYS_DEFAULT = 7;
    static final int DAYS_MAX = 365;
    static final int MIN_LIKES_DEFAULT = 3;
    static final int MIN_LIKES_MAX = 1000;

    static final String SHOWING = "SHOWING";
    static final String SCHEDULED = "SCHEDULED";
    static final String EXPIRED = "EXPIRED";
    static final String OFF = "OFF";
    static final String BLOCKED = "BLOCKED";

    private final HighlightRepository highlights;
    private final AdminPostRepositoryQDSL posts;
    private final AdminPostSummaries summaries;
    private final HighlightCommentRepositoryQDSL commentCounts;
    private final BoardCatalog catalog;
    private final FileRepository files;
    private final ImageStorage storage;
    private final SiteImageFiles imageFiles;
    private final OperatorRepository operators;
    private final AuditLogService audit;
    private final SnowflakeIdGenerator ids;

    public HighlightAdminService(HighlightRepository highlights, AdminPostRepositoryQDSL posts, AdminPostSummaries summaries,
                                 HighlightCommentRepositoryQDSL commentCounts, BoardCatalog catalog, FileRepository files, ImageStorage storage,
                                 SiteImageFiles imageFiles, OperatorRepository operators, AuditLogService audit, SnowflakeIdGenerator ids) {
        this.highlights = highlights;
        this.posts = posts;
        this.summaries = summaries;
        this.commentCounts = commentCounts;
        this.catalog = catalog;
        this.files = files;
        this.storage = storage;
        this.imageFiles = imageFiles;
        this.operators = operators;
        this.audit = audit;
        this.ids = ids;
    }

    // ── 읽기 ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public HighlightListView list() {
        List<HighlightEntity> rows = highlights.findAllByOrderBySortOrderAscIdAsc();
        List<HighlightItemView> items = views(rows);
        int visible = (int) items.stream().filter(i -> SHOWING.equals(i.status())).count();
        return new HighlightListView(visible, items.size(), items);
    }

    /** 대상 글 찾기 — 글 번호(숫자 {@value #ID_DIGITS_MIN}자리 이상)거나 제목 일부({@value #SEARCH_MIN}자 이상). 지정할 수 있고 아직 하이라이트가 아닌 글 최대 10개 */
    @Transactional(readOnly = true)
    public List<AdminPostRefView> search(String q) {
        String text = q == null ? "" : q.strip();
        if (text.codePointCount(0, text.length()) < SEARCH_MIN) {
            throw ValidationException.of("q", new MessageRef("highlight.search.tooShort", String.valueOf(SEARCH_MIN)));
        }
        if (text.length() > SEARCH_MAX) {
            text = text.substring(0, SEARCH_MAX);
        }
        Instant now = Instant.now();
        BoardCatalogSnapshot s = catalog.snapshot();
        List<Long> boardIds = activeBoardIds(s);
        Set<Long> taken = takenPostIds();
        List<AdminPostRow> found;
        Long byNumber = text.length() >= ID_DIGITS_MIN && text.chars().allMatch(Character::isDigit) ? parseId(text) : null;
        if (byNumber != null) {
            found = posts.assignableById(byNumber, boardIds, taken, now);
        } else {
            found = posts.searchTitle(text, boardIds, taken, now, SEARCH_LIMIT);
        }
        Map<Long, ProfileEntity> members = summaries.authors(found);
        return found.stream().map(r -> summaries.view(r, s, members, now)).toList();
    }

    /** 하이라이트 후보 — 추천 많은 글(지정할 수 있고 아직 하이라이트가 아닌 글) */
    @Transactional(readOnly = true)
    public HighlightCandidatesView candidates(String daysRaw, String minLikesRaw, String boardIdRaw) {
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        Integer days = intParam(daysRaw, DAYS_DEFAULT, 0, DAYS_MAX);
        if (days == null) {
            fields.put("days", new MessageRef("highlight.candidates.days", "0", String.valueOf(DAYS_MAX)));
        }
        Integer minLikes = intParam(minLikesRaw, MIN_LIKES_DEFAULT, 1, MIN_LIKES_MAX);
        if (minLikes == null) {
            fields.put("minLikes", new MessageRef("highlight.candidates.minLikes", "1", String.valueOf(MIN_LIKES_MAX)));
        }
        BoardCatalogSnapshot s = catalog.snapshot();
        List<Long> boardIds = activeBoardIds(s);
        Long boardId = null;
        if (boardIdRaw != null && !boardIdRaw.isBlank()) {
            boardId = parseId(boardIdRaw);
            if (boardId == null || !boardIds.contains(boardId)) {
                fields.put("boardId", new MessageRef("highlight.candidates.board"));
            }
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        Instant now = Instant.now();
        Instant since = days == 0 ? null : now.minus(Duration.ofDays(days));
        List<AdminPostRow> found = posts.candidates(since, minLikes, boardId == null ? boardIds : List.of(boardId), takenPostIds(), now, CANDIDATE_LIMIT);
        Map<Long, ProfileEntity> members = summaries.authors(found);
        return new HighlightCandidatesView(days, minLikes, boardId == null ? null : Long.toString(boardId),
                found.stream().map(r -> summaries.view(r, s, members, now)).toList());
    }

    // ── 쓰기 ──────────────────────────────────────────────────────────────

    @Transactional
    public HighlightItemView create(AdminActor actor, HighlightSaveRequest body) {
        HighlightSaveRequest b = body == null ? new HighlightSaveRequest(null, null, null, null, null, null, null, null) : body;
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        Long postId = null;
        if (b.postId() == null || b.postId().isBlank()) {
            fields.put("postId", new MessageRef("highlight.postId.required"));
        } else {
            postId = parseId(b.postId());
            if (postId == null) {
                fields.put("postId", new MessageRef("highlight.post." + HighlightPostInvalidException.NOT_FOUND));
            }
        }
        Fields f = checkFields(b, fields);
        Long imageFileId = imageFileIdOf(b.imageFileId(), fields);
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        Instant now = Instant.now();
        requireAssignable(postId, now);
        List<HighlightEntity> all = highlights.findAllWithLockByOrderBySortOrderAscIdAsc(); // 만들기를 줄 세운다(중복·맨 끝 순서)
        long target = postId;
        if (all.stream().anyMatch(h -> h.getPostId() == target)) {
            throw new HighlightDuplicateException();
        }
        FileEntity image = attachImage(imageFileId, null, now);
        int order = all.stream().mapToInt(HighlightEntity::getSortOrder).max().orElse(0) + 1;
        boolean visible = b.visible() == null || b.visible();
        boolean debate = Boolean.TRUE.equals(b.debate());
        HighlightEntity row = HighlightEntity.create(ids.nextId(), postId, f.title(), f.summary(), image == null ? null : image.getId(), visible, b.showFrom(),
                b.showUntil(), debate, order, actor.operatorId(), now);
        highlights.persist(row);
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.HIGHLIGHT_CREATE, AuditResult.SUCCESS)
                .target("HIGHLIGHT", Long.toString(row.getId())).with("postId", Long.toString(postId)).with("after", snapshot(row)));
        log.info("하이라이트를 만들었다 id={} postId={} by operatorId={}", row.getId(), postId, actor.operatorId());
        return views(List.of(row), order).get(0);
    }

    @Transactional
    public HighlightItemView update(AdminActor actor, String idText, HighlightSaveRequest body) {
        HighlightEntity row = requireLocked(idText);
        HighlightSaveRequest b = body == null ? new HighlightSaveRequest(null, null, null, null, null, null, null, null) : body;
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        Fields f = checkFields(b, fields);
        Long imageFileId = imageFileIdOf(b.imageFileId(), fields); // 여기서는 형식만 — 새 파일이면 attachImage 가 용도·상태를 확인한다
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        Instant now = Instant.now();
        Map<String, Object> before = snapshot(row);
        Long oldImage = row.getImageFileId();
        Long newImage = imageFileId;
        if (!Objects.equals(oldImage, newImage)) {
            attachImage(newImage, oldImage, now);
        }
        row.change(f.title(), f.summary(), newImage, b.visible() == null ? row.isVisible() : b.visible(), b.showFrom(), b.showUntil(),
                b.debate() == null ? row.isDebate() : b.debate(), actor.operatorId(), now);
        Map<String, Object> after = snapshot(row);
        Map<String, Object> changedBefore = new LinkedHashMap<>();
        Map<String, Object> changedAfter = new LinkedHashMap<>();
        after.forEach((k, v) -> {
            if (!Objects.equals(before.get(k), v)) {
                changedBefore.put(k, before.get(k));
                changedAfter.put(k, v);
            }
        });
        if (!changedAfter.isEmpty()) {
            audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.HIGHLIGHT_UPDATE, AuditResult.SUCCESS)
                    .target("HIGHLIGHT", Long.toString(row.getId())).with("postId", Long.toString(row.getPostId())).with("before", changedBefore)
                    .with("after", changedAfter));
            log.info("하이라이트를 고쳤다 id={} 칸={} by operatorId={}", row.getId(), changedAfter.keySet(), actor.operatorId());
        }
        return viewOf(row);
    }

    @Transactional
    public void order(AdminActor actor, HighlightOrderRequest body) {
        List<HighlightEntity> all = highlights.findAllWithLockByOrderBySortOrderAscIdAsc();
        List<String> wanted = body == null || body.ids() == null ? List.of() : body.ids();
        Map<Long, HighlightEntity> byId = new HashMap<>();
        all.forEach(h -> byId.put(h.getId(), h));
        List<Long> order = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (String raw : wanted) {
            Long id = parseId(raw);
            if (id == null || !byId.containsKey(id) || !seen.add(id)) {
                order = null;
                break;
            }
            order.add(id);
        }
        if (order == null || order.size() != all.size()) {
            throw ValidationException.of("ids", new MessageRef("highlight.order.mismatch"));
        }
        List<String> before = all.stream().map(h -> Long.toString(h.getId())).toList();
        for (int i = 0; i < order.size(); i++) {
            byId.get(order.get(i)).moveTo(i + 1);
        }
        List<String> after = order.stream().map(id -> Long.toString(id)).toList();
        if (!before.equals(after)) {
            audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.HIGHLIGHT_ORDER, AuditResult.SUCCESS)
                    .target("HIGHLIGHT", "order").with("before", before).with("after", after));
        }
    }

    @Transactional
    public HighlightItemView visible(AdminActor actor, String idText, HighlightVisibleRequest body) {
        if (body == null || body.visible() == null) {
            throw ValidationException.of("visible", new MessageRef("highlight.visible.required"));
        }
        HighlightEntity row = requireLocked(idText);
        if (row.isVisible() != body.visible()) {
            boolean from = row.isVisible();
            row.changeVisible(body.visible(), actor.operatorId(), Instant.now());
            audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.HIGHLIGHT_VISIBLE, AuditResult.SUCCESS)
                    .target("HIGHLIGHT", Long.toString(row.getId())).with("postId", Long.toString(row.getPostId())).with("from", from).with("to", body.visible()));
        }
        return viewOf(row);
    }

    /** 하이라이트만 지운다 — 대상 글은 그대로다. 이미 쓴 의견·입장도 글에 그대로 남는다 */
    @Transactional
    public void delete(AdminActor actor, String idText) {
        HighlightEntity row = requireLocked(idText);
        Long image = row.getImageFileId();
        highlights.delete(row);
        if (image != null) {
            retireImage(image);
        }
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.HIGHLIGHT_DELETE, AuditResult.SUCCESS)
                .target("HIGHLIGHT", Long.toString(row.getId())).with("postId", Long.toString(row.getPostId())));
        log.info("하이라이트를 지웠다 id={} postId={} by operatorId={}", row.getId(), row.getPostId(), actor.operatorId());
    }

    // ── 안쪽 ──────────────────────────────────────────────────────────────

    /** 검사를 통과한 글자 칸 */
    private record Fields(String title, String summary) {
    }

    private static Fields checkFields(HighlightSaveRequest b, Map<String, MessageRef> fields) {
        String title = trimToNull(b.title());
        if (title != null && title.codePointCount(0, title.length()) > TITLE_MAX) {
            fields.put("title", new MessageRef("highlight.title.tooLong", String.valueOf(TITLE_MAX)));
        }
        String summary = trimToNull(b.summary());
        if (summary != null && summary.codePointCount(0, summary.length()) > SUMMARY_MAX) {
            fields.put("summary", new MessageRef("highlight.summary.tooLong", String.valueOf(SUMMARY_MAX)));
        }
        if (b.showFrom() != null && b.showUntil() != null && !b.showUntil().isAfter(b.showFrom())) {
            fields.put("showUntil", new MessageRef("highlight.showUntil.beforeFrom"));
        }
        return new Fields(title, summary);
    }

    /** 대표 이미지 파일 ID 형식 — 비면 null, 틀리면 칸 오류 */
    private static Long imageFileIdOf(String raw, Map<String, MessageRef> fields) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Long id = parseId(raw);
        if (id == null) {
            fields.put("imageFileId", new MessageRef("highlight.image.notFound"));
        }
        return id;
    }

    /**
     * 이미지를 바꾼다 — 새 파일(올려 둔 HIGHLIGHT_IMAGE)을 쓰는 중으로 만들고, 예전 파일은 장부 행을 지우고 저장소 파일은 커밋 뒤에 지운다. 새 파일이 맞지 않으면 {@code 400 fields.imageFileId}.
     *
     * @return 새 파일(없으면 null)
     */
    private FileEntity attachImage(Long newId, Long oldId, Instant now) {
        FileEntity file = null;
        if (newId != null) {
            file = files.findWithLockById(newId).filter(f -> f.getPurpose() == FilePurpose.HIGHLIGHT_IMAGE && f.getStatus() == FileStatus.PENDING)
                    .orElseThrow(() -> ValidationException.of("imageFileId", new MessageRef("highlight.image.notFound")));
            file.markUsed(now);
        }
        if (oldId != null) {
            retireImage(oldId);
        }
        return file;
    }

    /** 예전 이미지 — 장부 행을 지우고(하이라이트 행이 먼저 다른 값을 가리킨 뒤 플러시 때 지워진다) 저장소 파일은 커밋 뒤에 지운다 */
    private void retireImage(long fileId) {
        files.findById(fileId).ifPresent(old -> {
            String key = old.getStorageKey();
            files.delete(old);
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        imageFiles.deleteQuietly(List.of(key));
                    }
                });
            }
        });
    }

    /** 지정할 수 있는 글인가 — 아니면 400 HIGHLIGHT_POST_INVALID */
    private void requireAssignable(long postId, Instant now) {
        AdminPostRow row = summaries.rows(List.of(postId)).get(postId);
        if (row == null) {
            throw new HighlightPostInvalidException(HighlightPostInvalidException.NOT_FOUND);
        }
        EffectiveBoard board = row.boardId() == null ? null : catalog.snapshot().board(row.boardId()).orElse(null);
        switch (row.stateAt(now, board != null && board.active())) {
            case AdminPostRow.DELETED, AdminPostRow.HIDDEN -> throw new HighlightPostInvalidException(HighlightPostInvalidException.NOT_PUBLIC);
            case AdminPostRow.ADULT -> throw new HighlightPostInvalidException(HighlightPostInvalidException.ADULT);
            case AdminPostRow.BOARD_OFF -> throw new HighlightPostInvalidException(HighlightPostInvalidException.BOARD_OFF);
            default -> {
            }
        }
    }

    private HighlightEntity requireLocked(String idText) {
        Long id = parseId(idText);
        if (id == null) {
            throw new HighlightNotFoundException();
        }
        return highlights.findWithLockById(id).orElseThrow(HighlightNotFoundException::new);
    }

    private Set<Long> takenPostIds() {
        Set<Long> out = new HashSet<>();
        highlights.findAllByOrderBySortOrderAscIdAsc().forEach(h -> out.add(h.getPostId()));
        return out;
    }

    private static List<Long> activeBoardIds(BoardCatalogSnapshot s) {
        return s.boards().stream().filter(EffectiveBoard::active).map(EffectiveBoard::id).toList();
    }

    /** 감사용 칸 값 */
    private static Map<String, Object> snapshot(HighlightEntity row) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("title", row.getTitle());
        map.put("summary", row.getSummary());
        map.put("imageFileId", row.getImageFileId() == null ? null : Long.toString(row.getImageFileId()));
        map.put("visible", row.isVisible());
        map.put("showFrom", row.getShowFrom() == null ? null : row.getShowFrom().toString());
        map.put("showUntil", row.getShowUntil() == null ? null : row.getShowUntil().toString());
        map.put("debate", row.isDebate());
        return map;
    }

    /** 한 줄 — 순서 번호는 전체 차례에서 센다 */
    private HighlightItemView viewOf(HighlightEntity row) {
        List<HighlightEntity> all = highlights.findAllByOrderBySortOrderAscIdAsc();
        int order = 1;
        for (HighlightEntity h : all) {
            if (h.getId().equals(row.getId())) {
                break;
            }
            order++;
        }
        return views(List.of(row), order).get(0);
    }

    private List<HighlightItemView> views(List<HighlightEntity> rows) {
        return views(rows, 1);
    }

    /** 줄들 — 글·작성자·댓글 수·이미지·운영자를 한 번에 읽는다. {@code firstOrder} 부터 번호를 붙인다 */
    private List<HighlightItemView> views(List<HighlightEntity> rows, int firstOrder) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Instant now = Instant.now();
        BoardCatalogSnapshot s = catalog.snapshot();
        List<Long> postIds = rows.stream().map(HighlightEntity::getPostId).toList();
        Map<Long, AdminPostRow> postRows = summaries.rows(postIds);
        Map<Long, ProfileEntity> members = summaries.authors(postRows.values());
        Map<Long, Integer> visibleAll = new HashMap<>();
        postRows.values().forEach(r -> visibleAll.put(r.id(), r.commentCount() == null ? 0 : r.commentCount()));
        Map<Long, HighlightCommentCountsView> counts = commentCounts.counts(postIds, visibleAll, now);
        Map<Long, String> imageUrls = new HashMap<>();
        List<Long> imageIds = rows.stream().map(HighlightEntity::getImageFileId).filter(Objects::nonNull).toList();
        if (!imageIds.isEmpty()) {
            files.findAllById(imageIds).forEach(f -> imageUrls.put(f.getId(), storage.publicUrl(f.getStorageKey())));
        }
        Map<Long, String> operatorNames = new HashMap<>();
        List<Long> operatorIds = rows.stream().map(HighlightEntity::getUpdatedBy).filter(Objects::nonNull).distinct().toList();
        if (!operatorIds.isEmpty()) {
            operators.findAllById(operatorIds).forEach(o -> operatorNames.put(o.getId(), o.getOperatorName()));
        }
        List<HighlightItemView> out = new ArrayList<>();
        int order = firstOrder;
        for (HighlightEntity h : rows) {
            AdminPostRow post = postRows.get(h.getPostId());
            AdminPostRefView postView = post == null ? null : summaries.view(post, s, members, now);
            String status = status(h, postView, now);
            out.add(new HighlightItemView(Long.toString(h.getId()), order++, h.getTitle(), h.getSummary(),
                    h.getImageFileId() == null ? null : Long.toString(h.getImageFileId()), h.getImageFileId() == null ? null : imageUrls.get(h.getImageFileId()),
                    h.isVisible(), h.getShowFrom(), h.getShowUntil(), status, h.isDebate(), postView, counts.get(h.getPostId()), h.getCreatedAt(),
                    h.getUpdatedAt(), h.getUpdatedBy() == null ? null : new HighlightOperatorView(Long.toString(h.getUpdatedBy()), operatorNames.get(h.getUpdatedBy()))));
        }
        return out;
    }

    /** 상태 — BLOCKED → OFF → SCHEDULED → EXPIRED → SHOWING */
    static String status(HighlightEntity h, AdminPostRefView post, Instant now) {
        if (post == null || !AdminPostRow.PUBLIC.equals(post.state())) {
            return BLOCKED;
        }
        if (!h.isVisible()) {
            return OFF;
        }
        if (h.getShowFrom() != null && h.getShowFrom().isAfter(now)) {
            return SCHEDULED;
        }
        if (h.getShowUntil() != null && !h.getShowUntil().isAfter(now)) {
            return EXPIRED;
        }
        return SHOWING;
    }

    private static Integer intParam(String raw, int fallback, int min, int max) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(raw.strip());
            return value < min || value > max ? null : value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String trimToNull(String raw) {
        return raw == null || raw.isBlank() ? null : raw.strip();
    }

    /** 문자열 ID → long. 형식이 틀리면 null */
    static Long parseId(String raw) {
        if (raw == null || raw.isBlank() || raw.strip().length() > 19) {
            return null;
        }
        try {
            long id = Long.parseLong(raw.strip());
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
