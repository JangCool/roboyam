package com.community.menu.dto.response;

import java.util.List;

/**
 * 폴더 — admin-web {@code MenuFolderDto}.
 *
 * @param divider 상단 가로 메뉴 첫 줄 구분선 {@code NONE} · {@code BEFORE} · {@code AFTER}
 */
public record MenuFolderView(String id, String kind, String label, String nameEn, boolean visible, String audience, String minGrade, boolean collapsible,
                             boolean collapsed, boolean autoAdd, String divider, List<MenuTreeNodeView> children) implements MenuTreeNodeView {
}
