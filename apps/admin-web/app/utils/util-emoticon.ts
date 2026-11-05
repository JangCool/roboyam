import type {
    EmoticonCoverView,
    EmoticonItemView,
    EmoticonPackRules,
    EmoticonPackStatus,
    EmoticonPackVersionView,
    EmoticonReviewStatus,
    EmoticonSettlementStatus,
} from '~/composables/emoticon/api/useEmoticonAdminApi.types';

/**
 * 로보티콘 운영 화면의 순수 규칙 — 서버 규칙을 **미리 알려 주는 용도**이고 판정은 서버다(`forms.md`).
 * 규격 값은 계약 `emoticon-contract.md` 「목업 가정」 4(로보얌콘 기준)이고 user-web 제작 가이드·업로드 검사와 같은 값이어야 한다.
 * 서버 규칙 이름(서버 단계에 만든다 — 후보): 업로드 검사 `EmoticonImageValidator` · 신청 검사 `EmoticonSubmissionService` · 설정 `emoticon_settings` CHECK.
 */

/** 규격 검사 결과 하나 — `error` 는 규격을 어긴 것, `warn` 은 권장을 넘은 것 */
export type EmoticonSpecLevel = 'error' | 'warn';
export type EmoticonSpecCode =
    | 'SIZE'
    | 'FORMAT'
    | 'FRAMES'
    | 'LOOP'
    | 'BYTES_MAX'
    | 'BYTES_RECOMMENDED'
    | 'ALT_LENGTH'
    | 'COUNT'
    | 'COVER_SIZE'
    | 'COVER_FORMAT'
    | 'COVER_BYTES'
    | 'NAME_LENGTH'
    | 'TAGS'
    | 'PAID_OFF';

export interface EmoticonSpecIssue {
    code: EmoticonSpecCode;
    level: EmoticonSpecLevel;
    /** 로보티콘 하나의 문제면 그 아이디, 팩 전체면 null */
    itemId: string | null;
    /** 문구 자리 값(실제 값 등) */
    params: Record<string, string | number>;
}

/** 새 판 비교 표시 */
export type EmoticonDiffMark = 'added' | 'changedImage' | 'changedAlt';

export interface EmoticonVersionDiff {
    added: EmoticonItemView[];
    removed: EmoticonItemView[];
    /** 같은 아이디인데 그림(`contentHash`)이 바뀐 것 */
    changedImage: EmoticonItemView[];
    /** 같은 아이디·같은 그림인데 이름표만 바뀐 것 — 옛 이름표를 같이 준다 */
    changedAlt: { item: EmoticonItemView; before: string }[];
    nameChanged: boolean;
    descriptionChanged: boolean;
    tagsChanged: boolean;
    priceChanged: boolean;
    coverChanged: boolean;
    orderChanged: boolean;
    /** 로보티콘 아이디 → 표시 */
    marks: Record<string, EmoticonDiffMark>;
}

/** 정산 은행 하나 — 이름은 고유 이름이라 번역 파일 대신 영문명을 함께 둔다(`useLocalizedName`) */
export interface EmoticonBank {
    code: string;
    name: string;
    nameEn: string;
}

/**
 * 정산 은행(서버 `emoticon_payout_accounts` 은행 CHECK 11가지 · user-web `UtilEmoticon.banks()` 와 같은 코드) — 원문 보기 응답의 `bank`(코드)를 이름으로 바꾼다.
 * 두 앱이 같은 목록을 따로 들고 있다(공유 레이어로 올리지 않았다 — 은행이 바뀌면 두 곳·서버 CHECK 를 같이 고친다)
 */
