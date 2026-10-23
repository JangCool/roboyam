package com.community.notificationtemplate.repository.master;

import com.community.notificationtemplate.entity.NotificationMailTemplateEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

public interface NotificationMailTemplateRepository extends RoboyamRepository<NotificationMailTemplateEntity, Long> {

    /** 만든 차례(id) */
    List<NotificationMailTemplateEntity> findAllByOrderByIdAsc();

    /** 행을 쓰기 잠금으로 읽는다 — 같은 템플릿을 동시에 고치거나 지우는 요청을 줄 세운다. 트랜잭션 안에서만 의미가 있다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<NotificationMailTemplateEntity> findWithLockById(Long id);
}
