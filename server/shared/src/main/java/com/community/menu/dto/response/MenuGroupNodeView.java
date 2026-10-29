package com.community.menu.dto.response;

import java.util.List;
import java.util.Map;

/**
 * 공개 메뉴의 폴더 — user-web {@code MenuGroupNode}. 이름과 아래 노드들, 그리고 운영자가 연결을 골랐고 이 사람이 그 대상을 볼 수 있을 때만 이름을 누르면 가는 곳.
 *
 * @param id        노드 ID(문자열). 관리자 미리보기의 저장 전 노드는 null
 * @param divider   상단 가로 메뉴 첫 줄에서 이 그룹 탭 옆의 구분선 {@code NONE} · {@code BEFORE} · {@code AFTER}
 * @param path      이름을 누르면 가는 곳(항목의 {@code path} 와 같은 계산). 연결이 없거나 이 사람에게 보일 수 없으면 null
 * @param newTab    {@code path} 를 새 탭으로 여는가. {@code path} 가 null 이면 null
 * @param extra     항목의 {@code extra} 와 같다({@code URL} 의 https 주소면 {@code external: true}). 없으면 null
 * @param linkClick 상단 첫 줄 그룹 탭을 누르면 {@code NAVIGATE_OPEN}(이동하고 하위 메뉴도 열기) · {@code NAVIGATE}(이동만). {@code path} 가 null 이면 null
 */
public record MenuGroupNodeView(String id, String kind, String label, String nameEn, boolean collapsible, boolean collapsed, String divider,
                                List<MenuNodeView> children, String path, Boolean newTab, Map<String, Object> extra, String linkClick)
        implements MenuNodeView {

    public static MenuGroupNodeView of(String id, String label, String nameEn, boolean collapsible, boolean collapsed, String divider,
                                       List<MenuNodeView> children) {
        return new MenuGroupNodeView(id, "GROUP", label, nameEn, collapsible, collapsed, divider, children, null, null, null, null);
    }

    /** 이름을 누르면 가는 곳을 붙인다 */
    public MenuGroupNodeView withLink(String newPath, boolean newNewTab, Map<String, Object> newExtra, String newLinkClick) {
        return new MenuGroupNodeView(id, kind, label, nameEn, collapsible, collapsed, divider, children, newPath, newNewTab, newExtra, newLinkClick);
    }
}
