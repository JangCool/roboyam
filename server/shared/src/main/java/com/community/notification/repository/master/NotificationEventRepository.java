package com.community.notification.repository.master;

import com.community.notification.entity.NotificationEventEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

public interface NotificationEventRepository extends RoboyamRepository<NotificationEventEntity, String> {

    /** 행을 쓰기 잠금으로 읽는다 — 같은 이벤트 설정을 동시에 바꾸는 요청을 줄 세울 때. 트랜잭션 안에서만 의미가 있다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<NotificationEventEntity> findWithLockByCode(String code);
}
