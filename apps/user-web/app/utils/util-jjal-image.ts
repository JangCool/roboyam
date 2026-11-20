import UtilJjal, { type JjalPrepared, type JjalPrepareProblem } from '~/utils/util-jjal';

/**
 * 짤 올리기 — 브라우저에서 줄이기(계약 `docs/features/my-jjal-contract.md` 「올리기 — 브라우저가 줄이고 서버는 검사만」, 사용자 확정 2026-11-20).
 * 긴 변 300px 로 줄여 움짤은 GIF, 정지 그림은 PNG(JPEG 원본은 JPEG)로 만든다. 서버는 다시 만들지 않고 검사만 한다.
 *   - 정지 그림(PNG·JPEG·정지 WebP·한 장짜리 GIF): `createImageBitmap` → canvas → `toBlob`.
 *   - GIF 움짤: omggif 로 프레임을 풀고(되돌리기 방식 1·2·3 반영) 줄여 gifenc 로 다시 GIF.
 *   - 움직이는 WebP: 브라우저에 `ImageDecoder` 가 있으면 프레임을 풀어 GIF, 없으면 「GIF로 올려 주세요」.
 *   - 10초·100장을 넘으면 만들기 전에 알린다(서버 규칙과 같은 값 — `UtilJjal`).
 * ⚠ 이 파일은 올리기 창(`JjalUploadDialog`)만 가져오고, 두 라이브러리는 줄일 때 `import()` 로 받는다 — 홈·글 화면 첫 로딩에 들어가지 않게(`first-load.md`).
 * 파일 형식은 이름·MIME 이 아니라 첫 바이트로 본다(서버와 같은 방식).
 */

/** 줄이기 문제 — 문구는 `UtilJjalMessages.prepare` */
export class JjalPrepareError extends Error {
    constructor(
        readonly problem: JjalPrepareProblem,
        readonly detail: { frames?: number; durationMs?: number } = {},
    ) {
        super(problem);
        this.name = 'JjalPrepareError';
    }
}

type SourceKind = 'gif' | 'png' | 'jpeg' | 'webp';

/** 줄인 프레임 하나 */
interface Frame {
    rgba: Uint8ClampedArray;
    delayMs: number;
}

interface Surface {
    canvas: HTMLCanvasElement;
    ctx: CanvasRenderingContext2D;
}

function ascii(bytes: Uint8Array, at: number, text: string): boolean {
    for (let i = 0; i < text.length; i++) if (bytes[at + i] !== text.charCodeAt(i)) return false;
    return true;
}

/** 첫 바이트로 형식을 본다 — GIF·PNG·JPEG·WebP 만 */
function sniff(bytes: Uint8Array): SourceKind | null {
    if (ascii(bytes, 0, 'GIF8')) return 'gif';
    if (bytes[0] === 0x89 && ascii(bytes, 1, 'PNG')) return 'png';
    if (bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff) return 'jpeg';
    if (ascii(bytes, 0, 'RIFF') && ascii(bytes, 8, 'WEBP')) return 'webp';
    return null;
}

/** 움직이는 WebP 인가 — 확장 머리(VP8X)의 애니메이션 표시 */
function animatedWebp(bytes: Uint8Array): boolean {
    return ascii(bytes, 12, 'VP8X') && ((bytes[20] ?? 0) & 0x02) !== 0;
}

/** 긴 변 300px 이하로(키우지는 않는다) */
function fit(width: number, height: number): { width: number; height: number } {
    const scale = Math.min(1, UtilJjal.MAX_SIDE / Math.max(width, height));
    return { width: Math.max(1, Math.round(width * scale)), height: Math.max(1, Math.round(height * scale)) };
}

function surface(width: number, height: number): Surface {
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const ctx = canvas.getContext('2d', { willReadFrequently: true });
    if (!ctx) throw new JjalPrepareError('BROKEN');
    ctx.imageSmoothingEnabled = true;
    ctx.imageSmoothingQuality = 'high';
    return { canvas, ctx };
}

function toBlob(canvas: HTMLCanvasElement, type: string, quality?: number): Promise<Blob> {
    return new Promise((resolve, reject) => canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new JjalPrepareError('BROKEN'))), type, quality));
}

/** GIF 지연(1/100초) → ms — 브라우저처럼 0·1(10ms 이하)은 100ms 로 본다 */
function gifDelayMs(centiseconds: number): number {
    return centiseconds < 2 ? 100 : centiseconds * 10;
}

