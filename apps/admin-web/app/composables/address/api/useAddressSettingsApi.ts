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
 * 계약: `docs/features/address-search-contract.md`. 권한: 조회 `site.read`, 저장·파일 올리기 `site.manage`(없으면 403 `PERMISSION_DENIED`). 쓰기는 CSRF.
 *  - `GET /api/admin/address/settings` → `AddressSettingsView`
 *  - `PUT /api/admin/address/settings` `{ provider, scriptUrl, staleWarnDays }` → 같은 모양. 400 `VALIDATION` `fields.provider`·`fields.scriptUrl`·`fields.staleWarnDays`
 *  - `POST /api/admin/address/imports` 멀티파트(`files` 1~2개 — 한글 전체분 zip 필수 · 영문 zip 선택, `charset`) → 202 `AddressImportStarted`.
 *      400 `VALIDATION` `fields.files`·`fields.charset`, 한 번에 하나만(돌고 있으면 409 `ADDRESS_IMPORT_RUNNING`), 파일 300MB 이하(넘으면 413 `FILE_TOO_LARGE`).
 *      기존 주소 DB 를 통째로 바꾼다(되돌릴 수 없다).
 *  - `GET /api/admin/address/imports/current` → 돌고 있는 작업(`AddressImportProgress`) 또는 없음 — 화면을 다시 열었을 때 이어서 보인다
 *  - `GET /api/admin/address/imports/{jobId}` → `AddressImportProgress`(화면이 1.5초마다 묻는다). 없으면 404 `ADDRESS_IMPORT_JOB_NOT_FOUND`
 *  - `GET /api/admin/address/imports/history?page=&size=` → `AddressDbLoadPage`
 */
const PATH = {
    root: '/api/admin/address',
    settings: () => `${PATH.root}/settings`,
    imports: () => `${PATH.root}/imports`,
    currentImport: () => `${PATH.root}/imports/current`,
    importOf: (jobId: string) => `${PATH.root}/imports/${encodeURIComponent(jobId)}`,
    history: () => `${PATH.root}/imports/history`,
};

/** 파일 올리기를 기다리는 시간 — 한글 + 영문 zip(합쳐 수백 MB)을 느린 회선으로 보내도 끊지 않게 */
const UPLOAD_TIMEOUT_MS = 30 * 60_000;

export function useAddressSettingsApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 제공처·스크립트 주소·갱신 권고 일수와 지금 올라 있는 주소 DB */
        settings: (): Promise<AddressSettingsView> => client.get<AddressSettingsView>(PATH.settings()),

        saveSettings: (body: AddressSettingsSaveRequest): Promise<AddressSettingsView> => client.put<AddressSettingsView>(PATH.settings(), body),

        /** 주소 DB 파일 올리기 — 작업 번호만 바로 오고 적재는 서버에서 이어진다 */
        startImport: (files: File[], charset: AddressImportCharset): Promise<AddressImportStarted> => {
            const form = new FormData();
            for (const file of files) form.append('files', file);
            form.append('charset', charset);
            return client.post<AddressImportStarted>(PATH.imports(), form, { timeoutMs: UPLOAD_TIMEOUT_MS });
        },

        /** 돌고 있는 적재 작업 — 없으면 null(서버는 `data` 를 비워 보낸다) */
        currentImport: async (): Promise<AddressImportProgress | null> => (await client.get<AddressImportProgress | null | undefined>(PATH.currentImport())) ?? null,

        importProgress: (jobId: string): Promise<AddressImportProgress> => client.get<AddressImportProgress>(PATH.importOf(jobId)),

        /** 적재 이력 — 최신이 위 */
        history: (page: number, size: number): Promise<AddressDbLoadPage> => client.get<AddressDbLoadPage>(PATH.history(), { query: { page, size } }),
    };
}
