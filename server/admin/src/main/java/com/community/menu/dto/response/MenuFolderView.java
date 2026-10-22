package com.community.menu.dto.response;

import java.util.List;

/** 폴더 — admin-web {@code MenuFolderDto} */
public record MenuFolderView(String id, String kind, String label, String nameEn, boolean visible, String audience, String minGrade, boolean collapsible,
                             boolean collapsed, boolean autoAdd, List<MenuTreeNodeView> children) implements MenuTreeNodeView {
}
