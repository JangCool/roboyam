import type { Ref } from 'vue';

/**
 * 가젯 카드의 **눈에 보이는 여백**(잉크 기준) 맞추기 — 사용자 결정 2026-10-31: 보이는 첫 요소의 윗선 ↔ 카드 위, 마지막 보이는 요소의 아랫선 ↔ 카드 아래가 좌우 여백(테마 `--gadget-card-pad-x`)과 같아야 한다(±2px).
 * CSS 상자로만 맞추면 마지막 줄의 보이지 않는 여유(투명 버튼 터치 영역 · 글자 반행간 · 빈 상태 블록의 아래 안 여백 …)가 아래 여백에 더해진다. 그래서 카드의 첫·마지막 자식에
 * 위·아래 바깥 여백을 직접 걸어(음수 가능 — 터치 영역은 그대로 두고 보이는 글리프만 여백선에 맞춘다) 「카드 안 여백 + 그 자식 여백 + 자식 안의 보이지 않는 여유 = 좌우 여백」이 되게 한다.
 *
 *  - 잉크: 글자 = 텍스트 노드 Range 의 줄 상자를 글자 크기로 근사한 글리프 윗선·아랫선(줄 상자 가운데에 em 상자, 그 위 14% · 아래 10% 를 뺀다 — 한글·숫자 윗선/밑선 근사).
 *    아이콘(i)·svg·img·video·canvas = 요소 상자. 그 밖의 요소는 바탕·테두리·그림자가 보일 때만 상자(투명 버튼·줄 상자는 잉크가 아니다).
 *    `visibility:hidden`·`display:none`·투명(opacity 0)·크기 1px 이하(sr-only)는 뺀다.
 *  - 기준 잉크는 **위치로** 고른다(2026-10-31): 카드 안 모든 잉크 중 화면상 가장 위·가장 아래(DOM 마지막 자식을 따라가지 않는다 — 세로로 먼저 채우는 두 열 목록은 DOM 끝이 오른쪽 열 끝이라
 *    긴 왼쪽 열 끝 줄을 놓쳤다). 지금 보이는 여백을 재서 첫·마지막 자식 바깥 여백을 그 차이만큼 옮긴다. 칸이 커서 늘어난 빈 곳(카드 높이 − 본래 높이 `naturalHeight`)은
 *    가장 아래 잉크가 그 빈 곳 위에 있을 때만(밀린 줄·「더보기」 줄이 아니면) 빼고 잰다 — 그래서 격자 높이 재기와 되먹임이 없고, 최소 상태에서 카드 = 내용이다.
 *  - 빈 상태 안내(`data-grid-float` — 공통 빈 상태 · 인기글 기간 빈 문구 · 잠김 안내, 정본 2026-10-31): 「안내 바로 위에 보이는 마지막 잉크 밑선(머리 제목·기간 탭 칩 …, 없으면 카드 위 테두리)
 *    → 안내 윗선」(A) = 「안내 밑선 → 카드 아래 테두리」(B). 최소 상태에서는 카드 아래 여백(세는 여백 — 마지막 자식 아래 바깥 여백)을 B = T = max(A0, 좌우 여백) 이 되게 잡아
 *    빈 상태 카드의 최소 높이를 정하고(A0 = 띄움 여백 없을 때의 A), 칸에 여유가 있으면 그 여유를 안내 위·아래 바깥 여백으로 나눠 A = B 를 지킨다.
 *    격자 높이 재기와 아래 맞추기는 띄움 여백을 세지 않는다(남는 공간이라 — 세면 상자가 줄어들지 못한다).
 *  - 카드 모양이 없는 가젯(평평 `FLAT`)은 하지 않는다(보이는 상자가 없다). 높이·DOM·글꼴이 바뀌면(ResizeObserver — 카드와 늘어나는 상자 안 요소 · MutationObserver · 글꼴 로드) 다음 그리기에 다시 맞춘다.
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

    /** 카드의 자식, 늘어나는 상자 안은 그 자식까지 지켜본다(늘어난 상자는 안 내용이 바뀌어도 자기 크기가 그대로다) */
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
        // 지난번에 건 자식 중 이번에 첫·마지막이 아닌 것은 먼저 되돌린다(그 뒤에 잰다)
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
        // 칸 높이(격자가 정한 값). 칸에 남는 높이가 없으면(최소 상태 — 칸이 줄어든 직후 포함) 지난 상태에서 건 띄움 여백을 재기 전에 바로 없앤다
        // (남겨 두면 카드가 칸보다 길어져 넘치고, 그 길이로 잰 아래 여백이 틀어진다)
        const slot = slotHeight(el);
        if (float && (slot === null || slot - naturalHeight(el) <= 0.5)) {
            setPx(float, 'marginTop', null);
            setPx(float, 'marginBottom', null);
        }

        // 화면상 가장 위·가장 아래 잉크(카드 안 모든 글자 줄 상자 근사·보이는 요소 상자 중 — DOM 순서가 아니라 위치로. 두 열 목록의 긴 열 끝 줄 등)
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
        // 칸이 커서 늘어난 빈 곳 — **격자가 정한 칸 높이** − 본래 높이(띄운 안내 여백·밀린 줄 위 여백 제외). 카드 자신의 높이로 재면 안 된다:
        // 띄움 여백이 남아 카드가 칸보다 길어지면(넘침) 그 넘친 만큼이 다시 「남는 높이」로 잡혀 여백이 줄지 않는 되먹임이 난다(2026-10-31 — 영상·짤·소모임 빈 상태)
        const extra = slot === null ? 0 : Math.max(0, slot - naturalHeight(el));

        // 위 — 보이는 위 여백(카드 위 안쪽 → 가장 위 잉크, 띄운 안내가 맨 위면 그 띄움 여백 제외)이 좌우 여백이 되게 첫 자식 위 바깥 여백을 그만큼 옮긴다
        const floatAbove = float && ink.topEl && float.contains(ink.topEl) ? num(float.style.marginTop) : 0;
        const gapTop = ink.top - (rect.top + borderT) - floatAbove;
        const top = num(getComputedStyle(first).marginTop) + (padX - gapTop);

        // 아래 — 가장 아래 잉크 밑선 → 카드 아래까지에서 늘어난 빈 곳을 뺀다: 띄운 안내 안이면 그 아래 띄움 여백, 밀린 줄(쪽 넘김)·본문 뒤 줄(「더보기」)이면 0(빈 곳은 그 위), 그 밖은 늘어난 빈 곳 전부(아래에 있다)
        const owner = ink.bottomEl;
        let slack = extra;
        // 띄운 안내 안이면 그 아래의 빈 곳 = 늘어난 빈 곳 − 안내 위 띄움 여백(아직 띄움 여백을 나누기 전이면 빈 곳은 모두 안내 아래 몸통에 있다 —
        // 예전처럼 「안내 아래 띄움 여백」만 빼면 첫 번에 빈 곳 전부를 보이는 여백으로 잘못 보고 아래를 그만큼 당겨, 본래 높이가 줄고 빈 곳이 다시 커지는 되먹임이 났다)
        if (float && owner && float.contains(owner)) slack = Math.max(0, extra - num(float.style.marginTop));
        else if (owner && (owner.closest('[data-grid-push]') || !insideBody(el, owner))) slack = 0;
        // 빈 상태 안내 — 「안내 바로 위 마지막 잉크 밑선 → 안내 윗선」(A) = 「안내 밑선 → 카드 아래 테두리」(B). 최소 상태 기준 T = max(A0, 좌우 여백)
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

        // 칸에 여유가 있으면 띄움 여백으로 나눠 A = B — 위 = (E + T − A0)/2, 아래 = 나머지(격자 높이 재기는 이 여백을 세지 않는다 — 되먹임 없음)
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
        // 글꼴이 늦게 오면 줄 상자(글리프 근사)가 바뀌는데 카드 크기는 그대로일 수 있다 — 글꼴 로드 뒤에 다시
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

