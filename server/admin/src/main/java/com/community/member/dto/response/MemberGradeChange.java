package com.community.member.dto.response;

import java.time.Instant;

/**
 * 등급 변경 이력 한 줄 — admin-web {@code MemberGradeChange}. 이메일 같은 개인정보는 없다.
 *
 * @param changeId      이력 번호(문자열)
 * @param fromGrade     이전 등급 키 — 처음 부여면 null. 지워진 등급의 키일 수 있다(화면이 중립 뱃지로 그린다)
 * @param kind          {@code AUTO}(자동 승급) · {@code ADMIN}(운영자 변경·등급 삭제로 인한 이동) · {@code RESUME}(자동 승급 재개)
 * @param reason        사유 — 운영자가 입력한 글, 없으면 시스템 사유 코드를 요청 언어로 푼 문구, 둘 다 없으면 null
 * @param actorNickname 처리한 운영자 이름 — 자동이면 null, 운영자가 지워졌으면 「삭제된 운영자」(요청 언어)
 */
public record MemberGradeChange(String changeId, String fromGrade, String toGrade, String kind, String reason, String actorNickname, Instant changedAt) {
}
