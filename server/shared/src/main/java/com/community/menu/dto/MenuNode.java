package com.community.menu.dto;

import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * 메뉴 트리의 노드 하나 — 저장된 트리(DB), 저장 요청(정리·검사한 것), 미리보기 입력이 모두 이 모양이다. 공개 응답·관리자 응답은 이것으로 만든다.
 *
 * @param id         노드 ID. 저장 전 새 노드는 null
 * @param kind       {@code FOLDER} · {@code ITEM}
 * @param label      이름(항목은 null = 대상 이름)
 * @param audience   {@code ALL} · {@code GUEST} · {@code MEMBER} · {@code GRADE_MIN} · {@code STAFF}
 * @param minGrade   {@code GRADE_MIN} 일 때의 등급 키. 그 밖에는 null
 * @param targetType 항목의 연결 유형(모르는 유형도 그대로). 폴더는 선택 연결의 유형(화면이 아는 유형만) 또는 null(연결 없음)
 * @param targetId   {@code BOARD} 의 게시판 ID(글자). 모르는 유형의 값도 그대로
 * @param targetKey  {@code BUILTIN} 의 앱 화면 키. 모르는 유형의 값도 그대로
 * @param settings   유형별 설정 — {@code PATH} {@code {path,newTab}} · {@code URL} {@code {href,newTab}} · 모르는 유형은 받은 그대로 · 그 밖에는 null
 * @param children   하위 노드(폴더만, 항목은 빈 목록)
 * @param divider    상단 가로 메뉴 첫 줄에서 이 노드 옆의 세로 구분선 {@code NONE} · {@code BEFORE} · {@code AFTER}(null 은 {@code NONE})
 * @param description 항목의 한 줄 설명(메뉴 설명 줄, {@value MenuLimits#DESCRIPTION_MAX}자까지). 폴더·없음은 null
 * @param linkClick  연결이 있는 폴더의 상단 첫 줄 탭을 누를 때 {@code NAVIGATE_OPEN}(이동하고 하위 메뉴도 열기) · {@code NAVIGATE}(이동만).
 *                   항목·연결 없는 폴더는 null
 */
public record MenuNode(Long id, String kind, String label, String nameEn, boolean visible, String audience, String minGrade, boolean collapsible,
                       boolean collapsed, boolean autoAdd, String targetType, String targetId, String targetKey, JsonNode settings, boolean mobileTab,
                       List<MenuNode> children, String divider, String description, String linkClick) {

    public static final String FOLDER = "FOLDER";
    public static final String ITEM = "ITEM";

    /** 구분선 없음 */
    public static final String DIVIDER_NONE = "NONE";

    /** 폴더 연결 탭을 누르면 이동하고 하위 메뉴도 연다(기본) */
    public static final String LINK_CLICK_NAVIGATE_OPEN = "NAVIGATE_OPEN";

    /** 폴더 연결 탭을 누르면 이동만 한다 */
    public static final String LINK_CLICK_NAVIGATE = "NAVIGATE";

    public MenuNode {
        children = children == null ? List.of() : List.copyOf(children);
        divider = divider == null ? DIVIDER_NONE : divider;
    }

    /** 구분선·설명·폴더 연결 탭 동작이 없는 노드(설치 프리셋·게시판 자동 추가·대상 정리) — 검사({@code MenuTreeValidator})가 요청의 칸을 다시 붙인다 */
    public MenuNode(Long id, String kind, String label, String nameEn, boolean visible, String audience, String minGrade, boolean collapsible,
                    boolean collapsed, boolean autoAdd, String targetType, String targetId, String targetKey, JsonNode settings, boolean mobileTab,
                    List<MenuNode> children) {
        this(id, kind, label, nameEn, visible, audience, minGrade, collapsible, collapsed, autoAdd, targetType, targetId, targetKey, settings, mobileTab,
                children, DIVIDER_NONE, null, null);
    }

    public MenuNode withExtras(String newDivider, String newDescription) {
        return new MenuNode(id, kind, label, nameEn, visible, audience, minGrade, collapsible, collapsed, autoAdd, targetType, targetId, targetKey, settings,
                mobileTab, children, newDivider, newDescription, linkClick);
    }

    public boolean isFolder() {
        return FOLDER.equals(kind);
    }

    public boolean isItem() {
        return ITEM.equals(kind);
    }

    /** 연결 대상이 있는 폴더인가 */
    public boolean hasFolderLink() {
        return isFolder() && targetType != null;
    }

    public MenuNode withChildren(List<MenuNode> newChildren) {
        return new MenuNode(id, kind, label, nameEn, visible, audience, minGrade, collapsible, collapsed, autoAdd, targetType, targetId, targetKey, settings,
                mobileTab, newChildren, divider, description, linkClick);
    }

    public MenuNode withAutoAdd(boolean value) {
        return new MenuNode(id, kind, label, nameEn, visible, audience, minGrade, collapsible, collapsed, value, targetType, targetId, targetKey, settings,
                mobileTab, children, divider, description, linkClick);
    }

    public MenuNode withId(Long newId) {
        return new MenuNode(newId, kind, label, nameEn, visible, audience, minGrade, collapsible, collapsed, autoAdd, targetType, targetId, targetKey, settings,
                mobileTab, children, divider, description, linkClick);
    }

    /** 트리의 노드 수(하위 포함) */
    public static int count(List<MenuNode> nodes) {
        int total = 0;
        for (MenuNode node : nodes) {
            total += 1 + count(node.children());
        }
        return total;
    }
}