/** 격자가 정한 칸 높이(px — 칸 요소의 `data-grid-height`). 한 열(모바일 — 칸이 내용 높이)·격자 밖이면 null(늘어난 빈 곳 없음) */
function slotHeight(card: Element): number | null {
    const v = card.parentElement?.getAttribute('data-grid-height');
    if (v === null || v === undefined || v === '') return null;
    const n = Number(v);
    return Number.isFinite(n) ? n : null;
}

/**
 * 여백 걸기 — 계산값(소수)을 받아 정수 px 로 건다. 지금 걸린 값과 1px 미만으로만 다르면 그대로 둔다
 * (소수 측정값의 반올림이 ±1px 로 뒤집히며 측정 → 보정 → 재배치가 왕복하지 않게 — 한 번 걸린 값은 1px 이상 어긋날 때만 바뀐다).
 */
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

/** 잉크가 카드 본문(늘어나는 첫 `data-grid-fill` 자식 — 칸이 커서 늘어나는 곳) 안에 있는가 */
function insideBody(card: Element, owner: Element): boolean {
    for (const child of card.children) if (child.hasAttribute('data-grid-fill') && child.contains(owner)) return true;
    return false;
}

/**
 * 늘어나는 상자의 본래 높이(격자 `HomeGadgetGrid` 의 `naturalHeight` 와 같은 규칙) — 자식 높이 + 바깥 여백 + 줄 간격 + 안 여백·테두리.
 * 밀린 줄(`data-grid-push`)의 위 여백, 띄운 덩어리(`data-grid-float`)의 위·아래 여백은 남는 공간이라 세지 않는다.
 */
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

/** 카드 안에서 `target` 바로 위에 보이는 마지막 잉크의 밑선(뷰포트 px) — 앞선 형제들(조상의 앞선 형제 포함)의 잉크 중 가장 아래. 없으면 null(머리 없는 카드의 첫 안내) */
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

/**
 * 요소 안 잉크의 위·아래 끝(뷰포트 px)과 그 잉크의 주인 요소 — 없으면 null. `skipSelf` 면 그 요소 자신의 바탕·테두리는 잉크로 치지 않는다(카드).
 * 위치로 고른다 — 모든 하위 글자 줄 상자(글리프 근사)·보이는 요소 상자 중 가장 위·가장 아래.
 */
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
