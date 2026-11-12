package com.community.notification.repository.master;

import com.community.notification.entity.NotificationBuiltinChannelEntity;
import com.community.zconfiguration.jpa.IrobitRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

public interface NotificationBuiltinChannelRepository extends IrobitRepository<NotificationBuiltinChannelEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<NotificationBuiltinChannelEntity> findWithLockByChannelKey(String channelKey);
}
