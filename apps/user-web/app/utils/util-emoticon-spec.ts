import type { EmoticonRules } from '~/composables/emoticon/api/useEmoticonApi.types';
import type { EmoticonImageFormat } from '~/composables/emoticon/api/useEmoticonStudioApi.types';

/**
 * 이모티콘 팩 규격 검사 — 제작 가이드와 업로드 화면이 같은 값(`EmoticonRules`, 계약 가정 4)을 쓴다. 판정은 서버다(후보 `EmoticonImageValidator`·`EmoticonPackValidator`) —
 * 화면은 올리기 전에 미리 알린다. 그림은 파일 바이트를 훑어 읽는다(GIF 는 블록을 따라가며 이미지 블록 `0x2C` 를 센다 — 그림 데이터 속 같은 값은 건너뛴다, PNG 는 IHDR).
 * 순수 함수 — 상태·요청이 없다.
 */

/** 파일에서 읽은 그림 정보 */
export interface EmoticonImageInfo {
    format: EmoticonImageFormat;
    width: number;
    height: number;
    /** GIF 프레임 수(PNG 는 1) */
    frames: number;
    /** GIF 무한 반복(NETSCAPE2.0 반복 0) — 한 프레임 GIF·PNG 는 참으로 본다 */
    loopForever: boolean;
}

/** 문제 하나 — `warn` 은 올릴 수 있지만 알려 주는 것(권장 용량 넘음) */
export type EmoticonSpecCode = 'TYPE' | 'DIMENSION' | 'TOO_LARGE' | 'TOO_MANY_FRAMES' | 'NOT_LOOP' | 'HEAVY' | 'COVER_TYPE';

export interface EmoticonSpecProblem {
    code: EmoticonSpecCode;
    severity: 'error' | 'warn';
}

/** 팩 칸의 문제 */
export type EmoticonFieldProblem = 'REQUIRED' | 'TOO_SHORT' | 'TOO_LONG' | 'PATTERN' | 'TOO_MANY' | 'TOO_FEW' | 'DUPLICATE';

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
            // 확장 블록 — NETSCAPE2.0 이면 반복 횟수를 읽는다
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
            // 이미지 블록 — 머리 10바이트 · (지역 색표) · LZW 최소 코드 크기 1바이트 · 데이터 부분 블록
            frames += 1;
            const localPacked = b[i + 9] ?? 0;
            i += 10;
            if (localPacked & 0x80) i += 3 * (1 << ((localPacked & 0x07) + 1));
            i += 1;
            const next = skipSubBlocks(b, i);
            if (next < 0) break;
            i = next;
            continue;
        }
        break; // 모르는 표식 — 깨진 파일
    }
    return { format: 'GIF', width, height, frames: Math.max(frames, 1), loopForever: frames <= 1 || loopCount === 0 };
}

function inspectPng(b: Uint8Array): EmoticonImageInfo | null {
    // 서명 8바이트 + IHDR(길이 4 · "IHDR" 4 · 가로 4 · 세로 4)
    if (b.length < 24 || String.fromCharCode(...b.slice(12, 16)) !== 'IHDR') return null;
    return { format: 'PNG', width: u32be(b, 16), height: u32be(b, 20), frames: 1, loopForever: true };
}

export default class UtilEmoticonSpec {
    /** 올릴 수 있는 형식(파일 고르기 창) */
    static readonly ACCEPT = 'image/gif,image/png';
    /** 대표 이미지 형식 */
    static readonly COVER_ACCEPT = 'image/png';
    /** 팩 이름 — 한글·영문(숫자·띄어쓰기 허용) */
    static readonly NAME_PATTERN = /^[0-9A-Za-z가-힣ㄱ-ㅎㅏ-ㅣ ]+$/;

    /**
     * 파일 바이트에서 형식·크기·프레임을 읽는다. GIF·PNG 가 아니거나 깨졌으면 null.
     * @param {Uint8Array} bytes - 파일 내용.
     * @returns {EmoticonImageInfo | null} 그림 정보.
     */
    static inspect(bytes: Uint8Array): EmoticonImageInfo | null {
        const head = String.fromCharCode(...bytes.slice(0, 6));
        if (head === 'GIF87a' || head === 'GIF89a') return inspectGif(bytes);
        if (bytes[0] === 0x89 && bytes[1] === 0x50 && bytes[2] === 0x4e && bytes[3] === 0x47) return inspectPng(bytes);
        return null;
    }

