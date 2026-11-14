/** 실어 온 응답 하나 — 상태 코드와 본문(서버 응답 래퍼 그대로) */
export interface BootResponse {
    status: number;
    body: unknown;
}

interface BootPayload {
    lang?: string;
    responses?: Record<string, { s: number; b: unknown }>;
}

/**
 * 첫 화면 데이터 — api 서버가 `index.html` 에 함께 실어 내린 GET API 응답들(`<script id="irb-boot" type="application/json">`, 서버 `BootPreloadRenderer`,
 * 사용자 결정 2026-11-14 「권하는 순서」 3번). `useApiClient` 가 GET 을 보내기 전에 {@link take} 로 꺼내 쓰면 그 왕복이 없어진다.
 *
 *  - **한 번만** 쓴다 — 꺼내면 지운다(두 번째부터는 서버에 묻는다). 화면이 처음 뜬 뒤 {@link FRESH_MS} 가 지나면 쓰지 않는다(오래된 값을 보이지 않게).
 *  - 주소는 경로 + 정렬한 질의로 맞춘다(`?a=1&b=2` 와 `?b=2&a=1`, 인코딩 차이 무관).
 *  - 서버가 응답을 만든 언어(`lang`)가 화면 언어와 다르면 쓰지 않는다.
 *  - 관리자 미리보기·설치 전·스크립트가 없는 빌드(개발 서버)에서는 비어 있다 — 평소처럼 서버에 묻는다.
 */
export default class UtilBootData {
    static readonly SCRIPT_ID = 'irb-boot';
    /** 페이지를 연 뒤 이 시간까지만 실어 온 응답을 쓴다 */
    static readonly FRESH_MS = 15_000;

    private static entries: Map<string, BootResponse> | null = null;
    private static lang: string | null = null;

    /**
     * 그 주소의 실어 온 응답을 꺼낸다(꺼내면 지운다)
     * @param {string} url - 요청 주소(질의 포함, `/api/...`).
     * @param {string} language - 지금 화면 언어(`ko`·`en`).
     * @returns {BootResponse | null} 응답. 없거나 언어가 다르거나 오래됐으면 null.
     */
    static take(url: string, language: string): BootResponse | null {
        if (typeof document === 'undefined' || performance.now() > UtilBootData.FRESH_MS) return null;
        const entries = UtilBootData.load();
        if (!entries.size || (UtilBootData.lang && UtilBootData.lang !== language)) return null;
        const key = UtilBootData.normalize(url);
        const hit = entries.get(key);
        if (!hit) return null;
        entries.delete(key);
        return hit;
    }

    /**
     * 그 주소의 실어 온 응답 본문을 지우지 않고 엿본다 — 미리 받기 판단용(`plugins/spa-warmup.client.ts`). 화면 데이터로는 {@link take} 를 쓴다
     * @param {string} url - 요청 주소.
     * @returns {unknown} 응답 본문(서버 래퍼). 없으면 null.
     */
    static peek(url: string): unknown {
        if (typeof document === 'undefined') return null;
        return UtilBootData.load().get(UtilBootData.normalize(url))?.body ?? null;
    }

    /**
     * 주소를 비교용으로 — 경로 + 이름순으로 정렬한 질의
     * @param {string} url - 주소.
     * @returns {string} 비교용 주소.
     */
    static normalize(url: string): string {
        const parsed = new URL(url, 'http://boot.local');
        parsed.searchParams.sort();
        const query = parsed.searchParams.toString();
        return query ? `${parsed.pathname}?${query}` : parsed.pathname;
    }

    private static load(): Map<string, BootResponse> {
        if (UtilBootData.entries) return UtilBootData.entries;
        const map = new Map<string, BootResponse>();
        UtilBootData.entries = map;
        const script = document.getElementById(UtilBootData.SCRIPT_ID);
        if (!script?.textContent) return map;
        try {
            const payload = JSON.parse(script.textContent) as BootPayload;
            UtilBootData.lang = payload.lang ?? null;
            for (const [url, entry] of Object.entries(payload.responses ?? {})) {
                map.set(UtilBootData.normalize(url), { status: entry.s, body: entry.b });
            }
        } catch {
            // 깨진 데이터면 쓰지 않는다 — 평소처럼 서버에 묻는다
        }
        script.remove();
        return map;
    }
}
