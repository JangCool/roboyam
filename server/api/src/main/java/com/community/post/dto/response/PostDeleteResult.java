package com.community.post.dto.response;

import java.time.Instant;

/** 글 지우기 결과 — 보호 중이고 예약 삭제면 {@code scheduledAt}(지워질 시각), 바로 지웠으면 null */
public record PostDeleteResult(Instant scheduledAt) {
}
