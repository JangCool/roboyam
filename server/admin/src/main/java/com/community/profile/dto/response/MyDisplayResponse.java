package com.community.profile.dto.response;

import com.community.site.dto.response.DisplayValuesView;

/**
 * 내 표시 형식 — 「쓴다」와 값, 그리고 쓰지 않을 때 따르는 사이트 표시 기본값.
 *
 * @param use           내 표시 형식을 쓰는가(꺼도 {@code values} 는 남는다)
 * @param values        내가 정한 값. 한 번도 저장한 적이 없으면 {@code inherited} 와 같은 값
 * @param inherited     사이트 표시 기본값({@code site_settings.display_*}) — 끄면 이 값으로 본다
 * @param effectiveFrom 지금 적용되는 값의 출처 — {@code USER}(내 값) · {@code SYSTEM}(사이트 기본값)
 */
public record MyDisplayResponse(boolean use, DisplayValuesView values, DisplayValuesView inherited, String effectiveFrom) {
}
