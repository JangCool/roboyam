import type { TermsDocument, TermsKind, TermsSummary, TermsVersion } from '~/composables/terms/api/useTermsApi.types';

/**
 * 공개 약관 API — 서버 주소 문자열(`/api/terms…`)이 있는 **유일한 곳**이다. 상태는 없다(요약 상태는 `useTermsSummary`).
 * ⚠ 서비스 이름(용어)의 `/api/site/terms`(`useSiteTerms`)와 다른 기능이다.
 *
 * 계약(`docs/features/terms-contract.md` 「API」 공개, 비로그인): 오류는 모두 `ApiError`(useApiClient)로 던져진다.
 *   - `GET /api/terms/summary` → `{ showLinks, kinds:[{kind, current, notice}] }`
 *   - `GET /api/terms/{kind}` → `{ kind, current|null, upcoming|null, history }` · 모르는 종류 404
 *   - `GET /api/terms/{kind}/versions/{version}` → 게시본 + `body` · 없으면 404
 * 서버가 짧은 사본과 `Cache-Control: no-cache` + `ETag`(브라우저가 매번 확인, 같으면 304)로 내려 주므로 화면이 따로 캐시하지 않는다. 경로의 `{kind}` 는 소문자다.
 */
const PATH = {
    root: '/api/terms',
    summary: () => `${PATH.root}/summary`,
    kind: (kind: TermsKind) => `${PATH.root}/${kind.toLowerCase()}`,
    version: (kind: TermsKind, version: number) => `${PATH.kind(kind)}/versions/${encodeURIComponent(String(version))}`,
};

export function useTermsApi() {
    const client = useApiClient();

    return {
        summary: (signal?: AbortSignal): Promise<TermsSummary> => client.get<TermsSummary>(PATH.summary(), { signal }),

        document: (kind: TermsKind, signal?: AbortSignal): Promise<TermsDocument> => client.get<TermsDocument>(PATH.kind(kind), { signal }),

        version: (kind: TermsKind, version: number, signal?: AbortSignal): Promise<TermsVersion> => client.get<TermsVersion>(PATH.version(kind, version), { signal }),
    };
}
