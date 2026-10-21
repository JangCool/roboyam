package com.community.notification.dto;

import java.util.List;

/**
 * 알림 이메일을 보내 달라는 요청 — 발행기가 업무 트랜잭션 안에서 알림 행을 만들며 함께 알려 두고, 트랜잭션이 <b>커밋된 뒤에만</b> 메일 발송기가 받는다(롤백되면 메일도 없다).
 * 받는 사람의 이메일 주소·언어는 발송 때 읽는다(주소를 이벤트에 싣지 않는다). 문구는 키 + 인자로 들고 와 받는 사람의 언어로 푼다.
 *
 * @param operatorIds 메일을 받을 운영자(이미 이벤트의 이메일 전달·운영자의 이메일 수신 설정을 통과한 사람)
 * @param linkPath    인자를 채운 이동 경로(없으면 null)
 */
public record NotificationEmailRequested(String eventCode, String titleKey, String bodyKey, String argsJson, String linkPath, List<Long> operatorIds) {

    public NotificationEmailRequested {
        operatorIds = List.copyOf(operatorIds);
    }
}