const BANKS: readonly EmoticonBank[] = Object.freeze([
    { code: 'KB', name: '국민', nameEn: 'KB Kookmin' },
    { code: 'SHINHAN', name: '신한', nameEn: 'Shinhan' },
    { code: 'WOORI', name: '우리', nameEn: 'Woori' },
    { code: 'HANA', name: '하나', nameEn: 'Hana' },
    { code: 'NH', name: '농협', nameEn: 'NongHyup' },
    { code: 'IBK', name: '기업', nameEn: 'IBK' },
    { code: 'KAKAO', name: '카카오뱅크', nameEn: 'KakaoBank' },
    { code: 'TOSS', name: '토스뱅크', nameEn: 'Toss Bank' },
    { code: 'KBANK', name: '케이뱅크', nameEn: 'K bank' },
    { code: 'SC', name: 'SC제일', nameEn: 'SC First' },
    { code: 'POST', name: '우체국', nameEn: 'Korea Post' },
]);

/** 반려·판매 중단 사유의 문제 */
export type EmoticonReasonProblem = 'REQUIRED' | 'TOO_SHORT' | 'TOO_LONG';

export default class UtilEmoticon {
    /**
     * 가격 바꾸기(`POST …/packs/{id}/price`, S2)가 서버에 있는가 — S2 서버가 붙어 true(팩 서랍의 [가격 바꾸기]를 보인다). 다음에 손댈 때 이 상수와 쓰는 곳의 조건을 지워도 된다
     */
    static readonly PRICE_CHANGE_READY = true;

    /**
     * 은행 코드 → 은행(모르면 null).
     * @param {string | null | undefined} code - 은행 코드(`KB` …).
     * @returns {EmoticonBank | null} 은행.
     */
    static bank(code: string | null | undefined): EmoticonBank | null {
        return BANKS.find((b) => b.code === code) ?? null;
    }

    /**
     * 고정 규격 — 설정에서 바꾸지 않는다(그리기 크기 · 제작 가이드 문구 · 업로드 검사와 묶여 있어 바꾸면 이미 승인된 팩이 규격을 어기게 된다).
     * 팩당 개수(`itemMin`·`itemMax`)는 설정 칸이라 여기 없고 `EmoticonPackRules` 로 받는다.
     */
    static readonly SPEC = {
        size: 200,
        /** 그림 190 + 둘레 5px 투명 */
        artSize: 190,
        padding: 5,
        formats: ['GIF', 'PNG'] as const,
        maxFrames: 24,
        maxBytes: 2 * 1024 * 1024,
        recommendedBytes: 300 * 1024,
        coverMaxBytes: 1024 * 1024,
        nameMin: 2,
        nameMax: 20,
        altMin: 1,
        altMax: 10,
        tagsMax: 5,
        /** 표시 크기 — 글·댓글 100, 고르기 창 64 */
        displaySize: 100,
        pickerSize: 64,
    } as const;

    /** 심사 서랍의 표시 크기 토글 */
    static readonly PREVIEW_SIZES = [100, 80, 64] as const;

    /** 사유 5~200자(반려·판매 중단 — 작가에게 그대로 보인다) */
    static readonly REASON = { min: 5, max: 200 } as const;

    /** 설정 칸 범위 — 서버 CHECK 와 같게(후보) */
    static readonly SETTING_RANGES = {
        rewardPerAcquire: { min: 0, max: 1000 },
        rewardDailyCap: { min: 1, max: 100_000 },
        itemMin: { min: 1, max: 40 },
        itemMax: { min: 8, max: 60 },
        pendingLimit: { min: 1, max: 10 },
        feeRatePercent: { min: 0, max: 90 },
        settlementDay: { min: 1, max: 28 },
        payoutMinimum: { min: 0, max: 1_000_000 },
        refundDays: { min: 1, max: 30 },
    } as const;

    /** 가격 단계 한 칸 — 100원 단위, 100~100,000원, 1~10단계(서버 CHECK 후보) */
    static readonly PRICE_TIER = { min: 100, max: 100_000, unit: 100, maxCount: 10 } as const;

    /** 개인 작가 원천징수율(사업소득 3% + 지방소득세 0.3%) — 법이 정한 값이라 설정이 아니다 */
    static readonly WITHHOLDING_PERCENT = 3.3;

