/**
 * 주소 검색 API 의 주고받는 모양(`useAddressApi`). 계약: `docs/features/address-search-contract.md`.
 * 원본: pionworld2 `core.address`(`AddressSearchSettingsResponse`·`AddressSearchResultResponse`·`AddressSearchPageResponse`). 회원 화면에는 제공처·스크립트 주소만 내려 준다(적재 이력은 관리자 몫).
 */

/**
 * 우편번호 찾기 제공처 — 관리자 설정 › 주소 검색이 고른다.
 *  · `DAUM` 다음(카카오) 우편번호 서비스만(기본)
 *  · `INTERNAL` 자체 주소 DB 만(행정안전부 도로명주소 파일을 관리자가 올린 것)
 *  · `DAUM_FALLBACK_INTERNAL` 다음 먼저, 다음 스크립트를 못 불러오면 자체 주소 DB
 */
export type AddressProvider = 'DAUM' | 'INTERNAL' | 'DAUM_FALLBACK_INTERNAL';

/** `GET /api/address/config` — 회원 화면이 우편번호 찾기를 열 때 한 번 받는다 */
export interface AddressSearchConfig {
    provider: AddressProvider;
    /** 다음 우편번호 스크립트 주소 — 관리자가 비워 두면 서버가 기본값(공식 주소)을 내려 준다 */
    scriptUrl: string;
}

/** 자체 주소 DB 검색 결과 한 줄 */
export interface AddressSearchResult {
    /** 새 우편번호 5자리 */
    postcode: string;
    /** 도로명 주소(시·도부터 건물 번호까지) */
    roadAddress: string;
    /** 지번 주소 — 지번 파일이 없던 줄은 빈 글자 */
    jibunAddress: string;
    /** 건물 이름 — 없으면 빈 글자 */
    buildingName: string;
    /** 영문 도로명 주소 — 영문 파일(`rneng_*`)을 같이 올렸을 때만 */
    roadAddressEn: string | null;
}

/** `GET /api/address/search?keyword=&page=&size=` — 쪽 번호는 0부터 */
export interface AddressSearchPage {
    items: AddressSearchResult[];
    total: number;
    page: number;
    size: number;
}
