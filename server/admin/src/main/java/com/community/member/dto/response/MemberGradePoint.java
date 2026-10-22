package com.community.member.dto.response;

import java.util.List;

/**
 * 등급·포인트 구획 — admin-web {@code MemberGradePoint}. 현재 등급은 상세의 {@code grade}, 잔액은 {@code point} 를 쓴다(중복해서 내리지 않는다).
 * 등급 이력·승급 상태·다음 등급 달성도와 최근 포인트 내역({@code pointEntries})을 채운다. 닉네임 변경권은 교환 기능이 없어 {@code nicknameTicketCount} 가 0 이다.
 *
 * @param autoPromotion {@code ACTIVE}(정상) · {@code PAUSED_ADMIN}(운영자가 올려 둔 회원이고 등급 옵션이 자동 승급 중지) · {@code PAUSED_SANCTION}(정지 회원이라 중지)
 * @param next          다음 등급과 조건 달성도 — 마지막 등급이면 null
 * @param gradeHistory  최근 것이 먼저, 최대 {@code MemberGradePointAssembler#HISTORY_LIMIT} 건
 */
public record MemberGradePoint(String autoPromotion, MemberGradeNext next, List<MemberGradeChange> gradeHistory, int nicknameTicketCount,
                               List<MemberPointEntry> pointEntries) {
}
