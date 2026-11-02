import type { PublicDataSaveRequest, PublicDataSettingsView, PublicDataTest } from '~/composables/publicdata/api/usePublicDataSettingsApi.types';

/**
 * 공공데이터포털 설정 API — 서버 주소 문자열(`/api/admin/public-data-settings…`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 서버 후보(포트원 설정과 같은 규칙 — `SecretEncryptor` 암호화, 응답 `no-store`):
 *  - `GET    /api/admin/public-data-settings` → `PublicDataSettingsView`  (`site.read` — 키 값은 오지 않는다)
 *  - `PUT    /api/admin/public-data-settings` `{ serviceKey }` → 같은 모양  (`site.secret`, 감사 — 칸 이름만)
 *      · 오류: `400 VALIDATION`(`fields.serviceKey` — 빈 값·공백 포함·500자 초과) · `503 CRYPTO_KEY_MISSING`
 *  - `DELETE /api/admin/public-data-settings/service-key` → 204  (`site.secret`) — 환경 변수가 있으면 그 값으로 돌아간다 · `404 PUBLIC_DATA_KEY_NOT_FOUND`
 *  - `POST   /api/admin/public-data-settings/test` → `PublicDataTest`  (`site.read`) — 저장된 키로 국세청 상태조회 한 번(구현이 가짜여도 실제 국세청)
 *
 * ⚠ 목업 — 서버가 생기기 전까지 이 파일 안의 메모리 상태로 답한다(새로고침하면 처음으로). 서버 구현이 들어오면 `mock*` 을 지우고 `client` 로 바꾼다.
 */
const PATH = {
    root: '/api/admin/public-data-settings',
    serviceKey: '/api/admin/public-data-settings/service-key',
    test: '/api/admin/public-data-settings/test',
};

/** ⚠ 목업 상태 — local 처럼 가짜 구현 · 키 없음에서 시작 */
const mockState: PublicDataSettingsView = {
    ntsProvider: 'FAKE',
    cryptoKeyConfigured: true,
    serviceKey: { source: 'NONE', updatedAt: null, updatedBy: null },
    lastTest: null,
};

const mockDelay = <T>(value: T): Promise<T> => new Promise((resolve) => setTimeout(() => resolve(structuredClone(value)), 300));

export function usePublicDataSettingsApi() {
    void PATH;

    return {
        get: (): Promise<PublicDataSettingsView> => mockDelay(mockState),

        /** 서비스 키 저장 — 값은 이 호출의 인자로만 지나가고 어디에도 남기지 않는다(목업은 값을 버리고 「설정됨」만 남긴다) */
        save: (_body: PublicDataSaveRequest): Promise<PublicDataSettingsView> => {
            mockState.serviceKey = { source: 'SCREEN', updatedAt: new Date().toISOString(), updatedBy: '최고관리자' };
            mockState.lastTest = null;
            return mockDelay(mockState);
        },

        /** 화면에서 저장한 키 지우기 — 환경 변수가 있으면 그 값으로 돌아간다(목업은 환경 변수 없음) */
        removeServiceKey: (): Promise<void> => {
            mockState.serviceKey = { source: 'NONE', updatedAt: new Date().toISOString(), updatedBy: '최고관리자' };
            mockState.lastTest = null;
            return mockDelay(undefined);
        },

        /** 연결 시험 — 목업: 키가 있으면 성공, 없으면 키 없음 */
        test: (): Promise<PublicDataTest> => {
            mockState.lastTest = { result: mockState.serviceKey.source === 'NONE' ? 'NO_KEY' : 'OK', testedAt: new Date().toISOString(), testedBy: '최고관리자' };
            return mockDelay(mockState.lastTest);
        },
    };
}
