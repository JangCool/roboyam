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
 * @param divider   상단 가로 메뉴 첫 줄에서 이 항목 옆의 구분선 {@code NONE} · {@code BEFORE} · {@code AFTER}
 * @param description 운영자가 적은 한 줄 설명(메뉴 설명 줄) — 없으면 null(화면 기본 설명)
 * @param shortcut  바로가기 — 다른 묶음에 진짜 자리가 있는 게시판 줄(menu-shortcut-contract). 나머지 칸은 일반 게시판 항목과 같다. 그 밖에는 false
 * @param textColor 글자 색 — null = 기본, 게시판 표시색 12색 키 또는 {@code #RRGGBB}(V186)
 * @param bold      굵게(V186)
 * @param badge     이름 옆 배지 글자(4자까지) — 없으면 null(V186)
 */
public record MenuItemNodeView(String id, String kind, String type, String key, String label, String nameEn, String path, boolean matchPrefix,
                               boolean newTab, boolean mobileTab, MenuBoardView board, Map<String, Object> extra, String divider, String description,
                               boolean shortcut, String textColor, boolean bold, String badge)
        implements MenuNodeView {
}
