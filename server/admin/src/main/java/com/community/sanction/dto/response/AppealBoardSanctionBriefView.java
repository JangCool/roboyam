package com.community.sanction.dto.response;

import java.time.Instant;

/** 이의제기 줄의 게시판 제재 요약(admin-web {@code AppealBoardSanctionBrief}) */
public record AppealBoardSanctionBriefView(String id, long no, String kind, String status, BoardSanctionBoardRefView board, BoardSanctionIssuerView issuedBy,
                                           Instant startsAt, Instant endsAt) {
}
