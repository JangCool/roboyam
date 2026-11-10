import type { Ref } from 'vue';

/**
 * 가젯 카드의 보이는 여백(잉크 기준)을 좌우 여백과 같게 — 첫·마지막 자식 바깥 여백을 옮긴다(음수 가능: 투명 버튼 터치 영역·글자 반행간은 잉크가 아니다).
 * 콘솔 확인: `apps/user-web/scripts/gadget-ink-padding.js`(같은 규칙).
 */
export function useGadgetInk(card: Ref<HTMLElement | null>, enabled: () => boolean): void {
    let frame = 0;
    let resize: ResizeObserver | null = null;
    let mutation: MutationObserver | null = null;
    let marked: HTMLElement[] = [];
    let floated: HTMLElement[] = [];
    const watched = new WeakSet<Element>();

    function clear(): void {
        for (const el of [...marked, ...floated]) {
            el.style.marginTop = '';
            el.style.marginBottom = '';
        }
        marked = [];
        floated = [];
    }

    /** 늘어난 상자는 안 내용이 바뀌어도 자기 크기가 그대로라 그 자식까지 지켜본다 */
    function watchTree(el: Element): void {
        for (const child of el.children) {
            if (!watched.has(child)) {
                watched.add(child);
                resize?.observe(child);
            }
            if (child.hasAttribute('data-grid-fill')) watchTree(child);
        }
    }

    function apply(): void {
        frame = 0;
        run(false);
    }

    /** `retry` = 넘침 검사로 한 번 더 도는 회차(다시 돌지 않는다) */
    function run(retry: boolean): void {
        const el = card.value;
        if (!el || !enabled()) {
            clear();
            return;
        }
        const kids = visibleChildren(el);
        const first = kids[0];
        const last = kids[kids.length - 1];
        if (!first || !last) {
            clear();
            return;
        }
        for (const m of marked) {
            if (m !== first) m.style.marginTop = '';
            if (m !== last) m.style.marginBottom = '';
        }
        marked = [first, last].filter((m, i, list) => list.indexOf(m) === i);
        const floats = Array.from(el.querySelectorAll<HTMLElement>('[data-grid-float]')).filter((f) => getComputedStyle(f).display !== 'none' && last.contains(f));
        for (const f of floated) {
            if (floats[0] === f) continue;
            f.style.marginTop = '';
            f.style.marginBottom = '';
        }
        const float = floats[0] ?? null;
        floated = float ? [float] : [];
        // 칸에 남는 높이가 없으면 지난 띄움 여백을 재기 전에 없앤다(남기면 카드가 넘쳐 아래 여백이 틀어진다)
        const slot = slotHeight(el);
        if (float && (slot === null || slot - naturalHeight(el) <= 0.5)) {
            setPx(float, 'marginTop', null);
            setPx(float, 'marginBottom', null);
        }

        // DOM 순서가 아니라 위치로(두 열 목록은 DOM 끝이 짧은 열 끝일 수 있다)
        const ink = inkOf(el, true);
        if (!ink) {
            clear();
            return;
        }
        const cs = getComputedStyle(el);
        const rect = el.getBoundingClientRect();
        const padX = num(cs.paddingLeft);
        const borderT = num(cs.borderTopWidth);
        const borderB = num(cs.borderBottomWidth);
        // 늘어난 빈 곳은 카드가 아니라 칸 높이로 잰다(카드로 재면 넘친 만큼이 다시 빈 곳으로 잡혀 되먹임)
        const extra = slot === null ? 0 : Math.max(0, slot - naturalHeight(el));

        const floatAbove = float && ink.topEl && float.contains(ink.topEl) ? num(float.style.marginTop) : 0;
        const gapTop = ink.top - (rect.top + borderT) - floatAbove;
        const top = num(getComputedStyle(first).marginTop) + (padX - gapTop);

        // 아래 여백에서 늘어난 빈 곳을 뺀다 — 밀린 줄·본문 뒤 줄이면 빈 곳은 그 위라 0
        const owner = ink.bottomEl;
        let slack = extra;
        // 띄운 안내 안이면 안내 아래 빈 곳 = 늘어난 빈 곳 − 안내 위 띄움 여백(나누기 전이면 빈 곳은 모두 안내 아래에 있다)
        if (float && owner && float.contains(owner)) slack = Math.max(0, extra - num(float.style.marginTop));
        else if (owner && (owner.closest('[data-grid-push]') || !insideBody(el, owner))) slack = 0;
        // 빈 상태 안내 — 「안내 위 잉크 → 안내」(A) = 「안내 → 카드 아래 테두리」(B), 최소 상태 B = T = max(A0, 좌우 여백)
        let a0 = 0;
        let target = padX;
        let gapBottom = rect.bottom - borderB - ink.bottom - slack;
        if (float) {
            const fi = inkOf(float, false);
            if (fi) {
                const above = inkAbove(el, float);
                a0 = fi.top - (above ?? rect.top) - num(float.style.marginTop);
                target = Math.max(a0, padX);
                gapBottom = rect.bottom - ink.bottom - slack; // 카드 아래 테두리 바깥까지
            }
        }
        const bottom = num(getComputedStyle(last).marginBottom) + (target - gapBottom);
        setPx(first, 'marginTop', top);
        setPx(last, 'marginBottom', bottom);

        // 여유는 띄움 여백으로 나눠 A = B — 격자 높이 재기는 이 여백을 세지 않는다
        if (float) {
            const e = slot === null ? 0 : slot - naturalHeight(el);
            if (e <= 0.5) {
                setPx(float, 'marginTop', null);
                setPx(float, 'marginBottom', null);
            } else {
                const mt = Math.min(e, Math.max(0, (e + target - a0) / 2));
                setPx(float, 'marginTop', mt);
                setPx(float, 'marginBottom', Math.max(0, e - mt));
            }
        }
        watchTree(el);

        // 카드가 칸보다 길면(띄움 여백이 남은 것) 없애고 한 번만 다시 맞춘다
        const slotNow = slotHeight(el);
        if (!retry && float && slotNow !== null && el.getBoundingClientRect().height > slotNow + 1 && (float.style.marginTop !== '' || float.style.marginBottom !== '')) {
            setPx(float, 'marginTop', null);
            setPx(float, 'marginBottom', null);
            run(true);
        }
    }

    function schedule(): void {
        if (typeof window === 'undefined' || frame) return;
        frame = window.requestAnimationFrame(apply);
    }

    onMounted(() => {
        const el = card.value;
        if (!el) return;
        if (typeof ResizeObserver !== 'undefined') {
            resize = new ResizeObserver(schedule);
            resize.observe(el);
            // 칸만 줄고 카드는 (띄움 여백 때문에) 그대로인 경우가 있어 칸도 지켜본다
            if (el.parentElement) resize.observe(el.parentElement);
        }
        if (typeof MutationObserver !== 'undefined') {
            mutation = new MutationObserver(schedule);
            mutation.observe(el, { childList: true, subtree: true, characterData: true });
            if (el.parentElement) mutation.observe(el.parentElement, { attributes: true, attributeFilter: ['data-grid-height'] });
        }
        // 글꼴이 늦게 오면 카드 크기는 그대로인데 줄 상자가 바뀔 수 있다
        if (typeof document !== 'undefined' && document.fonts) {
            void document.fonts.ready.then(schedule);
            document.fonts.addEventListener('loadingdone', schedule);
        }
        schedule();
    });
    watch(enabled, schedule);
    onBeforeUnmount(() => {
        resize?.disconnect();
        mutation?.disconnect();
        if (typeof document !== 'undefined' && document.fonts) document.fonts.removeEventListener('loadingdone', schedule);
        if (frame) window.cancelAnimationFrame(frame);
    });
}

