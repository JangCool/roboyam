package com.community.membernotification.dto.response;

/**
 * 알림을 일으킨 사람. 읽을 때의 현재 값이다 — 닉네임을 바꾸면 따라가고 탈퇴했으면 {@code withdrawn=true} + 「탈퇴한 회원」. IP·이메일은 싣지 않는다.
 *
 * @param kind     {@code MEMBER} · {@code GUEST}
 * @param memberId 회원 ID — 비회원이면 null
 * @param name     닉네임(비회원은 그때 입력한 이름)
 */
public record NotificationActorView(String kind, String memberId, String name, boolean withdrawn) {
}
