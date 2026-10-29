/**
 * 주소 검색 설정 API 의 주고받는 모양(`useAddressSettingsApi`). 계약: `docs/features/address-search-contract.md`.
 * 원본: pionworld2 `core.address`(`AddressSearchSettingsResponse`·`AddressImportProgressResponse`) — 적재 이력은 이곳의 `address_db_loads`.
 */

/**
 * 회원 화면 「우편번호 찾기」의 제공처.
 *  · `DAUM` 다음(카카오) 우편번호 서비스만(기본)
 *  · `INTERNAL` 자체 주소 DB 만
 *  · `DAUM_FALLBACK_INTERNAL` 다음 먼저, 다음 스크립트를 못 불러오면 자체 주소 DB
 */
export type AddressProvider = 'DAUM' | 'INTERNAL' | 'DAUM_FALLBACK_INTERNAL';

/** 지금 올라 있는 주소 DB — 한 번도 올린 적이 없으면 `loadedAt` 이 null */
export interface AddressDbState {
    loadedAt: string | null;
    /** 올린 파일 이름(여럿이면 쉼표) */
    sourceLabel: string | null;
    /** 도로명 주소 줄 수 */
    entryCount: number | null;
    /** 영문 주소가 붙은 줄 수 — 영문 파일을 같이 올리지 않았으면 0 */
    englishCount: number | null;
    /** 마지막 적재가 `staleWarnDays` 보다 오래됐다(올린 적이 없어도 true) */
    stale: boolean;
}

/** `GET`·`PUT /api/admin/address/settings` */
export interface AddressSettingsView {
    provider: AddressProvider;
    /** 저장한 다음 스크립트 주소 — 비워 두면 null(서버 기본값을 쓴다) */
    scriptUrl: string | null;
    /** 서버 기본 스크립트 주소(공식 주소) — 입력칸의 자리표시자 */
    defaultScriptUrl: string;
    /** 마지막 적재 뒤 며칠이 지나면 갱신을 권할까 */
    staleWarnDays: number;
    /** 고칠 수 있는 범위 — 서버와 같은 값을 받아 화면이 미리 알린다 */
    staleWarnDaysMin: number;
    staleWarnDaysMax: number;
    db: AddressDbState;
    updatedAt: string | null;
    /** 마지막으로 바꾼 운영자 표시 이름 */
    updatedBy: string | null;
}

/** `PUT /api/admin/address/settings` 본문 — `scriptUrl` 이 null·빈 글자면 기본값으로 돌린다 */
export interface AddressSettingsSaveRequest {
    provider: AddressProvider;
    scriptUrl: string | null;
    staleWarnDays: number;
}

/**
 * 적재 단계 — 건수 세기 → 도로명 파일 넣기 → 지번·영문 붙이기 → 바꿔 끼우기 → 끝(또는 실패).
 * 새 데이터는 스테이징 표에 다 넣은 뒤 이름만 맞바꾼다 — 적재 중에도 회원 검색은 앞 데이터로 된다.
 */
export type AddressImportPhase = 'COUNTING' | 'PARSING' | 'MERGING' | 'SWAPPING' | 'DONE' | 'FAILED';

/** `GET /api/admin/address/imports/{jobId}` · `GET /api/admin/address/imports/current`(돌고 있는 작업, 없으면 null) */
export interface AddressImportProgress {
    jobId: string;
    phase: AddressImportPhase;
    /** -1 = 아직 세는 중(진행률을 낼 수 없다) */
    totalRows: number;
    processedRows: number;
    done: boolean;
    failed: boolean;
    /** 실패했으면 멈춘 단계(`COUNTING`~`SWAPPING`), 아니면 null */
    failedPhase: AddressImportPhase | null;
    /** 끝났으면 결과 한 줄, 실패면 서버 사유 */
    message: string | null;
    fileNames: string[];
    startedAt: string;
    startedBy: string | null;
}

/** `POST /api/admin/address/imports`(멀티파트 `files[]` + `charset`) — 받자마자 작업 번호만 준다 */
export interface AddressImportStarted {
    jobId: string;
}

/** 파일을 읽을 글자 집합 — 행정안전부 파일은 CP949(`MS949`) */
export type AddressImportCharset = 'MS949' | 'UTF-8';

/** 적재 이력 한 줄(끝난 적재 — `address_db_loads` 의 DONE 줄) */
export interface AddressDbLoadRow {
    /** 적재 ID(Snowflake — 문자열) */
    id: string;
    loadedAt: string;
    sourceLabel: string;
    entryCount: number;
    englishCount: number;
    loadedBy: string | null;
}

/** `GET /api/admin/address/imports/history?page=&size=` — 최신이 위, 쪽 번호는 0부터 */
export interface AddressDbLoadPage {
    items: AddressDbLoadRow[];
    total: number;
    page: number;
    size: number;
}
