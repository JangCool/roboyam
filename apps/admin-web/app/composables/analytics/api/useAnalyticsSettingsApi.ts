import type { AnalyticsSettingHistoryPage, AnalyticsSettingsRequest, AnalyticsSettingsView } from '~/composables/analytics/api/useAnalyticsSettingsApi.types';

/**
 * 통계 수집 설정 API — 서버 주소 문자열(`/api/admin/analytics/settings…`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 계약은 `docs/features/site-analytics-contract.md` 「API (확정)」·「2단계」.
 *   - `GET /api/admin/analytics/settings` → 200 `AnalyticsSettingsView`(`{ enabled, visits, rawEvents, rawRetentionDays, referrers, flows, environment, searchTerms, bots, notificationSends, boardActivity, updatedAt, updatedBy }`)  (`site.read`)
 *   - `PUT /api/admin/analytics/settings` 본문 `AnalyticsSettingsRequest`(위 칸 중 `updatedAt`·`updatedBy` 뺀 것) → 200 저장한 값  (`site.manage`, CSRF, 감사)
 *       · 400 VALIDATION(`fields.rawRetentionDays` — 30·90·180·365 밖) · 403 PERMISSION_DENIED. 같은 값 저장은 이력·감사를 남기지 않는다.
 *       · 2단계 칸(`environment`·`searchTerms`·`bots`)은 서버가 빠지면 저장값을 유지하지만, 이 화면은 늘 세 칸을 함께 보낸다.
 *   - `GET /api/admin/analytics/settings/history?page=&size=` → 200 설정 이력 한 쪽(`SettingHistoryPage` — 소셜 로그인·메일 서버 설정 이력과 같은 모양, 칸 이름 키 `settingHistory.field.*`)  (`site.read`)
 * 오류는 모두 `ApiError`(useApiClient)로 던져진다.
 */
const PATH = {
    settings: '/api/admin/analytics/settings',
    history: '/api/admin/analytics/settings/history',
};

export function useAnalyticsSettingsApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 지금 설정 */
        settings: (): Promise<AnalyticsSettingsView> => client.get<AnalyticsSettingsView>(PATH.settings),

        /** 저장 — 저장한 값을 돌려준다 */
        save: (body: AnalyticsSettingsRequest): Promise<AnalyticsSettingsView> => client.put<AnalyticsSettingsView>(PATH.settings, body),

        /** 변경 이력 한 쪽(`page` 0부터) */
        history: (params: { page: number; size: number }): Promise<AnalyticsSettingHistoryPage> =>
            client.get<AnalyticsSettingHistoryPage>(PATH.history, { query: params }),
    };
}
