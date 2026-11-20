package com.community.jjal.dto.response;

import java.util.List;

/** {@code GET /api/jjals?ids=} — 모르는 ID 는 빠진다(화면은 토큰 글자 그대로) */
public record JjalLookupView(List<JjalView> items) {
}
