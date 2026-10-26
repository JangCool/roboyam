package com.community.group.service;

import com.community.board.entity.BoardEntity;
import com.community.board.repository.master.BoardRepository;
import com.community.board.service.BoardFields;
import com.community.group.dto.response.GroupCategoriesResponse;
import com.community.group.dto.response.GroupCategoryBoardView;
import com.community.group.dto.response.GroupCategoryCandidateView;
import com.community.group.dto.response.GroupCategoryItemView;
import com.community.group.entity.GroupCategoryBoardEntity;
import com.community.group.entity.GroupCategoryEntity;
import com.community.group.exception.GroupCategoryNotFoundException;
import com.community.group.repository.master.GroupCategoryBoardRepository;
import com.community.group.repository.master.GroupCategoryRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 소모임 분류 응답을 조립한다(분류 목록 + 할당할 수 있는 게시판, 게시판 → 분류). 분류·할당·게시판 세 표를 합치므로 매퍼가 아니라 여기서 만든다.
 * 표가 작아(분류 수십·소모임 게시판 수십) 한 번에 읽는다. 부르는 서비스의 트랜잭션 안에서 돈다.
 */
@Component
public class GroupCategoryViews {

    private final GroupCategoryRepository categories;
    private final GroupCategoryBoardRepository assignments;
    private final BoardRepository boards;

    public GroupCategoryViews(GroupCategoryRepository categories, GroupCategoryBoardRepository assignments, BoardRepository boards) {
        this.categories = categories;
        this.assignments = assignments;
        this.boards = boards;
    }

    /** GET 본문 전체 */
    public GroupCategoriesResponse body() {
        List<GroupCategoryEntity> cats = categories.findAllByOrderBySortOrderAscIdAsc();
        List<BoardEntity> allBoards = boards.findAllByOrderBySortOrderAscIdAsc();
        Map<Long, BoardEntity> boardById = new HashMap<>();
        allBoards.forEach(board -> boardById.put(board.getId(), board));
        Map<Long, List<GroupCategoryBoardEntity>> rowsByCategory = new HashMap<>();
        Map<Long, Long> categoryOfBoard = new HashMap<>();
        for (GroupCategoryBoardEntity row : assignments.findAllByOrderByCategoryIdAscAssignedOrderAscBoardIdAsc()) {
            rowsByCategory.computeIfAbsent(row.getCategoryId(), k -> new ArrayList<>()).add(row);
            categoryOfBoard.put(row.getBoardId(), row.getCategoryId());
        }
        Map<Long, Long> groupCounts = groupCountsByBoard();

        List<GroupCategoryItemView> items = new ArrayList<>();
        Map<Long, String> nameById = new LinkedHashMap<>();
        for (GroupCategoryEntity cat : cats) {
            nameById.put(cat.getId(), cat.getName());
            List<GroupCategoryBoardView> views = new ArrayList<>();
            for (GroupCategoryBoardEntity row : rowsByCategory.getOrDefault(cat.getId(), List.of())) {
                BoardEntity board = boardById.get(row.getBoardId());
                if (board != null) {
                    views.add(new GroupCategoryBoardView(Long.toString(board.getId()), board.getSlug(), board.getName(), board.isActive(),
                            board.isInvestmentNotice(), groupCounts.getOrDefault(board.getId(), 0L)));
                }
            }
            long groupCount = views.stream().mapToLong(GroupCategoryBoardView::groupCount).sum();
            boolean investmentNotice = views.stream().anyMatch(GroupCategoryBoardView::investmentNotice);
            items.add(new GroupCategoryItemView(Long.toString(cat.getId()), cat.getName(), cat.getSortOrder(), views.size(), groupCount, investmentNotice,
                    List.copyOf(views), cat.getUpdatedAt()));
        }

        List<GroupCategoryCandidateView> available = new ArrayList<>();
        for (BoardEntity board : allBoards) {
            if (!BoardFields.KIND_GROUP.equals(board.getKind()) || !board.isActive()) {
                continue;
            }
            Long categoryId = categoryOfBoard.get(board.getId());
            available.add(new GroupCategoryCandidateView(Long.toString(board.getId()), board.getSlug(), board.getName(),
                    categoryId == null ? null : Long.toString(categoryId), categoryId == null ? null : nameById.get(categoryId), board.isInvestmentNotice(),
                    groupCounts.getOrDefault(board.getId(), 0L)));
        }
        return new GroupCategoriesResponse(List.copyOf(items), List.copyOf(available));
    }

    /** 분류 하나(만들기 응답) */
    public GroupCategoryItemView item(long categoryId) {
        String id = Long.toString(categoryId);
        return body().categories().stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow(GroupCategoryNotFoundException::new);
    }

    /** 게시판 ID → 할당된 분류(게시판 관리 목록·상세의 {@code groupCategoryId}·{@code groupCategoryName}) */
    public Map<Long, GroupCategoryEntity> categoryByBoard() {
        Map<Long, GroupCategoryEntity> byId = new HashMap<>();
        categories.findAll().forEach(cat -> byId.put(cat.getId(), cat));
        Map<Long, GroupCategoryEntity> out = new HashMap<>();
        for (GroupCategoryBoardEntity row : assignments.findAll()) {
            GroupCategoryEntity cat = byId.get(row.getCategoryId());
            if (cat != null) {
                out.put(row.getBoardId(), cat);
            }
        }
        return out;
    }

    /** 이 분류의 게시판들을 쓰는 소모임 수(열림·비활성·대기) — 삭제의 {@code GROUP_CATEGORY_NOT_EMPTY} 검사 자리 */
    public long groupCount(long categoryId) {
        Map<Long, Long> counts = groupCountsByBoard();
        if (counts.isEmpty()) {
            return 0;
        }
        return assignments.findByCategoryIdOrderByAssignedOrderAscBoardIdAsc(categoryId).stream()
                .mapToLong(row -> counts.getOrDefault(row.getBoardId(), 0L)).sum();
    }

    /**
     * 게시판 ID → 그 게시판을 쓰는 소모임 수(삭제·반려 제외 — 열림·비활성·대기).
     * 로보얌에는 아직 소모임 표가 없어 늘 비어 있다(모든 {@code groupCount} = 0) — <b>소모임 서버 단계에서 채운다</b>.
     */
    private Map<Long, Long> groupCountsByBoard() {
        return Map.of();
    }
}
