/**
 * 공공데이터포털 설정 API 의 요청·응답 모양(`/settings/public-data`) — 서비스 키 하나(국세청 사업자 상태조회·진위확인이 같이 쓴다, business-contract 「심사 규칙」).
 * 키 값은 **절대 내려오지 않는다** — 설정됐는지·어디서 왔는지·마지막 변경만. 서버는 포트원 설정과 같이 `SecretEncryptor` 로 암호화해 DB 에 둔다(서버 단계).
 */

/** 지금 쓰는 국세청 조회 구현 — 배포 설정 `irobit.business.nts.provider`(IROBIT_NTS_PROVIDER)가 정하고 화면에서 바꿀 수 없다. `FAKE` 는 local·시험 전용(dev·prod 에서는 서버가 뜨지 않는다) */
export type NtsProvider = 'ODCLOUD' | 'FAKE';

/**
 * 서비스 키를 어디서 읽나 — 화면에서 저장한 값 · 배포 환경 변수(`IROBIT_NTS_SERVICE_KEY`) · 없음.
 * 화면 값이 있으면 화면 값이 먼저다. 화면 값을 지우면 환경 변수로 돌아간다.
 */
export type PublicDataKeySource = 'SCREEN' | 'ENV' | 'NONE';

export interface PublicDataKeyState {
    source: PublicDataKeySource;
    /** 화면에서 마지막으로 저장·지운 순간 — 한 번도 없으면 null */
    updatedAt: string | null;
    updatedBy: string | null;
}

/**
 * 연결 시험 결과 — 저장된 키로 국세청 상태조회를 실제로 한 번 부른다(구현이 가짜여도 실제 국세청으로).
 * 성공 · 키가 틀림(공공데이터포털 401·403) · 키 없음 · 응답 없음(제한 시간) · 그 밖 오류
 */
export type PublicDataTestResult = 'OK' | 'KEY_INVALID' | 'NO_KEY' | 'TIMEOUT' | 'ERROR';

export interface PublicDataTest {
    result: PublicDataTestResult;
    testedAt: string;
    testedBy: string | null;
}

/** API 주소 설정의 칸 하나 — 지금 쓰는 값 · 기본값 · 직접 넣었나(false 면 기본값을 쓰는 중) */
export interface PublicDataEndpointField {
    value: string;
    defaultValue: string;
    custom: boolean;
}

/** 국세청 사업자 상태 조회 주소 */
export interface PublicDataNtsEndpoint {
    url: PublicDataEndpointField;
    updatedAt: string | null;
    updatedBy: string | null;
}

/** 공휴일 받기(한국천문연구원 특일 정보) — 주소 · 요청 글자 틀 · 응답의 날짜·이름 칸 이름 */
export interface PublicDataHolidayEndpoint {
    url: PublicDataEndpointField;
    queryTemplate: PublicDataEndpointField;
    dateField: PublicDataEndpointField;
    nameField: PublicDataEndpointField;
    updatedAt: string | null;
    updatedBy: string | null;
    /** 마지막 특일 정보 연결 시험 — 없으면 null */
    lastTest: PublicDataTest | null;
}

/** API 주소 설정 — 저장·되돌리기 응답도 이 모양 전체다 */
export interface PublicDataEndpoints {
    nts: PublicDataNtsEndpoint;
    holiday: PublicDataHolidayEndpoint;
}

/** 주소 묶음 이름(`…/endpoints/{kind}`) */
export type PublicDataEndpointKind = keyof PublicDataEndpoints;

/** `PUT …/endpoints/nts` — 빈 칸은 기본값으로 돌아간다 */
export interface PublicDataNtsEndpointSaveRequest {
    url: string;
}

/** `PUT …/endpoints/holiday` — 빈 칸은 기본값으로 돌아간다 */
export interface PublicDataHolidayEndpointSaveRequest {
    url: string;
    queryTemplate: string;
    dateField: string;
    nameField: string;
}

/** `GET /api/admin/public-data-settings` */
export interface PublicDataSettingsView {
    ntsProvider: NtsProvider;
    /** false 면 서버에 암호화 키(`IROBIT_CRYPTO_KEY`)가 없어 저장할 수 없다(지우기는 된다) */
    cryptoKeyConfigured: boolean;
    serviceKey: PublicDataKeyState;
    /** 마지막 국세청 연결 시험 — 없으면 null */
    lastTest: PublicDataTest | null;
    /** API 주소 설정 */
    endpoints: PublicDataEndpoints;
}

/** `PUT /api/admin/public-data-settings` */
export interface PublicDataSaveRequest {
    serviceKey: string;
}
