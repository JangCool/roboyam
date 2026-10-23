package com.community.notificationtemplate.repository.master;

import com.community.notificationtemplate.entity.NotificationKakaoTemplateEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import java.util.List;

public interface NotificationKakaoTemplateRepository extends RoboyamRepository<NotificationKakaoTemplateEntity, Long> {

    List<NotificationKakaoTemplateEntity> findAllByOrderByIdAsc();
}
