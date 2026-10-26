package com.community.pointshop.dto.response;

import java.util.List;

/**
 * {@code GET /api/avatars} — 328줄(카탈로그 차례). 로그인했을 때만 {@code balance}·{@code owned}·{@code selected}·{@code refundable} 이 채워진다(비로그인은 0·false).
 *
 * @param selectedId 지금 고른 아바타 ID — 고른 적 없거나 카탈로그에 없는 값이면 null({@code basic-default} 로 그린다)
 */
public record AvatarCatalogView(long balance, String selectedId, int refundDays, List<AvatarCategoryView> categories, List<AvatarItemView> items) {
}
