package com.community.notificationtemplate.repository.master;

import com.community.notificationtemplate.catalog.MessageChannel;
import com.community.notificationtemplate.entity.NotificationChatTemplateEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

public interface NotificationChatTemplateRepository extends RoboyamRepository<NotificationChatTemplateEntity, Long> {

    /** 그 종류의 템플릿, 만든 차례 */
    List<NotificationChatTemplateEntity> findAllByToolOrderByIdAsc(MessageChannel tool);

    long countByTool(MessageChannel tool);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<NotificationChatTemplateEntity> findWithLockById(Long id);
}
