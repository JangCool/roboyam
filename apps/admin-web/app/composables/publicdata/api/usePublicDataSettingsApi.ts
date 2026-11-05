import type {
    PublicDataEndpointKind,
    PublicDataEndpoints,
    PublicDataHolidayEndpointSaveRequest,
    PublicDataNtsEndpointSaveRequest,
    PublicDataSaveRequest,
    PublicDataSettingsView,
    PublicDataTest,
} from '~/composables/publicdata/api/usePublicDataSettingsApi.types';

/**
 * 공공데이터포털 설정 API — 서버 주소 문자열(`/api/admin/public-data-settings…`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 계약 `docs/features/business-contract.md` 「국세청 진위확인 · 공공데이터 설정」(서버 `PublicDataSettingsController` — `SecretEncryptor` 암호화, 응답 `no-store`):
 *  - `GET    /api/admin/public-data-settings` → `PublicDataSettingsView`  (`site.read` — 키 값은 오지 않는다, API 주소 설정 `endpoints` 포함)
 *  - `PUT    /api/admin/public-data-settings` `{ serviceKey }` → 같은 모양  (`site.secret`, 감사 — 칸 이름만)
 *      · 오류: `400 VALIDATION`(`fields.serviceKey` — 빈 값·공백 포함·500자 초과) · `503 CRYPTO_KEY_MISSING`
 *  - `DELETE /api/admin/public-data-settings/service-key` → 204  (`site.secret`) — 환경 변수가 있으면 그 값으로 돌아간다 · `404 PUBLIC_DATA_KEY_NOT_FOUND`
 *  - `POST   /api/admin/public-data-settings/test` → `PublicDataTest`  (`site.read`) — 저장된 키·국세청 주소로 국세청 상태조회 한 번(구현이 가짜여도 실제 국세청)
 *  - `PUT    /api/admin/public-data-settings/endpoints/nts` `{ url }` · `…/endpoints/holiday` `{ url, queryTemplate, dateField, nameField }` → `PublicDataEndpoints`  (`site.secret`)
 *      · 빈 칸은 기본값으로 돌아간다 · 오류: `400 VALIDATION`(`fields.url`·`fields.queryTemplate`·`fields.dateField`·`fields.nameField` — 서버 문구)
 *  - `DELETE /api/admin/public-data-settings/endpoints/{nts|holiday}` → `PublicDataEndpoints`  (`site.secret`) — 그 묶음의 모든 칸을 기본값으로
 *  - `POST   /api/admin/public-data-settings/holiday-test` → `PublicDataTest`  (`site.read`) — 지금 키·주소로 특일 정보 올해 1월 한 달
 */
const PATH = {
    root: '/api/admin/public-data-settings',
    serviceKey: '/api/admin/public-data-settings/service-key',
    test: '/api/admin/public-data-settings/test',
    holidayTest: '/api/admin/public-data-settings/holiday-test',
    endpoint: (kind: PublicDataEndpointKind) => `/api/admin/public-data-settings/endpoints/${kind}`,
};

export function usePublicDataSettingsApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        get: (): Promise<PublicDataSettingsView> => client.get<PublicDataSettingsView>(PATH.root),

        /** 서비스 키 저장 — 값은 이 호출의 인자로만 지나가고 어디에도 남기지 않는다 */
        save: (body: PublicDataSaveRequest): Promise<PublicDataSettingsView> => client.put<PublicDataSettingsView>(PATH.root, body),

        /** 화면에서 저장한 키 지우기 — 환경 변수가 있으면 그 값으로 돌아간다 */
        removeServiceKey: (): Promise<void> => client.del<void>(PATH.serviceKey),

        /** 국세청 연결 시험 */
        test: (): Promise<PublicDataTest> => client.post<PublicDataTest>(PATH.test),

        /** 특일 정보(공휴일 받기) 연결 시험 */
        holidayTest: (): Promise<PublicDataTest> => client.post<PublicDataTest>(PATH.holidayTest),

        /** 국세청 주소 저장 — 빈 칸은 기본값 */
        saveNtsEndpoint: (body: PublicDataNtsEndpointSaveRequest): Promise<PublicDataEndpoints> => client.put<PublicDataEndpoints>(PATH.endpoint('nts'), body),

        /** 공휴일 받기 주소·틀·칸 이름 저장 — 빈 칸은 기본값 */
        saveHolidayEndpoint: (body: PublicDataHolidayEndpointSaveRequest): Promise<PublicDataEndpoints> => client.put<PublicDataEndpoints>(PATH.endpoint('holiday'), body),

        /** 그 묶음의 모든 칸을 기본값으로 */
        resetEndpoint: (kind: PublicDataEndpointKind): Promise<PublicDataEndpoints> => client.del<PublicDataEndpoints>(PATH.endpoint(kind)),
    };
}
