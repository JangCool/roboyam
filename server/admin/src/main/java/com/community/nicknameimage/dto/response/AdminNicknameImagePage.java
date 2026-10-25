package com.community.nicknameimage.dto.response;

import java.util.List;

/** {@code GET /api/admin/nickname-images} — 관리자 목록 규약 {@code {items,total,page,size}} + 거르기와 상관없는 검토 대기 수 */
public record AdminNicknameImagePage(List<AdminNicknameImageView> items, long total, int page, int size, long pendingCount) {
}
