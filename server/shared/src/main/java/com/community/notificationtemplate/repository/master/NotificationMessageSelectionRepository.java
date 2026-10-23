package com.community.notificationtemplate.repository.master;

import com.community.notificationtemplate.entity.NotificationMessageSelectionEntity;
import com.community.notificationtemplate.entity.NotificationMessageSelectionId;
import com.community.zconfiguration.jpa.RoboyamRepository;
import java.util.List;

public interface NotificationMessageSelectionRepository extends RoboyamRepository<NotificationMessageSelectionEntity, NotificationMessageSelectionId> {

    /** 대상의 모든 채널 설정 */
    List<NotificationMessageSelectionEntity> findAllByTargetTypeAndTargetCode(String targetType, String targetCode);
}
