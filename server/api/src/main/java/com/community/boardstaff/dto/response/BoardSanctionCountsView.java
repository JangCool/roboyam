package com.community.boardstaff.dto.response;

/** 그 게시판에서 받은 제재 수(무효 제외, 해제 포함) — 경고 · 쓰기 제한 */
public record BoardSanctionCountsView(long warnings, long restrictions) {
}
