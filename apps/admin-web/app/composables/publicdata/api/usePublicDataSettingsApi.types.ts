/**
 * 공공데이터포털 설정 API 의 요청·응답 모양(`/settings/public-data`) — 서비스 키 하나(국세청 사업자 상태조회·진위확인이 같이 쓴다, business-contract 「심사 규칙」).
 * 키 값은 **절대 내려오지 않는다** — 설정됐는지·어디서 왔는지·마지막 변경만. 서버는 포트원 설정과 같이 `SecretEncryptor` 로 암호화해 DB 에 둔다(서버 단계).
 */

/** 지금 쓰는 국세청 조회 구현 — 배포 설정 `roboyam.business.nts.provider`(ROBOYAM_NTS_PROVIDER)가 정하고 화면에서 바꿀 수 없다. `FAKE` 는 local·시험 전용(dev·prod 에서는 서버가 뜨지 않는다) */
export type NtsProvider = 'ODCLOUD' | 'FAKE';

/**
 * 서비스 키를 어디서 읽나 — 화면에서 저장한 값 · 배포 환경 변수(`ROBOYAM_NTS_SERVICE_KEY`) · 없음.
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

/** `GET /api/admin/public-data-settings` */
export interface PublicDataSettingsView {
    ntsProvider: NtsProvider;
    /** false 면 서버에 암호화 키(`ROBOYAM_CRYPTO_KEY`)가 없어 저장할 수 없다(지우기는 된다) */
    cryptoKeyConfigured: boolean;
    serviceKey: PublicDataKeyState;
    /** 마지막 연결 시험 — 없으면 null */
    lastTest: PublicDataTest | null;
}

/** `PUT /api/admin/public-data-settings` */
export interface PublicDataSaveRequest {
    serviceKey: string;
}
