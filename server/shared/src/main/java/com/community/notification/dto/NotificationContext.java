package com.community.notification.dto;

import com.community.notification.entity.NotificationSeverity;
import com.community.zconfiguration.message.MessageRef;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 알림 한 건을 발행할 때 건네는 맥락 — 만드는 쪽이 아는 사실만 담는다(받는 사람은 이벤트의 수신 규칙이 정한다). 값은 바뀌지 않으며 {@code actor(..)} 같은 메서드가 새 값을 돌려준다.
 *
 * <pre>{@code
 * publisher.publish(AdminNotificationEvent.OPERATOR_ACCOUNT_CREATED,
 *         NotificationContext.empty().actor(actor.operatorId()).args(operator.getOperatorName()));
 * }</pre>
 *
 * <p>인자({@code args})는 문구의 {@code {0}} {@code {1}} 자리에 들어간다. 글자·수 외에 {@link MessageRef} 를 줄 수 있다 — 읽는 사람의 언어로 풀리는 문구(배치 이름, 「역할 추가」 같은 사유 문장)다.
 * <b>비밀번호·토큰·승인번호·이메일 원문은 인자로 싣지 않는다.</b> 운영자 이름·배치 이름·역할 이름 같은 표시용 값만 싣는다.
 *
 * @param actorId           행위자 운영자(없으면 null — 배치 감시자). 이벤트가 {@code excludeActor} 이면 이 운영자는 받지 않는다
 * @param targetOperatorId  대상 운영자 — 수신 규칙이 {@code SELF} 인 이벤트의 받는 사람
 * @param args              문구 인자 (글자 · 수 · {@link MessageRef})
 * @param dedupeKey         같은 이벤트에서 이 값이 같은 알림을 묶는 키(이벤트의 {@code collapseSeconds} 안에서). 없으면 그 이벤트 전체가 한 묶음이다
 * @param linkArgs          이동 경로 템플릿의 {@code {이름}} 자리를 채우는 값(발행 때 URL 인코딩한다)
 * @param severity          이 알림만 심각도를 바꾸고 싶을 때(없으면 이벤트의 기본값)
 * @param extraRecipientIds 수신 규칙 말고 더 받을 운영자(예: 초대를 보낸 운영자). 활성 운영자만 받는다
 */
public record NotificationContext(Long actorId, Long targetOperatorId, List<Object> args, String dedupeKey, Map<String, String> linkArgs,
                                  NotificationSeverity severity, Set<Long> extraRecipientIds) {

    public NotificationContext {
        args = args == null ? List.of() : List.copyOf(args);
        linkArgs = linkArgs == null ? Map.of() : Map.copyOf(linkArgs);
        extraRecipientIds = extraRecipientIds == null ? Set.of() : Set.copyOf(extraRecipientIds);
    }

    public static NotificationContext empty() {
        return new NotificationContext(null, null, List.of(), null, Map.of(), null, Set.of());
    }

    public NotificationContext actor(Long operatorId) {
        return new NotificationContext(operatorId, targetOperatorId, args, dedupeKey, linkArgs, severity, extraRecipientIds);
    }

    public NotificationContext target(Long operatorId) {
        return new NotificationContext(actorId, operatorId, args, dedupeKey, linkArgs, severity, extraRecipientIds);
    }

    public NotificationContext args(Object... values) {
        List<Object> list = Arrays.stream(values).map(value -> value == null ? (Object) "" : value).toList(); // 빈 값은 빈 글자로 — 문구 자리가 비어 보이게
        return new NotificationContext(actorId, targetOperatorId, list, dedupeKey, linkArgs, severity, extraRecipientIds);
    }

    public NotificationContext dedupe(String key) {
        return new NotificationContext(actorId, targetOperatorId, args, key, linkArgs, severity, extraRecipientIds);
    }

    public NotificationContext link(String name, String value) {
        Map<String, String> next = new LinkedHashMap<>(linkArgs);
        next.put(name, value);
        return new NotificationContext(actorId, targetOperatorId, args, dedupeKey, next, severity, extraRecipientIds);
    }

    public NotificationContext severity(NotificationSeverity value) {
        return new NotificationContext(actorId, targetOperatorId, args, dedupeKey, linkArgs, value, extraRecipientIds);
    }

    public NotificationContext alsoTo(Long operatorId) {
        if (operatorId == null) {
            return this;
        }
        Set<Long> next = new LinkedHashSet<>(extraRecipientIds);
        next.add(operatorId);
        return new NotificationContext(actorId, targetOperatorId, args, dedupeKey, linkArgs, severity, next);
    }
}
