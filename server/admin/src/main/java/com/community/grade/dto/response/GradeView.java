package com.community.grade.dto.response;

/**
 * 등급 한 단계 — admin-web 의 {@code Grade} 모양이다.
 *
 * @param level       서열(1..N) — 목록 순서와 같다. 1 은 기본 등급
 * @param minDays     가입 후 일수 조건(기본 등급은 0)
 * @param minActivity 글+댓글 수 조건(기본 등급은 0)
 * @param badgeIcon   PrimeIcons 클래스. 아이콘 없음이면 null
 * @param memberCount 이 등급인 회원 수(탈퇴 회원 제외) — 등급을 지우면 이 회원들이 바로 아래 등급으로 이동한다
 */
public record GradeView(String gradeKey, int level, String label, int minDays, int minActivity, String badgeColor, String badgeIcon, long memberCount) {
}
