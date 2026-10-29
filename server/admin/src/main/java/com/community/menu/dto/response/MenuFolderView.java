package com.community.menu.dto.response;

import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * 폴더 — admin-web {@code MenuFolderDto}.
 *
 * @param divider    상단 가로 메뉴 첫 줄 구분선 {@code NONE} · {@code BEFORE} · {@code AFTER}
 * @param targetType 폴더 이름을 누르면 가는 곳의 연결 유형({@code BOARD}·{@code BUILTIN}·{@code PATH}·{@code URL}). 연결이 없으면 null(아래 네 칸도 null)
 * @param targetId   {@code BOARD} 의 게시판 ID(글자)
 * @param targetKey  {@code BUILTIN} 의 앱 화면 키
 * @param settings   {@code PATH} {@code {path,newTab}} · {@code URL} {@code {href,newTab}}
 * @param linkClick  {@code NAVIGATE_OPEN} · {@code NAVIGATE}. 연결이 없으면 null
 */
public record MenuFolderView(String id, String kind, String label, String nameEn, boolean visible, String audience, String minGrade, boolean collapsible,
                             boolean collapsed, boolean autoAdd, String divider, String targetType, String targetId, String targetKey, JsonNode settings,
                             String linkClick, List<MenuTreeNodeView> children) implements MenuTreeNodeView {
}
