package com.community.operator.dto.response;

/** 운영자 목록 한 줄의 이름 옆 딱지(admin-web {@code OperatorMemberAccountRef}) — 이어진 사이트 회원 계정. {@code active} 거짓이면 「멈춤」 */
public record OperatorMemberAccountRef(String userId, String nickname, boolean active) {
}
