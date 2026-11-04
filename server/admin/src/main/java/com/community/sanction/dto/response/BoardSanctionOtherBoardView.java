package com.community.sanction.dto.response;

import java.time.Instant;

/** 다른 게시판에서 받은 게시판 제재 요약 — 최근 7일 안 VOIDED 아닌 수 · VOIDED 아닌 전체 수 · 진행 중 쓰기 제한의 끝 */
public record BoardSanctionOtherBoardView(BoardSanctionBoardRefView board, long recentCount, long total, Instant activeUntil) {
}
