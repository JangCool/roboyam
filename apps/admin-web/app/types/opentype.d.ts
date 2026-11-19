/** opentype.js 1.3.4 — 메뉴 이름 그림(`composables/menu/useMenuNameArt.ts`)이 쓰는 부분만 적은 타입(패키지에 타입이 없다) */
declare module 'opentype.js' {
    export interface PathCommand {
        type: 'M' | 'L' | 'Q' | 'C' | 'Z';
        x?: number;
        y?: number;
        x1?: number;
        y1?: number;
        x2?: number;
        y2?: number;
    }
    export interface Path {
        commands: PathCommand[];
    }
    export interface Glyph {
        advanceWidth?: number;
        getPath(x: number, y: number, fontSize: number): Path;
    }
    export interface Font {
        unitsPerEm: number;
        charToGlyphIndex(char: string): number;
        charToGlyph(char: string): Glyph;
    }
    export function parse(buffer: ArrayBuffer): Font;
    const opentype: { parse: typeof parse };
    export default opentype;
}
