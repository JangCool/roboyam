import { ApiError } from '~/composables/common/useApiClient';
import type { PointLedgerKind, PointReasonCode, PointSettingsField, PointSettingsValues } from '~/composables/point/api/usePointApi.types';

/**
 * 포인트 화면(정책·내역·조정)의 순수 계산 — 서버 규칙을 **미리 알려 주는 용도**이고 판정은 서버다(`forms.md`).
 * 서버 규칙 이름(community 기준): 정책 범위는 `PointSettingsService`(`point_settings` CHECK 와 `limits`), 조정은 `PointAdminService.adjust`(delta 0 아닌 정수 −10000~10000 · reason 1~100자, 연속 공백은 한 칸).
 */

/** 정책 칸 하나의 입력 문제 */
export type PointSettingsProblem = 'REQUIRED' | 'NOT_INTEGER' | 'OUT_OF_RANGE';

/** 정책 입력값 — 칸마다 입력 글자 */
export type PointSettingsForm = Record<PointSettingsField, string>;

/** 정책 묶음(같은 「꺼짐」 판정을 받는 칸들) */
export type PointSettingsGroup = 'attendance' | 'post' | 'comment' | 'popular' | 'likeBonus' | 'dealEnd' | 'signup' | 'shop';

/** 조정 방향 — 지급 · 차감 */
export type PointAdjustMode = 'GRANT' | 'DEDUCT';

export type PointAmountProblem = 'REQUIRED' | 'NOT_INTEGER' | 'OUT_OF_RANGE';
export type PointReasonProblem = 'REQUIRED' | 'TOO_LONG';

/** 조정 뒤 예상 — 차감이 잔액보다 크면 0P 까지만 반영된다 */
export interface PointAdjustPreview {
    /** 실제 반영될 값(부호 포함) */
    applied: number;
    /** 처리 후 잔액 */
    after: number;
    /** 요청한 차감이 잔액보다 커서 잘렸다 */
    clipped: boolean;
}

/** 칸 순서 = 화면·요청 순서 */
const FIELDS: readonly PointSettingsField[] = [
    'attendancePoints',
    'postPoints',
    'postDailyCap',
    'commentPoints',
    'commentDailyCap',
    'popularRewardPoints',
    'popularRewardTopN',
    'likeBonusThreshold',
    'likeBonusPoints',
    'likeBonusDailyLimit',
    'dealEndRequestPoints',
    'dealEndRequestDailyLimit',
    'signupPoints',
    'nicknameTicketPrice',
];

/** 입력 범위 — community `limits` 그대로 + 가입 포인트(0~10000) */
const LIMITS: Record<PointSettingsField, readonly [number, number]> = {
    attendancePoints: [0, 10000],
    postPoints: [0, 10000],
    postDailyCap: [0, 1000],
    commentPoints: [0, 10000],
    commentDailyCap: [0, 1000],
    popularRewardPoints: [0, 10000],
    popularRewardTopN: [0, 50],
    likeBonusThreshold: [1, 1000],
    likeBonusPoints: [0, 10000],
    likeBonusDailyLimit: [0, 1000],
    dealEndRequestPoints: [0, 10000],
    dealEndRequestDailyLimit: [0, 1000],
    signupPoints: [0, 10000],
    nicknameTicketPrice: [1, 100000],
};

/** 기본값 — community `defaults` 그대로 + 가입 포인트 0(지급 안 함) */
const DEFAULTS: PointSettingsValues = {
    attendancePoints: 10,
    postPoints: 20,
    postDailyCap: 5,
    commentPoints: 5,
    commentDailyCap: 20,
    popularRewardPoints: 100,
    popularRewardTopN: 5,
    likeBonusThreshold: 5,
    likeBonusPoints: 10,
    likeBonusDailyLimit: 3,
    dealEndRequestPoints: 3,
    dealEndRequestDailyLimit: 5,
    signupPoints: 0,
    nicknameTicketPrice: 1000,
};

