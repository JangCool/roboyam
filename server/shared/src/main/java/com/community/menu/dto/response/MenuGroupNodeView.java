package com.community.menu.dto.response;

import java.util.List;

/**
 * 공개 메뉴의 폴더 — user-web {@code MenuGroupNode}. 이름과 아래 노드들뿐이다(주소 없음).
 *
 * @param id      노드 ID(문자열). 관리자 미리보기의 저장 전 노드는 null
 * @param divider 상단 가로 메뉴 첫 줄에서 이 그룹 탭 옆의 구분선 {@code NONE} · {@code BEFORE} · {@code AFTER}
 */
public record MenuGroupNodeView(String id, String kind, String label, String nameEn, boolean collapsible, boolean collapsed, String divider,
                                List<MenuNodeView> children) implements MenuNodeView {

    public static MenuGroupNodeView of(String id, String label, String nameEn, boolean collapsible, boolean collapsed, String divider,
                                       List<MenuNodeView> children) {
        return new MenuGroupNodeView(id, "GROUP", label, nameEn, collapsible, collapsed, divider, children);
    }
}
