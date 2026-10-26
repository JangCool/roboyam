package com.community.group.service;

import com.community.group.entity.GroupCategoryBoardEntity;
import com.community.group.entity.GroupCategoryEntity;
import com.community.group.repository.master.GroupCategoryBoardRepository;
import com.community.group.repository.master.GroupCategoryRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 소모임 분류 읽기 모델 — 분류(이름·순서) · 분류에 할당한 게시판. 소모임의 분류는 그 게시판이 할당된 분류로 정해지는 파생값이다(없으면 「분류 없음」).
 * 표가 작아(분류 수십·게시판 수십) 요청마다 한 번에 읽는다(캐시 없음 — 관리자가 바꾸면 바로 보인다). 부르는 서비스의 트랜잭션 안에서 돈다.
 */
@Component
public class GroupCategoryLookup {

    private final GroupCategoryRepository categories;
    private final GroupCategoryBoardRepository assignments;

    public GroupCategoryLookup(GroupCategoryRepository categories, GroupCategoryBoardRepository assignments) {
        this.categories = categories;
        this.assignments = assignments;
    }

    /** 한 시점의 분류 사본 */
    public record Snapshot(List<GroupCategoryEntity> ordered, Map<Long, GroupCategoryEntity> byId, Map<Long, Long> categoryOfBoard,
                           Map<Long, List<Long>> boardsOfCategory) {

        /** 그 게시판의 분류(없으면 null) */
        public GroupCategoryEntity ofBoard(Long boardId) {
            Long categoryId = boardId == null ? null : categoryOfBoard.get(boardId);
            return categoryId == null ? null : byId.get(categoryId);
        }

        /** 그 분류에 할당된 게시판(할당 순서) */
        public List<Long> boards(long categoryId) {
            return boardsOfCategory.getOrDefault(categoryId, List.of());
        }
    }

    public Snapshot snapshot() {
        List<GroupCategoryEntity> ordered = categories.findAllByOrderBySortOrderAscIdAsc();
        Map<Long, GroupCategoryEntity> byId = new LinkedHashMap<>();
        ordered.forEach(cat -> byId.put(cat.getId(), cat));
        Map<Long, Long> categoryOfBoard = new HashMap<>();
        Map<Long, List<Long>> boardsOfCategory = new HashMap<>();
        for (GroupCategoryBoardEntity row : assignments.findAllByOrderByCategoryIdAscAssignedOrderAscBoardIdAsc()) {
            categoryOfBoard.put(row.getBoardId(), row.getCategoryId());
            boardsOfCategory.computeIfAbsent(row.getCategoryId(), k -> new ArrayList<>()).add(row.getBoardId());
        }
        return new Snapshot(ordered, byId, categoryOfBoard, boardsOfCategory);
    }
}
