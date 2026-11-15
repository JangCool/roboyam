import type { TagCount, TagPopularQuery } from '~/composables/tag/api/useTagApi.types';

/**
 * 글 태그 API(관리자) — 서버 주소(`/api/admin/tags…`)가 있는 **유일한 곳**이다. 상태는 없다. 계약 `docs/features/post-tags-contract.md` 「API 후보」.
 *   - `GET /api/admin/tags/popular?boards=slug,slug&days=30&limit=30` → `[{ name, count }]`(달린 수 많은 차례, boards 없으면 전체 — 가려진·삭제된 글은 세지 않는다)
 * 가젯 ⚙ 「태그 탭」의 추천 목록이 쓴다(`usePopularTags`).
 */
const PATH = {
    popular: '/api/admin/tags/popular',
};

export function useTagApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 최근 많이 달린 태그 — 빈 `boards` 는 보내지 않는다(전체) */
        popular: (query: TagPopularQuery, signal?: AbortSignal): Promise<TagCount[]> =>
            client.get<TagCount[]>(PATH.popular, { query: { boards: query.boards.length ? query.boards.join(',') : undefined, days: query.days, limit: query.limit }, signal }),
    };
}
