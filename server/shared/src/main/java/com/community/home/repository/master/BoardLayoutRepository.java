package com.community.home.repository.master;

import com.community.home.entity.BoardLayoutEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

/** 테마별 게시판 레이아웃({@code board_layouts}) — 키는 테마 id */
public interface BoardLayoutRepository extends RoboyamRepository<BoardLayoutEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BoardLayoutEntity> findWithLockByTheme(String theme);
}
