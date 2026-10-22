package com.community.menu.repository.master;

import com.community.menu.entity.MenuSetEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

public interface MenuSetRepository extends RoboyamRepository<MenuSetEntity, String> {

    /**
     * 그룹 행을 쓰기 잠금으로 읽는다 — 같은 그룹의 트리 저장·정보 수정·삭제·게시판 자동 추가/삭제를 줄 세운다(버전 비교가 이 잠금 아래에서 일어난다).
     * 트랜잭션 안에서만 의미가 있다
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MenuSetEntity> findWithLockBySetKey(String setKey);

    /** 만든 차례(기본 그룹이 먼저) */
    List<MenuSetEntity> findAllByOrderByCreatedAtAscSetKeyAsc();
}