/** 길이 검사 — 100장·10초 */
function checkLength(frames: number, durationMs: number): void {
    if (frames > UtilJjal.MAX_FRAMES) throw new JjalPrepareError('TOO_MANY_FRAMES', { frames, durationMs });
    if (durationMs > UtilJjal.MAX_DURATION_MS) throw new JjalPrepareError('TOO_LONG', { frames, durationMs });
}

function checkSize(blob: Blob): void {
    if (blob.size > UtilJjal.MAX_BYTES) throw new JjalPrepareError('RESULT_TOO_BIG');
}

/** 정지 그림 — 첫 장을 줄여 PNG(JPEG 원본은 JPEG) */
async function prepareStill(file: Blob, kind: SourceKind): Promise<JjalPrepared> {
    let bitmap: ImageBitmap;
    try {
        bitmap = await createImageBitmap(file);
    } catch {
        throw new JjalPrepareError('BROKEN');
    }
    const size = fit(bitmap.width, bitmap.height);
    const out = surface(size.width, size.height);
    out.ctx.drawImage(bitmap, 0, 0, size.width, size.height);
    bitmap.close();
    const jpeg = kind === 'jpeg';
    const blob = await toBlob(out.canvas, jpeg ? 'image/jpeg' : 'image/png', jpeg ? 0.9 : undefined);
    checkSize(blob);
    return { blob, filename: jpeg ? 'jjal.jpg' : 'jjal.png', width: size.width, height: size.height, animated: false, frames: 1, durationMs: 0 };
}

/** 줄인 프레임들 → GIF(프레임마다 256색 팔레트, 투명은 1비트) */
async function encodeGif(frames: Frame[], width: number, height: number): Promise<JjalPrepared> {
    const { GIFEncoder, quantize, applyPalette } = await import('gifenc');
    const gif = GIFEncoder();
    let durationMs = 0;
    for (const frame of frames) {
        const palette = quantize(frame.rgba, 256, { format: 'rgba4444', oneBitAlpha: true });
        const index = applyPalette(frame.rgba, palette, 'rgba4444');
        const transparentIndex = palette.findIndex((c) => (c[3] ?? 255) === 0);
        // 프레임마다 다 그린 한 장이라 앞 프레임을 지우고(2) 그린다 — 투명 자리에 앞 프레임이 비치지 않게
        gif.writeFrame(index, width, height, { palette, delay: frame.delayMs, transparent: transparentIndex >= 0, transparentIndex: Math.max(0, transparentIndex), repeat: 0, dispose: 2 });
        durationMs += frame.delayMs;
    }
    gif.finish();
    const blob = new Blob([gif.bytes()], { type: 'image/gif' });
    checkSize(blob);
    return { blob, filename: 'jjal.gif', width, height, animated: true, frames: frames.length, durationMs };
}

/** GIF — 한 장이면 정지 그림, 여러 장이면 프레임을 풀어 줄인 뒤 다시 GIF */
async function prepareGif(file: Blob, bytes: Uint8Array): Promise<JjalPrepared> {
    const mod = await import('omggif');
    // CJS 라이브러리 — 번들러에 따라 이름 내보내기 또는 default 안에 온다
    const Reader = mod.GifReader ?? (mod as unknown as { default: typeof mod }).default.GifReader;
    let reader: InstanceType<typeof Reader>;
    try {
        reader = new Reader(bytes);
    } catch {
        throw new JjalPrepareError('BROKEN');
    }
    const count = reader.numFrames();
    if (count < 1) throw new JjalPrepareError('BROKEN');
    if (count === 1) return prepareStill(file, 'png');
    let durationMs = 0;
    for (let i = 0; i < count; i++) durationMs += gifDelayMs(reader.frameInfo(i).delay);
    checkLength(count, durationMs);

    const w = reader.width;
    const h = reader.height;
    const size = fit(w, h);
    const src = surface(w, h);
    const out = surface(size.width, size.height);
    let buffer = new Uint8ClampedArray(w * h * 4);
    const frames: Frame[] = [];
    for (let i = 0; i < count; i++) {
        const info = reader.frameInfo(i);
        const previous = info.disposal === 3 ? buffer.slice() : null;
        try {
            reader.decodeAndBlitFrameRGBA(i, buffer);
        } catch {
            throw new JjalPrepareError('BROKEN');
        }
        src.ctx.putImageData(new ImageData(buffer.slice(), w, h), 0, 0);
        out.ctx.clearRect(0, 0, size.width, size.height);
        out.ctx.drawImage(src.canvas, 0, 0, size.width, size.height);
        frames.push({ rgba: out.ctx.getImageData(0, 0, size.width, size.height).data, delayMs: gifDelayMs(info.delay) });
        // 다음 프레임 전에 — 2 이 자리를 바탕(투명)으로 · 3 그리기 전 상태로
        if (info.disposal === 2) {
            for (let y = info.y; y < Math.min(h, info.y + info.height); y++) buffer.fill(0, (y * w + info.x) * 4, (y * w + Math.min(w, info.x + info.width)) * 4);
        } else if (previous) {
            buffer = previous;
        }
    }
    return encodeGif(frames, size.width, size.height);
}

