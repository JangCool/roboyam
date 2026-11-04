package com.community.sanction.dto.response;

import java.time.Instant;

/** 회원 상세 「게시판 제재」 구획의 게시판 하나 — 무효 아닌 경고 수 · 쓰기 제한 수 · 진행 중 쓰기 제한의 끝(없으면 null) */
public record MemberBoardSanctionBoardView(BoardSanctionBoardRefView board, long warningCount, long restrictCount, Instant activeUntil) {
}
