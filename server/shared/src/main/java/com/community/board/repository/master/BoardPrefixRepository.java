package com.community.board.repository.master;

import com.community.board.entity.BoardPrefixEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import java.util.List;

public interface BoardPrefixRepository extends RoboyamRepository<BoardPrefixEntity, Long> {

    List<BoardPrefixEntity> findByBoardIdOrderBySortOrderAscIdAsc(Long boardId);
}
