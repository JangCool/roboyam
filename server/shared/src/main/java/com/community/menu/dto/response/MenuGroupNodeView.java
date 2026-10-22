package com.community.menu.dto.response;

import java.util.List;

/**
 * 공개 메뉴의 폴더 — user-web {@code MenuGroupNode}. 이름과 아래 노드들뿐이다(주소 없음).
 *
 * @param id 노드 ID(문자열). 관리자 미리보기의 저장 전 노드는 null
 */
public record MenuGroupNodeView(String id, String kind, String label, String nameEn, boolean collapsible, boolean collapsed, List<MenuNodeView> children)
        implements MenuNodeView {

    public static MenuGroupNodeView of(String id, String label, String nameEn, boolean collapsible, boolean collapsed, List<MenuNodeView> children) {
        return new MenuGroupNodeView(id, "GROUP", label, nameEn, collapsible, collapsed, children);
    }
}
