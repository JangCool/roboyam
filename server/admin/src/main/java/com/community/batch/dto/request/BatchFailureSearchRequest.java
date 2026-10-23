package com.community.batch.dto.request;

/**
 * 실행의 건별 실패 조회 조건(쿼리 문자열). 값 해석은 서비스가 하고 틀린 값은 칸 이름과 함께 400 으로 알린다.
 *
 * @param resolved {@code true} 해결한 것만 · {@code false} 미해결만 · 비우면 전체
 * @param page     0 부터(기본 0)
 * @param size     1~100(기본 20)
 */
public record BatchFailureSearchRequest(String resolved, String page, String size) {
}
