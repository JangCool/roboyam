package com.community.notification.repository.master;

import com.community.notification.entity.OperatorNotificationMuteEntity;
import com.community.notification.entity.OperatorNotificationMuteId;
import com.community.zconfiguration.jpa.RoboyamRepository;
import java.util.Collection;
import java.util.List;

public interface OperatorNotificationMuteRepository extends RoboyamRepository<OperatorNotificationMuteEntity, OperatorNotificationMuteId> {

    /** 운영자 한 명이 끈 이벤트들 */
    List<OperatorNotificationMuteEntity> findByOperatorId(Long operatorId);

    /** 그 이벤트를 끈 운영자들 중 {@code operatorIds} 안에 있는 사람 — 발행 때 수신자를 거를 때 */
    List<OperatorNotificationMuteEntity> findByEventCodeAndOperatorIdIn(String eventCode, Collection<Long> operatorIds);

    void deleteByOperatorIdAndEventCode(Long operatorId, String eventCode);

    /** 코드에서 사라진 이벤트들의 끄기 기록을 지운다(카탈로그 맞춤) */
    void deleteByEventCodeIn(Collection<String> eventCodes);
}
