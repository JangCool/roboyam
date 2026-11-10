package com.community.osslicense.dto.request;

/** {@code PUT /api/admin/oss-licenses/entries/{id}/hidden} — 숨기기·보이기만 */
public record OssNoticeHiddenRequest(Boolean hidden) {
}
