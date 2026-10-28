package com.community.home.dto.response;

import com.community.home.dto.HomeGridLayout;
import java.util.List;

/**
 * {@code GET /api/home/gadgets} — user-web {@code HomeGadgetsResponse}. 배치는 지금 사이트 테마의 것(저장한 것, 없으면 테마 기본 배치)이다.
 *
 * @param theme 이 배치가 어느 테마의 것인가
 */
public record HomeGadgetsResponse(String theme, HomeGridLayout layout, List<HomeGadgetView> gadgets) {
}