    /** 부가세 포함 판매액을 나누는 값 — 사이트가 판매자라 부가세(10%)는 사이트가 낸다 */
    static readonly VAT_DIVISOR = 1.1;

    /**
     * 정산 한 건 계산 — 설정 화면의 예시 줄만 쓴다(정산 금액은 서버 `EmoticonSettlementMath` 가 계산해 준다 — 같은 규칙):
     *  정산 기준 = ⌊(판매 − 환불) ÷ 1.1⌋ · 부가세 = (판매 − 환불) − 정산 기준 · 작가 몫 = ⌊정산 기준 × (1 − 수수료율)⌋ · 수수료 = 정산 기준 − 작가 몫.
     *  예: 2,500원 한 건 → 정산 기준 2,272원(부가세 228원) → 작가 몫 1,590원(수수료 30% 682원).
     */
    static creatorShare(netSales: number, feeRatePercent: number): { baseAmount: number; vatAmount: number; shareAmount: number; feeAmount: number } {
        const baseAmount = Math.floor((netSales * 10) / 11);
        const shareAmount = Math.floor((baseAmount * (100 - feeRatePercent)) / 100);
        return { baseAmount, vatAmount: netSales - baseAmount, shareAmount, feeAmount: baseAmount - shareAmount };
    }

    /** 개인 작가 원천징수 — ⌊금액 × 3.3% ÷ 10⌋ × 10(10원 미만 버림) */
    static withholding(amount: number): number {
        return Math.floor((amount * UtilEmoticon.WITHHOLDING_PERCENT) / 100 / 10) * 10;
    }

    /** 가격 단계 한 칸 검사 — 문제 코드 또는 null */
    static tierProblem(value: number | null, tiers: readonly number[]): 'NOT_INTEGER' | 'RANGE' | 'UNIT' | 'DUPLICATE' | 'TOO_MANY' | null {
        const T = UtilEmoticon.PRICE_TIER;
        if (value === null) return 'NOT_INTEGER';
        if (value < T.min || value > T.max) return 'RANGE';
        if (value % T.unit !== 0) return 'UNIT';
        if (tiers.includes(value)) return 'DUPLICATE';
        if (tiers.length >= T.maxCount) return 'TOO_MANY';
        return null;
    }

    static settlementSeverity(status: EmoticonSettlementStatus): 'secondary' | 'info' | 'success' | 'danger' {
        if (status === 'CONFIRMED') return 'info';
        if (status === 'PAID') return 'success';
        if (status === 'HELD') return 'danger';
        return 'secondary';
    }

    /** 원 — `2,500원`(문구 키 없이 숫자만 — 단위는 부모 문구가 붙인다) */
    static won(value: number, locale: string): string {
        return value.toLocaleString(locale);
    }

    /** `YYYY-MM` → 「2026년 10월」 꼴을 만들 재료 */
    static monthParts(month: string): { year: number; month: number } {
        const [y, m] = month.split('-').map(Number);
        return { year: y ?? 0, month: m ?? 0 };
    }

    /** 자주 쓰는 반려 사유(문구 키 `emoticon.rejectPreset.<code>`·`emoticon.rejectPresetText.<code>`) */
    static readonly REJECT_PRESETS = ['SPEC', 'SMALL', 'RIGHTS', 'ADULT', 'HATE', 'ALT', 'SIMILAR'] as const;
    /** 자주 쓰는 판매 중단 사유 */
    static readonly SUSPEND_PRESETS = ['RIGHTS_REPORT', 'POLICY', 'CREATOR_REQUEST'] as const;

    static packStatusSeverity(status: EmoticonPackStatus): 'success' | 'danger' | 'info' | 'warn' | 'secondary' {
        if (status === 'APPROVED') return 'success';
        if (status === 'SUSPENDED') return 'danger';
        if (status === 'PENDING') return 'info';
        if (status === 'REJECTED') return 'warn';
        return 'secondary';
    }

    static reviewStatusSeverity(status: EmoticonReviewStatus): 'success' | 'info' | 'warn' {
        if (status === 'APPROVED') return 'success';
        if (status === 'REJECTED') return 'warn';
        return 'info';
    }

