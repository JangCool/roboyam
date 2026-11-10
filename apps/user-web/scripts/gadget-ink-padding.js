/*
 * 가젯 카드의 보이는 여백(잉크 기준) 확인 — 브라우저 콘솔용, 빌드에 포함되지 않는다. 잉크 규칙은 `app/composables/gadget/useGadgetInk.ts` 와 같다.
 *
 * 쓰는 법: 홈 `/` · `/p/{key}` · 레이아웃이 켜진 `/boards/{slug}`(관리자 미리보기 iframe 안도 됨)에서 이 파일 내용을 콘솔에 붙여 넣는다.
 *          가젯 카드마다 표가 나오고, 다시 재려면 `gadgetInkPadding()` 을 부른다.
 * 판정: 위·아래·왼쪽이 좌우 기준 ±2px. 넘침(카드 아래 − 칸 아래)은 0, 늘린 빈 곳(`data-grid-stretched`)만큼 아래 여백이 큰 것은 정상.
 *       빈 상태 안내(`data-grid-float`)는 「안내 위」 = 「안내 아래」 ±2px.
 */
(function () {
    const TOL = 2;

    const num = (v) => parseFloat(v) || 0;

    function visibleColor(color) {
        if (!color || color === 'transparent') return false;
        const rgba = /rgba?\(([^)]+)\)/.exec(color);
        if (rgba) {
            const parts = rgba[1].split(/[\s,/]+/).filter(Boolean);
            return parts.length < 4 || parseFloat(parts[3]) > 0;
        }
        return !/\/\s*0(\.0+)?\s*\)$/.test(color);
    }

    function paints(el, cs) {
        const tag = el.tagName.toUpperCase();
        if (['I', 'IMG', 'SVG', 'VIDEO', 'CANVAS'].includes(tag)) return true;
        if (visibleColor(cs.backgroundColor) || (cs.backgroundImage && cs.backgroundImage !== 'none')) return true;
        if (cs.boxShadow && cs.boxShadow !== 'none') return true;
        return ['top', 'right', 'bottom', 'left'].some((s) => num(cs.getPropertyValue(`border-${s}-width`)) > 0 && visibleColor(cs.getPropertyValue(`border-${s}-color`)));
    }

    /** 요소 안 잉크의 끝 — ink(글자 근사) · raw(글자 줄 상자 그대로). withSelf 면 그 요소 자신의 바탕·테두리도 잉크(카드는 빼고 잰다) */
    function measure(card, withSelf) {
        const ink = { top: Infinity, bottom: -Infinity, left: Infinity, right: -Infinity };
        const raw = { top: Infinity, bottom: -Infinity };
        const add = (box, r) => {
            box.top = Math.min(box.top, r.top);
            box.bottom = Math.max(box.bottom, r.bottom);
            if ('left' in box) {
                box.left = Math.min(box.left, r.left);
                box.right = Math.max(box.right, r.right);
            }
        };
        const visit = (el, isCard) => {
            const cs = getComputedStyle(el);
            if (cs.display === 'none' || cs.visibility === 'hidden' || cs.opacity === '0') return;
            const rect = el.getBoundingClientRect();
            if (rect.width <= 1 || rect.height <= 1) return;
            if (!isCard && paints(el, cs)) {
                add(ink, rect);
                add(raw, rect);
                if (['I', 'IMG', 'SVG'].includes(el.tagName.toUpperCase())) return;
            }
            for (const node of el.childNodes) {
                if (node.nodeType === Node.TEXT_NODE) {
                    if (!node.textContent || !node.textContent.trim()) continue;
                    const range = document.createRange();
                    range.selectNodeContents(node);
                    const f = num(cs.fontSize);
                    for (const r of range.getClientRects()) {
                        if (r.width <= 0 || r.height <= 0) continue;
                        add(raw, r);
                        const emTop = r.top + (r.height - f) / 2;
                        add(ink, { top: emTop + f * 0.14, bottom: emTop + f * 0.9, left: r.left, right: r.right });
                    }
                } else if (node.nodeType === Node.ELEMENT_NODE) {
                    visit(node, false);
                }
            }
        };
        visit(card, !withSelf);
        return { ink, raw };
    }

    function inkAbove(card, target) {
        let bottom = null;
        let node = target;
        while (node && node !== card) {
            for (let prev = node.previousElementSibling; prev; prev = prev.previousElementSibling) {
                const ink = measure(prev, true).ink;
                if (Number.isFinite(ink.bottom)) bottom = bottom === null ? ink.bottom : Math.max(bottom, ink.bottom);
            }
            node = node.parentElement;
        }
        return bottom;
    }

    function report() {
        const cards = document.querySelectorAll('section.gadget-card[data-testid="home-gadget"], [data-testid="home-board-card"].gadget-card');
        const rows = [];
        for (const card of cards) {
            const cs = getComputedStyle(card);
            const r = card.getBoundingClientRect();
            const inner = {
                top: r.top + num(cs.borderTopWidth),
                bottom: r.bottom - num(cs.borderBottomWidth),
                left: r.left + num(cs.borderLeftWidth),
                right: r.right - num(cs.borderRightWidth),
            };
            const { ink, raw } = measure(card);
            if (!Number.isFinite(ink.top)) continue;
            const base = num(cs.paddingLeft);
            const row = {
                종류: card.dataset.gadget || 'BOARD_CARD(안쪽)',
                제목: (card.querySelector('[data-testid="home-gadget-title"]')?.textContent || card.getAttribute('aria-label') || '').trim().slice(0, 24),
                기준: Math.round(base),
                위: Math.round(ink.top - inner.top),
                아래: Math.round(inner.bottom - ink.bottom),
                왼쪽: Math.round(ink.left - inner.left),
                오른쪽: Math.round(inner.right - ink.right),
                '위(원값)': Math.round(raw.top - inner.top),
                '아래(원값)': Math.round(inner.bottom - raw.bottom),
                '늘린 빈 곳': card.closest('[data-grid-stretched]')?.getAttribute('data-grid-stretched') ?? '',
                넘침: card.parentElement?.hasAttribute('data-grid-height') ? Math.round(r.bottom - card.parentElement.getBoundingClientRect().bottom) : '',
            };
            const empty = Array.from(card.querySelectorAll('[data-grid-float]')).find((el) => getComputedStyle(el).display !== 'none');
            let emptyOff = false;
            if (empty) {
                const e = measure(empty, true).ink;
                const above = inkAbove(card, empty);
                const ref = above === null ? r.top : above;
                const up = Math.round(e.top - ref);
                const down = Math.round(r.bottom - e.bottom);
                row['안내 위'] = up;
                row['안내 아래'] = down;
                if (Math.abs(up - down) <= TOL) row['빈 상태'] = '통과';
                else {
                    row['빈 상태'] = `✗ 위−아래 ${up - down}`;
                    emptyOff = true;
                }
            }
            const keys = empty ? ['위', '왼쪽'] : ['위', '아래', '왼쪽'];
            const stretched = Number(row['늘린 빈 곳']) || 0;
            const off = keys.filter((k) => Math.abs(row[k] - (k === '아래' ? stretched : 0) - base) > TOL);
            if (emptyOff) off.push('빈 상태');
            if (typeof row.넘침 === 'number' && row.넘침 > 1) off.push('넘침');
            row.벗어남 = off.length ? `✗ ${off.join('·')}` : '';
            rows.push(row);
        }
        console.table(rows);
        return rows;
    }

    window.gadgetInkPadding = report;
    report();
})();
