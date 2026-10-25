import { mockGroupApprove, mockGroupDeactivate, mockGroupDetail, mockGroupList, mockGroupReactivate, mockGroupReject, mockGroupRequests } from '~/composables/group/api/mock/group-mock';
import type { GroupDetail, GroupListPage, GroupListQuery, GroupOpenRequestList } from '~/composables/group/api/useGroupAdminApi.types';

/**
 * 소모임 개설 승인·소모임 관리 API — 서버 주소 문자열(`/api/admin/groups…`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 계약: community `docs/api-groups.md` 6-1·6-2·11-9 → 로보얌 `docs/features/group-contract.md` 「관리자 화면」. **서버에 아직 없다(목업)**.
 *
 * 권한(로보얌 코드 — 서버 `Permission` 은 서버 단계): 조회 `group.read`, 승인·반려·비활성화·다시 열기 `group.manage`. 없으면 `403 PERMISSION_DENIED`.
 *   - `GET  /api/admin/groups/requests` → `GroupOpenRequestList`(승인 대기, 오래된 신청이 위)
 *   - `POST /api/admin/groups/{id}/approve` → 204(신청자가 모임장·첫 멤버). `PENDING` 이 아니면 `404 GROUP_NOT_FOUND`
 *   - `POST /api/admin/groups/{id}/reject` `{ reason }`(1~200자 필수) → 204. `400 VALIDATION fields.reason` · `404 GROUP_NOT_FOUND`
 *   - `GET  /api/admin/groups?q=&status=&closed=&offline=&visibility=&page=&size=` → `GroupListPage`(최신순 고정, `counts` 는 검색어만 적용)
 *   - `GET  /api/admin/groups/{id}` → `GroupDetail`. 열림·비활성이 아니면 `404 GROUP_NOT_FOUND`
 *   - `POST /api/admin/groups/{id}/deactivate` `{ reason }`(1~200자 필수, 모임장에게 전달) → 204. `409 GROUP_ALREADY_INACTIVE` · `400 fields.reason`
 *   - `POST /api/admin/groups/{id}/reactivate` `{ memo }`(0~200자, 운영자만 봄) → 204. `409 GROUP_NOT_INACTIVE` · `400 fields.memo`
 * 응답 `Cache-Control: no-store`. 운영 로그(감사): `GROUP_APPROVE`·`GROUP_REJECT`·`GROUP_DEACTIVATE`·`GROUP_REACTIVATE`(서버 단계).
 *
 * ⚠ MOCK: 모든 메서드가 `./mock/group-mock.ts` 를 부른다. 서버가 붙으면 `// MOCK:` 줄과 목업 import 를 지우고 `// REAL:` 줄을 살린다.
 */
const PATH = {
    requests: '/api/admin/groups/requests',
    approve: (id: string) => `/api/admin/groups/${encodeURIComponent(id)}/approve`,
    reject: (id: string) => `/api/admin/groups/${encodeURIComponent(id)}/reject`,
    groups: '/api/admin/groups',
    group: (id: string) => `/api/admin/groups/${encodeURIComponent(id)}`,
    deactivate: (id: string) => `/api/admin/groups/${encodeURIComponent(id)}/deactivate`,
    reactivate: (id: string) => `/api/admin/groups/${encodeURIComponent(id)}/reactivate`,
};

export function useGroupAdminApi() {
    // REAL: const auth = useAdminAuthApi();
    // REAL: const client = useApiClient({ refreshCsrf: () => auth.me() });
    void PATH; // MOCK: 모든 메서드가 아직 목업이라 주소를 쓰는 곳이 없다 — 서버가 붙으면 이 줄을 지운다

    return {
        /** 승인 대기 개설 신청 — 오래된 신청이 위 */
        requests: (signal?: AbortSignal): Promise<GroupOpenRequestList> =>
            // MOCK:
            (void signal, mockGroupRequests()),
        // REAL: client.get<GroupOpenRequestList>(PATH.requests, { signal }),

        /** 승인 → 신청자가 모임장·첫 멤버가 되고 소모임이 열린다(204) */
        approve: (id: string): Promise<void> =>
            // MOCK:
            mockGroupApprove(id),
        // REAL: client.post<void>(PATH.approve(id)),

        /** 반려(204) — 사유는 필수 1~200자이고 신청자에게 그대로 전달된다 */
        reject: (id: string, reason: string): Promise<void> =>
            // MOCK:
            mockGroupReject(id, reason),
        // REAL: client.post<void>(PATH.reject(id), { reason }),

        /** 소모임 관리 목록 — 열림·비활성, 최신순(개설일) */
        list: (query: GroupListQuery, signal?: AbortSignal): Promise<GroupListPage> =>
            // MOCK:
            (void signal, mockGroupList(query)),
        // REAL: client.get<GroupListPage>(PATH.groups, { query: { ...query }, signal }),

        /** 소모임 상세 — 읽기 전용 정보 + 비활성 기록 */
        detail: (id: string, signal?: AbortSignal): Promise<GroupDetail> =>
            // MOCK:
            (void signal, mockGroupDetail(id)),
        // REAL: client.get<GroupDetail>(PATH.group(id), { signal }),

        /** 비활성화(204) — 사유는 필수 1~200자이고 모임장에게 그대로 전달된다 */
        deactivate: (id: string, reason: string): Promise<void> =>
            // MOCK:
            mockGroupDeactivate(id, reason),
        // REAL: client.post<void>(PATH.deactivate(id), { reason }),

        /** 다시 열기(204) — 메모는 선택 0~200자이고 운영자만 본다 */
        reactivate: (id: string, memo: string): Promise<void> =>
            // MOCK:
            mockGroupReactivate(id, memo),
        // REAL: client.post<void>(PATH.reactivate(id), { memo }),
    };
}
