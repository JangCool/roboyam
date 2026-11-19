import type { Font } from 'opentype.js';
import type { MenuNameArt } from '~/composables/menu/api/useMenuApi.types';
import UtilMenuNameArt from '~/utils/util-menu-name-art';
import type { NameArtGlyph } from '~/utils/util-menu-name-art';

/**
 * 메뉴 이름 그림 만들기 — 「이름 모양 › 로고 글꼴 그림」(achu-page-contract, 사용자 2026-11-19 「관리자 화면에서」).
 * 이름에 든 글자가 속한 개구체(Gaegu 700) 조각 woff 만 받아 opentype.js 로 윤곽을 읽고 패스로 굳힌다 — 공개 사이트는 글꼴 없이 그 패스만 그린다.
 * 조각 목록(`700.css`)·받은 글꼴·opentype.js 는 처음 쓸 때 한 번만 받는다(이 창을 열고 그림을 고를 때만 — 관리자 첫 화면에 싣지 않는다).
 * 글꼴에 없는 글자가 있으면 null(화면이 「그림으로 만들 수 없는 글자」를 알린다).
 */

/** 조각 woff 주소 — 파일 이름(`gaegu-N-700-normal`) → 받기 */
const WOFF = import.meta.glob<string>('#gaegu-font/files/gaegu-*-700-normal.woff', { query: '?url', import: 'default' });

type Slices = ReturnType<typeof UtilMenuNameArt.parseSlices>;

let slicesLoad: Promise<Slices> | null = null;
let parser: Promise<(buffer: ArrayBuffer) => Font> | null = null;
const fonts = new Map<string, Promise<Font>>();

function slices(): Promise<Slices> {
    slicesLoad ??= import('#gaegu-font/700.css?raw').then((m) => UtilMenuNameArt.parseSlices(m.default));
    return slicesLoad;
}

function parse(): Promise<(buffer: ArrayBuffer) => Font> {
    parser ??= import('opentype.js').then((m) => m.parse ?? m.default.parse);
    return parser;
}

function font(file: string): Promise<Font> {
    let hit = fonts.get(file);
    if (!hit) {
        const load = Object.entries(WOFF).find(([path]) => path.endsWith(`/${file}.woff`))?.[1];
        if (!load) return Promise.reject(new Error(`no font slice ${file}`));
        hit = Promise.all([load(), parse()])
            .then(([url, read]) => fetch(url).then((res) => (res.ok ? res.arrayBuffer() : Promise.reject(new Error(`font ${res.status}`)))).then(read))
            .catch((e) => {
                fonts.delete(file);
                throw e;
            });
        fonts.set(file, hit);
    }
    return hit;
}

/**
 * 이름 → 그림. 글꼴에 없는 글자가 있으면 null, 받기에 실패하면 예외
 * @param {string} name - 메뉴 이름(앞뒤 공백은 뺀다).
 * @returns {Promise<MenuNameArt | null>} 그림.
 */
async function make(name: string): Promise<MenuNameArt | null> {
    const chars = Array.from(name.trim());
    if (chars.length === 0) return null;
    const list = await slices();
    const files = chars.map((ch) => UtilMenuNameArt.sliceOf(list, ch));
    if (files.some((f) => f === null)) return null;
    const glyphs: NameArtGlyph[] = [];
    for (const [i, ch] of chars.entries()) {
        const ft = await font(files[i] as string);
        if (ft.charToGlyphIndex(ch) <= 0) return null;
        const g = ft.charToGlyph(ch);
        glyphs.push({ commands: g.getPath(0, 0, UtilMenuNameArt.SIZE).commands, advance: ((g.advanceWidth ?? 0) / ft.unitsPerEm) * UtilMenuNameArt.SIZE });
    }
    return UtilMenuNameArt.compose(glyphs);
}

export function useMenuNameArt() {
    return { make };
}
