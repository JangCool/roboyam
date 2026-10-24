package com.community.grade.dto.response;

/**
 * 등급을 가리키는 짧은 모양(user-web {@code GradeRef}).
 *
 * @param code  등급 키(gradeKey)
 * @param label 등급 이름(운영자가 정한 이름 — 등급 표에 영어 이름 칸이 없어 한 언어다)
 */
public record GradeRefView(String code, String label) {
}
