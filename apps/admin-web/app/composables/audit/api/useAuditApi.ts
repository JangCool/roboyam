import { mockAuditOptions, mockAuditSearch } from '~/composables/audit/api/mock/audit-mock';
import type { AuditOptions, AuditPage, AuditQuery } from '~/composables/audit/api/useAuditApi.types';

/**
 * 감사 로그 API — 서버 주소 문자열(`/api/admin/audit-logs…`)이 있는 **유일한 곳**이다. 상태는 없다. **읽기만 있다** — 고치거나 지우는 API 가 없다(고칠 수 있으면 감사가 아니다).
 *
 * 후보 계약(확정 전): 오류는 모두 `ApiError`(useApiClient)로 던져진다. 칸 이름은 hamonica3 의 것과 같다(워크스페이스 칸 제외).
 *   - `GET /api/admin/audit-logs?from&to&actor&action&targetType&target&result&page&size` → 200 `{ items: AuditLogView[], total, page(0부터), size }` (발생 시각 역순, 한 쪽씩 — 몇십만 건이 될 수 있어 통째로 받지 않는다)
 *   - `GET /api/admin/audit-logs/options` → 200 `{ actions: [{ value, label }], targetTypes: [{ value, label }] }` (거르기 고르개 목록, 라벨은 서버가 `Accept-Language` 로 준다)
 *
 * ⚠ 미정(서버 정책): 이 화면을 연 것도 감사 로그에 남기는가(hamonica3 는 첫 쪽을 부를 때만 남겼다), 누가 볼 수 있는가(hamonica3 는 `audit.read` 를 최고 관리자에게만 줬다 — 지금 관리자 앱은 ADMIN·STAFF 모두 들어온다),
 *   보관 기간(보관 기간이 지난 기록을 치우는 배치), 기록하는 작업의 종류와 `changes` 에 담을 칸.
 *
 * ⚠ MOCK: 지금은 서버가 없어서 `./mock/audit-mock.ts` 를 부른다(스위치는 그 파일 머리말). 서버가 붙으면 `// MOCK:` 줄을 지우고 `// REAL:` 줄을 되살린다.
 */
const PATH = {
    root: '/api/admin/audit-logs',
    options: () => `${PATH.root}/options`,
};

export function useAuditApi() {
    // REAL: const client = useApiClient();
    void PATH;

    return {
        /** 거른 목록 한 쪽 — 빈 값은 보내지 않는다(서버가 "거르지 않음"으로 받는다) */
        search: (query: AuditQuery): Promise<AuditPage> =>
            // MOCK:
            mockAuditSearch(query),
        // REAL: client.get<AuditPage>(PATH.root, { query: { from: query.from || undefined, to: query.to || undefined, actor: query.actor || undefined, action: query.action || undefined, targetType: query.targetType || undefined, target: query.target || undefined, result: query.result || undefined, page: query.page, size: query.size } }),

        /** 거르기 고르개가 쓸 목록 */
        loadOptions: (): Promise<AuditOptions> =>
            // MOCK:
            mockAuditOptions(),
        // REAL: client.get<AuditOptions>(PATH.options()),
    };
}
