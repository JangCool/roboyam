import type { MyNicknameImage, NicknameImageLimits, NicknameImageRequirement } from '~/composables/profile/api/useNicknameImageApi.types';

/** 서버가 거절한 갈래 — 오류 **코드**로 가른다 */
export type NicknameImageOutcome = 'REQUIREMENTS_NOT_MET' | 'REAPPLY_TOO_SOON' | 'RESTRICTED' | 'UNVERIFIED' | 'VALIDATION' | 'RATE_LIMITED' | 'TOO_LARGE' | 'NOT_FOUND' | 'NETWORK' | 'SERVER' | 'OTHER';

/** 화면 덩어리 — `layout` 의 결과 */
export interface NicknameImageLayout {
    ineligible: boolean;
    current: boolean;
    request: 'PENDING' | 'REJECTED' | null;
    form: boolean;
    startable: boolean;
    waitSeconds: number;
}

/** 올리기 전에 화면이 알아낸 문제의 코드 — 문구는 `UtilNicknameImageMessages.problem` */
export type NicknameImageProblemCode = 'FORMAT' | 'EMPTY' | 'TOO_LARGE' | 'UNREADABLE' | 'RATIO';

const KB = 1024;
const MIME_BY_EXT: Record<string, string> = { png: 'image/png', jpg: 'image/jpeg', jpeg: 'image/jpeg', gif: 'image/gif', webp: 'image/webp' };
const ALLOWED_MIME = new Set(Object.values(MIME_BY_EXT));

function extensionOf(name: string): string {
    const dot = name.lastIndexOf('.');
    return dot < 0 ? '' : name.slice(dot + 1).toLowerCase();
}

/**
 * 닉네임 이미지 계산 — 올리기 전 검사(형식·용량·4:1)·재신청까지 남은 시간·화면 덩어리·서버 오류 갈래(community `UtilNicknameImage`, 변경권 규칙은 뺐다).
 * 규칙의 출처는 서버다(서버 후보 `NicknameImagePolicy`) — 화면은 거절당할 것을 보내기 전에 알려 줄 뿐이고, GIF 프레임 수·깨진 파일은 서버만 안다.
 */
export default class UtilNicknameImage {
    static readonly DEFAULT_MAX_BYTES = 200 * KB;
    static readonly DEFAULT_WIDTH = 240;
    static readonly DEFAULT_HEIGHT = 60;
    static readonly RATIO_TOLERANCE = 0.01;
    static readonly DEFAULT_REAPPLY_HOURS = 72;
    static readonly ACCEPT = 'image/png,image/jpeg,image/gif,image/webp';

    /**
     * 파일 형식 — 브라우저가 못 알려 주면 확장자로.
     * @param {Pick<File, 'name' | 'type'>} file - 파일.
     * @returns {string} MIME. 모르면 빈 문자열.
     */
    static mimeOf(file: Pick<File, 'name' | 'type'>): string {
        return file.type.toLowerCase() || MIME_BY_EXT[extensionOf(file.name)] || '';
    }

    /** GIF 인가 */
    static isGif(file: Pick<File, 'name' | 'type'>): boolean {
        return UtilNicknameImage.mimeOf(file) === 'image/gif';
    }

    /**
     * 형식·용량 검사 — 문제가 없으면 null.
     * @param {Pick<File, 'name' | 'type' | 'size'>} file - 파일.
     * @param {Pick<NicknameImageLimits, 'maxBytes'> | null} limits - 한도.
     * @returns {NicknameImageProblemCode | null} 문제.
     */
    static precheck(file: Pick<File, 'name' | 'type' | 'size'>, limits?: Pick<NicknameImageLimits, 'maxBytes'> | null): NicknameImageProblemCode | null {
        if (!ALLOWED_MIME.has(UtilNicknameImage.mimeOf(file))) return 'FORMAT';
        if (file.size <= 0) return 'EMPTY';
        if (file.size > (limits?.maxBytes ?? UtilNicknameImage.DEFAULT_MAX_BYTES)) return 'TOO_LARGE';
        return null;
    }

    /**
     * 가로세로가 4:1(오차 1%)인가.
     * @param {number} width - 가로.
     * @param {number} height - 세로.
     * @param {Pick<NicknameImageLimits, 'width' | 'height'> | null} limits - 저장 크기.
     * @returns {boolean} 맞으면 true.
     */
    static isRatioOk(width: number, height: number, limits?: Pick<NicknameImageLimits, 'width' | 'height'> | null): boolean {
        if (!Number.isFinite(width) || !Number.isFinite(height) || width <= 0 || height <= 0) return false;
        const target = (limits?.width ?? UtilNicknameImage.DEFAULT_WIDTH) / (limits?.height ?? UtilNicknameImage.DEFAULT_HEIGHT);
        return Math.abs(width / height - target) / target <= UtilNicknameImage.RATIO_TOLERANCE;
    }

    /**
     * 반려 뒤 재신청 대기 시간(시간) — 1 이상의 정수면 그 값, 아니면 72.
     * @param {unknown} raw - `limits.rejectedReapplyHours`.
     * @returns {number} 시간.
     */
    static reapplyHoursOf(raw: unknown): number {
        return typeof raw === 'number' && Number.isInteger(raw) && raw > 0 ? raw : UtilNicknameImage.DEFAULT_REAPPLY_HOURS;
    }

