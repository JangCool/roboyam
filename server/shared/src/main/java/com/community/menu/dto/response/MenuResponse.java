package com.community.menu.dto.response;

import java.util.List;

/**
 * {@code GET /api/menu} 응답(user-web {@code MenuResponse}) — 관리자 미리보기도 같은 모양이다.
 *
 * @param nodes      보는 사람 기준으로 걸러 정렬까지 끝낸 트리
 * @param mobileTabs 모바일 상단 탭 항목 ID(깊이 우선 차례, {@code SIDE} 그룹만, 최대 10)
 * @param version    바뀌었는지 비교용 불투명 문자열(미리보기는 null)
 */
public record MenuResponse(List<MenuNodeView> nodes, List<String> mobileTabs, String version) {
}
