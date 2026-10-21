import type { AuditOptions, AuditPage, AuditQuery } from '~/composables/audit/api/useAuditApi.types';

/**
 * 감사 로그 API — 서버 주소 문자열(`/api/admin/audit-logs…`)이 있는 **유일한 곳**이다. 상태는 없다. **읽기만 있다** — 고치거나 지우는 API 가 없다(고칠 수 있으면 감사가 아니다).
 *
 * 계약(서버 `AuditLogController`, `docs/features/roles-contract.md` 「4단계 구현 결과」): 오류는 모두 `ApiError`(useApiClient)로 던져진다. 필요한 권한은 `audit.read` — 없으면 403 `PERMISSION_DENIED`.
 *   - `GET /api/admin/audit-logs?from&to&actor&action&targetType&target&result&page&size` → 200 `{ items: AuditLogView[], total, page(0부터), size }`
 *       발생 시각 최근순(같으면 ID 최근순), 정렬 파라미터 없음. `size` 1~100(기본 20). 틀린 조건은 400 VALIDATION `fields.<칸>`(`from|to|actor|action|targetType|target|result|page|size`)
 *       `actor` 는 운영자 ID 일치 또는 이름의 일부(이메일로는 못 찾는다), `target` 은 대상 ID 의 일부 또는 지금 이름의 일부
 *   - `GET /api/admin/audit-logs/options` → 200 `{ actions, targetTypes, results }` 각 `[{ value, label }]` — 거르기 고르개 목록. 라벨은 요청 언어(`Accept-Language`)라 언어를 바꾸면 다시 받아야 한다
 * 이 조회 자체는 감사 기록으로 남기지 않는다(서버 가정).
 */
const PATH = {
    root: '/api/admin/audit-logs',
    options: () => `${PATH.root}/options`,
};

export function useAuditApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 거른 목록 한 쪽 — 빈 값은 보내지 않는다(서버가 "거르지 않음"으로 받는다) */
        search: (query: AuditQuery): Promise<AuditPage> =>
            client.get<AuditPage>(PATH.root, {
                query: {
                    from: query.from || undefined,
                    to: query.to || undefined,
                    actor: query.actor || undefined,
                    action: query.action || undefined,
                    targetType: query.targetType || undefined,
                    target: query.target || undefined,
                    result: query.result || undefined,
                    page: query.page,
                    size: query.size,
                },
            }),

        /** 거르기 고르개가 쓸 목록 */
        loadOptions: (): Promise<AuditOptions> => client.get<AuditOptions>(PATH.options()),
    };
}
