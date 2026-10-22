package com.community.menu.dto.request;

import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * 트리 저장·미리보기 요청의 노드 — admin-web {@code MenuFolderDto}·{@code MenuItemDto} 를 한 모양으로 받는다({@code kind} 로 가른다). 검사는 {@code MenuTreeValidator}.
 *
 * @param id       지금 그룹에 있는 노드 ID(문자열). 새 노드는 null — 그룹에 없는 ID 도 새 노드로 본다
 * @param kind     {@code FOLDER} · {@code ITEM}
 * @param settings 유형별 설정(모르는 유형은 받은 그대로 보존하려고 JSON 그대로 받는다)
 */
public record MenuNodeRequest(String id, String kind, String label, String nameEn, Boolean visible, String audience, String minGrade, Boolean collapsible,
                              Boolean collapsed, Boolean autoAdd, List<MenuNodeRequest> children, String targetType, String targetId, String targetKey,
                              JsonNode settings, Boolean mobileTab) {
}
