import type { MenuNameArt } from '~/composables/menu/api/useMenuApi.types';

/** 글자 윤곽 명령 하나(opentype.js `Path.commands` 의 모양) */
export interface NameArtCommand {
    type: string;
    x?: number;
    y?: number;
    x1?: number;
    y1?: number;
    x2?: number;
    y2?: number;
}

/** 한 글자 — 윤곽 명령(글자 크기 `SIZE` 기준, 기준선 y=0)과 다음 글자까지의 폭 */
export interface NameArtGlyph {
    commands: NameArtCommand[];
    advance: number;
}

/**
 * 메뉴 이름 그림(「이름 모양 › 로고 글꼴 그림」, achu-page-contract)의 순수 계산 — 글꼴 조각 고르기(unicode-range 풀기)와 윤곽 → 패스.
 * 글꼴을 받고 읽는 일은 `useMenuNameArt` 가 한다. `robotikon/logo/build.mjs`(캐릭터 로고)와 같은 방식이다.
 */
export default class UtilMenuNameArt {
    /** 윤곽을 뽑는 글자 크기(viewBox 단위) */
    static readonly SIZE = 100;
    /**
     * 공개 메뉴에서 그릴 높이 = 잉크 높이(글자 크기 배수) × 이 값 — 「아추!」(잉크 0.672em)를 옆 메뉴 글자와 맞춘 0.9em 에서 잡았다(사용자 2026-11-19).
     * 글자마다 높이가 달라도 같은 글자 크기로 보이게 비율로 둔다
     */
    static readonly HEIGHT_SCALE = 0.9 / 0.672;
    /** 같은 색 테두리 두께(viewBox 단위) — 사용자 2026-11-19 「좀 두껍다」로 4 → 2 */
    static readonly STROKE = 2;

    /**
     * 글꼴 CSS(`@fontsource/gaegu/700.css`)의 `@font-face` 들 → 조각 파일 이름과 unicode-range
     * @param {string} css - CSS 글.
     * @returns {{ file: string; ranges: [number, number][] }[]} 조각 목록(파일 이름은 `gaegu-N-700-normal`, 확장자 없음).
     */
    static parseSlices(css: string): { file: string; ranges: [number, number][] }[] {
        const out: { file: string; ranges: [number, number][] }[] = [];
        for (const block of css.split('@font-face').slice(1)) {
            const file = /files\/([\w-]+)\.woff2?/.exec(block)?.[1];
            const range = /unicode-range:\s*([^;]+);/.exec(block)?.[1];
            if (!file || !range) continue;
            const ranges = range.split(',').map((part): [number, number] => {
                const [a, b] = part.trim().replace(/^U\+/i, '').split('-');
                const from = parseInt(a ?? '', 16);
                return [from, b ? parseInt(b, 16) : from];
            });
            out.push({ file, ranges: ranges.filter(([a, b]) => Number.isFinite(a) && Number.isFinite(b)) });
        }
        return out;
    }

    /**
     * 이 글자가 든 조각 파일 이름 — 없으면 null
     * @param {{ file: string; ranges: [number, number][] }[]} slices - 조각 목록.
     * @param {string} char - 글자 하나.
     * @returns {string | null} 파일 이름.
     */
    static sliceOf(slices: { file: string; ranges: [number, number][] }[], char: string): string | null {
        const code = char.codePointAt(0) ?? -1;
        return slices.find((s) => s.ranges.some(([a, b]) => code >= a && code <= b))?.file ?? null;
    }

    /**
     * 글자들을 옆으로 이어 그린 패스 — 잉크 상자의 왼쪽 위를 (0,0)으로 옮기고 소수 한 자리. 그릴 잉크가 없으면 null
     * @param {NameArtGlyph[]} glyphs - 글자들(차례대로, 각자 x=0 기준 윤곽).
     * @returns {MenuNameArt | null} 이름 그림.
     */
    static compose(glyphs: NameArtGlyph[]): MenuNameArt | null {
        const placed: NameArtCommand[] = [];
        let x = 0;
        for (const g of glyphs) {
            for (const c of g.commands) placed.push({ ...c, x: shift(c.x, x), x1: shift(c.x1, x), x2: shift(c.x2, x) });
            x += g.advance;
        }
        let x1 = Infinity;
        let y1 = Infinity;
        let x2 = -Infinity;
        let y2 = -Infinity;
        for (const c of placed) {
            for (const [px, py] of [[c.x, c.y], [c.x1, c.y1], [c.x2, c.y2]] as const) {
                if (px === undefined || py === undefined) continue;
                x1 = Math.min(x1, px);
                x2 = Math.max(x2, px);
                y1 = Math.min(y1, py);
                y2 = Math.max(y2, py);
            }
        }
        if (!Number.isFinite(x1) || x2 <= x1 || y2 <= y1) return null;
        const n = (v: number) => String(+v.toFixed(1));
        const d = placed
            .map((c) => {
                if (c.type === 'Z') return 'Z';
                const pts = c.type === 'Q' ? [c.x1, c.y1, c.x, c.y] : c.type === 'C' ? [c.x1, c.y1, c.x2, c.y2, c.x, c.y] : [c.x, c.y];
                return c.type + pts.map((v, i) => n((v ?? 0) - (i % 2 === 0 ? x1 : y1))).join(' ');
            })
            .join('');
        const h = y2 - y1;
        return { d, w: +(x2 - x1).toFixed(1), h: +h.toFixed(1), em: +((h / UtilMenuNameArt.SIZE) * UtilMenuNameArt.HEIGHT_SCALE).toFixed(3), sw: UtilMenuNameArt.STROKE };
    }
}

function shift(v: number | undefined, dx: number): number | undefined {
    return v === undefined ? undefined : v + dx;
}
