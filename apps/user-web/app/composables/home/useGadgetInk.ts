import type { Ref } from 'vue';

/**
 * 가젯 카드의 **눈에 보이는 여백**(잉크 기준) 맞추기 — 사용자 결정 2026-10-31: 보이는 첫 요소의 윗선 ↔ 카드 위, 마지막 보이는 요소의 아랫선 ↔ 카드 아래가 좌우 여백(테마 `--gadget-card-pad-x`)과 같아야 한다(±2px).
 * CSS 상자로만 맞추면 마지막 줄의 보이지 않는 여유(투명 버튼 터치 영역 · 글자 반행간 · 빈 상태 블록의 아래 안 여백 …)가 아래 여백에 더해진다. 그래서 카드의 첫·마지막 자식에
 * 위·아래 바깥 여백을 직접 걸어(음수 가능 — 터치 영역은 그대로 두고 보이는 글리프만 여백선에 맞춘다) 「카드 안 여백 + 그 자식 여백 + 자식 안의 보이지 않는 여유 = 좌우 여백」이 되게 한다.
 *
 *  - 잉크: 글자 = 텍스트 노드 Range 의 줄 상자를 글자 크기로 근사한 글리프 윗선·아랫선(줄 상자 가운데에 em 상자, 그 위 14% · 아래 10% 를 뺀다 — 한글·숫자 윗선/밑선 근사).
 *    아이콘(i)·svg·img·video·canvas = 요소 상자. 그 밖의 요소는 바탕·테두리·그림자가 보일 때만 상자(투명 버튼·줄 상자는 잉크가 아니다).
 *    `visibility:hidden`·`display:none`·투명(opacity 0)·크기 1px 이하(sr-only)는 뺀다.
 *  - 늘어나는 상자(`data-grid-fill` — 껍데기 본문, 바닥 조작 줄이 있는 가젯 본문)는 안으로 내려가 첫·마지막 자식의 여유를 더한다 — 칸이 커서 늘어난 높이(아래 빈 곳)는 세지 않는다
 *    (그래서 격자 높이 재기 `naturalHeight` 와 되먹임이 없다: 최소 상태에서 카드 = 내용, 걸어 둔 여백은 자식 자신의 여백을 빼고 계산해 다시 재도 같은 값).
 *  - 카드 모양이 없는 가젯(평평 `FLAT`)은 하지 않는다(보이는 상자가 없다). 높이·DOM 이 바뀌면(ResizeObserver · MutationObserver) 다음 그리기에 다시 맞춘다.
 * 브라우저 콘솔 확인용 스크립트: `apps/user-web/scripts/gadget-ink-padding.js`(같은 잉크 규칙).
 * ⚠ setup 에서 부른다.
 * @param {Ref<HTMLElement | null>} card - 가젯 카드(`HomeGadgetFrame` 의 section).
 * @param {() => boolean} enabled - 맞출지(카드 모양이 있을 때).
 */
export function useGadgetInk(card: Ref<HTMLElement | null>, enabled: () => boolean): void {
    let frame = 0;
    let resize: ResizeObserver | null = null;
    let mutation: MutationObserver | null = null;
    let marked: HTMLElement[] = [];

    function clear(): void {
        for (const el of marked) {
            el.style.marginTop = '';
            el.style.marginBottom = '';
        }
        marked = [];
    }

    function apply(): void {
        frame = 0;
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
        const cs = getComputedStyle(el);
        const padX = num(cs.paddingLeft);
        const lead = leading(first);
        const trail = trailing(last);
        const top = lead === null ? null : Math.round(padX - num(cs.paddingTop) - lead);
        const bottom = trail === null ? null : Math.round(padX - num(cs.paddingBottom) - trail);
        // 지난번에 건 자식 중 이번에 첫·마지막이 아닌 것은 되돌린다
        for (const m of marked) {
            if (m !== first) m.style.marginTop = '';
            if (m !== last) m.style.marginBottom = '';
        }
        marked = [first, last].filter((m, i, list) => list.indexOf(m) === i);
        setPx(first, 'marginTop', top);
        setPx(last, 'marginBottom', bottom);
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
        }
        if (typeof MutationObserver !== 'undefined') {
            mutation = new MutationObserver(schedule);
            mutation.observe(el, { childList: true, subtree: true, characterData: true });
        }
        schedule();
    });
    watch(enabled, schedule);
    onBeforeUnmount(() => {
        resize?.disconnect();
        mutation?.disconnect();
        if (frame) window.cancelAnimationFrame(frame);
    });
}

function num(value: string): number {
    return parseFloat(value) || 0;
}

function setPx(el: HTMLElement, key: 'marginTop' | 'marginBottom', value: number | null): void {
    const next = value === null ? '' : `${value}px`;
    if (el.style[key] !== next) el.style[key] = next;
}

