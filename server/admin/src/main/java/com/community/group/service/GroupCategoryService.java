package com.community.group.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.board.entity.BoardEntity;
import com.community.board.repository.master.BoardRepository;
import com.community.board.service.BoardFields;
import com.community.group.dto.request.GroupCategoryCreateRequest;
import com.community.group.dto.request.GroupCategoryOrderRequest;
import com.community.group.dto.request.GroupCategorySaveRequest;
import com.community.group.dto.response.GroupCategoriesResponse;
import com.community.group.dto.response.GroupCategoryItemView;
import com.community.group.entity.GroupCategoryBoardEntity;
import com.community.group.entity.GroupCategoryEntity;
import com.community.group.exception.GroupCategoryNameTakenException;
import com.community.group.exception.GroupCategoryNotEmptyException;
import com.community.group.exception.GroupCategoryNotFoundException;
import com.community.group.exception.GroupCategoryStaleException;
import com.community.group.repository.master.GroupCategoryBoardRepository;
import com.community.group.repository.master.GroupCategoryRepository;
import com.community.permission.dto.AdminActor;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소모임 분류 관리 (docs/features/group-contract.md 「관리자 화면」, admin-web {@code useGroupCategoryApi.ts} 머리말 — community docs/api-groups.md 6-3).
 *
 * <ul>
 *   <li>권한은 컨트롤러가 선언한다(조회 {@code group.read}, 나머지 {@code group.settings}).</li>
 *   <li>이름: {@link GroupCategoryNames} — 400 {@code fields.name}, 같은 이름(공백·대소문자 무시) 409 {@code GROUP_CATEGORY_NAME_TAKEN}.</li>
 *   <li>저장은 이 분류의 게시판 <b>전체 교체</b>(보낸 순서가 할당 순서). 다른 분류의 게시판은 옮겨지고 그 분류의 {@code updatedAt} 도 오른다.
 *       <b>새로 넣는</b> 게시판만 켜진 소모임 게시판이어야 한다(이미 이 분류에 있는 꺼진 게시판은 그대로 받는다) — 아니면 400 {@code fields.boardIds}.</li>
 *   <li>「다른 관리자가 먼저 고쳤어요」: 요청 {@code updatedAt} 보다 서버 값이 더 최근(밀리초 비교)이면 409 {@code GROUP_CATEGORY_STALE} — 잠금 안에서 비교한다.
 *       순서 바꾸기·삭제는 비교하지 않는다.</li>
 *   <li>동시성: 분류 표를 바꾸는 입구는 모두 {@link GroupCategoryLock} 하나로 줄을 세운다(분류를 고른 게시판 만들기 포함).</li>
 *   <li>감사 {@code GROUP_CATEGORY_CREATE|UPDATE|ORDER|DELETE}(대상 {@code GROUP_CATEGORY} = 분류 ID, 순서는 {@code order}). 바뀐 것이 없으면 남기지 않는다.</li>
 * </ul>
 */
@Service
public class GroupCategoryService {

    private static final Logger log = LoggerFactory.getLogger(GroupCategoryService.class);
    static final String TARGET = "GROUP_CATEGORY";
    private static final int ORDER_STEP = 10;

    private final GroupCategoryRepository categories;
    private final GroupCategoryBoardRepository assignments;
    private final BoardRepository boards;
    private final GroupCategoryViews views;
    private final SnowflakeIdGenerator ids;
    private final AuditLogService audit;
    private final EntityManager entityManager;

