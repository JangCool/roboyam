package com.community.address.dto.response;

/** 적재를 받았다 — 진행은 {@code GET /api/admin/address/imports/{jobId}} 로 묻는다 */
public record AddressImportStartedResponse(String jobId) {
}
