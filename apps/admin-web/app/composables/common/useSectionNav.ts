import type { SectionNavEntry, SectionNavStatus } from '~/utils/util-section-nav';
import UtilSectionNav from '~/utils/util-section-nav';

/**
 * 우측 구역 내비(앵커 메뉴)의 상태와 동작 — community 관리자 앱의 `useSectionNav` 를 admin-web 셸에 맞게 옮긴 것이다.
 * 구획이 스스로 등록하고(`AppGadget` 의 `anchor`), 셸(`layouts/default.vue`)이 현재 구획 추적을 켜고(`start`), 항목을 누르면 이동한다(`go`). 그리는 것은 `SectionNav` 다.
 *
 * community 와 다른 점 — **스크롤하는 것이 문서가 아니라 본문 영역(`.app-content`, id `SCROLL_ID`)이다**:
 *  - 구획 위치는 그 영역 위쪽 끝을 기준으로 잰다. 이동은 구획의 `scroll-margin-top`(`--section-nav-offset`)이 정한다 — 상단 네비·메뉴 줄은 스크롤 영역 **밖**이라 그 높이를 더하지 않는다.
 *  - 스크롤 감시도 그 영역(`scroll` · 크기 변화 · `IntersectionObserver` 의 `root`)에 건다.
 *  - 주소 조각(`#anchor`) 쓰기·복원은 하지 않는다 — 내 정보처럼 탭마다 구획이 달라 다른 탭의 조각이 남으면 안 맞는다.
 *  - 페이지 머리 안으로 목록을 옮겨 그리는 자리(`railHost`)와 선택 상자 자리(`selectHost`)는 없다 — 목록은 셸 본문 격자의 오른쪽 열, 선택 상자는 본문 맨 위에 그린다.
 *
 * 범위(`SECTION_NAV_SCOPE`): 서랍처럼 자기 스크롤 영역을 가진 곳은 범위 이름을 provide 하고 `start(스크롤 영역)` 를 부르면, 그 안의 `AppGadget`·`SectionNav` 가 셸과 따로 된 목록·현재 구획 추적을 쓴다. provide 가 없으면 셸 범위라 기존 화면은 그대로다.
 *
 * 반환: `items`(정리한 항목, 위에서 아래) · `visible`(2개 이상일 때만 true) · `active`(현재 구획) · `register(entry)`(해제 함수를 돌려준다) · `setStatus(key, status)`(바뀜·오류 칸 수만 바꾼다) ·
 * `go(anchor)`(스크롤 이동 + 제목 초점 + 테두리 강조) · `focusTitle` · `start()`/`stop()`/`reset()`(셸이 한 번 켜고 끈다, 화면이 바뀌면 `reset`).
 */

/** 본문 스크롤 영역의 id — 셸이 `.app-content` 에 건다 */
export const SECTION_NAV_SCROLL_ID = 'app-scroll';

/** 이동 뒤 구획 테두리 강조를 켜는 표식 — `main.css` 의 `[data-nav-flash]` 규칙이 윤곽선을 그린다 */
const FLASH_ATTR = 'data-nav-flash';

/** 구획 내비의 범위를 정하는 provide/inject 키 — 서랍처럼 자기 스크롤 영역을 가진 곳이 범위 이름을 provide 하면 그 안의 `AppGadget`·`SectionNav` 가 셸과 따로 된 구역 내비를 쓴다(셸 내비에 섞이지 않는다). provide 가 없으면 셸 범위 */
export const SECTION_NAV_SCOPE = 'section-nav-scope';

/** 관찰·타이머는 범위(셸 하나 · 서랍마다 하나)마다 한 벌이라 모듈 안에 둔다 — 상태가 아니라 브라우저 자원이다 */
interface NavContext {
    container: HTMLElement | null;
    band: IntersectionObserver | null;
    sizeWatcher: ResizeObserver | null;
    lockedAnchor: string | null;
    lockTimer: ReturnType<typeof setTimeout> | null;
    flashTarget: Element | null;
    flashTimer: ReturnType<typeof setTimeout> | null;
    scrollFrame: number | null;
    started: boolean;
    stopWatch: (() => void) | null;
}
const contexts = new Map<string, NavContext>();

