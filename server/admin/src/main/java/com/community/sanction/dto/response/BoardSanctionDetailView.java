package com.community.sanction.dto.response;

import java.util.List;

/** 게시판 제재 하나 + 그 회원의 게시판 제재 이력(모든 게시판, 최근 것이 위, 최대 30개) — admin-web {@code BoardSanctionDetail} */
public record BoardSanctionDetailView(BoardSanctionLineView sanction, List<BoardSanctionLineView> history) {
}
