package com.community.board.dto.request;

/** {@code PATCH /api/admin/boards/{id}/active} */
public record BoardActiveRequest(Boolean active) {
}