    /**
     * 이모티콘 그림 한 개의 문제 — 형식(GIF/PNG) · 정확히 200×200 · 용량(2MB, 권장 300KB) · GIF 24프레임 이하 · 무한 반복.
     * @param {EmoticonImageInfo | null} info - `inspect` 결과.
     * @param {number} size - 파일 바이트.
     * @param {EmoticonRules} rules - 규격.
     * @returns {EmoticonSpecProblem[]} 문제들(없으면 빈 배열).
     */
    static imageProblems(info: EmoticonImageInfo | null, size: number, rules: EmoticonRules): EmoticonSpecProblem[] {
        if (!info) return [{ code: 'TYPE', severity: 'error' }];
        const out: EmoticonSpecProblem[] = [];
        if (info.width !== rules.imageSize || info.height !== rules.imageSize) out.push({ code: 'DIMENSION', severity: 'error' });
        if (size > rules.maxBytes) out.push({ code: 'TOO_LARGE', severity: 'error' });
        if (info.format === 'GIF' && info.frames > rules.maxFrames) out.push({ code: 'TOO_MANY_FRAMES', severity: 'error' });
        if (info.format === 'GIF' && !info.loopForever) out.push({ code: 'NOT_LOOP', severity: 'error' });
        if (size <= rules.maxBytes && size > rules.recommendedBytes) out.push({ code: 'HEAVY', severity: 'warn' });
        return out;
    }

    /**
     * 대표 이미지의 문제 — 200×200 PNG · 1MB 이하.
     * @param {EmoticonImageInfo | null} info - `inspect` 결과.
     * @param {number} size - 파일 바이트.
     * @param {EmoticonRules} rules - 규격.
     * @returns {EmoticonSpecProblem[]} 문제들.
     */
    static coverProblems(info: EmoticonImageInfo | null, size: number, rules: EmoticonRules): EmoticonSpecProblem[] {
        if (!info || info.format !== 'PNG') return [{ code: 'COVER_TYPE', severity: 'error' }];
        const out: EmoticonSpecProblem[] = [];
        if (info.width !== rules.imageSize || info.height !== rules.imageSize) out.push({ code: 'DIMENSION', severity: 'error' });
        if (size > rules.coverMaxBytes) out.push({ code: 'TOO_LARGE', severity: 'error' });
        return out;
    }

    /** 막는 문제가 있는가 */
    static blocking(problems: EmoticonSpecProblem[]): boolean {
        return problems.some((p) => p.severity === 'error');
    }

    /**
     * 팩 이름의 문제 — 2~20자, 한글·영문.
     * @param {string} name - 이름.
     * @param {EmoticonRules} rules - 규격.
     * @returns {EmoticonFieldProblem | null} 문제(없으면 null).
     */
    static nameProblem(name: string, rules: EmoticonRules): EmoticonFieldProblem | null {
        const value = name.trim();
        if (!value) return 'REQUIRED';
        if (value.length < rules.nameMin) return 'TOO_SHORT';
        if (value.length > rules.nameMax) return 'TOO_LONG';
        if (!UtilEmoticonSpec.NAME_PATTERN.test(value)) return 'PATTERN';
        return null;
    }

    /** 이름표의 문제 — 1~10자 */
    static labelProblem(label: string, rules: EmoticonRules): EmoticonFieldProblem | null {
        const value = label.trim();
        if (!value) return 'REQUIRED';
        if (value.length > rules.labelMax) return 'TOO_LONG';
        return null;
    }

    /** 태그 목록의 문제 — 5개 이하, 한 개 10자 이하, 겹침 없음 */
    static tagsProblem(tags: string[], rules: EmoticonRules): EmoticonFieldProblem | null {
        if (tags.length > rules.maxTags) return 'TOO_MANY';
        if (tags.some((t) => t.length > rules.tagMax)) return 'TOO_LONG';
        if (new Set(tags).size !== tags.length) return 'DUPLICATE';
        return null;
    }

    /** 팩 개수의 문제 — 8~40개 */
    static countProblem(count: number, rules: EmoticonRules): EmoticonFieldProblem | null {
        if (count < rules.minCount) return 'TOO_FEW';
        if (count > rules.maxCount) return 'TOO_MANY';
        return null;
    }

    /**
     * 쉼표·띄어쓰기로 적은 태그 글자를 목록으로(앞의 `#` 는 뗀다, 빈 것 제외).
     * @param {string} text - 입력 글자.
     * @returns {string[]} 태그들.
     */
    static parseTags(text: string): string[] {
        return text
            .split(/[,\s]+/)
            .map((t) => t.replace(/^#+/, '').trim())
            .filter(Boolean);
    }

    /**
     * 바이트를 KB·MB 글자로(소수 한 자리까지).
     * @param {number} bytes - 바이트.
     * @returns {string} 예: `300KB`, `2MB`, `1.4MB`.
     */
    static bytes(bytes: number): string {
        if (bytes >= 1024 * 1024) return `${Math.round((bytes / (1024 * 1024)) * 10) / 10}MB`;
        return `${Math.max(1, Math.round(bytes / 1024))}KB`;
    }
}
