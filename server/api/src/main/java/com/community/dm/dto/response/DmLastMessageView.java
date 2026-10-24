package com.community.dm.dto.response;

import java.time.Instant;

/** 쪽지함 한 줄의 마지막 쪽지 — {@code preview} 는 앞 40자(줄바꿈은 한 칸) */
public record DmLastMessageView(String id, boolean mine, String preview, Instant createdAt) {
}