/** 묶음마다 칸과 「꺼짐」 규칙 — 칸 중 하나라도 0 이면 꺼진다(문턱은 1 이상이라 끄는 칸이 아니다). 상점은 끌 수 없다 */
const GROUPS: Record<PointSettingsGroup, { fields: readonly PointSettingsField[]; offWhenZero: readonly PointSettingsField[] }> = {
    attendance: { fields: ['attendancePoints'], offWhenZero: ['attendancePoints'] },
    post: { fields: ['postPoints', 'postDailyCap'], offWhenZero: ['postPoints', 'postDailyCap'] },
    comment: { fields: ['commentPoints', 'commentDailyCap'], offWhenZero: ['commentPoints', 'commentDailyCap'] },
    popular: { fields: ['popularRewardPoints', 'popularRewardTopN'], offWhenZero: ['popularRewardPoints', 'popularRewardTopN'] },
    likeBonus: { fields: ['likeBonusThreshold', 'likeBonusPoints', 'likeBonusDailyLimit'], offWhenZero: ['likeBonusPoints', 'likeBonusDailyLimit'] },
    dealEnd: { fields: ['dealEndRequestPoints', 'dealEndRequestDailyLimit'], offWhenZero: ['dealEndRequestPoints', 'dealEndRequestDailyLimit'] },
    signup: { fields: ['signupPoints'], offWhenZero: ['signupPoints'] },
    shop: { fields: ['nicknameTicketPrice'], offWhenZero: [] },
};

/** 원장 사유 코드 — 고르개 차례 */
const REASON_CODES: readonly PointReasonCode[] = [
    'ATTENDANCE',
    'POST',
    'COMMENT',
    'POPULAR',
    'POST_REVOKE',
    'POST_RESTORE',
    'COMMENT_REVOKE',
    'TICKET_PURCHASE',
    'ADMIN_ADJUST',
    'DEAL_END_REQUEST',
    'LIKE_BONUS',
    'LIKE_BONUS_REVOKE',
    'SIGNUP',
];

const KINDS: readonly PointLedgerKind[] = ['EARN', 'USE', 'REVOKE'];

export default class UtilPoint {
    static readonly FIELDS = FIELDS;
    static readonly DEFAULTS = DEFAULTS;
    static readonly REASON_CODES = REASON_CODES;
    static readonly KINDS = KINDS;
    static readonly ADJUST_MODES: readonly PointAdjustMode[] = ['GRANT', 'DEDUCT'];
    /** 조정 한 번의 금액 범위 — 서버는 delta −10000~10000(0 불가) */
    static readonly AMOUNT_MIN = 1;
    static readonly AMOUNT_MAX = 10000;
    /** 조정 사유 최대 글자 수 */
    static readonly REASON_MAX = 100;

    /** 칸의 입력 범위 [최소, 최대] */
    static limitsOf(field: PointSettingsField): readonly [number, number] {
        return LIMITS[field];
    }

    /** 묶음의 칸들 */
    static fieldsOf(group: PointSettingsGroup): readonly PointSettingsField[] {
        return GROUPS[group].fields;
    }

    /**
     * 입력 글자를 정수로 읽는다 — 앞뒤 공백과 천 단위 쉼표는 허용한다.
     * @returns 0 이상 정수. 비었거나 정수가 아니면 null.
     */
    static parse(text: string): number | null {
        const cleaned = text.trim().replace(/,/g, '');
        return /^\d{1,9}$/.test(cleaned) ? Number(cleaned) : null;
    }

    /** 칸 하나의 문제 — 비었나 · 정수인가 · 범위 안인가 */
    static settingsProblem(field: PointSettingsField, text: string): PointSettingsProblem | null {
        if (!text.trim()) return 'REQUIRED';
        const value = UtilPoint.parse(text);
        if (value === null) return 'NOT_INTEGER';
        const [min, max] = LIMITS[field];
        return value < min || value > max ? 'OUT_OF_RANGE' : null;
    }

    /** 모든 칸의 문제 — 문제가 있는 칸만 담는다 */
    static validateSettings(form: PointSettingsForm): Partial<Record<PointSettingsField, PointSettingsProblem>> {
        const out: Partial<Record<PointSettingsField, PointSettingsProblem>> = {};
        for (const field of FIELDS) {
            const problem = UtilPoint.settingsProblem(field, form[field]);
            if (problem) out[field] = problem;
        }
        return out;
    }

    /** 서버 값 → 입력 글자 */
    static formOf(values: PointSettingsValues): PointSettingsForm {
        return Object.fromEntries(FIELDS.map((field) => [field, String(values[field])])) as PointSettingsForm;
    }

