package com.community.business.entity;

/**
 * 국세청 진위확인 결과(business-contract 「국세청 진위확인」) — 등록번호·상호·대표자명·개업일이 국세청 등록 정보와 맞는가.
 * <ul>
 *   <li>{@code MATCH} 일치 — 상태(계속·휴업·폐업)는 같은 응답의 상태 칸으로 채운다.</li>
 *   <li>{@code MISMATCH} 불일치 — 신청은 400 {@code BUSINESS_NTS_MISMATCH} 로 받지 않고, 승인은 409 {@code BUSINESS_NTS_MISMATCH}(잠금).</li>
 *   <li>{@code UNCHECKED} 진위 미확인 — 개업일이 없는 옛 신청(상태조회만 했다). 사유를 적어 승인한다.</li>
 *   <li>{@code UNAVAILABLE} 조회 불가 — 장애·제한 시간·키 없음. 신청은 받고, 사유를 적어 승인한다.</li>
 * </ul>
 */
public enum NtsValidity {
    MATCH, MISMATCH, UNCHECKED, UNAVAILABLE
}
