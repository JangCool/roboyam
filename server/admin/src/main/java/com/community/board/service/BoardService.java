package com.community.board.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.board.dto.BoardPolicyValues;
import com.community.board.dto.NewBoard;
import com.community.board.dto.request.BoardActiveRequest;
import com.community.board.dto.request.BoardCommonPrefixSaveRequest;
import com.community.board.dto.request.BoardCreateRequest;
import com.community.board.dto.request.BoardOrderRequest;
import com.community.board.dto.request.BoardPrefixSaveRequest;
import com.community.board.dto.request.BoardSaveRequest;
import com.community.board.dto.response.BoardDetailView;
import com.community.board.dto.response.BoardListItemView;
import com.community.board.dto.response.BoardListResponse;
import com.community.board.entity.BoardEntity;
import com.community.board.entity.BoardPrefixEntity;
import com.community.board.exception.BoardHasPostsException;
import com.community.board.exception.BoardInvestmentNoticeLockedException;
import com.community.board.exception.BoardNameDuplicateException;
import com.community.board.exception.BoardNotFoundException;
import com.community.board.exception.BoardPrefixInUseException;
import com.community.board.exception.BoardPrefixLimitException;
import com.community.board.exception.BoardSlugDuplicateException;
import com.community.board.repository.master.BoardContentQueries;
import com.community.board.repository.master.BoardPrefixRepository;
import com.community.board.repository.master.BoardRepository;
import com.community.grade.entity.GradeEntity;
import com.community.grade.repository.master.GradeRepositoryQDSL;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.permission.exception.PermissionDeniedException;
import com.community.permission.service.AccessDeniedRecorder;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시판 목록·만들기·설정 저장·켜고 끄기·순서·삭제 (docs/features/board-contract.md).
 *
 * <ul>
 *   <li>권한: 입구는 컨트롤러가 선언하고, 저장({@link #save})은 <b>바뀐 부분의 권한만</b> 여기서 본다 — 기본 정보·말머리 {@code board.manage},
 *       정책(잠금·안내문 포함) {@code board.settings}, 매니저·스텝 한도 칸 {@code board.staff.limit}. 모자라면 403 {@code PERMISSION_DENIED} + 감사 기록(DENIED).</li>
 *   <li>입력 오류는 400 {@code VALIDATION} + 칸 경로({@code name} · {@code policy.writeMin} · {@code prefixes[0].name} …). 칸 범위·값 목록은 {@link BoardFields}.</li>
 *   <li>이름은 대소문자 무시 유일(409 {@code BOARD_NAME_DUPLICATE}), slug 는 유일·만든 뒤 변경 불가(409 {@code BOARD_SLUG_DUPLICATE}).
 *       투자 고지는 켜기만(409 {@code BOARD_INVESTMENT_NOTICE_LOCKED}). 사용 중 고유 말머리 최대 6(409 {@code PREFIX_LIMIT}), 글이 있는 말머리는 못 지운다(409 {@code PREFIX_IN_USE}).</li>
 *   <li>쓰기 자격: GUEST · 등급 키(등급 표에 있는 것) · STAFF, 댓글은 OFF 도. 핫딜·직접홍보는 GUEST 불가, 핫딜 글쓰기는 첫 등급(새싹) 불가.</li>
 *   <li>삭제는 삭제되지 않은 글(임시저장 포함)이 없을 때만(409 {@code BOARD_HAS_POSTS}) — 남은 삭제된 글·댓글도 함께 지운다.</li>
 *   <li>감사 기록은 바뀐 값만 before/after 로 남긴다.</li>
 * </ul>
 */
@Service
public class BoardService {

    private static final Logger log = LoggerFactory.getLogger(BoardService.class);
    private static final Set<String> STAFF_LIMIT_FIELDS = Set.of("managerMax", "staffPostDailyLimit", "staffCommentDailyLimit", "staffSanctionDailyLimit");

    private final BoardRepository boards;
    private final BoardPrefixRepository prefixes;
    private final BoardContentQueries content;
    private final BoardCreator creator;
    private final BoardViews views;
    private final GradeRepositoryQDSL grades;
    private final SnowflakeIdGenerator ids;
    private final AuditLogService audit;
    private final AccessDeniedRecorder denied;

    public BoardService(BoardRepository boards, BoardPrefixRepository prefixes, BoardContentQueries content, BoardCreator creator, BoardViews views,
                        GradeRepositoryQDSL grades, SnowflakeIdGenerator ids, AuditLogService audit, AccessDeniedRecorder denied) {
        this.boards = boards;
        this.prefixes = prefixes;
        this.content = content;
        this.creator = creator;
        this.views = views;
        this.grades = grades;
        this.ids = ids;
        this.audit = audit;
        this.denied = denied;
    }

    @Transactional(readOnly = true)
    public BoardListResponse list() {
        return new BoardListResponse(views.list());
    }

    @Transactional(readOnly = true)
    public BoardDetailView bySlug(String slug) {
        BoardEntity board = slug == null ? null : boards.findBySlug(slug.strip()).orElse(null);
        if (board == null) {
            throw new BoardNotFoundException();
        }
        return views.detail(board);
    }

    @Transactional(readOnly = true)
    public BoardDetailView byId(String boardId) {
        Long id = BoardRules.parseId(boardId);
        return views.detail(id == null ? notFound() : boards.findById(id).orElseGet(BoardService::notFound));
    }

    // ── 만들기 ─────────────────────────────────────────────────────────────

    @Transactional
    public BoardListItemView create(AdminActor actor, BoardCreateRequest request) {
        String name = BoardRules.strip(request == null ? null : request.name());
        String slug = BoardRules.strip(request == null ? null : request.slug());
        String description = BoardRules.strip(request == null ? null : request.description());
        String layout = request == null ? null : request.layout();

        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        nameProblem(name).ifPresent(problem -> invalid.put("name", problem));
        if (slug.isEmpty()) {
            invalid.put("slug", new MessageRef("board.slug.required"));
        } else if (slug.length() < BoardFields.SLUG_MIN || slug.length() > BoardFields.SLUG_MAX) {
            invalid.put("slug", new MessageRef("board.slug.length", String.valueOf(BoardFields.SLUG_MIN), String.valueOf(BoardFields.SLUG_MAX)));
        } else if (!BoardFields.SLUG.matcher(slug).matches()) {
            invalid.put("slug", new MessageRef("board.slug.format"));
        } else if (BoardFields.SLUG_RESERVED.contains(slug)) {
            invalid.put("slug", new MessageRef("board.slug.reserved"));
        }
        descriptionProblem(description).ifPresent(problem -> invalid.put("description", problem));
        if (layout == null || !BoardFields.LAYOUTS.contains(layout)) {
            invalid.put("layout", new MessageRef("board.field.choice"));
        }
        if (!invalid.isEmpty()) {
            throw new ValidationException(invalid);
        }
        if (boards.existsBySlug(slug)) {
            throw new BoardSlugDuplicateException();
        }
        List<BoardEntity> all = boards.findAllByOrderBySortOrderAscIdAsc();
        if (all.stream().anyMatch(board -> board.getName().equalsIgnoreCase(name))) {
            throw new BoardNameDuplicateException();
        }
        Instant now = Instant.now();
        int nextOrder = all.stream().mapToInt(BoardEntity::getSortOrder).max().orElse(0) + 1;
        BoardEntity board = creator.create(new NewBoard(slug, name, null, description, BoardFields.KIND_GENERAL, layout, !"CARD".equals(layout), null, false,
                null, new HashSet<>(BoardFields.COMMON_PREFIXES), List.of()), nextOrder, actor.operatorId(), now);
        flushOrDuplicated();
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("slug", slug);
        after.put("name", name);
        after.put("layout", layout);
        audit.record(event(actor, AuditActions.BOARD_CREATE, board.getId()).with("before", Map.of()).with("after", after));
        log.info("게시판을 만들었다 boardId={} by operatorId={}", board.getId(), actor.operatorId());
        return views.itemOf(board.getId());
    }

    // ── 저장(기본 정보 + 정책 + 말머리) ──────────────────────────────────────

    @Transactional
    public BoardDetailView save(AdminActor actor, String boardId, BoardSaveRequest request) {
        BoardEntity board = lock(boardId);
        if (request == null) {
            throw ValidationException.of("name", new MessageRef("board.field.required"));
        }
        Input in = normalize(board, request);
        List<BoardPrefixEntity> current = views.sortedPrefixes(board.getId());
        List<BoardPrefixEntity> currentOwn = current.stream().filter(p -> !p.isCommon()).toList();
        Map<Long, BoardPrefixEntity> commonById = new LinkedHashMap<>();
        current.stream().filter(BoardPrefixEntity::isCommon).forEach(p -> commonById.put(p.getId(), p));
        if (in.prefixes == null) {
            // 말머리 목록이 없으면 고유 말머리를 그대로 둔다(모두 지우라는 뜻으로 읽지 않는다)
            in.prefixes = currentOwn.stream()
                    .map(p -> new BoardPrefixSaveRequest(Long.toString(p.getId()), p.getName(), p.isStaffOnly(), p.getColor(), p.isActive())).toList();
        }

        // 1) 바뀐 부분의 권한
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        boolean basicChanged = diff(before, after, "name", board.getName(), in.name)
                | diff(before, after, "nameEn", board.getNameEn(), in.nameEn)
                | diff(before, after, "description", board.getDescription(), in.description)
                | diff(before, after, "layout", board.getLayout(), in.layout)
                | diff(before, after, "viewToggle", board.isViewToggle(), in.viewToggleEffective())
                | diff(before, after, "color", board.getColor(), in.color)
                | diff(before, after, "allowAdult", board.isAllowAdult(), in.allowAdult)
                | diff(before, after, "investmentNotice", board.isInvestmentNotice(), in.investmentNotice);
        boolean settingsChanged = diff(before, after, "writeLock", board.getWriteLock(), in.writeLock)
                | diff(before, after, "introText", board.getIntroText(), in.introText)
                | diff(before, after, "writeGuideText", board.getWriteGuideText(), in.writeGuideText);
        boolean limitChanged = false;
        Map<String, Object> oldPolicy = board.policy().toMap();
        Map<String, Object> newPolicy = in.policy.toMap();
        for (String field : BoardFields.POLICY_FIELDS) {
            if (diff(before, after, "policy." + field, oldPolicy.get(field), newPolicy.get(field))) {
                if (STAFF_LIMIT_FIELDS.contains(field)) {
                    limitChanged = true;
                } else {
                    settingsChanged = true;
                }
            }
        }
        List<String> oldCommon = activeCommon(commonById.values());
        Map<Long, Boolean> commonWanted = new HashMap<>();
        if (request.commonPrefixes() != null) {
            for (BoardCommonPrefixSaveRequest item : request.commonPrefixes()) {
                Long id = item == null ? null : BoardRules.parseId(item.id());
                if (id != null && commonById.containsKey(id) && item.active() != null) {
                    commonWanted.put(id, item.active());
                }
            }
        }
        List<String> newCommon = new ArrayList<>();
        for (BoardPrefixEntity p : commonById.values()) {
            if (commonWanted.getOrDefault(p.getId(), p.isActive())) {
                newCommon.add(p.getCommonKey());
            }
        }
        boolean prefixChanged = diff(before, after, "commonPrefixes", oldCommon, newCommon)
                | diff(before, after, "prefixes", ownSummary(currentOwn), requestSummary(in.prefixes));
        requirePermissions(actor, board, basicChanged || prefixChanged, settingsChanged, limitChanged);

        // 2) 입력 검사
        List<GradeEntity> gradeList = grades.findAllOrdered();
        Set<String> gradeKeys = new HashSet<>();
        gradeList.forEach(g -> gradeKeys.add(g.getGradeKey()));
        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        nameProblem(in.name).ifPresent(problem -> invalid.put("name", problem));
        if (in.nameEn != null && (in.nameEn.length() > BoardFields.NAME_EN_MAX || BoardRules.hasControl(in.nameEn))) {
            invalid.put("nameEn", new MessageRef("board.nameEn.length", String.valueOf(BoardFields.NAME_EN_MAX)));
        }
        descriptionProblem(in.description).ifPresent(problem -> invalid.put("description", problem));
        if (!BoardFields.LAYOUTS.contains(in.layout)) {
            invalid.put("layout", new MessageRef("board.field.choice"));
        }
        if ("CARD".equals(board.getLayout()) && "CARD".equals(in.layout) && in.viewToggle) {
            invalid.put("viewToggle", new MessageRef("board.viewToggle.card"));
        }
        if (in.color != null && !BoardFields.COLORS.contains(in.color)) {
            invalid.put("color", new MessageRef("board.field.choice"));
        }
        if (in.writeLock != null && !BoardFields.WRITE_LOCKS.contains(in.writeLock)) {
            invalid.put("writeLock", new MessageRef("board.field.choice"));
        }
        if (BoardRules.length(in.introText) > BoardFields.TEXT_MAX) {
            invalid.put("introText", new MessageRef("board.text.length", String.valueOf(BoardFields.TEXT_MAX)));
        }
        if (BoardRules.length(in.writeGuideText) > BoardFields.TEXT_MAX) {
            invalid.put("writeGuideText", new MessageRef("board.text.length", String.valueOf(BoardFields.TEXT_MAX)));
        }
        BoardRules.check(newPolicy, policySpecs(), "policy.", true, gradeKeys, BoardFields.guestAllowed(board.getKind()), invalid);
        if (BoardFields.KIND_DEAL.equals(board.getKind()) && !gradeList.isEmpty() && gradeList.get(0).getGradeKey().equals(in.policy.writeMin())) {
            invalid.putIfAbsent("policy.writeMin", new MessageRef("board.policy.dealGrade"));
        }
        checkPrefixes(in.prefixes, currentOwn, invalid);
        if (!invalid.isEmpty()) {
            throw new ValidationException(invalid);
        }

        // 3) 업무 규칙(409)
        if (board.isInvestmentNotice() && !in.investmentNotice) {
            throw new BoardInvestmentNoticeLockedException();
        }
        if (!board.getName().equalsIgnoreCase(in.name)
                && boards.findAllByOrderBySortOrderAscIdAsc().stream().anyMatch(other -> !other.getId().equals(board.getId()) && other.getName().equalsIgnoreCase(in.name))) {
            throw new BoardNameDuplicateException();
        }
        long activeOwn = in.prefixes.stream().filter(p -> !Boolean.FALSE.equals(p.active())).count();
        if (activeOwn > BoardFields.PREFIX_ACTIVE_MAX && activeOwn > currentOwn.stream().filter(BoardPrefixEntity::isActive).count()) {
            throw new BoardPrefixLimitException(BoardFields.PREFIX_ACTIVE_MAX);
        }
        Set<Long> kept = new HashSet<>();
        in.prefixes.forEach(p -> {
            Long id = BoardRules.parseId(p.id());
            if (id != null) {
                kept.add(id);
            }
        });
        List<BoardPrefixEntity> removed = currentOwn.stream().filter(p -> !kept.contains(p.getId())).toList();
        if (!removed.isEmpty()) {
            Map<Long, Long> usage = content.prefixUsage(board.getId());
            if (removed.stream().anyMatch(p -> usage.getOrDefault(p.getId(), 0L) > 0)) {
                throw new BoardPrefixInUseException();
            }
        }

        // 4) 적용
        if (after.isEmpty()) {
            return views.detail(board);
        }
        Instant now = Instant.now();
        board.changeBasic(in.name, in.nameEn, in.description, in.layout, in.viewToggle, in.color, in.allowAdult, in.investmentNotice);
        board.changeGuides(in.writeLock, in.introText, in.writeGuideText);
        board.changePolicy(in.policy);
        board.touch(actor.operatorId(), now);
        for (BoardPrefixEntity p : commonById.values()) {
            boolean active = commonWanted.getOrDefault(p.getId(), p.isActive());
            if (active != p.isActive()) {
                p.changeActive(active, now);
            }
        }
        removed.forEach(prefixes::delete);
        Map<Long, BoardPrefixEntity> ownById = new HashMap<>();
        currentOwn.forEach(p -> ownById.put(p.getId(), p));
        int order = 10;
        for (BoardPrefixSaveRequest item : in.prefixes) {
            String name = BoardRules.strip(item.name());
            boolean staffOnly = Boolean.TRUE.equals(item.staffOnly());
            boolean active = !Boolean.FALSE.equals(item.active());
            Long id = BoardRules.parseId(item.id());
            if (id == null) {
                prefixes.persist(BoardPrefixEntity.own(ids.nextId(), board.getId(), name, staffOnly, item.color(), active, order++, now));
            } else {
                ownById.get(id).change(name, staffOnly, item.color(), active, order++, now);
            }
        }
        flushOrDuplicated();
        audit.record(event(actor, AuditActions.BOARD_UPDATE, board.getId()).with("before", before).with("after", after));
        log.info("게시판 설정을 저장했다 boardId={} 칸={} by operatorId={}", board.getId(), after.keySet(), actor.operatorId());
        return views.detail(board);
    }

    // ── 켜고 끄기 · 순서 · 삭제 ─────────────────────────────────────────────

    @Transactional
    public BoardListItemView setActive(AdminActor actor, String boardId, BoardActiveRequest request) {
        BoardEntity board = lock(boardId);
        if (request == null || request.active() == null) {
            throw ValidationException.of("active", new MessageRef("board.field.required"));
        }
        boolean active = request.active();
        if (board.isActive() != active) {
            board.changeActive(active);
            board.touch(actor.operatorId(), Instant.now());
            audit.record(event(actor, AuditActions.BOARD_ACTIVE, board.getId()).with("from", Boolean.toString(!active)).with("to", Boolean.toString(active)));
            log.info("게시판을 {} boardId={} by operatorId={}", active ? "켰다" : "껐다", board.getId(), actor.operatorId());
        }
        return views.itemOf(board.getId());
    }

    @Transactional
    public BoardListResponse order(AdminActor actor, BoardOrderRequest request) {
        List<BoardEntity> all = boards.findAllByOrderBySortOrderAscIdAsc();
        Map<Long, BoardEntity> byId = new LinkedHashMap<>();
        all.forEach(board -> byId.put(board.getId(), board));
        List<Long> wanted = new ArrayList<>();
        if (request != null && request.ids() != null) {
            for (String raw : request.ids()) {
                wanted.add(BoardRules.parseId(raw));
            }
        }
        if (wanted.size() != all.size() || wanted.contains(null) || new LinkedHashSet<>(wanted).size() != wanted.size() || !byId.keySet().containsAll(wanted)) {
            throw ValidationException.of("ids", new MessageRef("board.order.mismatch"));
        }
        List<String> beforeSlugs = all.stream().map(BoardEntity::getSlug).toList();
        List<String> afterSlugs = wanted.stream().map(id -> byId.get(id).getSlug()).toList();
        Instant now = Instant.now();
        for (int i = 0; i < wanted.size(); i++) {
            BoardEntity board = byId.get(wanted.get(i));
            if (board.getSortOrder() != i + 1) {
                board.reorder(i + 1);
                board.touch(actor.operatorId(), now);
            }
        }
        if (!beforeSlugs.equals(afterSlugs)) {
            boards.flush();
            audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.BOARD_ORDER, AuditResult.SUCCESS).target("BOARD", "order")
                    .with("before", Map.of("order", beforeSlugs)).with("after", Map.of("order", afterSlugs)));
            log.info("게시판 순서를 바꿨다 by operatorId={}", actor.operatorId());
        }
        return new BoardListResponse(views.list());
    }

    @Transactional
    public void delete(AdminActor actor, String boardId) {
        BoardEntity board = lock(boardId);
        if (content.hasLivePosts(board.getId())) {
            throw new BoardHasPostsException();
        }
        content.deleteContent(board.getId());
        String slug = board.getSlug();
        String name = board.getName();
        boards.delete(board);
        boards.flush();
        audit.record(event(actor, AuditActions.BOARD_DELETE, board.getId()).with("deleted", List.of(slug + " (" + name + ")")));
        log.info("게시판을 지웠다 boardId={} by operatorId={}", board.getId(), actor.operatorId());
    }

    // ── 안쪽 ────────────────────────────────────────────────────────────────

    /** 정리한 저장 입력 */
    private static final class Input {
        String name;
        String nameEn;
        String description;
        String layout;
        boolean viewToggle;
        String color;
        boolean allowAdult;
        boolean investmentNotice;
        String writeLock;
        String introText;
        String writeGuideText;
        BoardPolicyValues policy;
        List<BoardPrefixSaveRequest> prefixes;

        /** 카드형은 보기 전환이 늘 꺼진다 */
        boolean viewToggleEffective() {
            return !"CARD".equals(layout) && viewToggle;
        }
    }

    private static Input normalize(BoardEntity board, BoardSaveRequest request) {
        Input in = new Input();
        in.name = BoardRules.strip(request.name());
        in.nameEn = BoardRules.stripToNull(request.nameEn());
        in.description = BoardRules.strip(request.description());
        in.layout = request.layout() == null ? board.getLayout() : request.layout();
        in.viewToggle = request.viewToggle() == null ? board.isViewToggle() : request.viewToggle();
        in.color = request.color() == null || request.color().isBlank() ? null : request.color();
        in.allowAdult = request.allowAdult() == null ? board.isAllowAdult() : request.allowAdult();
        in.investmentNotice = request.investmentNotice() == null ? board.isInvestmentNotice() : request.investmentNotice();
        in.writeLock = request.writeLock() == null || request.writeLock().isBlank() ? null : request.writeLock();
        in.introText = BoardRules.normalizeText(request.introText());
        in.writeGuideText = BoardRules.normalizeText(request.writeGuideText());
        in.policy = request.policy() == null ? board.policy() : request.policy();
        in.prefixes = request.prefixes() == null ? null : request.prefixes().stream().filter(Objects::nonNull).toList();
        return in;
    }

    private void requirePermissions(AdminActor actor, BoardEntity board, boolean manage, boolean settings, boolean limit) {
        List<String> missing = new ArrayList<>();
        if (manage && !actor.can(Permission.BOARD_MANAGE)) {
            missing.add(Permission.BOARD_MANAGE.code());
        }
        if (settings && !actor.can(Permission.BOARD_SETTINGS)) {
            missing.add(Permission.BOARD_SETTINGS.code());
        }
        if (limit && !actor.can(Permission.BOARD_STAFF_LIMIT)) {
            missing.add(Permission.BOARD_STAFF_LIMIT.code());
        }
        if (!missing.isEmpty()) {
            denied.record(actor, "MISSING_PERMISSION", "BOARD", Long.toString(board.getId()), missing);
            throw new PermissionDeniedException();
        }
    }

    /** 고유 말머리 검사 — 이름(비었나·길이·공통 이름 포함 중복)·색·모르는 id → {@code prefixes[i].…} */
    private static void checkPrefixes(List<BoardPrefixSaveRequest> items, List<BoardPrefixEntity> currentOwn, Map<String, MessageRef> invalid) {
        Set<Long> ownIds = new HashSet<>();
        currentOwn.forEach(p -> ownIds.add(p.getId()));
        Map<String, Integer> seen = new HashMap<>();
        BoardFields.COMMON_PREFIX_NAMES.values().forEach(name -> seen.merge(name.toLowerCase(Locale.ROOT), 1, Integer::sum));
        BoardFields.COMMON_PREFIX_NAMES_EN.values().forEach(name -> seen.merge(name.toLowerCase(Locale.ROOT), 1, Integer::sum));
        for (BoardPrefixSaveRequest item : items) {
            String key = BoardRules.strip(item.name()).toLowerCase(Locale.ROOT);
            if (!key.isEmpty()) {
                seen.merge(key, 1, Integer::sum);
            }
        }
        Set<Long> usedIds = new HashSet<>();
        for (int i = 0; i < items.size(); i++) {
            BoardPrefixSaveRequest item = items.get(i);
            String path = "prefixes[" + i + "]";
            if (item.id() != null) {
                Long id = BoardRules.parseId(item.id());
                if (id == null || !ownIds.contains(id) || !usedIds.add(id)) {
                    invalid.put(path + ".id", new MessageRef("board.prefix.unknown"));
                }
            }
            String name = BoardRules.strip(item.name());
            if (name.isEmpty()) {
                invalid.put(path + ".name", new MessageRef("board.prefix.name.required"));
            } else if (name.length() > BoardFields.PREFIX_NAME_MAX || BoardRules.hasControl(name)) {
                invalid.put(path + ".name", new MessageRef("board.prefix.name.length", String.valueOf(BoardFields.PREFIX_NAME_MAX)));
            } else if (seen.getOrDefault(name.toLowerCase(Locale.ROOT), 0) > 1) {
                invalid.put(path + ".name", new MessageRef("board.prefix.name.duplicate"));
            }
            if (item.color() != null && !BoardFields.COLORS.contains(item.color())) {
                invalid.put(path + ".color", new MessageRef("board.field.choice"));
            }
        }
    }

    private static List<String> activeCommon(java.util.Collection<BoardPrefixEntity> common) {
        return common.stream().filter(BoardPrefixEntity::isActive).map(BoardPrefixEntity::getCommonKey).toList();
    }

    private static List<String> ownSummary(List<BoardPrefixEntity> own) {
        return own.stream().map(p -> summary(Long.toString(p.getId()), p.getName(), p.isStaffOnly(), p.getColor(), p.isActive())).toList();
    }

    private static List<String> requestSummary(List<BoardPrefixSaveRequest> items) {
        return items.stream().map(p -> summary(p.id() == null ? "new" : p.id().strip(), BoardRules.strip(p.name()), Boolean.TRUE.equals(p.staffOnly()),
                p.color(), !Boolean.FALSE.equals(p.active()))).toList();
    }

    private static String summary(String id, String name, boolean staffOnly, String color, boolean active) {
        return name + (staffOnly ? " [staff]" : "") + (color == null ? "" : " [" + color + "]") + (active ? "" : " [off]") + ("new".equals(id) ? " [new]" : "");
    }

    /** 다르면 before/after 에 담고 true */
    private static boolean diff(Map<String, Object> before, Map<String, Object> after, String key, Object oldValue, Object newValue) {
        if (Objects.equals(oldValue, newValue)) {
            return false;
        }
        before.put(key, oldValue == null ? "" : oldValue);
        after.put(key, newValue == null ? "" : newValue);
        return true;
    }

    private static java.util.Optional<MessageRef> nameProblem(String name) {
        if (name.isEmpty()) {
            return java.util.Optional.of(new MessageRef("board.name.required"));
        }
        if (name.length() > BoardFields.NAME_MAX || BoardRules.hasControl(name)) {
            return java.util.Optional.of(new MessageRef("board.name.length", String.valueOf(BoardFields.NAME_MAX)));
        }
        return java.util.Optional.empty();
    }

    private static java.util.Optional<MessageRef> descriptionProblem(String description) {
        if (description.length() > BoardFields.DESCRIPTION_MAX || BoardRules.hasControl(description)) {
            return java.util.Optional.of(new MessageRef("board.description.length", String.valueOf(BoardFields.DESCRIPTION_MAX)));
        }
        return java.util.Optional.empty();
    }

    private BoardEntity lock(String boardId) {
        Long id = BoardRules.parseId(boardId);
        return id == null ? notFound() : boards.findWithLockById(id).orElseGet(BoardService::notFound);
    }

    private static BoardEntity notFound() {
        throw new BoardNotFoundException();
    }

    /** 이름·slug 유일 색인 위반(동시 요청)을 중복 오류로 바꾼다 */
    private void flushOrDuplicated() {
        try {
            boards.flush();
        } catch (DataIntegrityViolationException e) {
            String message = String.valueOf(e.getMostSpecificCause().getMessage());
            if (message.contains("ux_boards_slug")) {
                throw new BoardSlugDuplicateException();
            }
            if (message.contains("ux_boards_name")) {
                throw new BoardNameDuplicateException();
            }
            throw e;
        }
    }

    private static AuditEvent event(AdminActor actor, String action, long boardId) {
        return AuditEvent.byOperator(actor.operatorId(), actor.ip(), action, AuditResult.SUCCESS).target("BOARD", Long.toString(boardId));
    }

    /** 기본 설정 칸 정의 중 게시판별 정책 칸만 */
    private static Map<String, BoardFields.Spec> policySpecs() {
        Map<String, BoardFields.Spec> specs = new LinkedHashMap<>();
        BoardFields.POLICY_FIELDS.forEach(field -> specs.put(field, BoardFields.DEFAULTS.get(field)));
        return specs;
    }
}
