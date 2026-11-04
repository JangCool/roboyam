package com.community.sanction.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * 이의제기 상세(admin-web {@code AppealDetail}) — {@code history} 는 그 회원의 사이트 제재(현황의 이력과 같다). 게시판 제재 이의제기({@code kind} BOARD)는 {@code sanction} 이 null 이고
 * {@code boardSanction}·{@code boardHistory}(그 회원의 게시판 제재 이력)가 있다.
 */
public record AppealDetailView(String id, long no, String status, String reason, Instant createdAt, String answer, Instant answeredAt, SanctionActorRefView answeredBy,
                               SanctionUserRefView user, String kind, SanctionLineView sanction, List<SanctionLineView> history, BoardSanctionLineView boardSanction,
                               List<BoardSanctionLineView> boardHistory) {
}
