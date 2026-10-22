package com.community.board.dto.request;

/** {@code POST /api/admin/boards/defaults/privacy/run} — axis 는 ip · guest · deletedContent · reportRecord · staffAction */
public record BoardPrivacyRunRequest(String axis) {
}