/** 흐름에 있는 보이는 자식(감춤·absolute·fixed 제외) */
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

/** 첫 잉크까지 — 요소 위 끝에서 첫 잉크 윗선까지(늘어나는 상자는 안 여백 + 첫 자식 위 여백 + 그 자식의 여유). 잉크가 없으면 null */
function leading(el: HTMLElement): number | null {
    if (el.hasAttribute('data-grid-fill')) {
        const cs = getComputedStyle(el);
        let acc = num(cs.paddingTop) + num(cs.borderTopWidth);
        for (const child of visibleChildren(el)) {
            const ccs = getComputedStyle(child);
            const inner = leading(child);
            if (inner !== null) return acc + num(ccs.marginTop) + inner;
            // 잉크 없는 앞 자식(빈 칸)은 그 높이째 여유로 치고 다음 자식으로
            acc += num(ccs.marginTop) + child.getBoundingClientRect().height + num(ccs.marginBottom);
        }
        return null;
    }
    const ink = inkOf(el);
    return ink ? ink.top - el.getBoundingClientRect().top : null;
}

/** 마지막 잉크부터 — 마지막 잉크 아랫선에서 요소 아래 끝까지(늘어나는 상자는 마지막 자식 아래 여백 + 안 여백, 늘어난 빈 곳은 세지 않는다). 잉크가 없으면 null */
function trailing(el: HTMLElement): number | null {
    if (el.hasAttribute('data-grid-fill')) {
        const cs = getComputedStyle(el);
        const tail = num(cs.paddingBottom) + num(cs.borderBottomWidth);
        const kids = visibleChildren(el);
        const last = kids[kids.length - 1];
        if (!last) return null;
        const inner = trailing(last);
        return inner === null ? null : tail + num(getComputedStyle(last).marginBottom) + inner;
    }
    const ink = inkOf(el);
    return ink ? el.getBoundingClientRect().bottom - ink.bottom : null;
}

/** 색이 보이는가 — 투명(알파 0)이 아니면 */
function visibleColor(color: string): boolean {
    if (!color || color === 'transparent') return false;
    const rgba = /rgba?\(([^)]+)\)/.exec(color);
    if (rgba) {
        const parts = rgba[1]!.split(/[\s,/]+/).filter(Boolean);
        return parts.length < 4 || parseFloat(parts[3]!) > 0;
    }
    return !/\/\s*0(\.0+)?\s*\)$/.test(color);
}

/** 요소 자체가 잉크인가 — 아이콘·그림 요소, 또는 바탕·테두리·그림자가 보일 때 */
function paints(el: Element, cs: CSSStyleDeclaration): boolean {
    const tag = el.tagName;
    if (tag === 'I' || tag === 'IMG' || tag === 'SVG' || tag === 'svg' || tag === 'VIDEO' || tag === 'CANVAS') return true;
    if (visibleColor(cs.backgroundColor) || (cs.backgroundImage && cs.backgroundImage !== 'none')) return true;
    if (cs.boxShadow && cs.boxShadow !== 'none') return true;
    const sides = ['Top', 'Right', 'Bottom', 'Left'] as const;
    return sides.some((s) => num(cs.getPropertyValue(`border-${s.toLowerCase()}-width`)) > 0 && visibleColor(cs.getPropertyValue(`border-${s.toLowerCase()}-color`)));
}

/** 글자 줄 상자 → 글리프 윗선·아랫선 근사(em 상자를 줄 상자 가운데에 두고 위 14% · 아래 10% 를 뺀다) */
function textInk(rect: DOMRect, fontSize: number): { top: number; bottom: number } {
    const emTop = rect.top + (rect.height - fontSize) / 2;
    return { top: emTop + fontSize * 0.14, bottom: emTop + fontSize * 0.9 };
}

/** 요소 안 잉크의 위·아래 끝(뷰포트 px) — 없으면 null */
function inkOf(root: Element): { top: number; bottom: number } | null {
    let top = Number.POSITIVE_INFINITY;
    let bottom = Number.NEGATIVE_INFINITY;
    const add = (t: number, b: number) => {
        if (b - t <= 0) return;
        top = Math.min(top, t);
        bottom = Math.max(bottom, b);
    };
    const visit = (el: Element) => {
        const cs = getComputedStyle(el);
        if (cs.display === 'none' || cs.visibility === 'hidden' || cs.opacity === '0') return;
        const rect = el.getBoundingClientRect();
        if (rect.width <= 1 || rect.height <= 1) return; // 크기 0·sr-only
        if (paints(el, cs)) {
            add(rect.top, rect.bottom);
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
                    const ink = textInk(r, fontSize);
                    add(ink.top, ink.bottom);
                }
            } else if (node.nodeType === Node.ELEMENT_NODE) {
                visit(node as Element);
            }
        }
    };
    visit(root);
    return Number.isFinite(top) ? { top, bottom } : null;
}
