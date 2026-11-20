/**
 * 짤 올리기 창이 쓰는 작은 GIF 라이브러리의 타입 — 두 라이브러리 모두 타입을 싣지 않아 쓰는 만큼만 적는다(`utils/util-jjal-image.ts` 만 쓴다, 올리기 창을 열 때만 받는다).
 * gifenc 1.0.3(GIF 만들기 — https://github.com/mattdesl/gifenc) · omggif 1.0.10(GIF 풀기 — https://github.com/deanm/omggif).
 */

declare module 'gifenc' {
    /** 팔레트 — 색 하나가 [r, g, b] 또는 [r, g, b, a] */
    export type GifPalette = number[][];
    export type GifColorFormat = 'rgb565' | 'rgb444' | 'rgba4444';

    export interface GifFrameOptions {
        palette?: GifPalette;
        /** 이 프레임을 보이는 시간(ms) */
        delay?: number;
        transparent?: boolean;
        transparentIndex?: number;
        /** -1 = 한 번, 0 = 끝없이, n = n번 */
        repeat?: number;
        /** 다음 프레임 전에 할 일 — -1 지정 안 함 · 1 그대로 둠 · 2 바탕으로 지움 · 3 앞 상태로 */
        dispose?: number;
        colorDepth?: number;
        first?: boolean;
    }

    export interface GifEncoderInstance {
        writeFrame(index: Uint8Array, width: number, height: number, opts?: GifFrameOptions): void;
        finish(): void;
        bytes(): Uint8Array<ArrayBuffer>;
    }

    export function GIFEncoder(opt?: { initialCapacity?: number; auto?: boolean }): GifEncoderInstance;
    export function quantize(
        rgba: Uint8Array | Uint8ClampedArray,
        maxColors: number,
        opts?: { format?: GifColorFormat; oneBitAlpha?: boolean | number; clearAlpha?: boolean; clearAlphaThreshold?: number; clearAlphaColor?: number },
    ): GifPalette;
    export function applyPalette(rgba: Uint8Array | Uint8ClampedArray, palette: GifPalette, format?: GifColorFormat): Uint8Array;
    export default GIFEncoder;
}

declare module 'omggif' {
    export interface GifFrameInfo {
        x: number;
        y: number;
        width: number;
        height: number;
        has_local_palette: boolean;
        palette_offset: number | null;
        palette_size: number | null;
        data_offset: number;
        data_length: number;
        transparent_index: number | null;
        interlaced: boolean;
        /** 1/100초 단위 */
        delay: number;
        /** 0·1 그대로 둠 · 2 바탕으로 지움 · 3 앞 상태로 */
        disposal: number;
    }

    export class GifReader {
        constructor(buf: Uint8Array);
        width: number;
        height: number;
        numFrames(): number;
        loopCount(): number | null;
        frameInfo(frameNum: number): GifFrameInfo;
        decodeAndBlitFrameRGBA(frameNum: number, pixels: Uint8Array | Uint8ClampedArray): void;
    }
}
