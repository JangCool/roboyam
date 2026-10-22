package com.community.menu.dto.response;

/** 공개 메뉴 응답의 노드 — 폴더({@link MenuGroupNodeView}, {@code kind: 'GROUP'}) 또는 항목({@link MenuItemNodeView}, {@code kind: 'ITEM'}) */
public sealed interface MenuNodeView permits MenuGroupNodeView, MenuItemNodeView {

    String id();

    String kind();
}