/** 움직이는 WebP — `ImageDecoder` 로 다 그린 프레임을 받아 줄인 뒤 GIF. 못 풀면 「GIF로 올려 주세요」 */
async function prepareAnimatedWebp(file: Blob, bytes: Uint8Array<ArrayBuffer>): Promise<JjalPrepared> {
    if (typeof ImageDecoder === 'undefined') throw new JjalPrepareError('WEBP_ANIMATED_UNSUPPORTED');
    let decoder: ImageDecoder;
    try {
        if (!(await ImageDecoder.isTypeSupported('image/webp'))) throw new Error('unsupported');
        decoder = new ImageDecoder({ data: bytes, type: 'image/webp' });
        await decoder.tracks.ready;
    } catch {
        throw new JjalPrepareError('WEBP_ANIMATED_UNSUPPORTED');
    }
    try {
        const count = decoder.tracks.selectedTrack?.frameCount ?? 0;
        if (count < 1) throw new JjalPrepareError('BROKEN');
        if (count === 1) return await prepareStill(file, 'webp');
        if (count > UtilJjal.MAX_FRAMES) throw new JjalPrepareError('TOO_MANY_FRAMES', { frames: count });
        const frames: Frame[] = [];
        let out: Surface | null = null;
        let size = { width: 0, height: 0 };
        let durationMs = 0;
        for (let i = 0; i < count; i++) {
            const { image } = await decoder.decode({ frameIndex: i });
            if (!out) {
                size = fit(image.displayWidth, image.displayHeight);
                out = surface(size.width, size.height);
            }
            out.ctx.clearRect(0, 0, size.width, size.height);
            out.ctx.drawImage(image, 0, 0, size.width, size.height);
            // 길이는 마이크로초 — 없거나 너무 짧으면 100ms(GIF 와 같게)
            const delayMs = image.duration && image.duration >= 20_000 ? Math.round(image.duration / 1000) : 100;
            image.close();
            frames.push({ rgba: out.ctx.getImageData(0, 0, size.width, size.height).data, delayMs });
            durationMs += delayMs;
            if (durationMs > UtilJjal.MAX_DURATION_MS) throw new JjalPrepareError('TOO_LONG', { frames: count, durationMs });
        }
        return await encodeGif(frames, size.width, size.height);
    } catch (e) {
        if (e instanceof JjalPrepareError) throw e;
        throw new JjalPrepareError('BROKEN');
    } finally {
        decoder.close();
    }
}

export default class UtilJjalImage {
    /**
     * 고른 파일을 올릴 짤로 만든다.
     * @param {File} file - 끌어 놓거나 고른 파일.
     * @returns {Promise<JjalPrepared>} 올릴 파일과 크기.
     * @throws {JjalPrepareError} 형식·길이·크기 문제.
     */
    static async prepare(file: File): Promise<JjalPrepared> {
        if (file.size > UtilJjal.SOURCE_MAX_BYTES) throw new JjalPrepareError('SOURCE_TOO_BIG');
        const bytes = new Uint8Array(await file.arrayBuffer());
        const kind = sniff(bytes);
        if (!kind) throw new JjalPrepareError('UNSUPPORTED');
        if (kind === 'gif') return prepareGif(file, bytes);
        if (kind === 'webp' && animatedWebp(bytes)) return prepareAnimatedWebp(file, bytes);
        return prepareStill(file, kind);
    }
}
