package com.community.pointshop.dto.request;

import java.time.Instant;

/**
 * {@code POST /api/admin/point-shop/items} · {@code PUT /api/admin/point-shop/items/{id}} — 칸 전부(admin-web {@code PointShopItemSaveRequest}). 고칠 때도 읽기 전용 칸
 * ({@code code}·{@code kind}·아바타의 {@code avatarId})을 그대로 싣는다 — 바뀌었으면 {@code 400 fields.<칸>}. 종류에 맞지 않는 칸은 null 로 보낸다.
 * 칸 검사는 순서·경로 키가 계약이라 서비스가 모은다(칸 차례 = 이 선언 차례). {@code kind}·{@code usageType} 은 글자로 받는다 — 모르는 값도 형식 오류가 아니라
 * 다른 칸 오류와 함께 {@code 400 VALIDATION fields.kind|usageType} 으로 나가게(정책 저장 요청의 숫자 칸과 같은 관례).
 */
public record PointShopItemSaveRequest(String code, String kind, String usageType, String name, String nameEn, String description,
                                       String descriptionEn, String color, String borderStyle, String avatarId, Integer pricePoint, Integer durationDays, Integer stock,
                                       Instant saleStartAt, Instant saleEndAt, Boolean active, Integer sortOrder) {
}
