package com.community.notificationtemplate.repository.master;

import com.community.notificationtemplate.catalog.MessageChannel;
import com.community.notificationtemplate.catalog.MessageTargetType;
import com.community.notificationtemplate.entity.NotificationMessageEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

public interface NotificationMessageRepository extends RoboyamRepository<NotificationMessageEntity, Long> {

    /** 모든 사용자 문구, 만든 차례 */
    List<NotificationMessageEntity> findAllByOrderByIdAsc();

    /** 대상 · 채널의 사용자 문구, 만든 차례 */
    List<NotificationMessageEntity> findAllByTargetTypeAndTargetCodeAndChannelOrderByIdAsc(MessageTargetType targetType, String targetCode, MessageChannel channel);

    /** 대상의 사용자 문구(모든 채널), 만든 차례 */
    List<NotificationMessageEntity> findAllByTargetTypeAndTargetCodeOrderByIdAsc(MessageTargetType targetType, String targetCode);

    long countByTargetTypeAndTargetCodeAndChannel(MessageTargetType targetType, String targetCode, MessageChannel channel);

    /** 템플릿을 쓰는 사용자 문구(채널 · 템플릿 id) */
    List<NotificationMessageEntity> findAllByChannelAndTemplateId(MessageChannel channel, String templateId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<NotificationMessageEntity> findWithLockById(Long id);
}
