package com.community.boardstaff.dto.response;

import java.time.Instant;

/** 이 게시판에서 진행 중인 쓰기 제한 — 있으면 쓰기 제한 단계는 고를 수 없다(경고만) */
public record BoardSanctionActiveView(String boardSanctionId, Instant until) {
}
