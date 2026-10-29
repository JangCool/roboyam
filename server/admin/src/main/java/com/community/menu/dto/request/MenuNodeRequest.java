package com.community.menu.dto.request;

import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * 트리 저장·미리보기 요청의 노드 — admin-web {@code MenuFolderDto}·{@code MenuItemDto} 를 한 모양으로 받는다({@code kind} 로 가른다). 검사는 {@code MenuTreeValidator}.
 *
 * @param id       지금 그룹에 있는 노드 ID(문자열). 새 노드는 null — 그룹에 없는 ID 도 새 노드로 본다
 * @param kind     {@code FOLDER} · {@code ITEM}
 * @param settings 유형별 설정(모르는 유형은 받은 그대로 보존하려고 JSON 그대로 받는다)
 * @param divider  {@code NONE}(빠지면 이것) · {@code BEFORE} · {@code AFTER}
 * @param description 항목의 한 줄 설명(80자까지, 비우면 없음). 폴더는 무시한다
 * @param targetType  항목의 연결 유형. 폴더는 선택 연결(null·빈 값 = 연결 없음 — 나머지 연결 칸과 {@code linkClick} 은 버린다)
 * @param linkClick   연결이 있는 폴더의 상단 탭 동작 {@code NAVIGATE_OPEN}(빠지면 이것) · {@code NAVIGATE}. 항목·연결 없는 폴더는 무시한다
 */
public record MenuNodeRequest(String id, String kind, String label, String nameEn, Boolean visible, String audience, String minGrade, Boolean collapsible,
                              Boolean collapsed, Boolean autoAdd, List<MenuNodeRequest> children, String targetType, String targetId, String targetKey,
                              JsonNode settings, Boolean mobileTab, String divider, String description, String linkClick) {
}
