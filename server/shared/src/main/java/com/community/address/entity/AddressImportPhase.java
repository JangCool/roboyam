package com.community.address.entity;

/**
 * 주소 DB 적재 단계 — 건수 세기 → 도로명 파일 넣기 → 지번·영문 붙이기 → 바꿔 끼우기 → 끝(또는 실패). 화면이 같은 이름으로 단계 표시를 그린다.
 */
public enum AddressImportPhase {
    COUNTING,
    PARSING,
    MERGING,
    SWAPPING,
    DONE,
    FAILED
}