    public GroupCategoryService(GroupCategoryRepository categories, GroupCategoryBoardRepository assignments, BoardRepository boards,
                                GroupCategoryViews views, SnowflakeIdGenerator ids, AuditLogService audit, EntityManager entityManager) {
        this.categories = categories;
        this.assignments = assignments;
        this.boards = boards;
        this.views = views;
        this.ids = ids;
        this.audit = audit;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public GroupCategoriesResponse list() {
        return views.body();
    }

    // ── 만들기 ─────────────────────────────────────────────────────────────

    @Transactional
    public GroupCategoryItemView create(AdminActor actor, GroupCategoryCreateRequest request) {
        String raw = request == null ? null : request.name();
        GroupCategoryNames.problem(raw).ifPresent(problem -> {
            throw ValidationException.of("name", problem);
        });
        String name = GroupCategoryNames.normalize(raw);
        String key = GroupCategoryNames.key(name);
        GroupCategoryLock.acquire(entityManager);
        List<GroupCategoryEntity> all = categories.findAllByOrderBySortOrderAscIdAsc();
        if (all.stream().anyMatch(cat -> cat.getNameKey().equals(key))) {
            throw new GroupCategoryNameTakenException();
        }
        int sortOrder = all.stream().mapToInt(GroupCategoryEntity::getSortOrder).max().orElse(0) + ORDER_STEP;
        GroupCategoryEntity cat = GroupCategoryEntity.create(ids.nextId(), name, key, sortOrder, actor.operatorId(), now());
        categories.persist(cat);
        flushOrTaken();
        audit.record(event(actor, AuditActions.GROUP_CATEGORY_CREATE, Long.toString(cat.getId())).with("before", Map.of()).with("after", Map.of("name", name)));
        log.info("소모임 분류를 만들었다 categoryId={} by operatorId={}", cat.getId(), actor.operatorId());
        return views.item(cat.getId());
    }

    // ── 저장(이름 + 게시판 전체 교체) ────────────────────────────────────────

    @Transactional
    public GroupCategoriesResponse save(AdminActor actor, String categoryId, GroupCategorySaveRequest request) {
        Long id = GroupCategoryNames.parseId(categoryId);
        if (id == null) {
            throw new GroupCategoryNotFoundException();
        }
        GroupCategoryLock.acquire(entityManager);
        GroupCategoryEntity cat = categories.findById(id).orElseThrow(GroupCategoryNotFoundException::new);
        if (request == null) {
            throw ValidationException.of("name", new MessageRef("group.category.name.required"));
        }

        // 1) 다른 관리자가 먼저 고쳤나(잠금 안에서)
        String rawUpdatedAt = request.updatedAt() == null || request.updatedAt().isBlank() ? null : request.updatedAt().strip();
        if (rawUpdatedAt != null) {
            Instant loaded;
            try {
                loaded = Instant.parse(rawUpdatedAt);
            } catch (DateTimeParseException e) {
                throw ValidationException.of("updatedAt", new MessageRef("group.category.updatedAt.invalid"));
            }
            if (cat.getUpdatedAt().truncatedTo(ChronoUnit.MILLIS).isAfter(loaded.truncatedTo(ChronoUnit.MILLIS))) {
                throw new GroupCategoryStaleException();
            }
        }

        // 2) 입력 검사
        List<GroupCategoryBoardEntity> current = assignments.findByCategoryIdOrderByAssignedOrderAscBoardIdAsc(id);
        Set<Long> currentIds = new HashSet<>();
        current.forEach(row -> currentIds.add(row.getBoardId()));
        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        GroupCategoryNames.problem(request.name()).ifPresent(problem -> invalid.put("name", problem));
        List<Long> wanted = new ArrayList<>();
        Map<Long, BoardEntity> boardById = new HashMap<>();
        if (request.boardIds() == null) {
            invalid.put("boardIds", new MessageRef("group.category.boardIds.required"));
        } else {
            boards.findAllByOrderBySortOrderAscIdAsc().forEach(board -> boardById.put(board.getId(), board));
            boolean bad = false;
            for (String raw : request.boardIds()) {
                Long boardId = GroupCategoryNames.parseId(raw);
                BoardEntity board = boardId == null ? null : boardById.get(boardId);
                if (board == null || wanted.contains(boardId)) {
                    bad = true;
                    continue;
                }
                // 새로 넣는 게시판만 켜진 소모임 게시판이어야 한다 — 이미 이 분류에 있는 꺼진 게시판은 그대로 받는다(전체 교체라 화면이 같이 보낸다)
                if (!currentIds.contains(boardId) && (!BoardFields.KIND_GROUP.equals(board.getKind()) || !board.isActive())) {
                    bad = true;
                }
                wanted.add(boardId);
            }
            if (bad) {
                invalid.put("boardIds", new MessageRef("group.category.boardIds.invalid"));
            }
        }
        if (!invalid.isEmpty()) {
            throw new ValidationException(invalid);
        }

        // 3) 같은 이름
        String name = GroupCategoryNames.normalize(request.name());
        String key = GroupCategoryNames.key(name);
        List<GroupCategoryEntity> all = categories.findAllByOrderBySortOrderAscIdAsc();
        if (all.stream().anyMatch(other -> !other.getId().equals(id) && other.getNameKey().equals(key))) {
            throw new GroupCategoryNameTakenException();
        }

        // 4) 적용
        List<Long> beforeIds = current.stream().map(GroupCategoryBoardEntity::getBoardId).toList();
        String oldName = cat.getName();
        boolean nameChanged = !oldName.equals(name);
        boolean boardsChanged = !beforeIds.equals(wanted);
        if (!nameChanged && !boardsChanged) {
            return views.body();
        }
        Map<Long, GroupCategoryEntity> catById = new HashMap<>();
        all.forEach(other -> catById.put(other.getId(), other));
        Map<Long, GroupCategoryBoardEntity> rowByBoard = new HashMap<>();
        assignments.findAll().forEach(row -> rowByBoard.put(row.getBoardId(), row));
        Instant now = now();
        Set<Long> leftCategories = new LinkedHashSet<>();
        List<String> moved = new ArrayList<>();
        Set<Long> wantedSet = new HashSet<>(wanted);
        for (GroupCategoryBoardEntity row : current) {
            if (!wantedSet.contains(row.getBoardId())) {
                assignments.delete(row);
            }
        }
        for (int i = 0; i < wanted.size(); i++) {
            long boardId = wanted.get(i);
            GroupCategoryBoardEntity row = rowByBoard.get(boardId);
            if (row == null) {
                assignments.persist(GroupCategoryBoardEntity.assign(boardId, id, i + 1));
            } else if (!row.getCategoryId().equals(id)) {
                GroupCategoryEntity from = catById.get(row.getCategoryId());
                if (from != null) {
                    leftCategories.add(from.getId());
                    moved.add(boardById.get(boardId).getSlug() + " (" + from.getName() + ")");
                }
                row.moveTo(id, i + 1);
            } else if (row.getAssignedOrder() != i + 1) {
                row.moveTo(id, i + 1);
            }
        }
        if (nameChanged) {
            cat.rename(name, key);
        }
        cat.touch(actor.operatorId(), now);
        // 게시판이 빠져나간 분류도 올린다 — 그 분류를 보던 사람이 낡은 목록으로 옮겨진 게시판을 되돌리지 못하게
        leftCategories.forEach(other -> catById.get(other).touch(actor.operatorId(), now));
        flushOrTaken();

        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        if (nameChanged) {
            before.put("name", oldName);
            after.put("name", name);
        }
        if (boardsChanged) {
            before.put("boards", slugs(beforeIds, boardById));
            after.put("boards", slugs(wanted, boardById));
        }
        AuditEvent updated = event(actor, AuditActions.GROUP_CATEGORY_UPDATE, Long.toString(id)).with("before", before).with("after", after);
        audit.record(moved.isEmpty() ? updated : updated.with("moved", moved));
        log.info("소모임 분류를 저장했다 categoryId={} 이름바뀜={} 게시판={}개 옮김={}개 by operatorId={}", id, nameChanged, wanted.size(), moved.size(), actor.operatorId());
        return views.body();
    }

    // ── 순서 · 삭제 ────────────────────────────────────────────────────────

    @Transactional
    public GroupCategoriesResponse order(AdminActor actor, GroupCategoryOrderRequest request) {
        GroupCategoryLock.acquire(entityManager);
        List<GroupCategoryEntity> all = categories.findAllByOrderBySortOrderAscIdAsc();
        Map<Long, GroupCategoryEntity> byId = new LinkedHashMap<>();
        all.forEach(cat -> byId.put(cat.getId(), cat));
        List<Long> wanted = new ArrayList<>();
        if (request != null && request.ids() != null) {
            request.ids().forEach(raw -> wanted.add(GroupCategoryNames.parseId(raw)));
        }
        if (request == null || request.ids() == null || wanted.size() != all.size() || wanted.contains(null)
                || new LinkedHashSet<>(wanted).size() != wanted.size() || !byId.keySet().containsAll(wanted)) {
            throw ValidationException.of("ids", new MessageRef("group.category.order.mismatch"));
        }
        List<String> beforeNames = all.stream().map(GroupCategoryEntity::getName).toList();
        List<String> afterNames = wanted.stream().map(id -> byId.get(id).getName()).toList();
        boolean changed = false;
        for (int i = 0; i < wanted.size(); i++) {
            GroupCategoryEntity cat = byId.get(wanted.get(i));
            int sortOrder = (i + 1) * ORDER_STEP;
            if (cat.getSortOrder() != sortOrder) {
                cat.reorder(sortOrder);
                changed = true;
            }
        }
        if (changed) {
            categories.flush();
        }
        if (!beforeNames.equals(afterNames)) {
            audit.record(event(actor, AuditActions.GROUP_CATEGORY_ORDER, "order")
                    .with("before", Map.of("order", beforeNames)).with("after", Map.of("order", afterNames)));
            log.info("소모임 분류 순서를 바꿨다 by operatorId={}", actor.operatorId());
        }
        return views.body();
    }

    @Transactional
    public void delete(AdminActor actor, String categoryId) {
        Long id = GroupCategoryNames.parseId(categoryId);
        if (id == null) {
            throw new GroupCategoryNotFoundException();
        }
        GroupCategoryLock.acquire(entityManager);
        GroupCategoryEntity cat = categories.findById(id).orElseThrow(GroupCategoryNotFoundException::new);
        // 소모임(열림·비활성·대기)이 있는 분류는 못 지운다 — 소모임 서버 단계 전까지 늘 0 이라 지금은 걸리지 않는다
        long groupCount = views.groupCount(id);
        if (groupCount > 0) {
            throw new GroupCategoryNotEmptyException(groupCount);
        }
        List<GroupCategoryBoardEntity> rows = assignments.findByCategoryIdOrderByAssignedOrderAscBoardIdAsc(id);
        Map<Long, BoardEntity> boardById = new HashMap<>();
        boards.findAllById(rows.stream().map(GroupCategoryBoardEntity::getBoardId).toList()).forEach(board -> boardById.put(board.getId(), board));
        List<String> boardSlugs = slugs(rows.stream().map(GroupCategoryBoardEntity::getBoardId).toList(), boardById);
        String name = cat.getName();
        // 할당된 게시판은 「분류 없음」이 된다(행을 지운다)
        assignments.deleteAll(rows);
        categories.delete(cat);
        categories.flush();
        AuditEvent deleted = event(actor, AuditActions.GROUP_CATEGORY_DELETE, Long.toString(id)).with("deleted", List.of(name));
        audit.record(boardSlugs.isEmpty() ? deleted : deleted.with("boards", boardSlugs));
        log.info("소모임 분류를 지웠다 categoryId={} 게시판={}개 by operatorId={}", id, rows.size(), actor.operatorId());
    }

    // ── 게시판 만들기에서(같은 트랜잭션) ───────────────────────────────────────

    /**
     * 게시판 만들기가 고른 분류 — 분류 잠금을 잡고 읽는다. 없으면 400 {@code fields.groupCategoryId}(게시판은 만들어지지 않는다).
     * 부르는 쪽의 트랜잭션 안에서, 게시판 행을 넣기 <b>전에</b> 부른다(잠금 순서).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public GroupCategoryEntity lockForNewBoard(long categoryId) {
        GroupCategoryLock.acquire(entityManager);
        return categories.findById(categoryId)
                .orElseThrow(() -> ValidationException.of("groupCategoryId", new MessageRef("board.groupCategory.missing")));
    }

    /** 새 게시판을 그 분류의 끝에 할당하고 분류의 {@code updatedAt} 을 올린다. {@link #lockForNewBoard} 다음에 같은 트랜잭션에서 부른다 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendNewBoard(GroupCategoryEntity cat, long boardId, Long operatorId, Instant now) {
        int order = assignments.findTopByCategoryIdOrderByAssignedOrderDesc(cat.getId()).map(last -> last.getAssignedOrder() + 1).orElse(1);
        assignments.persist(GroupCategoryBoardEntity.assign(boardId, cat.getId(), order));
        cat.touch(operatorId, now.truncatedTo(ChronoUnit.MICROS));
    }

    // ── 안쪽 ────────────────────────────────────────────────────────────────

    /** DB 가 마이크로초까지 저장하므로 응답과 다음 비교가 같은 값을 보게 자른다 */
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private static List<String> slugs(List<Long> boardIds, Map<Long, BoardEntity> boardById) {
        return boardIds.stream().map(boardId -> {
            BoardEntity board = boardById.get(boardId);
            return board == null ? Long.toString(boardId) : board.getSlug();
        }).toList();
    }

    /** 이름 유일 색인 위반(동시 요청이 잠금을 우회한 경우의 마지막 방어)을 같은 이름 오류로 바꾼다 */
    private void flushOrTaken() {
        try {
            categories.flush();
        } catch (DataIntegrityViolationException e) {
            if (String.valueOf(e.getMostSpecificCause().getMessage()).contains("ux_group_categories_name_key")) {
                throw new GroupCategoryNameTakenException();
            }
            throw e;
        }
    }

    private static AuditEvent event(AdminActor actor, String action, String targetId) {
        return AuditEvent.byOperator(actor.operatorId(), actor.ip(), action, AuditResult.SUCCESS).target(TARGET, targetId);
    }
}
