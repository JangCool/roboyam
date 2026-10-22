package com.community.board.repository.master;

import com.community.board.entity.BoardStaffEntity;
import com.community.board.entity.BoardStaffId;
import com.community.zconfiguration.jpa.RoboyamRepository;
import java.util.List;

public interface BoardStaffRepository extends RoboyamRepository<BoardStaffEntity, BoardStaffId> {

    List<BoardStaffEntity> findByBoardId(Long boardId);

    /** 이 회원이 맡은 게시판 지정(공개 게시판의 「운영진」 판정) */
    List<BoardStaffEntity> findByUserId(Long userId);

    /** 이 회원이 맡은 게시판 수(매니저·스텝 합산) */
    long countByUserId(Long userId);

    long countByBoardIdAndRole(Long boardId, String role);

    boolean existsByBoardIdAndUserId(Long boardId, Long userId);
}
