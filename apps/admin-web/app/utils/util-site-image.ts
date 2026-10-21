/**
 * 사이트 아이콘·링크 미리보기 이미지(og:image)를 **올리기 전에 브라우저가 미리 걸러 보는** 규칙 — 파일 머리(PNG 서명 + IHDR 가로·세로)만 읽어 서버와 같은 순서로 견준다. 판정은 서버다(디코딩·화소 한도·정확한 용량은 서버만 안다).
 * 서버 규칙(후보, 커뮤니티 `docs/api-site-icon.md` 와 같다): **PNG 만**(이름·MIME 이 아니라 첫 바이트로 판별) · **2MB 이하**.
 *   - 아이콘: 정사각 · 한 변 512~4000px. 검사 순서 = 빈 파일 → 형식 → 용량 → 읽기 → 너무 큼 → 정사각 아님 → 너무 작음.
 *   - 링크 미리보기 이미지: 가로 1200~4000px(세로도 4000 이하) · 비율 1200:630 ±2%(안 맞으면 **자르지 않고 거절**). 검사 순서 = 빈 파일 → 형식 → 용량 → 읽기 → 너무 큼 → 비율 안 맞음 → 너무 작음(비율이 크기 부족보다 먼저다 — 모양부터 고치게).
 * SVG 는 파일이 아니라 소스 입력으로만 받는다(아이콘만) — 그 검사는 `UtilSiteSetting.logoSvg` 를 재사용하고 정사각은 서버가 판정한다. 화면에 보이는 문구는 `util-site-image-messages.ts`.
 */

/** 걸린 까닭 — 서버가 거절할 때와 같은 갈래 */
export type SiteImageProblem = 'EMPTY' | 'FORMAT' | 'SIZE' | 'CORRUPT' | 'TOO_BIG' | 'NOT_SQUARE' | 'WRONG_RATIO' | 'TOO_SMALL';

/** 어느 줄의 규칙인가 */
export type SiteImageKind = 'icon' | 'og';

/** PNG 머리에서 읽은 가로·세로 */
export interface SiteImageDims {
    width: number;
    height: number;
}

/** 파일을 검사한 결과 — 걸렸으면 까닭, 그리고 읽었다면 가로·세로(문구에 "지금 크기"를 넣는 데 쓴다) */
export interface SiteImageInspection {
    problem: SiteImageProblem | null;
    dims: SiteImageDims | null;
}

/** PNG 서명 8바이트 */
const PNG_SIGNATURE = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a];

export default class UtilSiteImage {
    /** 최대 용량(2MB) — 두 줄 모두 같다 */
    static readonly MAX_BYTES = 2 * 1024 * 1024;
    /** 파일 머리에서 읽을 바이트 수 — 서명 8 + IHDR 청크(길이 4 + 이름 4 + 가로 4 + 세로 4)까지 */
    static readonly HEAD_BYTES = 32;
    /** 파일 선택 창의 `accept` 값 — PNG 만 */
    static readonly ACCEPT = 'image/png,.png';
    /** 큰 쪽 한 변 상한(px) — 아이콘·링크 미리보기 이미지 같다 */
    static readonly MAX_SIDE = 4000;
    /** 아이콘 한 변 하한(px) */
    static readonly ICON_MIN_SIDE = 512;
    /** 링크 미리보기 이미지 가로 하한(px)과 목표 크기 — 서버가 1200×630 으로 맞춘다 */
    static readonly OG_MIN_WIDTH = 1200;
    static readonly OG_WIDTH = 1200;
    static readonly OG_HEIGHT = 630;
    /** 링크 미리보기 이미지 비율 허용 오차(%) */
    static readonly OG_RATIO_TOLERANCE_PERCENT = 2;

    /**
     * 첫 바이트가 PNG 서명인가 — 파일 이름·MIME 은 보지 않는다.
     * @param {Uint8Array} head - 파일의 첫 바이트들.
     * @returns {boolean} PNG 서명이면 true.
     */
    static isPng(head: Uint8Array): boolean {
        return head.length >= PNG_SIGNATURE.length && PNG_SIGNATURE.every((byte, i) => head[i] === byte);
    }

