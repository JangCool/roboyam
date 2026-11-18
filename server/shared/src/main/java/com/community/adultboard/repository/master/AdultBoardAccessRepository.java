package com.community.adultboard.repository.master;

import com.community.adultboard.entity.AdultBoardAccessEntity;
import com.community.zconfiguration.jpa.IrobitRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

/**
 * 회원의 19금 게시판 접근 설정·핀번호({@code member_adult_board_access}, V254). 회원 행을 지우면 외래키 {@code ON DELETE CASCADE} 로 함께 지워진다.
 */
public interface AdultBoardAccessRepository extends IrobitRepository<AdultBoardAccessEntity, Long> {

    /**
     * 행을 쓰기 잠금으로 읽는다 — 같은 회원의 핀 시도·설정 변경을 줄 세운다(틀린 횟수가 겹치지 않게). 트랜잭션 안에서만 의미가 있다.
     * 표준 잠금({@code FOR NO KEY UPDATE})으로 충분하다 — 이 행을 FK 로 가리키는 표가 없다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AdultBoardAccessEntity> findWithLockByUserId(Long userId);
}
