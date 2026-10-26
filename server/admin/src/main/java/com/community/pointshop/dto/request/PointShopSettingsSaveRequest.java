package com.community.pointshop.dto.request;

/** {@code PUT /api/admin/point-shop/settings} — 셀프 환불 기한 일수(1~30, 서비스가 검사 — {@code fields.refundDays}) */
public record PointShopSettingsSaveRequest(Integer refundDays) {
}
