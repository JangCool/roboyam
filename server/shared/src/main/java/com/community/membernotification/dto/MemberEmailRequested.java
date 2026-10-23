package com.community.membernotification.dto;

import com.community.membernotification.service.MemberNotificationMessages;

/**
 * 회원 알림 메일을 한 통 보내 달라는 요청 — 발행기가 업무 트랜잭션 안에서 알려 두고, 트랜잭션이 <b>커밋된 뒤에만</b> 메일 발송기({@code MemberNotificationEmailSender})가 받는다
 * (롤백되면 메일도 없다). 받는 회원의 이메일 주소·인증 여부·상태·언어는 발송 때 읽는다(주소를 이벤트에 싣지 않는다).
 *
 * @param userId      받는 회원
 * @param facts       문구 변수의 원천(일으킨 회원의 닉네임은 발송 때 채운다 — {@code actorUserId})
 * @param actorUserId 일으킨 회원(댓글·답글, 비회원이면 null — 이름은 {@code facts.actorName})
 * @param postId      링크가 가리키는 글(없으면 null)
 */
public record MemberEmailRequested(long userId, MemberNotificationMessages.Facts facts, Long actorUserId, Long postId) {

    @Override
    public String toString() {
        return "MemberEmailRequested[userId=" + userId + ", type=" + facts.type() + "]";
    }
}
