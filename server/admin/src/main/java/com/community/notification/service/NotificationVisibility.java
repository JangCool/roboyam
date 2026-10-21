package com.community.notification.service;

import com.community.notification.entity.AdminNotificationEvent;
import com.community.notification.entity.OperatorNotificationMuteEntity;
import com.community.notification.repository.master.OperatorNotificationMuteRepository;
import com.community.permission.dto.AdminActor;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 어떤 운영자에게 <b>보이는</b> 알림 이벤트 — 서랍 목록·안 읽은 수·요약이 모두 이 한 곳의 같은 거르기를 쓴다(그래서 서로 어긋나지 않는다).
 * 보이려면 ① 발행처가 있는 이벤트이고(준비 중 제외) ② 그 이벤트를 보는 데 필요한 권한이 <b>지금</b> 있고(권한을 잃으면 조회 시점에 사라진다 — 행은 보관 정책이 지운다)
 * ③ 운영자가 개인 설정에서 끄지 않았어야 한다(필수 이벤트는 끌 수 없어 늘 보인다). <b>트랜잭션 안에서</b> 부른다.
 */
@Component
public class NotificationVisibility {

    /** 이벤트를 영역(알림 영역 선언 순서)·코드순으로 늘어놓는 차례 */
    public static final Comparator<AdminNotificationEvent> ORDER = Comparator.<AdminNotificationEvent>comparingInt(event -> event.area().ordinal())
            .thenComparing(AdminNotificationEvent::code);

    private final OperatorNotificationMuteRepository mutes;

    public NotificationVisibility(OperatorNotificationMuteRepository mutes) {
        this.mutes = mutes;
    }

    /** 내가 보는 알림의 이벤트 코드들 */
    public Set<String> codesOf(AdminActor actor) {
        Set<String> muted = mutedCodes(actor.operatorId());
        return AdminNotificationEvent.visibleTo(actor::can).stream().filter(event -> event.mandatory() || !muted.contains(event.code()))
                .map(AdminNotificationEvent::code).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** 운영자가 끈 이벤트 코드들(필수 이벤트의 오래된 행이 있어도 호출하는 쪽이 무시한다) */
    public Set<String> mutedCodes(long operatorId) {
        return mutes.findByOperatorId(operatorId).stream().map(OperatorNotificationMuteEntity::getEventCode).collect(Collectors.toSet());
    }
}
