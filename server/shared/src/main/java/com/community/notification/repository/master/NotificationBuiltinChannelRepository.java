package com.community.notification.repository.master;

import com.community.notification.entity.NotificationBuiltinChannelEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

public interface NotificationBuiltinChannelRepository extends RoboyamRepository<NotificationBuiltinChannelEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<NotificationBuiltinChannelEntity> findWithLockByChannelKey(String channelKey);
}
