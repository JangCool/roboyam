package com.community.block.dto.request;

/** {@code PUT /api/users/{id}/memo} — 빈 메모(공백뿐 포함)는 지우기. 검사(20자·한 줄)는 서비스가 한다 */
public record MemoRequest(String memo) {
}
