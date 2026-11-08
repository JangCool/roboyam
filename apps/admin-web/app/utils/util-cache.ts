import type { CacheKind, CacheServerRole, CacheUsageView, CacheView } from '~/composables/cache/api/useCacheAdminApi.types';

/** TTL 입력 단위 — 초 · 분 */
export type CacheTtlUnit = 'SECONDS' | 'MINUTES';

/** 켜짐 표시 */
export type CacheEnabledState = 'ON' | 'OFF' | 'OFF_GLOBAL';

/** 목록 거르기 — 빈 글자는 거르지 않음 */
export interface CacheFilter {
    kind: CacheKind | '';
    server: CacheServerRole | '';
    /** 실제 켜짐(`effectiveEnabled`) 기준 */
    enabled: '' | 'ON' | 'OFF';
    keyword: string;
}

/**
 * 캐시 관리 화면의 순수 판정 — 입력만 보고 답한다(서버 상태·`t` 를 모른다). 문구는 `UtilCacheMessages`.
 */
export default class UtilCache {
    /** 갱신 대기인 캐시가 있는 동안 목록(과 열린 서랍)을 다시 읽는 간격 */
    static readonly POLL_MS = 3000;
    /** 갱신 대기가 이보다 오래면 「확인하지 않은 서버가 있다」고 안내한다 */
    static readonly STUCK_MS = 30_000;

    static readonly KINDS: readonly CacheKind[] = ['PUBLIC_VIEW', 'GADGET', 'SETTINGS_COPY', 'INTERNAL_RULE'];
    /** 캐시하지 않는 영역의 키 앞머리 — 공개 사이트 `data-cache-area="nocache:<가젯 종류>"` (user-web `util-cache-area.ts`) */
    static readonly NOCACHE_PREFIX = 'nocache:';
    static readonly ROLES: readonly CacheServerRole[] = ['API', 'ADMIN'];

    /**
     * 켜짐 표시 — 켜짐 · 꺼짐(이 캐시를 껐다) · 꺼짐(전체)(전체 스위치가 꺼졌다 — 항목 값은 남아 있다).
     * @param {Pick<CacheView, 'effectiveEnabled'>} cache - 캐시.
     * @param {boolean} globalEnabled - 전체 캐시 스위치.
     */
    static enabledState(cache: Pick<CacheView, 'effectiveEnabled'>, globalEnabled: boolean): CacheEnabledState {
        if (cache.effectiveEnabled) return 'ON';
        return globalEnabled ? 'OFF' : 'OFF_GLOBAL';
    }

    static isPending(cache: Pick<CacheView, 'status'>): boolean {
        return cache.status === 'PENDING';
    }

    /** 갱신 대기가 오래 걸리는가(서버 한 대가 확인을 못 하고 있을 수 있다) */
    static isStuck(cache: Pick<CacheView, 'status' | 'pendingSince'>, now: number): boolean {
        if (cache.status !== 'PENDING' || !cache.pendingSince) return false;
        return now - Date.parse(cache.pendingSince) > UtilCache.STUCK_MS;
    }

    /** TTL 을 기본값에서 바꿨는가 */
    static isTtlChanged(cache: Pick<CacheView, 'ttlSeconds' | 'defaultTtlSeconds'>): boolean {
        return cache.ttlSeconds !== cache.defaultTtlSeconds;
    }

    /** 적중률(0~1) — 요청이 하나도 없으면 null */
    static hitRate(hitCount: number, missCount: number): number | null {
        const total = hitCount + missCount;
        return total > 0 ? hitCount / total : null;
    }

    /** 적중률 글자 — `97.3%`, 없으면 `—` */
    static percent(rate: number | null): string {
        if (rate == null) return '—';
        return `${(Math.floor(rate * 1000) / 10).toFixed(1)}%`;
    }

    /** 분류 딱지 색 */
    static kindSeverity(kind: CacheKind): 'info' | 'secondary' | 'contrast' | 'success' {
        if (kind === 'GADGET') return 'success';
        if (kind === 'PUBLIC_VIEW') return 'info';
        if (kind === 'SETTINGS_COPY') return 'secondary';
        return 'contrast';
    }

    /** 분류 딱지 아이콘 — 색이 겹치는 분류(가젯 · 상태 「정상」)도 모양으로 가른다 */
    static kindIcon(kind: CacheKind): string {
        if (kind === 'PUBLIC_VIEW') return 'pi pi-globe';
        if (kind === 'GADGET') return 'pi pi-th-large';
        if (kind === 'SETTINGS_COPY') return 'pi pi-copy';
        return 'pi pi-cog';
    }

    /** 공개 화면에서 보이는(화면 영역·동작) 쓰이는 곳 */
    static screenUsages(cache: Pick<CacheView, 'usages'>): CacheUsageView[] {
        return cache.usages.filter((u) => u.kind !== 'SERVER');
    }

    /** 공개 화면에 보이는 곳이 하나도 없는가(서버 내부만) */
    static serverOnly(cache: Pick<CacheView, 'usages'>): boolean {
        return UtilCache.screenUsages(cache).length === 0;
    }