    /** 바이트 → `412KB` · `1.2MB` */
    static fileSize(bytes: number): string {
        if (bytes >= 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)}MB`;
        return `${Math.round(bytes / 1024)}KB`;
    }

    /** 로보티콘 하나의 규격 검사 */
    static itemIssues(item: EmoticonItemView): EmoticonSpecIssue[] {
        const S = UtilEmoticon.SPEC;
        const out: EmoticonSpecIssue[] = [];
        const push = (code: EmoticonSpecCode, level: EmoticonSpecLevel, params: Record<string, string | number> = {}) => out.push({ code, level, itemId: item.id, params });
        if (!(S.formats as readonly string[]).includes(item.format)) push('FORMAT', 'error', { format: item.format });
        if (item.width !== S.size || item.height !== S.size) push('SIZE', 'error', { width: item.width, height: item.height, size: S.size });
        if (item.format === 'GIF' && item.frames !== null && item.frames > S.maxFrames) push('FRAMES', 'error', { frames: item.frames, max: S.maxFrames });
        if (item.format === 'GIF' && item.loopForever === false) push('LOOP', 'warn');
        if (item.bytes > S.maxBytes) push('BYTES_MAX', 'error', { size: UtilEmoticon.fileSize(item.bytes), max: UtilEmoticon.fileSize(S.maxBytes) });
        else if (item.bytes > S.recommendedBytes) push('BYTES_RECOMMENDED', 'warn', { size: UtilEmoticon.fileSize(item.bytes), max: UtilEmoticon.fileSize(S.recommendedBytes) });
        const alt = item.alt.trim().length;
        if (alt < S.altMin || alt > S.altMax) push('ALT_LENGTH', 'error', { min: S.altMin, max: S.altMax });
        return out;
    }

    static coverIssues(cover: EmoticonCoverView): EmoticonSpecIssue[] {
        const S = UtilEmoticon.SPEC;
        const out: EmoticonSpecIssue[] = [];
        if (cover.format !== 'PNG') out.push({ code: 'COVER_FORMAT', level: 'error', itemId: null, params: { format: cover.format } });
        if (cover.width !== S.size || cover.height !== S.size) out.push({ code: 'COVER_SIZE', level: 'error', itemId: null, params: { width: cover.width, height: cover.height, size: S.size } });
        if (cover.bytes > S.coverMaxBytes) out.push({ code: 'COVER_BYTES', level: 'error', itemId: null, params: { size: UtilEmoticon.fileSize(cover.bytes), max: UtilEmoticon.fileSize(S.coverMaxBytes) } });
        return out;
    }

    /** 팩 전체의 규격 검사 — 팩 칸(이름·태그·개수·대표) 다음 로보티콘 차례 */
    static packIssues(pack: EmoticonPackVersionView, rules: EmoticonPackRules): EmoticonSpecIssue[] {
        const S = UtilEmoticon.SPEC;
        const out: EmoticonSpecIssue[] = [];
        const name = pack.name.trim().length;
        if (name < S.nameMin || name > S.nameMax) out.push({ code: 'NAME_LENGTH', level: 'error', itemId: null, params: { min: S.nameMin, max: S.nameMax } });
        if (pack.tags.length > S.tagsMax) out.push({ code: 'TAGS', level: 'error', itemId: null, params: { count: pack.tags.length, max: S.tagsMax } });
        // 유료 희망인데 유료 판매가 꺼져 있으면 유료로는 승인할 수 없다(무료로 승인은 된다) — 가격은 운영자가 승인 때 고른다
        if (pack.saleWish === 'PAID' && !rules.paidSalesEnabled) out.push({ code: 'PAID_OFF', level: 'warn', itemId: null, params: {} });
        const count = pack.items.length;
        if (count < rules.itemMin || count > rules.itemMax) out.push({ code: 'COUNT', level: 'error', itemId: null, params: { count, min: rules.itemMin, max: rules.itemMax } });
        out.push(...UtilEmoticon.coverIssues(pack.cover));
        for (const item of pack.items) out.push(...UtilEmoticon.itemIssues(item));
        return out;
    }

    /** 로보티콘 아이디 → 가장 무거운 문제 수준(격자 칸 표시) */
    static issueLevels(issues: readonly EmoticonSpecIssue[]): Record<string, EmoticonSpecLevel> {
        const out: Record<string, EmoticonSpecLevel> = {};
        for (const issue of issues) {
            if (!issue.itemId) continue;
            if (issue.level === 'error' || !out[issue.itemId]) out[issue.itemId] = issue.level;
        }
        return out;
    }

    /** 지금 판 → 새 판 비교 */
    static diff(current: EmoticonPackVersionView, next: EmoticonPackVersionView): EmoticonVersionDiff {
        const before = new Map(current.items.map((it) => [it.id, it] as const));
        const after = new Map(next.items.map((it) => [it.id, it] as const));
        const out: EmoticonVersionDiff = {
            added: [],
            removed: current.items.filter((it) => !after.has(it.id)),
            changedImage: [],
            changedAlt: [],
            nameChanged: current.name.trim() !== next.name.trim(),
            descriptionChanged: current.description.trim() !== next.description.trim(),
            tagsChanged: current.tags.join('\u0000') !== next.tags.join('\u0000'),
            priceChanged: current.saleWish !== next.saleWish || Boolean(next.priceRequestMemo),
            coverChanged: current.cover.imageUrl !== next.cover.imageUrl || current.cover.bytes !== next.cover.bytes,
            orderChanged: false,
            marks: {},
        };
        for (const item of next.items) {
            const old = before.get(item.id);
            if (!old) {
                out.added.push(item);
                out.marks[item.id] = 'added';
            } else if (old.contentHash !== item.contentHash) {
                out.changedImage.push(item);
                out.marks[item.id] = 'changedImage';
            } else if (old.alt !== item.alt) {
                out.changedAlt.push({ item, before: old.alt });
                out.marks[item.id] = 'changedAlt';
            }
        }
        // 차례 — 두 판 모두에 있는 것끼리의 앞뒤가 바뀌었나
        const keptBefore = current.items.filter((it) => after.has(it.id)).map((it) => it.id);
        const keptAfter = next.items.filter((it) => before.has(it.id)).map((it) => it.id);
        out.orderChanged = keptBefore.join('\u0000') !== keptAfter.join('\u0000');
        return out;
    }

    static reasonProblem(text: string): EmoticonReasonProblem | null {
        const v = text.trim();
        if (!v) return 'REQUIRED';
        if (v.length < UtilEmoticon.REASON.min) return 'TOO_SHORT';
        if (v.length > UtilEmoticon.REASON.max) return 'TOO_LONG';
        return null;
    }

    /** 숫자 칸 글자 → 정수(읽을 수 없으면 null). 쉼표는 무시한다 */
    static parseInt(text: string): number | null {
        const v = text.replaceAll(',', '').trim();
        if (!/^-?\d+$/.test(v)) return null;
        const n = Number(v);
        return Number.isSafeInteger(n) ? n : null;
    }

    /** 태그 글자(쉼표로 나눔) → 목록 — 빈 것·겹친 것을 뺀다 */
    static parseTags(text: string): string[] {
        const out: string[] = [];
        for (const raw of text.split(',')) {
            const tag = raw.trim();
            if (tag && !out.includes(tag)) out.push(tag);
        }
        return out;
    }

    /** 회원 관리 화면에서 그 회원을 여는 주소 */
    static memberLink(memberNo: string): { path: string; query: { memberNo: string } } {
        return { path: '/members', query: { memberNo } };
    }

    /** 팩 관리 화면에서 그 작가의 팩만 보는 주소 */
    static creatorPacksLink(memberNo: string): { path: string; query: { creator: string } } {
        return { path: '/robotikon/packs', query: { creator: memberNo } };
    }
}
