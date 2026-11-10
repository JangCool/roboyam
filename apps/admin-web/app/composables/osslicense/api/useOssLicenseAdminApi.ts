import type { OssCollectedFile, OssNoticeAdminView, OssNoticeEntry, OssNoticeEntrySaveRequest } from '~/composables/osslicense/api/useOssLicenseAdminApi.types';

/**
 * 오픈소스 라이선스 고지 관리 — 주소(`/api/admin/oss-licenses…` · `/oss-licenses.json`)가 있는 **유일한 곳**이다. 상태는 없다. 계약: `docs/features/oss-licenses-contract.md`.
 * 권한: 보기 `site.read`, 바꾸기 `site.manage`.
 *   - `GET /api/admin/oss-licenses` → `{ intro, introUpdatedAt, entries }`
 *   - `PUT /intro { intro }` → 같은 모양(400 `fields.intro`)
 *   - `POST /entries` → 201 한 줄 · `PUT /entries/{id}` → 200 한 줄 · 400 `fields.name|version|license|copyright|licenseText|sourceUrl|category` · 404 `OSS_NOTICE_ENTRY_NOT_FOUND`
 *   - `PUT /entries/{id}/hidden { hidden }` → 200 한 줄 · `DELETE /entries/{id}` → 204
 *   - 자동 수집 목록은 이 앱의 정적 파일 `/oss-licenses.json`(빌드 때 `scripts/collect-licenses.mjs` 가 공개 화면과 같은 내용으로 쓴다)
 */
const PATH = {
    root: '/api/admin/oss-licenses',
    intro: '/api/admin/oss-licenses/intro',
    entries: '/api/admin/oss-licenses/entries',
    entry: (id: string) => `/api/admin/oss-licenses/entries/${encodeURIComponent(id)}`,
    hidden: (id: string) => `/api/admin/oss-licenses/entries/${encodeURIComponent(id)}/hidden`,
    collected: '/oss-licenses.json',
};

export function useOssLicenseAdminApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        get: (): Promise<OssNoticeAdminView> => client.get<OssNoticeAdminView>(PATH.root),

        /** 안내 문구 — 비우면(null) 공개 화면 기본 문구 */
        saveIntro: (intro: string | null): Promise<OssNoticeAdminView> => client.put<OssNoticeAdminView>(PATH.intro, { intro }),

        create: (body: OssNoticeEntrySaveRequest): Promise<OssNoticeEntry> => client.post<OssNoticeEntry>(PATH.entries, body),

        update: (id: string, body: OssNoticeEntrySaveRequest): Promise<OssNoticeEntry> => client.put<OssNoticeEntry>(PATH.entry(id), body),

        setHidden: (id: string, hidden: boolean): Promise<OssNoticeEntry> => client.put<OssNoticeEntry>(PATH.hidden(id), { hidden }),

        remove: (id: string): Promise<void> => client.del<void>(PATH.entry(id)),

        /** 자동 수집 목록(정적 파일 — 공통 응답 틀이 없어 그대로 받는다) */
        collected: (): Promise<OssCollectedFile> => client.get<OssCollectedFile>(PATH.collected),
    };
}