function contextOf(key: string): NavContext {
    let context = contexts.get(key);
    if (!context) {
        context = { container: null, band: null, sizeWatcher: null, lockedAnchor: null, lockTimer: null, flashTarget: null, flashTimer: null, scrollFrame: null, started: false, stopWatch: null };
        contexts.set(key, context);
    }
    return context;
}

/** 스크롤 영역의 위쪽 끝 기준 구획 위치(px) — 확대(zoom)된 안쪽도 화면에 보이는 값으로 잰다 */
function topWithin(element: Element, root: HTMLElement): number {
    return element.getBoundingClientRect().top - root.getBoundingClientRect().top;
}

/** 구획 위쪽 여백이 화면에서 차지하는 길이(px) — `scroll-margin-top` 에 본문 글자 크기 배율(zoom)이 곱해진 값 */
function marginOf(element: HTMLElement): number {
    const margin = Number.parseFloat(getComputedStyle(element).scrollMarginTop) || 0;
    const scale = element.offsetHeight > 0 ? element.getBoundingClientRect().height / element.offsetHeight : 1;
    return margin * (Number.isFinite(scale) && scale > 0 ? scale : 1);
}

export function useSectionNav() {
    // 범위 — 서랍 같은 곳이 provide 한 이름이 있으면 그 범위의 목록·감시를 쓴다. 없으면 셸 범위(기존 화면은 그대로)
    const scope = inject<string | null>(SECTION_NAV_SCOPE, null);
    const suffix = scope ? `:${scope}` : '';
    const ctx = contextOf(scope ?? '');
    const entries = useState<SectionNavEntry[]>(`section-nav-entries${suffix}`, () => []);
    const current = useState<string | null>(`section-nav-current${suffix}`, () => null);

    const items = computed(() => UtilSectionNav.normalize(entries.value));
    const visible = computed(() => items.value.length >= UtilSectionNav.MIN_SECTIONS);
    /** 현재 구획 — 아직 정해지지 않았으면(처음 그린 직후) 첫 구획 */
    const active = computed(() => current.value ?? items.value[0]?.anchor ?? null);

    /* ── 등록 ── */

    function register(entry: SectionNavEntry): () => void {
        entries.value = order([...entries.value.filter((other) => other.key !== entry.key), entry]);
        return () => {
            entries.value = entries.value.filter((other) => other.key !== entry.key);
        };
    }

    /** 올라온 구획의 바뀐·오류 칸 수를 바꾼다 — 달라진 때만 목록을 새로 만든다(입력 한 글자마다 목록이 다시 그려지지 않게) */
    function setStatus(key: string, status: SectionNavStatus): void {
        const changes = UtilSectionNav.count(status.changes);
        const invalid = UtilSectionNav.count(status.invalid);
        const at = entries.value.findIndex((entry) => entry.key === key);
        if (at < 0) return;
        const before = entries.value[at] as SectionNavEntry;
        if (UtilSectionNav.count(before.changes) === changes && UtilSectionNav.count(before.invalid) === invalid) return;
        const next = entries.value.slice();
        next[at] = { ...before, changes, invalid };
        entries.value = next;
    }

    /** 등록은 마운트 순서라 화면 위치로 다시 줄 세운다(위치를 아는 것만 — 아직 그려지지 않았으면 등록 순서) */
    function order(list: SectionNavEntry[]): SectionNavEntry[] {
        const root = scrollRoot();
        if (!root) return list;
        const tops: Record<string, number> = {};
        for (const entry of list) {
            const element = document.getElementById(entry.anchor);
            if (element) tops[entry.anchor] = topWithin(element, root);
        }
        return UtilSectionNav.order(list, tops);
    }

    function scrollRoot(): HTMLElement | null {
        if (typeof document === 'undefined') return null;
        // 셸 범위는 본문 영역(`#app-scroll`), 서랍 같은 범위는 `start(root)` 로 받은 자기 스크롤 영역
        return ctx.container ?? (scope ? null : document.getElementById(SECTION_NAV_SCROLL_ID));
    }

    /* ── 이동 ── */

    function go(anchor: string, options: { instant?: boolean } = {}): void {
        if (typeof document === 'undefined') return;
        const target = document.getElementById(anchor);
        if (!target) return;
        lock(anchor);
        current.value = anchor;
        // 움직임 줄이기 설정이면 즉시, 아니면 부드럽게. 위쪽 여백은 구획의 `scroll-margin-top` 이 정한다
        const reduce = typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches === true;
        target.scrollIntoView({ behavior: options.instant === true || reduce ? 'auto' : 'smooth', block: 'start' });
        // 제목에 초점 — 다음 Tab 은 그 구획의 첫 입력칸으로 간다. 이미 스크롤 중이라 초점이 화면을 또 움직이지 않게 한다
        focusTitle(anchor);
        flash(target);
    }

    /** 구획 제목(h2, `tabindex=-1`)에 초점을 준다 — 화면은 움직이지 않는다 */
    function focusTitle(anchor: string): void {
        if (typeof document === 'undefined') return;
        const target = document.getElementById(anchor);
        if (!target) return;
        const title = target.querySelector('h2') ?? target;
        (title as HTMLElement).focus?.({ preventScroll: true });
    }

    function flash(target: Element): void {
        if (ctx.flashTarget && ctx.flashTarget !== target) ctx.flashTarget.removeAttribute(FLASH_ATTR);
        if (ctx.flashTimer) clearTimeout(ctx.flashTimer);
        ctx.flashTarget = target;
        target.setAttribute(FLASH_ATTR, 'true');
        ctx.flashTimer = setTimeout(() => {
            target.removeAttribute(FLASH_ATTR);
            if (ctx.flashTarget === target) ctx.flashTarget = null;
        }, UtilSectionNav.FLASH_MS);
    }

    /* ── 현재 구획 추적(스크롤 스파이) ── */

    /** 항목을 누른 직후 — 이동하는 동안 표시가 중간 구획을 지나며 깜빡이지 않게 누른 구획으로 잠근다. 스크롤이 멈추면(또는 스크롤이 없으면 LOCK_MS 뒤) 푼다 */
    function lock(anchor: string): void {
        ctx.lockedAnchor = anchor;
        if (ctx.lockTimer) clearTimeout(ctx.lockTimer);
        ctx.lockTimer = setTimeout(release, UtilSectionNav.LOCK_MS);
    }

    function release(): void {
        ctx.lockedAnchor = null;
        if (ctx.lockTimer) clearTimeout(ctx.lockTimer);
        ctx.lockTimer = null;
        recompute();
    }

    /** 감시 선(px) — 첫 구획의 위쪽 여백 + 8px. 구획이 아직 없으면 0 */
    function lineY(): number {
        const first = items.value[0] ? document.getElementById(items.value[0].anchor) : null;
        return (first ? marginOf(first) : 0) + UtilSectionNav.SPY_LINE_GAP_PX;
    }

    function recompute(): void {
        const root = scrollRoot();
        if (!root) return;
        const sections = items.value.flatMap((item) => {
            const element = document.getElementById(item.anchor);
            return element ? [{ anchor: item.anchor, top: topWithin(element, root) }] : [];
        });
        current.value = UtilSectionNav.activeAnchor({
            sections,
            line: lineY(),
            atTop: root.scrollTop <= 2,
            atBottom: root.scrollHeight - root.clientHeight > 2 && root.scrollTop + root.clientHeight >= root.scrollHeight - 2,
            locked: ctx.lockedAnchor,
        });
    }

    /** 구획을 다시 관찰한다 — 항목이 바뀌었거나 영역 높이가 바뀌어 감시 선 띠가 달라졌을 때 */
    function observe(): void {
        ctx.band?.disconnect();
        ctx.band = null;
        const root = scrollRoot();
        if (!root || typeof IntersectionObserver === 'undefined') return;
        const line = lineY();
        const bottomMargin = Math.max(0, root.clientHeight - line - 1);
        // 감시 선 한 줄(1px 높이 띠) — 구획이 이 띠에 들어오거나 나갈 때마다 현재 구획을 다시 가린다
        ctx.band = new IntersectionObserver(() => recompute(), { root, rootMargin: `-${line}px 0px -${bottomMargin}px 0px`, threshold: [0, 1] });
        for (const item of items.value) {
            const element = document.getElementById(item.anchor);
            if (element) ctx.band.observe(element);
        }
    }

    /** 스크롤 — 한 프레임에 한 번만 현재 구획을 다시 가린다. 항목을 누른 직후의 잠금은 `recompute` 가 존중한다 */
    function onScroll(): void {
        if (ctx.lockedAnchor !== null) {
            // 이동 중에는 멈출 때까지 잠금을 이어 둔다
            if (ctx.lockTimer) clearTimeout(ctx.lockTimer);
            ctx.lockTimer = setTimeout(release, UtilSectionNav.SETTLE_MS);
        }
        if (ctx.scrollFrame !== null) return;
        ctx.scrollFrame = window.requestAnimationFrame(() => {
            ctx.scrollFrame = null;
            if (ctx.started) recompute();
        });
    }

    function onResize(): void {
        if (!ctx.started) return;
        observe();
        recompute();
    }

    /** 현재 구획 추적을 켠다 — 셸은 인자 없이(본문 영역), 서랍은 자기 스크롤 영역을 넘긴다 */
    function start(root?: HTMLElement | null): void {
        if (ctx.started) return;
        ctx.container = root ?? (scope ? null : document.getElementById(SECTION_NAV_SCROLL_ID));
        if (!ctx.container) return;
        ctx.started = true;
        ctx.container.addEventListener('scroll', onScroll, { passive: true });
        // 영역 크기(창·사이드바 접힘·본문 폭·글자 크기)가 바뀌면 감시 선 띠와 구획 위치가 달라진다
        if (typeof ResizeObserver !== 'undefined') {
            ctx.sizeWatcher = new ResizeObserver(onResize);
            ctx.sizeWatcher.observe(ctx.container);
        }
        ctx.stopWatch = watch(
            () => items.value.map((item) => item.anchor).join('|'),
            () => {
                observe();
                recompute();
            },
            { flush: 'post', immediate: true },
        );
    }

    function stop(): void {
        ctx.started = false;
        ctx.stopWatch?.();
        ctx.stopWatch = null;
        ctx.band?.disconnect();
        ctx.sizeWatcher?.disconnect();
        ctx.band = null;
        ctx.sizeWatcher = null;
        ctx.container?.removeEventListener('scroll', onScroll);
        ctx.container = null;
        for (const timer of [ctx.lockTimer, ctx.flashTimer]) if (timer) clearTimeout(timer);
        ctx.lockTimer = ctx.flashTimer = null;
        if (ctx.scrollFrame !== null) window.cancelAnimationFrame(ctx.scrollFrame);
        ctx.scrollFrame = null;
        reset();
    }

    /** 화면이 바뀌었다 — 현재 구획·잠금을 비운다(항목은 구획이 사라지며 스스로 빠진다) */
    function reset(): void {
        current.value = null;
        ctx.lockedAnchor = null;
    }

    return { items, visible, active, register, setStatus, go, focusTitle, start, stop, reset };
}
