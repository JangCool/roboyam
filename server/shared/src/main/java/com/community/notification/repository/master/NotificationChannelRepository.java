package com.community.notification.repository.master;

import com.community.notification.entity.NotificationChannelEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

public interface NotificationChannelRepository extends RoboyamRepository<NotificationChannelEntity, Long> {

    /** 행을 쓰기 잠금으로 읽는다 — 같은 도구를 동시에 고치거나 지우는 요청을 줄 세울 때. 트랜잭션 안에서만 의미가 있다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<NotificationChannelEntity> findWithLockById(Long id);

    /** 만든 차례(id) 대로 모든 도구 */
    List<NotificationChannelEntity> findAllByOrderByIdAsc();

    boolean existsByChannelNameIgnoreCase(String channelName);

    boolean existsByChannelNameIgnoreCaseAndIdNot(String channelName, Long id);
}
