package com.community.notification.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 알림 이메일을 보내 달라는 요청 — 발행기가 업무 트랜잭션 안에서 알림 행을 만들며 함께 알려 두고, 트랜잭션이 <b>커밋된 뒤에만</b> 메일 발송기가 받는다(롤백되면 메일도 없다).
 * 받는 사람의 이메일 주소·언어는 발송 때 읽는다(주소를 이벤트에 싣지 않는다). 문구는 알림 행과 같은 인자로 들고 와 받는 사람의 언어로 알림 문구 엔진이 그린다.
 *
 * @param operatorIds     메일을 받을 운영자(이미 이벤트의 이메일 전달·운영자의 이메일 수신 설정을 통과한 사람)
 * @param teamOperatorIds     그중 <b>추가 수신 팀</b>으로 받는 운영자 — 조건 {@code recipientSource} 에 {@code TEAM}
 * @param teamOnlyOperatorIds 그중 기본 수신 규칙으로는 받지 않는(팀으로만 받는) 운영자 — 이들만 {@code DEFAULT} 가 없다(나머지는 {@code DEFAULT}, 둘 다면 둘 다 — 위에서부터 맞는 첫 규칙)
 * @param linkPath            인자를 채운 이동 경로(없으면 null)
 * @param conditions          발행 지점이 채운 조건 값
 * @param occurredAt          사건 시각(변수 {@code occurredAt})
 */
public record NotificationEmailRequested(String eventCode, String argsJson, String linkPath, List<Long> operatorIds, Set<Long> teamOperatorIds,
                                         Set<Long> teamOnlyOperatorIds, Map<String, List<String>> conditions, Instant occurredAt) {

    public NotificationEmailRequested {
        operatorIds = List.copyOf(operatorIds);
        teamOperatorIds = teamOperatorIds == null ? Set.of() : Set.copyOf(teamOperatorIds);
        teamOnlyOperatorIds = teamOnlyOperatorIds == null ? Set.of() : Set.copyOf(teamOnlyOperatorIds);
        conditions = conditions == null ? Map.of() : Map.copyOf(conditions);
    }
}
