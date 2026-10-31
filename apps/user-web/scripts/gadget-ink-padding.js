/*
 * 홈 가젯 「눈에 보이는 여백」(잉크 기준) 확인 스크립트 — 개발자가 브라우저 콘솔에 붙여 넣어 쓴다. 빌드에 포함되지 않는다(앱 코드가 아니다).
 *
 * 쓰는 법: 공개 화면(홈 `/` · 가젯 페이지 `/p/{key}` · 게시판 레이아웃이 켜진 `/boards/{slug}`, 관리자 미리보기 iframe 안도 됨)을 열고
 *          DevTools 콘솔에 이 파일 내용을 통째로 붙여 넣는다 → 가젯 카드(`section.gadget-card[data-testid="home-gadget"]`)마다 표가 나온다.
 *          게시판 카드 묶음의 안쪽 카드(`article.gadget-card`)도 함께 잰다. 다시 재려면 `gadgetInkPadding()` 을 부른다.
 *
 * 표의 칸: 종류(data-gadget) · 제목 · 좌우 기준(카드 안 왼쪽 여백 px) · 위/아래/왼쪽/오른쪽(잉크 — 글자 근사) · 위/아래(원값 — 글자 줄 상자 그대로) · 벗어남(±2px 를 넘는 칸).
 *   - 위   = 카드 위 테두리 안쪽 → 보이는 첫 요소 윗선      아래 = 보이는 마지막 요소 아랫선 → 카드 아래 테두리 안쪽
 *   - 왼쪽·오른쪽 = 카드 옆 테두리 안쪽 → 가장 바깥 잉크(오른쪽은 글이 짧으면 크게 나온다 — 줄이 끝까지 차지 않을 뿐이라 벗어남 판정은 위·아래·왼쪽만)
 *
 * 잉크 규칙(앱의 `app/composables/home/useGadgetInk.ts` 와 같다):
 *   - 글자: 텍스트 노드 Range 의 `getClientRects`(줄 상자). 원값은 그 상자 그대로, 잉크값은 em 상자(글자 크기)를 줄 상자 가운데에 두고 위 14% · 아래 10% 를 뺀 근사
 *     (한글·숫자의 윗선/밑선이 em 상자 안쪽에 그만큼 들어와 있다 — 글꼴마다 ±1px 남짓 다를 수 있다).
 *   - 아이콘(i)·svg·img·video·canvas: 요소 상자. 그 밖의 요소는 바탕색·바탕 그림·테두리·그림자가 보일 때만 그 상자(점·칩·사진 틀 등). 투명 상자(버튼 터치 영역 등)는 잉크가 아니다.
 *   - 뺀다: display:none · visibility:hidden · opacity 0 · 너비나 높이 1px 이하(sr-only 포함).
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

    /** 카드 안 잉크의 끝 — ink(글자 근사) · raw(글자 줄 상자 그대로) */
    function measure(card) {
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
        visit(card, true);
        return { ink, raw };
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
            };
            const off = ['위', '아래', '왼쪽'].filter((k) => Math.abs(row[k] - base) > TOL);
            row.벗어남 = off.length ? `✗ ${off.join('·')}` : '';
            rows.push(row);
        }
        console.table(rows);
        return rows;
    }

    window.gadgetInkPadding = report;
    report();
})();
