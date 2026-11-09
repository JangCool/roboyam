package com.community.installation.dto.response;

/**
 * 데모 데이터 진행 상태({@code GET /api/system/installation/demo}) — 이 밖의 정보는 담지 않는다.
 *
 * @param demo {@code NONE}(설치 전 · 구조만으로 설치) · {@code RUNNING}(넣는 중) · {@code APPLIED}(넣음) · {@code FAILED}(넣지 못해 건너뜀 — 구조만 있다)
 */
public record InstallDemoStatusResponse(String demo) {
}
