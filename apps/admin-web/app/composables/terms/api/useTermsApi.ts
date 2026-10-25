import type {
    TermsDraft,
    TermsDraftSaveRequest,
    TermsKind,
    TermsKindDetail,
    TermsOverview,
    TermsPublishRequest,
    TermsSettings,
    TermsVersionDetail,
} from '~/composables/terms/api/useTermsApi.types';

/**
 * 관리자 약관 API — 서버 주소 문자열(`/api/admin/terms…`)이 있는 **유일한 곳**이다. 상태는 없다.
 *
 * 계약(`docs/features/terms-contract.md` 「API」 관리자): 오류는 모두 `ApiError`(useApiClient)로 던져진다.
 *   - `GET /terms` → `{ showLinks, kinds }`  (`terms.read`)
 *   - `GET /terms/{kind}` → `{ kind, draft, versions }`(최신 버전 먼저)  (`terms.read`)
 *   - `GET /terms/{kind}/versions/{version}` → 게시본 + `body` · 없으면 404  (`terms.read`)
 *   - `PUT /terms/{kind}/draft` `{body}` → 초안 · `400 VALIDATION fields.body`  (`terms.manage`)
 *   - `DELETE /terms/{kind}/draft` → null(없어도 성공)  (`terms.manage`)
 *   - `POST /terms/{kind}/publish` `{effectiveDate, changeNote?}` → 새 게시본 · `fields.effectiveDate`·`changeNote`, 초안 없음 `fields.draft`  (`terms.manage`)
 *   - `PUT /terms/settings` `{showLinks}` → `{showLinks}`  (`terms.manage`)
 * 경로의 `{kind}` 는 소문자(`terms`·`privacy`), 본문의 `kind` 는 대문자다.
 */
const PATH = {
    root: '/api/admin/terms',
    kind: (kind: TermsKind) => `${PATH.root}/${kind.toLowerCase()}`,
    version: (kind: TermsKind, version: number) => `${PATH.kind(kind)}/versions/${encodeURIComponent(String(version))}`,
    draft: (kind: TermsKind) => `${PATH.kind(kind)}/draft`,
    publish: (kind: TermsKind) => `${PATH.kind(kind)}/publish`,
    settings: () => `${PATH.root}/settings`,
};

export function useTermsApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        overview: (): Promise<TermsOverview> => client.get<TermsOverview>(PATH.root),

        detail: (kind: TermsKind): Promise<TermsKindDetail> => client.get<TermsKindDetail>(PATH.kind(kind)),

        version: (kind: TermsKind, version: number): Promise<TermsVersionDetail> => client.get<TermsVersionDetail>(PATH.version(kind, version)),

        saveDraft: (kind: TermsKind, body: TermsDraftSaveRequest): Promise<TermsDraft> => client.put<TermsDraft>(PATH.draft(kind), body),

        deleteDraft: (kind: TermsKind): Promise<void> => client.del<void>(PATH.draft(kind)),

        publish: (kind: TermsKind, body: TermsPublishRequest): Promise<TermsVersionDetail> => client.post<TermsVersionDetail>(PATH.publish(kind), body),

        saveSettings: (body: TermsSettings): Promise<TermsSettings> => client.put<TermsSettings>(PATH.settings(), body),
    };
}
