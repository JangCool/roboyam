import type { AvatarUploadLimits } from '~/composables/avatar/api/useAvatarUploadApi.types';

/**
 * 아바타 올리기 규칙 — 화면이 미리 거르는 것(판정은 서버). 상태도 요청도 없다.
 * 한도는 서버 `GET /api/me/avatar-upload` 의 `limits`(서버 `AvatarUploadRules`)를 먼저 쓰고, 아직 못 받았으면 아래 기본값(서버와 같은 값 — 계약 「화면 확정 때 굳힌 것」)으로 본다:
 * PNG·JPG·WebP(정지)·GIF(정지·움직임), 300KB 이하, 정사각형(1:1, ±2%), 96×96 이상. 움직이는 WebP 는 받지 않는다(움직임은 GIF 로만). GIF 장면 수(30 이하)는 서버가 센다.
 */
const KB = 1024;

/** 화면이 알아낸 문제 — 글자는 `avatarUpload.problem.<code>` */
export type AvatarUploadProblem = 'FORMAT' | 'EMPTY' | 'TOO_LARGE' | 'UNREADABLE' | 'RATIO' | 'TOO_SMALL' | 'ANIMATED_WEBP';

/** 서버 형식 이름 → MIME */
const MIME_BY_FORMAT: Readonly<Record<string, string>> = { PNG: 'image/png', JPG: 'image/jpeg', JPEG: 'image/jpeg', GIF: 'image/gif', WEBP: 'image/webp' };

export default class UtilAvatarUpload {
    static readonly MAX_BYTES = 300 * KB;
    static readonly MIN_SIZE = 96;
    static readonly RATIO_TOLERANCE = 0.02;
    static readonly ACCEPT = 'image/png,image/jpeg,image/gif,image/webp';
    private static readonly TYPES = ['image/png', 'image/jpeg', 'image/gif', 'image/webp'];

    /**
     * 받는 MIME — 서버 `formats` 가 있으면 그것, 없으면 기본 넷
     * @param {Pick<AvatarUploadLimits, 'formats'> | null | undefined} limits - 서버 한도.
     * @returns {string[]} MIME 목록.
     */
    static mimesOf(limits?: Pick<AvatarUploadLimits, 'formats'> | null): string[] {
        const mapped = (limits?.formats ?? []).map((f) => MIME_BY_FORMAT[f.toUpperCase()]).filter((m): m is string => Boolean(m));
        return mapped.length ? [...new Set(mapped)] : UtilAvatarUpload.TYPES;
    }

    /**
     * 파일 고르기 `accept` 값
     * @param {Pick<AvatarUploadLimits, 'formats'> | null | undefined} limits - 서버 한도.
     * @returns {string} `image/png,image/jpeg,…`.
     */
    static acceptOf(limits?: Pick<AvatarUploadLimits, 'formats'> | null): string {
        return UtilAvatarUpload.mimesOf(limits).join(',');
    }

    /**
     * 용량 한도(바이트) — 서버 값 우선
     * @param {Pick<AvatarUploadLimits, 'maxBytes'> | null | undefined} limits - 서버 한도.
     * @returns {number} 바이트.
     */
    static maxBytesOf(limits?: Pick<AvatarUploadLimits, 'maxBytes'> | null): number {
        return limits?.maxBytes && limits.maxBytes > 0 ? limits.maxBytes : UtilAvatarUpload.MAX_BYTES;
    }

    /**
     * 가장 짧은 변의 최소(px) — 서버 값 우선
     * @param {Pick<AvatarUploadLimits, 'minSize'> | null | undefined} limits - 서버 한도.
     * @returns {number} px.
     */
    static minSizeOf(limits?: Pick<AvatarUploadLimits, 'minSize'> | null): number {
        return limits?.minSize && limits.minSize > 0 ? limits.minSize : UtilAvatarUpload.MIN_SIZE;
    }

    /**
     * 읽기 전에 거른다 — 형식·빈 파일·크기
     * @param {Pick<File, 'type' | 'size'>} file - 고른 파일.
     * @param {AvatarUploadLimits | null} [limits] - 서버 한도(없으면 기본값).
     * @returns {AvatarUploadProblem | null} 문제(없으면 null).
     */
    static precheck(file: Pick<File, 'type' | 'size'>, limits?: AvatarUploadLimits | null): AvatarUploadProblem | null {
        if (!UtilAvatarUpload.mimesOf(limits).includes(file.type)) return 'FORMAT';
        if (file.size === 0) return 'EMPTY';
        if (file.size > UtilAvatarUpload.maxBytesOf(limits)) return 'TOO_LARGE';
        return null;
    }

    /**
     * 그림 크기를 거른다 — 정사각형(서버 허용 오차) · 최소 크기
     * @param {number} width - 가로(px).
     * @param {number} height - 세로(px).
     * @param {AvatarUploadLimits | null} [limits] - 서버 한도(없으면 기본값).
     * @returns {AvatarUploadProblem | null} 문제(없으면 null).
     */
    static checkSize(width: number, height: number, limits?: AvatarUploadLimits | null): AvatarUploadProblem | null {
        if (width <= 0 || height <= 0) return 'UNREADABLE';
        const tolerance = limits && limits.ratioTolerancePercent >= 0 ? limits.ratioTolerancePercent / 100 : UtilAvatarUpload.RATIO_TOLERANCE;
        if (Math.abs(width / height - 1) > tolerance) return 'RATIO';
        if (Math.min(width, height) < UtilAvatarUpload.minSizeOf(limits)) return 'TOO_SMALL';
        return null;
    }

    /**
     * 움직이는 GIF 인가 — 그래픽 제어 확장(21 F9 04)이 두 번 이상이면 여러 장면으로 본다(화면의 어림 — 서버가 다시 센다)
     * @param {Uint8Array} bytes - 파일 내용.
     * @returns {boolean} 움직이면 true.
     */
    static isAnimatedGif(bytes: Uint8Array): boolean {
        let found = 0;
        for (let i = 0; i < bytes.length - 2; i++) {
            if (bytes[i] === 0x21 && bytes[i + 1] === 0xf9 && bytes[i + 2] === 0x04 && ++found > 1) return true;
        }
        return false;
    }

    /**
     * 움직이는 WebP 인가 — 확장 머리(VP8X)의 애니메이션 표시(flags 0x02). 서버 `NicknameImageProcessor.animatedWebp` 와 같은 판정
     * @param {Uint8Array} bytes - 파일 내용.
     * @returns {boolean} 움직이면 true.
     */
    static isAnimatedWebp(bytes: Uint8Array): boolean {
        const tag = (at: number) => String.fromCharCode(bytes[at] ?? 0, bytes[at + 1] ?? 0, bytes[at + 2] ?? 0, bytes[at + 3] ?? 0);
        return bytes.length > 21 && tag(0) === 'RIFF' && tag(8) === 'WEBP' && tag(12) === 'VP8X' && ((bytes[20] ?? 0) & 0x02) !== 0;
    }
}
