package com.community.grade.dto.request;

/**
 * 등급 저장 요청의 한 줄 — 목록 <b>순서가 곧 서열</b>이다. 이미 있는 등급은 {@code gradeKey} 를 보내고, 새 등급은 뺀다(서버가 발급한다).
 * 첫 줄은 기본 등급이라 {@code gradeKey} 가 그대로여야 하고 승급 조건은 보내도 무시한다. 검사는 칸 경로({@code grades[i].label} …)가 필요해 서비스가 한다.
 * 이름은 앞뒤 공백을 자른 뒤 저장한다(1~8자, 서로 달라야 한다). 아이콘이 없으면 null.
 */
public record GradeRequest(String gradeKey, String label, Integer minDays, Integer minActivity, String badgeColor, String badgeIcon) {
}
