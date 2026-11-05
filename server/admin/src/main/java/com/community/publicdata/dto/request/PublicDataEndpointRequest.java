package com.community.publicdata.dto.request;

/**
 * {@code PUT /api/admin/public-data-settings/endpoints/{api}} — 공공데이터 API 주소. 빈 칸은 기본값. 국세청({@code nts})은 {@code url} 만 쓴다.
 * 검사는 서비스가 한다(400 {@code fields.url}·{@code fields.queryTemplate}·{@code fields.dateField}·{@code fields.nameField}).
 */
public record PublicDataEndpointRequest(String url, String queryTemplate, String dateField, String nameField) {
}
