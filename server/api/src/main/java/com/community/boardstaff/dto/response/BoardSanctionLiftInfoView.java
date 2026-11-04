package com.community.boardstaff.dto.response;

import java.time.Instant;

/** 해제했으면 해제 사유·시각(매니저·운영자 — 누가 풀었는지는 싣지 않는다) */
public record BoardSanctionLiftInfoView(String reason, Instant at) {
}
