import type { PublicDataSaveRequest, PublicDataSettingsView, PublicDataTest } from '~/composables/publicdata/api/usePublicDataSettingsApi.types';

/**
 * 공공데이터포털 설정 API — 서버 주소 문자열(`/api/admin/public-data-settings…`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 계약 `docs/features/business-contract.md` 「국세청 진위확인 · 공공데이터 설정」(서버 `PublicDataSettingsController` — `SecretEncryptor` 암호화, 응답 `no-store`):
 *  - `GET    /api/admin/public-data-settings` → `PublicDataSettingsView`  (`site.read` — 키 값은 오지 않는다)
 *  - `PUT    /api/admin/public-data-settings` `{ serviceKey }` → 같은 모양  (`site.secret`, 감사 — 칸 이름만)
 *      · 오류: `400 VALIDATION`(`fields.serviceKey` — 빈 값·공백 포함·500자 초과) · `503 CRYPTO_KEY_MISSING`
 *  - `DELETE /api/admin/public-data-settings/service-key` → 204  (`site.secret`) — 환경 변수가 있으면 그 값으로 돌아간다 · `404 PUBLIC_DATA_KEY_NOT_FOUND`
 *  - `POST   /api/admin/public-data-settings/test` → `PublicDataTest`  (`site.read`) — 저장된 키로 국세청 상태조회 한 번(구현이 가짜여도 실제 국세청)
 */
const PATH = {
    root: '/api/admin/public-data-settings',
    serviceKey: '/api/admin/public-data-settings/service-key',
    test: '/api/admin/public-data-settings/test',
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

        /** 연결 시험 */
        test: (): Promise<PublicDataTest> => client.post<PublicDataTest>(PATH.test),
    };
}
