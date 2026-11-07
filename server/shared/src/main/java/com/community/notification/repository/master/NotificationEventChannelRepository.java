package com.community.notification.repository.master;

import com.community.notification.entity.NotificationEventChannelEntity;
import com.community.notification.entity.NotificationEventChannelId;
import com.community.zconfiguration.jpa.RoboyamRepository;

/** 이벤트별 도구(Slack · Mattermost — notification_channels)({@code notification_event_channels}) — 넣기는 {@code persist}. 질의는 {@link NotificationDeliveryRepository} */
public interface NotificationEventChannelRepository extends RoboyamRepository<NotificationEventChannelEntity, NotificationEventChannelId> {
}
