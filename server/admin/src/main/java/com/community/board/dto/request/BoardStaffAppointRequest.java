package com.community.board.dto.request;

/** {@code POST /api/admin/boards/{id}/staff} — role 은 MANAGER · STAFF */
public record BoardStaffAppointRequest(String memberId, String role) {
}
