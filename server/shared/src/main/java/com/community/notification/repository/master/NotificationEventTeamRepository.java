package com.community.notification.repository.master;

import com.community.notification.entity.NotificationEventTeamEntity;
import com.community.notification.entity.NotificationEventTeamId;
import com.community.zconfiguration.jpa.RoboyamRepository;

/** 이벤트별 추가 수신 팀(teams)({@code notification_event_teams}) — 넣기는 {@code persist}. 질의는 {@link NotificationDeliveryRepository} */
public interface NotificationEventTeamRepository extends RoboyamRepository<NotificationEventTeamEntity, NotificationEventTeamId> {
}
