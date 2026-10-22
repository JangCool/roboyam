package com.community.menu.dto;

import java.util.List;

/**
 * 관리자 메뉴 노드 하나 — 저장된 구성·레지스트리 기본 구성·검사한 요청이 모두 이 모양이다.
 *
 * @param id         노드 ID. 저장 전(기본 구성·새 노드)은 null
 * @param kind       {@code SECTION} · {@code FOLDER} · {@code SCREEN}
 * @param labelKey   기본 이름의 i18n 키(레지스트리의 묶음·부모 항목). 그 밖에는 null
 * @param targetType {@code SCREEN} 만 — {@code ADMIN_SCREEN} · {@code URL}
 * @param href       {@code URL} 만
 * @param permission {@code URL} 만 — 고른 권한 코드(없으면 null)
 * @param children   {@code SECTION}·{@code FOLDER} 의 하위(화면 항목은 빈 목록)
 */
public record AdminMenuNode(Long id, String kind, String labelKey, String label, String labelEn, String icon, boolean visible, String targetType,
                            String targetKey, String href, boolean newTab, String permission, List<AdminMenuNode> children) {

    public static final String SECTION = "SECTION";
    public static final String FOLDER = "FOLDER";
    public static final String SCREEN = "SCREEN";
    public static final String ADMIN_SCREEN = "ADMIN_SCREEN";
    public static final String URL = "URL";

    public AdminMenuNode {
        children = children == null ? List.of() : List.copyOf(children);
    }

    public boolean isScreen() {
        return SCREEN.equals(kind);
    }

    public AdminMenuNode withIdAndChildren(Long newId, List<AdminMenuNode> newChildren) {
        return new AdminMenuNode(newId, kind, labelKey, label, labelEn, icon, visible, targetType, targetKey, href, newTab, permission, newChildren);
    }

    public static int count(List<AdminMenuNode> nodes) {
        int total = 0;
        for (AdminMenuNode node : nodes) {
            total += 1 + count(node.children());
        }
        return total;
    }
}
