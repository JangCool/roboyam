/**
 * 첫 로딩 스플래시(`app/spa-loading-template.html` — 가운데 사이트 아이콘·이름)를 **첫 화면이 다 그려진 뒤 한 번에** 걷는다(사용자 2026-11-14).
 *
 * 회색 틀을 진짜 화면이 붙는 순간 지우면 「틀 → 상단 바만 있는 화면 → 본문」으로 화면이 두 번 바뀌어 체감이 나빴다. 그래서 스플래시는
 * 레이아웃·화면 조각까지 그려진 뒤(`app:suspense:resolve`)에도 남겨 두고, 첫 화면의 서버 요청이 모두 끝나고 로딩 표시(`aria-busy="true"`)가 사라진 채
 * {@link QUIET_MS} 동안 조용하면 걷는다. 요청이 오래 걸려도 {@link MAX_MS} 가 지나면 걷고(스플래시가 화면을 붙잡지 않게), 앱이 붙은 뒤 {@link SAFETY_MS} 면 무엇이든 걷는다.
 *
 * 요청 수는 `useApiClient` 의 `send` 와 가젯 조각 받기(`util-gadget-registry`)가 {@link track} 으로 센다. 스플래시를 걷은 뒤에는 세지 않는다.
 */
export default class UtilBootSplash {
    /** Nuxt 가 붙이는 이름(`#__nuxt-loader`) 대신 쓰는 이름 — Nuxt 기본은 `app:suspense:resolve` 에 그 이름으로 지우므로(데이터 요청 전) 이름을 바꿔 그 지우기를 피한다 */
    static readonly ELEMENT_ID = 'irb-splash';
    static readonly NUXT_ID = '__nuxt-loader';
    /** 요청·로딩 표시가 없는 채로 이만큼 지나면 다 그려졌다고 본다 */
    static readonly QUIET_MS = 200;
    /** 화면이 그려진 뒤(`app:suspense:resolve`) 이만큼 지나면 요청이 남아 있어도 걷는다 */
    static readonly MAX_MS = 3500;
    /** 앱이 붙은 뒤(`app:mounted`) 이만큼 지나면 무엇이든 걷는다 — 화면 조각을 끝내 못 받는 경우의 안전망 */
    static readonly SAFETY_MS = 10000;
    /** 서서히 사라지는 시간(템플릿 CSS `transition` 과 같게) */
    static readonly FADE_MS = 200;
    private static readonly POLL_MS = 50;

    private static pending = 0;
    private static active = true;

    /**
     * 서버 요청 하나를 센다 — 끝나면(성공·실패 모두) 뺀다. 스플래시를 걷은 뒤에는 그대로 돌려준다
     * @param {Promise<T>} request - 요청.
     * @returns {Promise<T>} 같은 요청.
     */
    static track<T>(request: Promise<T>): Promise<T> {
        if (!UtilBootSplash.active) return request;
        UtilBootSplash.pending++;
        const done = () => {
            UtilBootSplash.pending--;
        };
        request.then(done, done);
        return request;
    }

    /**
     * Nuxt 의 스플래시 요소를 넘겨받는다(이름을 바꿔 Nuxt 기본 지우기를 피한다)
     * @returns {HTMLElement | null} 스플래시 요소(없으면 null — 빌드에 템플릿이 없거나 이미 걷혔다).
     */
    static claim(): HTMLElement | null {
        const el = document.getElementById(UtilBootSplash.NUXT_ID);
        if (!el) {
            UtilBootSplash.active = false;
            return null;
        }
        el.id = UtilBootSplash.ELEMENT_ID;
        return el;
    }

    /**
     * 첫 화면이 조용해지면(또는 {@link MAX_MS} 뒤) 서서히 걷고 지운다
     * @param {HTMLElement} el - {@link claim} 이 돌려준 요소.
     */
    static hideWhenSettled(el: HTMLElement): void {
        const started = performance.now();
        let quietSince = started;
        const tick = () => {
            const now = performance.now();
            const busy = UtilBootSplash.pending > 0 || document.querySelector('#__nuxt [aria-busy="true"]') !== null;
            if (busy) quietSince = now;
            if (now - quietSince >= UtilBootSplash.QUIET_MS || now - started >= UtilBootSplash.MAX_MS) {
                UtilBootSplash.hide(el);
                return;
            }
            if (UtilBootSplash.active) window.setTimeout(tick, UtilBootSplash.POLL_MS);
        };
        tick();
    }

    /**
     * 서서히 걷고 지운다(이미 걷었으면 아무 일도 없다)
     * @param {HTMLElement} el - {@link claim} 이 돌려준 요소.
     */
    static hide(el: HTMLElement): void {
        if (!el.isConnected || el.dataset.leaving) return;
        el.dataset.leaving = '1';
        UtilBootSplash.active = false;
        el.querySelector('.irb-splash')?.classList.add('is-leaving');
        window.setTimeout(() => el.remove(), UtilBootSplash.FADE_MS);
    }
}
