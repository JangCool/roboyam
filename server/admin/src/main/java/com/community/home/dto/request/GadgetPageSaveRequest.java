package com.community.home.dto.request;

/**
 * {@code POST /api/admin/gadget-pages} · {@code PUT /api/admin/gadget-pages/{id}} — admin-web {@code GadgetPageCreateRequest}(칸 전부). 검사는 서비스가 칸 경로로 모은다.
 *
 * @param key         영문 주소 {@code ^[a-z][a-z0-9-]{1,29}$}
 * @param nameEn      영문 이름(빈 글자 = 없음)
 * @param folderId    기준 폴더(공개 메뉴 main 의 폴더 ID 글자) — 없으면 null
 * @param writeButton 머리의 글쓰기 단추 — 빠지면 true
 */
public record GadgetPageSaveRequest(String key, String name, String nameEn, String description, String folderId, Boolean writeButton) {
}
