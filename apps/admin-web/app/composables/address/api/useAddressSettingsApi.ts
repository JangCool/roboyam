import {
    mockAddressCurrentImport,
    mockAddressHistory,
    mockAddressImportProgress,
    mockAddressSaveSettings,
    mockAddressSettings,
    mockAddressStartImport,
} from '~/composables/address/api/mock/address-mock';
import type {
    AddressDbLoadPage,
    AddressImportCharset,
    AddressImportProgress,
    AddressImportStarted,
    AddressSettingsSaveRequest,
    AddressSettingsView,
} from '~/composables/address/api/useAddressSettingsApi.types';

/**
 * 주소 검색 설정 API — 서버 주소 문자열(`/api/admin/address/…`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 계약 후보(화면 확정 뒤 `docs/features/address-search-contract.md`). 권한: 조회 `site.read`, 저장·파일 올리기 `site.manage`(없으면 403 `PERMISSION_DENIED`). 쓰기는 CSRF.
 *  - `GET /api/admin/address/settings` → `AddressSettingsView`
 *  - `PUT /api/admin/address/settings` `{ provider, scriptUrl, staleWarnDays }` → 같은 모양. 400 `VALIDATION` `fields.scriptUrl`·`fields.staleWarnDays`
 *  - `POST /api/admin/address/imports` 멀티파트(`files` 1~2개 — 한글 전체분 zip 필수 · 영문 zip 선택, `charset`) → `AddressImportStarted`.
 *      한 번에 하나만(돌고 있으면 409 `ADDRESS_IMPORT_RUNNING`), 파일 300MB 이하(413). 기존 주소 DB 를 통째로 바꾼다(되돌릴 수 없다).
 *  - `GET /api/admin/address/imports/current` → 돌고 있는 작업(`AddressImportProgress`) 또는 null — 화면을 다시 열었을 때 이어서 보인다
 *  - `GET /api/admin/address/imports/{jobId}` → `AddressImportProgress`(화면이 1.5초마다 묻는다). 없으면 404 `ADDRESS_IMPORT_JOB_NOT_FOUND`
 *  - `GET /api/admin/address/imports/history?page=&size=` → `AddressDbLoadPage`
 *
 * ⚠ MOCK: 서버에 API 가 없어 `./mock/address-mock.ts` 를 부른다. 서버가 붙으면 `// MOCK:` 줄과 목업 import 를 지우고 `// REAL:` 줄을 살린다.
 */
const PATH = {
    root: '/api/admin/address',
    settings: () => `${PATH.root}/settings`,
    imports: () => `${PATH.root}/imports`,
    currentImport: () => `${PATH.root}/imports/current`,
    importOf: (jobId: string) => `${PATH.root}/imports/${encodeURIComponent(jobId)}`,
    history: () => `${PATH.root}/imports/history`,
};

export function useAddressSettingsApi() {
    // REAL: const auth = useAdminAuthApi();
    // REAL: const client = useApiClient({ refreshCsrf: () => auth.me() });
    void PATH;

    return {
        /** 제공처·스크립트 주소·갱신 권고 일수와 지금 올라 있는 주소 DB */
        settings: (): Promise<AddressSettingsView> =>
            // MOCK:
            mockAddressSettings(),
        // REAL: client.get<AddressSettingsView>(PATH.settings()),

        saveSettings: (body: AddressSettingsSaveRequest): Promise<AddressSettingsView> =>
            // MOCK:
            mockAddressSaveSettings(body),
        // REAL: client.put<AddressSettingsView>(PATH.settings(), body),

        /** 주소 DB 파일 올리기 — 작업 번호만 바로 오고 적재는 서버에서 이어진다 */
        startImport: (files: File[], charset: AddressImportCharset): Promise<AddressImportStarted> => {
            const form = new FormData();
            for (const file of files) form.append('files', file);
            form.append('charset', charset);
            // MOCK:
            return mockAddressStartImport(files);
            // REAL: return client.post<AddressImportStarted>(PATH.imports(), form);
        },

        /** 돌고 있는 적재 작업 — 없으면 null */
        currentImport: (): Promise<AddressImportProgress | null> =>
            // MOCK:
            mockAddressCurrentImport(),
        // REAL: client.get<AddressImportProgress | null>(PATH.currentImport()),

        importProgress: (jobId: string): Promise<AddressImportProgress> =>
            // MOCK:
            mockAddressImportProgress(jobId),
        // REAL: client.get<AddressImportProgress>(PATH.importOf(jobId)),

        /** 적재 이력 — 최신이 위 */
        history: (page: number, size: number): Promise<AddressDbLoadPage> =>
            // MOCK:
            mockAddressHistory(page, size),
        // REAL: client.get<AddressDbLoadPage>(PATH.history(), { query: { page, size } }),
    };
}
