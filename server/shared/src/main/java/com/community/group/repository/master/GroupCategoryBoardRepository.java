package com.community.group.repository.master;

import com.community.group.entity.GroupCategoryBoardEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import java.util.List;
import java.util.Optional;

public interface GroupCategoryBoardRepository extends RoboyamRepository<GroupCategoryBoardEntity, Long> {

    /** 할당 전부(분류·할당 순서, 같으면 게시판 ID 순) — 분류 수·게시판 수가 작아 한 번에 읽는다 */
    List<GroupCategoryBoardEntity> findAllByOrderByCategoryIdAscAssignedOrderAscBoardIdAsc();

    /** 그 분류의 할당(할당 순서, 같으면 게시판 ID 순) */
    List<GroupCategoryBoardEntity> findByCategoryIdOrderByAssignedOrderAscBoardIdAsc(Long categoryId);

    /** 그 분류의 마지막 할당(게시판 만들기가 끝에 붙일 자리) */
    Optional<GroupCategoryBoardEntity> findTopByCategoryIdOrderByAssignedOrderDesc(Long categoryId);
}
