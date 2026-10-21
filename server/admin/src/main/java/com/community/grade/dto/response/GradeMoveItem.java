package com.community.grade.dto.response;

/**
 * 지우는 등급 하나의 회원 이동 — 회원은 <b>저장돼 있는 서열</b>에서 바로 아래 남아 있는 등급으로 이동한다. 이름은 저장 요청 기준이다
 * (지금 저장된 이름이 아니라 요청에서 바뀐 이름). 이동할 회원이 0 명이어도 지우는 등급마다 한 줄이다.
 */
public record GradeMoveItem(String fromGradeKey, String fromLabel, String toGradeKey, String toLabel, long count) {
}
