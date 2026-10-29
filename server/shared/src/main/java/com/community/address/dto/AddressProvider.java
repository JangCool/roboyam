package com.community.address.dto;

/**
 * 회원 화면 「우편번호 찾기」의 제공처(관리자 설정 › 주소 검색). 다음(카카오) 우편번호는 무료 서비스라 보장 수준이 없어 재배포 없이 자체 주소 DB 로 바꿀 수 있게 한다.
 */
public enum AddressProvider {
    /** 다음 우편번호 화면만(기본) */
    DAUM,
    /** 자체 주소 DB 만 — 외부 스크립트를 막는 환경 등 */
    INTERNAL,
    /** 다음을 먼저 띄우고, 스크립트를 못 불러오면 자체 주소 DB 검색 */
    DAUM_FALLBACK_INTERNAL;

    /** 이름이 맞으면 그 값, 아니면 null */
    public static AddressProvider ofName(String name) {
        if (name == null) {
            return null;
        }
        for (AddressProvider value : values()) {
            if (value.name().equals(name)) {
                return value;
            }
        }
        return null;
    }
}
