import type { CacheDetailView, CacheEnabledRequest, CacheOverviewView, CacheRefreshResultView, CacheTtlRequest, CacheView } from '~/composables/cache/api/useCacheAdminApi.types';
// MOCK:
import CacheAdminMock from '~/composables/cache/api/mock/cache-admin-mock';

/**
 * 캐시 관리 API — 서버 주소 문자열(`/api/admin/caches…`)이 있는 **유일한 곳**이다. 상태는 없다.
 * ⚠ 화면 확정 전 목업(2026-11-08) — 서버(Spring Cache + Caffeine, 세대 번호 표)와 API 가 아직 없다. 메서드마다 `// MOCK:` 줄과 `// REAL:` 줄을 나란히 둔다.
 *
 * 계약 후보(관리자 서버가 DB 의 세대 번호 표와 각 서버가 보고한 상태를 읽어 합친다 — api 서버는 2~3대, 캐시는 서버마다 메모리에 따로 있다):
 *   - `GET  /api/admin/caches` → 200 `CacheOverviewView`(`cache.read`) — 서버 목록·확인 주기·캐시 목록(카탈로그 차례, 이름·대상 설명은 요청 언어로)
 *   - `GET  /api/admin/caches/{cacheKey}` → 200 `CacheDetailView`(`cache.read`) · 404 `CACHE_NOT_FOUND`
 *   - `POST /api/admin/caches/{cacheKey}/refresh` → 202 `CacheRefreshResultView`(`cache.manage`) — 세대 번호를 올리고 바로 돌아온다. 각 서버가 확인 주기마다 보고 비운다
 *   - `POST /api/admin/caches/refresh` → 202 `CacheRefreshResultView`(`cache.manage`) — 모든 캐시의 세대 번호를 올린다(전체 갱신)
 *   - `PUT  /api/admin/caches/{cacheKey}/ttl` `{ ttlSeconds }` → 200 `CacheView`(`cache.manage`) · 400 `VALIDATION` `fields.ttlSeconds`(캐시마다 허용 범위)
 *   - `PUT  /api/admin/caches/settings` `{ enabled }` → 200 `CacheOverviewView`(`cache.manage`) — 전체 캐시 켜기/끄기(DB 값, 각 서버가 확인 주기마다 따라간다). 항목별 값은 그대로 남는다
 *   - `PUT  /api/admin/caches/{cacheKey}/enabled` `{ enabled }` → 200 `CacheView`(`cache.manage`) · 409 `CACHE_GLOBAL_DISABLED`(전체가 꺼져 있음)
 *   - 꺼진 캐시의 `refresh` 는 409 `CACHE_DISABLED`(전체 갱신은 꺼진 캐시를 건너뛴다)
 * 공통 오류: 403 `PERMISSION_DENIED`.
 */
const PATH = {
    caches: '/api/admin/caches',
    cache: (cacheKey: string) => `${PATH.caches}/${encodeURIComponent(cacheKey)}`,
    refresh: (cacheKey: string) => `${PATH.cache(cacheKey)}/refresh`,
    refreshAll: '/api/admin/caches/refresh',
    ttl: (cacheKey: string) => `${PATH.cache(cacheKey)}/ttl`,
    settings: '/api/admin/caches/settings',
    enabled: (cacheKey: string) => `${PATH.cache(cacheKey)}/enabled`,
};

export function useCacheAdminApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });
    // MOCK: 갱신 기록·TTL 이력의 「누가」 — 서버는 세션에서 안다
    const { user } = useAdminAuth();
    const me = () => user.value?.nickname ?? '운영자';

    return {
        /** 위쪽 요약(서버·확인 주기) + 캐시 목록 */
        overview(): Promise<CacheOverviewView> {
            // MOCK:
            return CacheAdminMock.overview();
            // REAL: return client.get<CacheOverviewView>(PATH.caches);
        },

        /** 캐시 상세 — 서버별 상태·최근 갱신 기록·TTL 변경 이력 */
        detail(cacheKey: string): Promise<CacheDetailView> {
            // MOCK:
            return CacheAdminMock.detail(cacheKey);
            // REAL: return client.get<CacheDetailView>(PATH.cache(cacheKey));
        },

        /** 「지금 갱신」 — 세대 번호를 올린다(202). 각 서버가 몇 초 안에 비운다 */
        refresh(cacheKey: string): Promise<CacheRefreshResultView> {
            // MOCK:
            return CacheAdminMock.refresh(cacheKey, me());
            // REAL: return client.post<CacheRefreshResultView>(PATH.refresh(cacheKey));
        },

        /** 「전체 갱신」 — 모든 캐시의 세대 번호를 올린다(202) */
        refreshAll(): Promise<CacheRefreshResultView> {
            // MOCK:
            return CacheAdminMock.refreshAll(me());
            // REAL: return client.post<CacheRefreshResultView>(PATH.refreshAll);
        },

        /** TTL 을 바꾼다 — 판정(허용 범위)은 서버가 한다. 바뀐 줄을 돌려받는다 */
        updateTtl(cacheKey: string, ttlSeconds: number): Promise<CacheView> {
            // MOCK:
            return CacheAdminMock.updateTtl(cacheKey, ttlSeconds, me());
            // REAL: return client.put<CacheView>(PATH.ttl(cacheKey), { ttlSeconds } satisfies CacheTtlRequest);
        },

        /** 전체 캐시 켜기/끄기 — 바뀐 개요 전체를 돌려받는다 */
        setGlobalEnabled(enabled: boolean): Promise<CacheOverviewView> {
            // MOCK:
            return CacheAdminMock.setGlobalEnabled(enabled, me());
            // REAL: return client.put<CacheOverviewView>(PATH.settings, { enabled } satisfies CacheEnabledRequest);
        },

        /** 이 캐시 켜기/끄기 — 바뀐 줄을 돌려받는다 */
        setEnabled(cacheKey: string, enabled: boolean): Promise<CacheView> {
            // MOCK:
            return CacheAdminMock.setEnabled(cacheKey, enabled, me());
            // REAL: return client.put<CacheView>(PATH.enabled(cacheKey), { enabled } satisfies CacheEnabledRequest);
        },
    };
}
