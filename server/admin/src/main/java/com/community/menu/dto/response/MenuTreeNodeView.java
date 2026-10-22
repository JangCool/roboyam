package com.community.menu.dto.response;

/** 관리자 트리 응답의 노드 — 폴더({@link MenuFolderView}) 또는 항목({@link MenuItemView}). admin-web {@code MenuNodeDto} */
public sealed interface MenuTreeNodeView permits MenuFolderView, MenuItemView {

    String id();

    String kind();
}
