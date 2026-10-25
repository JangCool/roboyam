import { mockGroupCategories, mockGroupCategoryCreate, mockGroupCategoryDelete, mockGroupCategoryOrder, mockGroupCategorySave } from '~/composables/group/api/mock/group-mock';
import type { GroupCategoriesResponse, GroupCategoryCreateRequest, GroupCategoryItem, GroupCategoryOrderRequest, GroupCategorySaveRequest } from '~/composables/group/api/useGroupCategoryApi.types';

/**
 * 소모임 분류 API — 서버 주소 문자열(`/api/admin/group-categories…`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 계약: community `docs/api-groups.md` 6-3 → 로보얌 `docs/features/group-contract.md` 「관리자 화면」. **서버에 아직 없다(목업)**.
 *
 * 권한(로보얌 코드): 조회 `group.read`, 만들기·저장·순서·삭제 `group.settings`(community 는 최고 관리자 전용 `403 ADMIN_ONLY` — 로보얌은 권한 코드로 위임한다). 없으면 `403 PERMISSION_DENIED`.
 *   - `GET    /api/admin/group-categories` → `GroupCategoriesResponse`(분류 sortOrder 순 + 할당할 수 있는 켜진 소모임 게시판)
 *   - `POST   /api/admin/group-categories` `{ name }` → 201 `GroupCategoryItem`(맨 끝, 게시판 0개). `400 fields.name` · `409 GROUP_CATEGORY_NAME_TAKEN`
 *   - `PUT    /api/admin/group-categories/{id}` `{ name, boardIds, updatedAt? }` → 200 GET 본문 전체(게시판 전체 교체 — 다른 분류의 게시판은 옮겨진다).
 *             `400 fields.name|boardIds` · `404 GROUP_CATEGORY_NOT_FOUND` · `409 GROUP_CATEGORY_NAME_TAKEN` · `409 GROUP_CATEGORY_STALE`(다른 관리자가 먼저 고쳤다)
 *   - `PUT    /api/admin/group-categories/order` `{ ids }`(모든 분류) → 200 GET 본문 전체. `400 fields.ids`
 *   - `DELETE /api/admin/group-categories/{id}` → 204. 소모임이 있으면 `409 GROUP_CATEGORY_NOT_EMPTY`(최상위 `groupCount` → `ApiError.extras.groupCount`)
 * 운영 로그: `GROUP_CATEGORY_CREATE`·`GROUP_CATEGORY_UPDATE`·`GROUP_CATEGORY_ORDER`·`GROUP_CATEGORY_DELETE`(서버 단계).
 *
 * ⚠ MOCK: 모든 메서드가 `./mock/group-mock.ts` 를 부른다. 서버가 붙으면 `// MOCK:` 줄과 목업 import 를 지우고 `// REAL:` 줄을 살린다.
 */
const PATH = {
    categories: '/api/admin/group-categories',
    order: '/api/admin/group-categories/order',
    category: (id: string) => `/api/admin/group-categories/${encodeURIComponent(id)}`,
};

export function useGroupCategoryApi() {
    // REAL: const auth = useAdminAuthApi();
    // REAL: const client = useApiClient({ refreshCsrf: () => auth.me() });
    void PATH; // MOCK: 모든 메서드가 아직 목업이라 주소를 쓰는 곳이 없다 — 서버가 붙으면 이 줄을 지운다

    return {
        /** 분류 목록 + 할당할 수 있는 게시판 */
        list: (signal?: AbortSignal): Promise<GroupCategoriesResponse> =>
            // MOCK:
            (void signal, mockGroupCategories()),
        // REAL: client.get<GroupCategoriesResponse>(PATH.categories, { signal }),

        /** 분류 만들기 — 이름만 */
        create: (body: GroupCategoryCreateRequest): Promise<GroupCategoryItem> =>
            // MOCK:
            mockGroupCategoryCreate(body),
        // REAL: client.post<GroupCategoryItem>(PATH.categories, body),

        /** 분류 저장 — 이름 + 게시판 전체 교체 */
        save: (id: string, body: GroupCategorySaveRequest): Promise<GroupCategoriesResponse> =>
            // MOCK:
            mockGroupCategorySave(id, body),
        // REAL: client.put<GroupCategoriesResponse>(PATH.category(id), body),

        /** 분류 순서 전체 바꾸기 */
        order: (body: GroupCategoryOrderRequest): Promise<GroupCategoriesResponse> =>
            // MOCK:
            mockGroupCategoryOrder(body),
        // REAL: client.put<GroupCategoriesResponse>(PATH.order, body),

        /** 분류 지우기(204) — 할당된 게시판은 「분류 없음」이 된다 */
        remove: (id: string): Promise<void> =>
            // MOCK:
            mockGroupCategoryDelete(id),
        // REAL: client.del<void>(PATH.category(id)),
    };
}
