package com.community.notice.dto.response;

import java.util.List;

/**
 * 공지를 올릴 내 운영자 회원 계정(operator-member-account-contract).
 *
 * @param state         LINKED(힘이 살아 있음) · NOT_LINKED(연결 없음) · PAUSED(이어졌지만 멈춤)
 * @param userId        이어진 회원 ID(연결 없으면 null)
 * @param nickname      그 회원의 지금 닉네임(연결 없으면 null)
 * @param pausedReasons 멈춘 까닭 — OPERATOR_UNAVAILABLE · MEMBER_SUSPENDED(살아 있거나 연결이 없으면 빈 목록)
 */
public record NoticeWriterView(String state, String userId, String nickname, List<String> pausedReasons) {
}