function num(value: string): number {
    return parseFloat(value) || 0;
}

/** 한 열(모바일)·격자 밖이면 null */
function slotHeight(card: Element): number | null {
    const v = card.parentElement?.getAttribute('data-grid-height');
    if (v === null || v === undefined || v === '') return null;
    const n = Number(v);
    return Number.isFinite(n) ? n : null;
}

/** 1px 미만 차이는 그대로 둔다(소수 반올림이 뒤집히며 재기·보정이 왕복하지 않게) */
function setPx(el: HTMLElement, key: 'marginTop' | 'marginBottom', ideal: number | null): void {
    const current = el.style[key];
    if (ideal === null) {
        if (current !== '') el.style[key] = '';
        return;
    }
    if (current !== '' && Math.abs(num(current) - ideal) < 1) return;
    const next = `${Math.round(ideal)}px`;
    if (current !== next) el.style[key] = next;
}

function visibleChildren(el: Element): HTMLElement[] {
    const out: HTMLElement[] = [];
    for (const child of el.children) {
        if (!(child instanceof HTMLElement)) continue;
        const cs = getComputedStyle(child);
        if (cs.display === 'none' || cs.position === 'absolute' || cs.position === 'fixed') continue;
        out.push(child);
    }
    return out;
}

function insideBody(card: Element, owner: Element): boolean {
    for (const child of card.children) if (child.hasAttribute('data-grid-fill') && child.contains(owner)) return true;
    return false;
}