    /** 입력 글자 → 요청(모든 칸). 하나라도 읽을 수 없으면 null */
    static toRequest(form: PointSettingsForm): PointSettingsValues | null {
        const out: Partial<PointSettingsValues> = {};
        for (const field of FIELDS) {
            const value = UtilPoint.parse(form[field]);
            if (value === null) return null;
            out[field] = value;
        }
        return out as PointSettingsValues;
    }

    /** 저장된 값과 다른 칸 — 숫자로 읽어 비교한다(「010」과 「10」은 같다), 읽을 수 없는 글자는 바뀐 것으로 본다 */
    static changedFields(form: PointSettingsForm, saved: PointSettingsValues): PointSettingsField[] {
        return FIELDS.filter((field) => UtilPoint.parse(form[field]) !== saved[field]);
    }

    /** 묶음이 꺼졌나 — 입력 중인 값 기준(읽을 수 없는 칸은 판정하지 않는다) */
    static groupOff(group: PointSettingsGroup, form: PointSettingsForm): boolean {
        return GROUPS[group].offWhenZero.some((field) => UtilPoint.parse(form[field]) === 0);
    }

    /**
     * 서버가 거절한 칸(`400 VALIDATION` 의 `fields.<칸>`)과 칸에 붙지 않는 문구를 나눈다.
     * @returns 입력 오류가 아니면 null.
     */
    static settingsFieldErrorsOf(e: unknown): { fields: Partial<Record<PointSettingsField, string>>; rest: string } | null {
        if (!(e instanceof ApiError) || !e.fields || Object.keys(e.fields).length === 0) return null;
        const fields: Partial<Record<PointSettingsField, string>> = {};
        const rest: string[] = [];
        for (const [key, message] of Object.entries(e.fields)) {
            if ((FIELDS as readonly string[]).includes(key)) fields[key as PointSettingsField] = message;
            else rest.push(message);
        }
        return { fields, rest: rest.join(' ') };
    }

    // ── 원장 ─

    /** 종류 딱지 색 — 지급 초록 · 사용 회색 · 회수 빨강 */
    static kindSeverity(kind: PointLedgerKind): 'success' | 'secondary' | 'danger' {
        if (kind === 'EARN') return 'success';
        if (kind === 'REVOKE') return 'danger';
        return 'secondary';
    }

    /** 증감을 부호와 함께 — `+20` · `−500`(빼기 기호), 0 은 `0` */
    static signed(value: number, locale: string): string {
        const abs = Math.abs(value).toLocaleString(locale);
        if (value > 0) return `+${abs}`;
        if (value < 0) return `−${abs}`;
        return abs;
    }

    // ── 수동 조정 ─

    /** 사유 글자 수 — 앞뒤 공백을 떼고 연속 공백을 한 칸으로 줄인 뒤 유니코드 한 글자씩 센다(서버와 같다) */
    static reasonText(text: string): string {
        return text.trim().replace(/\s+/g, ' ');
    }

    static reasonLength(text: string): number {
        return [...UtilPoint.reasonText(text)].length;
    }

    static reasonProblem(text: string): PointReasonProblem | null {
        const length = UtilPoint.reasonLength(text);
        if (length === 0) return 'REQUIRED';
        return length > UtilPoint.REASON_MAX ? 'TOO_LONG' : null;
    }

    static amountProblem(text: string): PointAmountProblem | null {
        if (!text.trim()) return 'REQUIRED';
        const value = UtilPoint.parse(text);
        if (value === null) return 'NOT_INTEGER';
        return value < UtilPoint.AMOUNT_MIN || value > UtilPoint.AMOUNT_MAX ? 'OUT_OF_RANGE' : null;
    }

    /** 방향 + 금액 → delta. 금액이 맞지 않으면 null */
    static toDelta(mode: PointAdjustMode, amountText: string): number | null {
        if (UtilPoint.amountProblem(amountText)) return null;
        const amount = UtilPoint.parse(amountText) as number;
        return mode === 'GRANT' ? amount : -amount;
    }

    /** 처리 후 예상 — 잔액은 0 아래로 내려가지 않는다(서버 `PointService` 의 0P 바닥) */
    static preview(balance: number, delta: number): PointAdjustPreview {
        if (delta >= 0) return { applied: delta, after: balance + delta, clipped: false };
        const applied = -Math.min(-delta, balance);
        return { applied, after: balance + applied, clipped: applied !== delta };
    }
}