    /**
     * 재신청 가능 시각까지 남은 초.
     * @param {string | null | undefined} at - 가능 시각.
     * @param {number} now - 지금.
     * @returns {number} 초(0 이상).
     */
    static waitSeconds(at: string | null | undefined, now: number = Date.now()): number {
        const ms = at ? Date.parse(at) : Number.NaN;
        return Number.isNaN(ms) ? 0 : Math.max(0, Math.ceil((ms - now) / 1000));
    }

    /**
     * 신청 조건에 이 종류가 켜져 있나 — 이메일 인증 막음·등급 안내 링크를 서버가 요구할 때만 보인다.
     * @param {MyNicknameImage} state - 서버 상태.
     * @param {NicknameImageRequirement['kind']} kind - 조건 종류.
     * @returns {boolean} 켜져 있으면 true.
     */
    static requires(state: MyNicknameImage, kind: NicknameImageRequirement['kind']): boolean {
        return state.requirements.items.some((item) => item.kind === kind);
    }

    /**
     * 화면 덩어리 — 적용 중 · 대기/반려 · 신청 칸 · 열기 단추 · 재신청 대기.
     *  - 대기 중이면 신청 칸이 없다. 자격이 없으면 신청 칸이 없다(적용 중인 이미지는 그대로 보인다).
     *  - 신청한 적도 적용된 적도 없으면 칸이 바로 열려 있다. 적용 중·반려면 단추를 눌러야 열린다.
     * @param {MyNicknameImage} state - 서버 상태.
     * @param {boolean} opened - 신청 칸을 열었나.
     * @param {number} now - 지금.
     * @returns {NicknameImageLayout} 덩어리.
     */
    static layout(state: MyNicknameImage, opened: boolean, now: number = Date.now()): NicknameImageLayout {
        const request = state.request?.status ?? null;
        const current = state.current !== null;
        const canApply = state.eligible && request !== 'PENDING';
        const needsOpen = current || request === 'REJECTED';
        const form = canApply && (opened || !needsOpen);
        const wait = UtilNicknameImage.waitSeconds(request === 'REJECTED' ? state.request?.canReapplyAt : state.reapplyAt, now);
        return { ineligible: !state.eligible, current, request, form, startable: canApply && needsOpen && !opened, waitSeconds: wait };
    }

    /**
     * 서버가 거절·실패한 갈래 — 코드로 가른다.
     * @param {unknown} error - 잡은 오류.
     * @returns {NicknameImageOutcome} 갈래.
     */
    static outcomeOf(error: unknown): NicknameImageOutcome {
        const e = (typeof error === 'object' && error !== null ? error : {}) as { status?: unknown; code?: unknown };
        const status = typeof e.status === 'number' ? e.status : 0;
        const code = typeof e.code === 'string' ? e.code : '';
        if (code === 'NICKNAME_IMAGE_REQUIREMENTS_NOT_MET') return 'REQUIREMENTS_NOT_MET';
        if (code === 'NICKNAME_IMAGE_REAPPLY_TOO_SOON') return 'REAPPLY_TOO_SOON';
        if (code === 'ACCOUNT_RESTRICTED') return 'RESTRICTED';
        if (code === 'EMAIL_NOT_VERIFIED') return 'UNVERIFIED';
        if (code === 'NICKNAME_IMAGE_REQUEST_NOT_FOUND' || code === 'NICKNAME_IMAGE_NOT_FOUND') return 'NOT_FOUND';
        if (code === 'VALIDATION') return 'VALIDATION';
        if (code === 'RATE_LIMITED' || code === 'DAILY_LIMIT_REACHED' || status === 429) return 'RATE_LIMITED';
        if (status === 413) return 'TOO_LARGE';
        if (status === 0) return 'NETWORK';
        if (status >= 500) return 'SERVER';
        return 'OTHER';
    }

    /** 서버 상태를 다시 받아야 하는 갈래 — 화면이 아는 것과 서버가 어긋났다 */
    static needsReload(outcome: NicknameImageOutcome): boolean {
        return outcome === 'REQUIREMENTS_NOT_MET' || outcome === 'REAPPLY_TOO_SOON' || outcome === 'NOT_FOUND';
    }

    /** 막힌 까닭을 굳혀 둘 갈래 — 정지 중·이메일 인증 전 */
    static blockedReasonOf(outcome: NicknameImageOutcome): 'ACCOUNT_RESTRICTED' | 'EMAIL_NOT_VERIFIED' | null {
        if (outcome === 'RESTRICTED') return 'ACCOUNT_RESTRICTED';
        if (outcome === 'UNVERIFIED') return 'EMAIL_NOT_VERIFIED';
        return null;
    }

    /**
     * 용량 글자 — "180KB" · "1.2MB".
     * @param {number} bytes - 바이트.
     * @returns {string} 글자.
     */
    static formatBytes(bytes: number): string {
        if (!Number.isFinite(bytes) || bytes < 0) return '';
        if (bytes < KB) return `${bytes}B`;
        if (bytes < KB * KB) return `${Math.round(bytes / KB)}KB`;
        return `${(bytes / KB / KB).toFixed(1)}MB`;
    }
}