    /** 미리보기로 열 수 있는 줄인가 — 화면 영역·동작이고 경로나 예시 경로 규칙이 있다 */
    static openable(usage: CacheUsageView): boolean {
        return usage.kind !== 'SERVER' && (usage.path !== null || usage.sample !== null);
    }

    /** 미리보기의 대표 줄 — 첫 「화면 영역·동작」 줄 중 열 수 있는 것(고정 경로 또는 예시 경로). 없으면 null */
    static representativeUsage(cache: Pick<CacheView, 'usages'>): CacheUsageView | null {
        return cache.usages.find(UtilCache.openable) ?? null;
    }

    /** 예시 경로 틀에 번호를 넣는다 — `/posts/{id}` + `42` → `/posts/42` */
    static fillTemplate(template: string, id: string): string {
        return template.replace('{id}', encodeURIComponent(id.trim()));
    }

    /** 경로가 예시 경로 틀에 맞는가(번호 자리는 아무 값) */
    static matchesTemplate(template: string, path: string): boolean {
        const [head = '', tail = ''] = template.split('{id}');
        if (!path.startsWith(head) || path.length <= head.length) return false;
        const rest = path.slice(head.length);
        const id = tail ? rest.slice(0, rest.length - tail.length) : rest;
        return (!tail || rest.endsWith(tail)) && /^[^/?#]+$/.test(id);
    }

    /** 이 줄이 이 경로의 쪽인가(고정 경로가 같거나 예시 틀에 맞는다) */
    static usageAt(usage: CacheUsageView, path: string): boolean {
        return usage.path === path || (usage.sample !== null && UtilCache.matchesTemplate(usage.sample.template, path));
    }

    /** 예시 번호로 쓸 수 있는 글자인가(경로 조각 하나 — 영문·숫자·하이픈·밑줄) */
    static validSampleId(value: string): boolean {
        return /^[A-Za-z0-9_-]{1,64}$/.test(value.trim());
    }

    /** 이 경로가 로그인한 회원에게만 보이는 쪽인가 — 그 경로를 가진 쓰이는 곳 중 하나라도 로그인 필요면 */
    static loginRequiredAt(caches: readonly Pick<CacheView, 'usages'>[], path: string): boolean {
        return caches.some((c) => c.usages.some((u) => u.loginRequired && UtilCache.usageAt(u, path)));
    }

    /** 공개 사이트 영역 키 → 캐시하지 않는 가젯 종류(`nocache:AD` → `AD`). 캐시 키면 null */
    static noCacheKind(areaKey: string): string | null {
        return areaKey.startsWith(UtilCache.NOCACHE_PREFIX) ? areaKey.slice(UtilCache.NOCACHE_PREFIX.length) : null;
    }

    /** 미리보기 경로 다듬기 — `/` 로 시작하는 같은 사이트 경로만(쿼리는 둔다 — `/write?board=hotdeal`, 조각은 뺀다). 아니면 null */
    static cleanPath(value: string): string | null {
        const path = value.trim().split('#')[0] ?? '';
        if (!path.startsWith('/') || path.startsWith('//') || /\s/.test(path)) return null;
        return path;
    }

    /** 미리보기 iframe 주소 — 경로의 쿼리 뒤에 미리보기 표시를 잇는다 */
    static previewSrc(origin: string, path: string): string {
        const clean = UtilCache.cleanPath(path) ?? '/';
        return `${origin}${clean}${clean.includes('?') ? '&' : '?'}adminPreview=1&cachePreview=1`;
    }

    /** 초 → 입력칸 값(60으로 나누어떨어지는 1분 이상은 분) */
    static toUnit(seconds: number): { amount: number; unit: CacheTtlUnit } {
        if (seconds >= 60 && seconds % 60 === 0) return { amount: seconds / 60, unit: 'MINUTES' };
        return { amount: seconds, unit: 'SECONDS' };
    }

    /** 입력칸 값 → 초 */
    static toSeconds(amount: number, unit: CacheTtlUnit): number {
        return unit === 'MINUTES' ? amount * 60 : amount;
    }

    /** 허용 범위 안인가 — 미리 알리는 용도다. 판정은 서버(400 `fields.ttlSeconds`) */
    static inRange(seconds: number, cache: Pick<CacheView, 'minTtlSeconds' | 'maxTtlSeconds'>): boolean {
        return Number.isInteger(seconds) && seconds >= cache.minTtlSeconds && seconds <= cache.maxTtlSeconds;
    }

    /** 거르기 — 분류 · 적용 서버 · 이름/키/대상 검색(대소문자 무시) */
    static matches(cache: CacheView, filter: CacheFilter): boolean {
        if (filter.kind && cache.kind !== filter.kind) return false;
        if (filter.server && !cache.servers.includes(filter.server)) return false;
        if (filter.enabled === 'ON' && !cache.effectiveEnabled) return false;
        if (filter.enabled === 'OFF' && cache.effectiveEnabled) return false;
        const keyword = filter.keyword.trim().toLowerCase();
        if (!keyword) return true;
        return [cache.displayName, cache.cacheKey, cache.target, cache.gadgetKind ?? ''].some((text) => text.toLowerCase().includes(keyword));
    }
}
