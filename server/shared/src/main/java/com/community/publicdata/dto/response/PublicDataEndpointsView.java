package com.community.publicdata.dto.response;

import java.time.Instant;

/**
 * 공공데이터 API 주소(설정 › 시스템 › 공공데이터포털 — {@code GET /api/admin/public-data-settings} 의 {@code endpoints}, admin-web {@code usePublicDataSettingsApi.types.ts}).
 * 칸마다 지금 쓰는 값 · 기본값 · 직접 넣었는가. 비밀이 아니다(인증키는 요청 글자 틀에 {@code {serviceKey}} 자리로만 있다).
 *
 * @param nts     국세청 사업자 상태 조회 기본 주소(경로 {@code /api/nts-businessman/v1/…} 는 코드)
 * @param holiday 특일 정보 공휴일 조회(오퍼레이션 주소 · 요청 글자 틀 · 응답 날짜·이름 칸 이름)
 */
public record PublicDataEndpointsView(Nts nts, Holiday holiday) {

    /** 칸 하나 — {@code custom} 이면 직접 넣은 값, 아니면 기본값을 쓴다 */
    public record Field(String value, String defaultValue, boolean custom) {
    }

    /**
     * @param updatedAt 화면에서 마지막으로 저장·되돌린 순간(없으면 null)
     */
    public record Nts(Field url, Instant updatedAt, String updatedBy) {
    }

    /**
     * @param lastTest 마지막 연결 시험(올해 1월 한 달 조회) — 없으면 null(주소를 저장·되돌리면 지워진다)
     */
    public record Holiday(Field url, Field queryTemplate, Field dateField, Field nameField, Instant updatedAt, String updatedBy, PublicDataSettingsView.Test lastTest) {
    }
}
