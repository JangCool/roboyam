package com.community.highlight.repository.master;

import com.community.highlight.entity.HighlightBoardSettingEntity;
import com.community.zconfiguration.jpa.IrobitRepository;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

/** 게시판 추천 글 설정(게시판마다 행 하나, 없으면 자동 채우기 켬) */
public interface HighlightBoardSettingRepository extends IrobitRepository<HighlightBoardSettingEntity, Long> {

    /** 자동 채우기를 끈 게시판 행(관리자 곳 목록의 게시판 묶음) */
    List<HighlightBoardSettingEntity> findAllByAutoFillFalse();

    /** 스위치 바꾸기 — 같은 게시판의 동시 저장을 줄 세운다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<HighlightBoardSettingEntity> findWithLockByBoardId(Long boardId);
}
