/** 스플래시 템플릿(`app/spa-loading-template.html`)의 스크립트가 걸어 두는 단계 함수 */
interface SplashHandle {
    step(n: number): void;
}

declare global {
    interface Window {
        __irbSplash?: SplashHandle;
    }
}

/**
 * 첫 로딩 스플래시(`app/spa-loading-template.html`)를 단계별로 채우고, **첫 화면이 다 그려진 뒤 한 번에** 걷는다(사용자 2026-11-14 — hamonica2 `EntrySplash` 의
 * 진행 막대·단계 점·걷힐 때 효과를 참고하되, 단계는 고정 시간이 아니라 실제 진행으로 넘긴다).
 *
 * 단계(막대 4칸 · 점 4개 — 단계 안에서는 막대가 다음 칸 쪽으로 천천히 흘러 느린 회선에서도 멈춘 것처럼 보이지 않는다):
 *  1. 페이지 받음 — 템플릿 스크립트가 스스로 건다(JS 전)
 *  2. 화면 코드 받음 — 이 앱의 플러그인이 돌기 시작(`plugins/spa-splash.client.ts`)
 *  3. 화면 그림 — 레이아웃·화면 조각까지 그려짐(`app:suspense:resolve`)
 *  4. 내용 받음 — 첫 화면의 서버 요청·가젯 조각 받기가 끝나고 로딩 표시(`aria-busy="true"`)가 사라진 채 {@link QUIET_MS} 동안 조용함
 * 4단계를 {@link HOLD_MS} 보여 준 뒤 살짝 커지며 사라진다({@link FADE_MS}). 요청이 오래 걸려도 3단계 뒤 {@link MAX_MS} 면 걷고, 앱이 붙은 뒤 {@link SAFETY_MS} 면 무엇이든 걷는다.
 *
 * 회색 틀(11-13)을 진짜 화면이 붙는 순간 지우면 「틀 → 상단 바만 있는 화면 → 본문」으로 화면이 두 번 바뀌어 체감이 나빴다 — 그래서 본문까지 기다린다.
 * 요청 수는 `useApiClient` 의 `send` 와 테마·가젯 조각 받기(`util-theme`·`util-gadget-registry`)가 {@link track} 으로 센다. 스플래시를 걷은 뒤에는 세지 않는다.
 */
export default class UtilBootSplash {
    /** Nuxt 가 붙이는 이름(`#__nuxt-loader`) 대신 쓰는 이름 — Nuxt 기본은 `app:suspense:resolve` 에 그 이름으로 지우므로(데이터 요청 전) 이름을 바꿔 그 지우기를 피한다 */
    static readonly ELEMENT_ID = 'irb-splash';
    static readonly NUXT_ID = '__nuxt-loader';
    static readonly STEP_CODE = 2;
    static readonly STEP_DRAWN = 3;
    static readonly STEP_DONE = 4;
    /** 요청·로딩 표시가 없는 채로 이만큼 지나면 다 그려졌다고 본다 */
    static readonly QUIET_MS = 200;
    /** 화면이 그려진 뒤(`app:suspense:resolve`) 이만큼 지나면 요청이 남아 있어도 걷는다 */
    static readonly MAX_MS = 8000;
    /** 앱이 붙은 뒤(`app:mounted`) 이만큼 지나면 무엇이든 걷는다 — 화면 조각을 끝내 못 받는 경우의 안전망 */
    static readonly SAFETY_MS = 15000;
    /** 다 찬 막대를 보여 주는 시간 */
    static readonly HOLD_MS = 150;
    /** 사라지는 시간(템플릿 CSS `transition` 과 같게) */
    static readonly FADE_MS = 250;
    private static readonly POLL_MS = 50;

    private static pending = 0;
    private static active = true;

    /**
     * 서버 요청·조각 받기 하나를 센다 — 끝나면(성공·실패 모두) 뺀다. 스플래시를 걷은 뒤에는 그대로 돌려준다
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
     * 막대·점을 그 단계로(템플릿 스크립트의 함수 — 없으면 아무 일도 없다)
     * @param {number} n - 단계(1~4).
     */
    static step(n: number): void {
        window.__irbSplash?.step(n);
    }

    /**
     * 화면이 그려졌다 — 3단계로 두고, 첫 화면이 조용해지면(또는 {@link MAX_MS} 뒤) 걷는다
     * @param {HTMLElement} el - {@link claim} 이 돌려준 요소.
     */
    static hideWhenSettled(el: HTMLElement): void {
        UtilBootSplash.step(UtilBootSplash.STEP_DRAWN);
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
     * 막대를 채우고 잠깐 보인 뒤 살짝 커지며 사라진다(이미 걷는 중이면 아무 일도 없다)
     * @param {HTMLElement} el - {@link claim} 이 돌려준 요소.
     */
    static hide(el: HTMLElement): void {
        if (!el.isConnected || el.dataset.leaving) return;
        el.dataset.leaving = '1';
        UtilBootSplash.active = false;
        UtilBootSplash.step(UtilBootSplash.STEP_DONE);
        window.setTimeout(() => {
            el.querySelector('.irb-splash')?.classList.add('is-leaving');
            window.setTimeout(() => el.remove(), UtilBootSplash.FADE_MS);
        }, UtilBootSplash.HOLD_MS);
    }
}
