import type {
    PopularExcludeRequest,
    PopularExcluded,
    PopularPeriod,
    PopularRankingView,
    PopularSettingValues,
    PopularSettingsView,
} from '~/composables/content/api/usePopularApi.types';

/**
 * 인기글 API — 서버 주소(`/api/admin/popular-settings`·`/api/admin/popular-posts…`)가 있는 **유일한 곳**이다. 상태는 없다.
 * 서버 `popular.controller.PopularSettingsController`·`PopularPostController`. 계약: `docs/features/popular-highlight-contract.md` 「서버 구현 결과」.
 *   - `GET  /api/admin/popular-settings` → 200 `PopularSettingsView` (`popular.read` 또는 `popular.settings`)
 *   - `PUT  /api/admin/popular-settings` `PopularSettingValues`(11칸 모두) → 200 저장 뒤의 같은 모양 (`popular.settings`)
 *       · 400 VALIDATION `fields.<칸>`(범위·소수 자리·빠짐, 모아서). 세 가중치가 모두 0 이면 `fields.likeWeight`. 바뀐 칸이 없으면 쓰지 않는다(`recomputeRequested false`)
 *   - `GET  /api/admin/popular-posts?period=REALTIME|YESTERDAY|WEEK` → 200 `PopularRankingView` (`popular.read`) · 400 `fields.period`
 *   - `POST /api/admin/popular-posts/exclusions` `{ postId, reason, memo? }` → 201 새로 뺐다 · 200 이미 빠져 있다(사유를 덮어쓰지 않는다), 본문은 뺀 글 한 줄 (`popular.manage`)
 *       · 400 `fields.postId|reason|memo`(메모 100자 초과·`OTHER` 인데 비었음) · 404 `POST_NOT_FOUND`(없는 글·지운 글)
 *   - `DELETE /api/admin/popular-posts/exclusions/{postId}` → 204(멱등) (`popular.manage`) · 400 `fields.postId`
 * 저장·빼기·다시 넣기는 순위 다시 계산을 요청할 뿐이라 응답 직후엔 옛 순위일 수 있다 — `recomputePending` 으로 알린다. 응답은 `Cache-Control: no-store`. 공통 403 `PERMISSION_DENIED`.
 */
const PATH = {
    settings: '/api/admin/popular-settings',
    ranking: '/api/admin/popular-posts',
    exclusions: '/api/admin/popular-posts/exclusions',
    exclusion: (postId: string) => `/api/admin/popular-posts/exclusions/${encodeURIComponent(postId)}`,
};

export function usePopularApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 인기글 설정 + 기본값·범위 + 지금 실시간 상위 10개 */
        settings: (): Promise<PopularSettingsView> => client.get<PopularSettingsView>(PATH.settings),

        /** 인기글 설정을 통째로 저장한다 — 저장 뒤의 같은 모양 */
        saveSettings: (body: PopularSettingValues): Promise<PopularSettingsView> => client.put<PopularSettingsView>(PATH.settings, body),

        /** 그 기간의 지금 순위 + 뺀 글(기간과 무관) */
        ranking: (period: PopularPeriod): Promise<PopularRankingView> => client.get<PopularRankingView>(PATH.ranking, { query: { period } }),

        /** 인기글에서 뺀다 — 이미 빠져 있으면 그 줄 그대로(200) */
        exclude: (body: PopularExcludeRequest): Promise<PopularExcluded> => client.post<PopularExcluded>(PATH.exclusions, body),

        /** 다시 넣는다(멱등) — 점수·조건이 받쳐 줄 때만 순위에 돌아온다 */
        restore: (postId: string): Promise<void> => client.del<void>(PATH.exclusion(postId)),
    };
}
