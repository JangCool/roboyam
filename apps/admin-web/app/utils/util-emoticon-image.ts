import type { EmoticonImageFormat } from '~/composables/emoticon/api/useEmoticonAdminApi.types';

/**
 * 로보티콘 그림 파일 읽기 — 파일 바이트에서 형식·크기·프레임 수·무한 반복을 읽는다(서버 업로드 검사 후보 `EmoticonImageValidator` 와 같은 읽기).
 * user-web `utils/util-emoticon-spec.ts` 의 읽기와 같은 방식이다(두 앱이 같은 값을 보게 — 바꿀 때 함께):
 *  · GIF: 머리(논리 화면 크기) → 전체 색표 → 블록을 따라간다. 확장 블록(`0x21`)은 부분 블록을 건너뛰며 NETSCAPE2.0 이면 반복 횟수를 읽고,
 *    이미지 블록(`0x2C`)을 하나씩 센다(그림 데이터 속 같은 값은 부분 블록째 건너뛰므로 세지 않는다). 끝 표식(`0x3B`) 뒤 바이트는 읽지 않는다.
 *  · PNG: 서명 + IHDR 의 가로·세로.
 * 순수 함수 — 상태·요청이 없다.
 */

/** 파일에서 읽은 그림 정보 */
export interface EmoticonImageInfo {
    format: EmoticonImageFormat;
    width: number;
    height: number;
    /** GIF 프레임 수(PNG 는 1) */
    frames: number;
    /** GIF 무한 반복(NETSCAPE2.0 반복 0) — 한 프레임 GIF·PNG 는 참 */
    loopForever: boolean;
}

function u16le(b: Uint8Array, at: number): number {
    return (b[at] ?? 0) | ((b[at + 1] ?? 0) << 8);
}

function u32be(b: Uint8Array, at: number): number {
    return (((b[at] ?? 0) << 24) | ((b[at + 1] ?? 0) << 16) | ((b[at + 2] ?? 0) << 8) | (b[at + 3] ?? 0)) >>> 0;
}

/** GIF 부분 블록 묶음(크기 바이트 + 데이터 … 0)을 건너뛴 다음 자리. 끝을 넘으면 -1 */
function skipSubBlocks(b: Uint8Array, at: number): number {
    let i = at;
    while (i < b.length) {
        const size = b[i] ?? 0;
        i += 1;
        if (size === 0) return i;
        i += size;
    }
    return -1;
}

function inspectGif(b: Uint8Array): EmoticonImageInfo | null {
    if (b.length < 13) return null;
    const width = u16le(b, 6);
    const height = u16le(b, 8);
    const packed = b[10] ?? 0;
    let i = 13;
    if (packed & 0x80) i += 3 * (1 << ((packed & 0x07) + 1)); // 전체 색표
    let frames = 0;
    let loopCount: number | null = null;
    while (i < b.length) {
        const marker = b[i];
        if (marker === 0x3b) break; // 끝
        if (marker === 0x21) {
            const label = b[i + 1];
            const size = b[i + 2] ?? 0;
            if (label === 0xff && size === 11) {
                const id = String.fromCharCode(...b.slice(i + 3, i + 14));
                if (id === 'NETSCAPE2.0' && b[i + 14] === 3 && b[i + 15] === 1) loopCount = u16le(b, i + 16);
            }
            const next = skipSubBlocks(b, i + 2);
            if (next < 0) break;
            i = next;
            continue;
        }
        if (marker === 0x2c) {
            frames += 1;
            const localPacked = b[i + 9] ?? 0;
            i += 10;
            if (localPacked & 0x80) i += 3 * (1 << ((localPacked & 0x07) + 1));
            i += 1; // LZW 최소 코드 크기
            const next = skipSubBlocks(b, i);
            if (next < 0) break;
            i = next;
            continue;
        }
        break; // 모르는 표식 — 깨진 파일
    }
    if (frames === 0) return null;
    return { format: 'GIF', width, height, frames, loopForever: frames <= 1 || loopCount === 0 };
}

function inspectPng(b: Uint8Array): EmoticonImageInfo | null {
    // 서명 8바이트 + IHDR(길이 4 · "IHDR" 4 · 가로 4 · 세로 4)
    if (b.length < 24 || String.fromCharCode(...b.slice(12, 16)) !== 'IHDR') return null;
    return { format: 'PNG', width: u32be(b, 16), height: u32be(b, 20), frames: 1, loopForever: true };
}

export default class UtilEmoticonImage {
    /** 로보티콘 그림 파일 고르기 */
    static readonly ACCEPT = 'image/gif,image/png';
    /** 대표 이미지 파일 고르기 */
    static readonly COVER_ACCEPT = 'image/png';

    /**
     * 파일 바이트에서 형식·크기·프레임을 읽는다. 확장자·MIME 이 아니라 바이트로 가른다. GIF·PNG 가 아니거나 깨졌으면 null.
     * @param {Uint8Array} bytes - 파일 내용.
     * @returns {EmoticonImageInfo | null} 그림 정보.
     */
    static inspect(bytes: Uint8Array): EmoticonImageInfo | null {
        const head = String.fromCharCode(...bytes.slice(0, 6));
        if (head === 'GIF87a' || head === 'GIF89a') return inspectGif(bytes);
        if (bytes[0] === 0x89 && bytes[1] === 0x50 && bytes[2] === 0x4e && bytes[3] === 0x47) return inspectPng(bytes);
        return null;
    }
}