/** 격자 `naturalHeight` 와 같은 규칙 — 밀린 줄 위 여백·띄운 덩어리 여백은 남는 공간이라 세지 않는다 */
function naturalHeight(el: Element): number {
    if (!el.hasAttribute('data-grid-fill')) return el.getBoundingClientRect().height;
    const cs = getComputedStyle(el);
    let sum = num(cs.paddingTop) + num(cs.paddingBottom) + num(cs.borderTopWidth) + num(cs.borderBottomWidth);
    let count = 0;
    for (const child of el.children) {
        const ccs = getComputedStyle(child);
        if (ccs.display === 'none' || ccs.position === 'absolute' || ccs.position === 'fixed') continue;
        const h = naturalHeight(child);
        if (h === 0 && !child.hasAttribute('data-grid-fill')) continue;
        const float = child.hasAttribute('data-grid-float');
        const top = float || child.hasAttribute('data-grid-push') ? 0 : num(ccs.marginTop);
        sum += h + top + (float ? 0 : num(ccs.marginBottom));
        count += 1;
    }
    return sum + (count > 1 ? (count - 1) * num(cs.rowGap) : 0);
}

/** `target` 앞(조상의 앞 형제 포함) 잉크 중 가장 아래 밑선 — 없으면 null */
function inkAbove(card: Element, target: Element): number | null {
    let bottom: number | null = null;
    let node: Element | null = target;
    while (node && node !== card) {
        let prev = node.previousElementSibling;
        while (prev) {
            const ink = inkOf(prev);
            if (ink) bottom = bottom === null ? ink.bottom : Math.max(bottom, ink.bottom);
            prev = prev.previousElementSibling;
        }
        node = node.parentElement;
    }
    return bottom;
}

function visibleColor(color: string): boolean {
    if (!color || color === 'transparent') return false;
    const rgba = /rgba?\(([^)]+)\)/.exec(color);
    if (rgba) {
        const parts = rgba[1]!.split(/[\s,/]+/).filter(Boolean);
        return parts.length < 4 || parseFloat(parts[3]!) > 0;
    }
    return !/\/\s*0(\.0+)?\s*\)$/.test(color);
}

/** 아이콘·그림 요소, 또는 바탕·테두리·그림자가 보일 때 */
function paints(el: Element, cs: CSSStyleDeclaration): boolean {
    const tag = el.tagName;
    if (tag === 'I' || tag === 'IMG' || tag === 'SVG' || tag === 'svg' || tag === 'VIDEO' || tag === 'CANVAS') return true;
    if (visibleColor(cs.backgroundColor) || (cs.backgroundImage && cs.backgroundImage !== 'none')) return true;
    if (cs.boxShadow && cs.boxShadow !== 'none') return true;
    const sides = ['Top', 'Right', 'Bottom', 'Left'] as const;
    return sides.some((s) => num(cs.getPropertyValue(`border-${s.toLowerCase()}-width`)) > 0 && visibleColor(cs.getPropertyValue(`border-${s.toLowerCase()}-color`)));
}

/** 줄 상자 가운데 em 상자에서 위 14%·아래 10% 를 뺀 한글·숫자 글리프 근사 */
function textInk(rect: DOMRect, fontSize: number): { top: number; bottom: number } {
    const emTop = rect.top + (rect.height - fontSize) / 2;
    return { top: emTop + fontSize * 0.14, bottom: emTop + fontSize * 0.9 };
}

/** 요소 안 잉크의 위·아래 끝과 주인 요소 — `skipSelf` 면 자기 바탕·테두리는 치지 않는다(카드) */
function inkOf(root: Element, skipSelf = false): { top: number; bottom: number; topEl: Element | null; bottomEl: Element | null } | null {
    let top = Number.POSITIVE_INFINITY;
    let bottom = Number.NEGATIVE_INFINITY;
    let topEl: Element | null = null;
    let bottomEl: Element | null = null;
    const add = (t: number, b: number, owner: Element) => {
        if (b - t <= 0) return;
        if (t < top) {
            top = t;
            topEl = owner;
        }
        if (b > bottom) {
            bottom = b;
            bottomEl = owner;
        }
    };
    const visit = (el: Element) => {
        const cs = getComputedStyle(el);
        if (cs.display === 'none' || cs.visibility === 'hidden' || cs.opacity === '0') return;
        const rect = el.getBoundingClientRect();
        if (rect.width <= 1 || rect.height <= 1) return; // 크기 0·sr-only
        if (!(skipSelf && el === root) && paints(el, cs)) {
            add(rect.top, rect.bottom, el);
            if (el.tagName === 'I' || el.tagName === 'IMG' || el.tagName.toLowerCase() === 'svg') return;
        }
        for (const node of el.childNodes) {
            if (node.nodeType === Node.TEXT_NODE) {
                if (!node.textContent || !node.textContent.trim()) continue;
                const range = document.createRange();
                range.selectNodeContents(node);
                const fontSize = num(cs.fontSize);
                for (const r of range.getClientRects()) {
                    if (r.width <= 0 || r.height <= 0) continue;
                    const t = textInk(r, fontSize);
                    add(t.top, t.bottom, el);
                }
            } else if (node.nodeType === Node.ELEMENT_NODE) {
                visit(node as Element);
            }
        }
    };
    visit(root);
    return Number.isFinite(top) ? { top, bottom, topEl, bottomEl } : null;
}
