package com.community.notificationtemplate.repository.master;

import com.community.notificationtemplate.entity.NotificationKakaoTemplateEntity;
import com.community.zconfiguration.jpa.IrobitRepository;
import java.util.List;

public interface NotificationKakaoTemplateRepository extends IrobitRepository<NotificationKakaoTemplateEntity, Long> {

    List<NotificationKakaoTemplateEntity> findAllByOrderByIdAsc();
}
