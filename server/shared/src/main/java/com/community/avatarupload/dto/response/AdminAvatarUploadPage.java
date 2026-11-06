package com.community.avatarupload.dto.response;

import java.util.List;

/** {@code GET /api/admin/avatar-uploads} — 관리자 목록 규약 {@code {items,total,page,size}}(쪽은 1부터) + 거르기와 상관없는 심사 대기 수 */
public record AdminAvatarUploadPage(List<AdminAvatarUploadView> items, long total, int page, int size, long pendingCount) {
}
