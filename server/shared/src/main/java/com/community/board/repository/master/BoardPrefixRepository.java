package com.community.board.repository.master;

import com.community.board.entity.BoardPrefixEntity;
import com.community.zconfiguration.jpa.IrobitRepository;
import java.util.List;

public interface BoardPrefixRepository extends IrobitRepository<BoardPrefixEntity, Long> {

    List<BoardPrefixEntity> findByBoardIdOrderBySortOrderAscIdAsc(Long boardId);
}
