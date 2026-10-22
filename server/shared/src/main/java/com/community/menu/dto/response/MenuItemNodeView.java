package com.community.menu.dto.response;

import java.util.Map;

/**
 * 공개 메뉴의 항목 — user-web {@code MenuItemNode}.
 *
 * @param id        노드 ID(문자열). 관리자 미리보기의 저장 전 노드는 null
 * @param type      연결 유형({@code BOARD}·{@code BUILTIN}·{@code PATH}·{@code URL})
 * @param key       {@code BUILTIN} 의 앱 화면 키, 그 밖에는 null
 * @param label     이름 — 비어 있던 항목은 대상 이름
 * @param nameEn    영문명(없으면 null)
 * @param board     {@code BOARD} 항목에만 있다
 * @param extra     유형이 더 실어 보낸 칸({@code URL} 의 {@code external}). 없으면 null
 */
public record MenuItemNodeView(String id, String kind, String type, String key, String label, String nameEn, String path, boolean matchPrefix,
                               boolean newTab, boolean mobileTab, MenuBoardView board, Map<String, Object> extra) implements MenuNodeView {
}