    /**
     * PNG 머리(첫 청크 IHDR)에서 가로·세로를 읽는다.
     * @param {Uint8Array} head - 파일의 첫 바이트들(`HEAD_BYTES` 이상).
     * @returns {SiteImageDims | null} 가로·세로. PNG 가 아니거나 머리가 모자라거나 IHDR 이 아니면 null.
     */
    static dims(head: Uint8Array): SiteImageDims | null {
        if (!UtilSiteImage.isPng(head) || head.length < 24) return null;
        const type = String.fromCharCode(head[12]!, head[13]!, head[14]!, head[15]!);
        if (type !== 'IHDR') return null;
        const u32 = (at: number): number => ((head[at]! << 24) | (head[at + 1]! << 16) | (head[at + 2]! << 8) | head[at + 3]!) >>> 0;
        const width = u32(16);
        const height = u32(20);
        return width > 0 && height > 0 ? { width, height } : null;
    }

    /**
     * 가로:세로가 링크 미리보기 이미지 비율(1200:630)에서 허용 오차 안쪽인가.
     * @param {number} width - 가로(px).
     * @param {number} height - 세로(px).
     * @returns {boolean} 받는 비율이면 true.
     */
    static ogRatioFits(width: number, height: number): boolean {
        if (width <= 0 || height <= 0) return false;
        const target = UtilSiteImage.OG_WIDTH / UtilSiteImage.OG_HEIGHT;
        return Math.abs(width / height - target) / target <= UtilSiteImage.OG_RATIO_TOLERANCE_PERCENT / 100;
    }

    /**
     * 용량과 파일 머리만으로 규칙에 견준다 — 서버와 같은 순서.
     * @param {SiteImageKind} kind - 어느 줄의 규칙인가.
     * @param {number} size - 파일 용량(바이트).
     * @param {Uint8Array | null} head - 파일의 첫 바이트들. 못 읽었으면 null(읽기 실패).
     * @returns {SiteImageInspection} 걸린 까닭(통과면 null)과 읽은 가로·세로.
     */
    static check(kind: SiteImageKind, size: number, head: Uint8Array | null): SiteImageInspection {
        if (size <= 0) return { problem: 'EMPTY', dims: null };
        if (head && !UtilSiteImage.isPng(head)) return { problem: 'FORMAT', dims: null };
        if (size > UtilSiteImage.MAX_BYTES) return { problem: 'SIZE', dims: null };
        const dims = head ? UtilSiteImage.dims(head) : null;
        if (!dims) return { problem: 'CORRUPT', dims: null };
        if (Math.max(dims.width, dims.height) > UtilSiteImage.MAX_SIDE) return { problem: 'TOO_BIG', dims };
        if (kind === 'icon') {
            if (dims.width !== dims.height) return { problem: 'NOT_SQUARE', dims };
            if (dims.width < UtilSiteImage.ICON_MIN_SIDE) return { problem: 'TOO_SMALL', dims };
            return { problem: null, dims };
        }
        if (!UtilSiteImage.ogRatioFits(dims.width, dims.height)) return { problem: 'WRONG_RATIO', dims };
        if (dims.width < UtilSiteImage.OG_MIN_WIDTH) return { problem: 'TOO_SMALL', dims };
        return { problem: null, dims };
    }

    /**
     * 고른 파일의 머리만 읽어 규칙에 견준다 — 파일 전체를 읽지 않는다(`Blob.slice`).
     * @param {SiteImageKind} kind - 어느 줄의 규칙인가.
     * @param {File} file - 고른 파일.
     * @returns {Promise<SiteImageInspection>} 걸린 까닭(통과면 null)과 읽은 가로·세로. 머리를 못 읽으면 `CORRUPT`.
     */
    static async inspect(kind: SiteImageKind, file: File): Promise<SiteImageInspection> {
        if (file.size <= 0) return UtilSiteImage.check(kind, 0, null);
        let head: Uint8Array | null = null;
        try {
            head = new Uint8Array(await file.slice(0, UtilSiteImage.HEAD_BYTES).arrayBuffer());
        } catch {
            /* 읽지 못했다 — 아래에서 읽기 실패(CORRUPT)로 */
        }
        return UtilSiteImage.check(kind, file.size, head);
    }
}
