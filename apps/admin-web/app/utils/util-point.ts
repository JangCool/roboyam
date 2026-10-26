import { ApiError } from '~/composables/common/useApiClient';
import type { PointLedgerKind, PointReasonCode, PointSettingsField, PointSettingsNumberField, PointSettingsSwitchField, PointSettingsValues } from '~/composables/point/api/usePointApi.types';

/**
 * 포인트 화면(정책·내역·조정)의 순수 계산 — 서버 규칙을 **미리 알려 주는 용도**이고 판정은 서버다(`forms.md`).
 * 서버 규칙 이름(community 기준): 정책 범위는 `PointSettingsService`(`point_settings` CHECK 와 `limits`), 조정은 `PointAdminService.adjust`(delta 0 아닌 정수 −100000~100000, 크기가 정책 문턱 `adjustLargeThreshold` 초과면 `point.adjust.large` — 커머스 1-1 · reason 1~100자, 연속 공백은 한 칸).
 */

/** 정책 칸 하나의 입력 문제 */
export type PointSettingsProblem = 'REQUIRED' | 'NOT_INTEGER' | 'OUT_OF_RANGE';

/** 정책 입력값 — 정수 칸은 입력 글자, 켜기/끄기 칸은 불리언 */
export type PointSettingsForm = Record<PointSettingsNumberField, string> & Record<PointSettingsSwitchField, boolean>;

/** 정책 묶음(같은 딱지 판정을 받는 칸들) */
export type PointSettingsGroup = 'attendance' | 'attendanceStreak' | 'post' | 'comment' | 'earnConditions' | 'dailyTotal' | 'popular' | 'likeBonus' | 'dealEnd' | 'signup' | 'adjust';

/** 묶음·칸 딱지 — 꺼짐(0 = 그 규칙 끔) · 한도 없음(하루 총 한도 0) */
export type PointSettingsTag = 'OFF' | 'NO_LIMIT';

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

/** 정수 칸 순서 = 화면·요청 순서. 뒤쪽 9칸 중 정수 7칸은 커머스 1-3(`commerce.md` 6-2) */
const NUMBER_FIELDS: readonly PointSettingsNumberField[] = [
    'attendancePoints',
    'attendanceStreakDays',
    'attendanceStreakBonus',
    'postPoints',
    'postDailyCap',
    'commentPoints',
    'commentDailyCap',
    'postMinLength',
    'commentMinLength',
    'writeIntervalSeconds',
    'dailyTotalCap',
    'popularRewardPoints',
    'popularRewardTopN',
    'likeBonusThreshold',
    'likeBonusPoints',
    'likeBonusDailyLimit',
    'dealEndRequestPoints',
    'dealEndRequestDailyLimit',
    'signupPoints',
    'adjustLargeThreshold',
];

/** 켜기/끄기 칸(커머스 1-3) */
const SWITCH_FIELDS: readonly PointSettingsSwitchField[] = ['commentOwnPostExcluded', 'commentOncePerPost'];

/** 모든 칸 */
const FIELDS: readonly PointSettingsField[] = [...NUMBER_FIELDS, ...SWITCH_FIELDS];

/** 입력 범위 — community `limits` 그대로 + 가입 포인트(0~10000) + 커머스 1-3 칸(서버 CHECK 가 될 값) */
const LIMITS: Record<PointSettingsNumberField, readonly [number, number]> = {
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
    postMinLength: [0, 10000],
    commentMinLength: [0, 1000],
    writeIntervalSeconds: [0, 3600],
    dailyTotalCap: [0, 100000],
    attendanceStreakDays: [0, 365],
    attendanceStreakBonus: [0, 10000],
    adjustLargeThreshold: [0, 100000],
};

/** 기본값(서버 값을 받기 전 폼의 자리) — community `defaults` 그대로 + 가입 포인트 0 + 커머스 1-3 칸(`commerce.md` 6-2) */
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
    postMinLength: 50,
    commentMinLength: 10,
    commentOwnPostExcluded: true,
    commentOncePerPost: true,
    writeIntervalSeconds: 30,
    dailyTotalCap: 200,
    attendanceStreakDays: 7,
    attendanceStreakBonus: 20,
    adjustLargeThreshold: 10000,
};

/**
 * 묶음마다 칸과 딱지 규칙 — `offWhenZero` 칸 중 하나라도 0 이면 묶음이 「꺼짐」(문턱은 1 이상이라 끄는 칸이 아니다), `noLimitWhenZero` 칸이 0 이면 「한도 없음」.
 * 적립 조건 묶음은 칸마다 따로 꺼지므로(`FIELD_OFF_WHEN_ZERO`) 묶음 딱지가 없고, 조정 문턱은 끄는 개념이 없어 딱지가 없다. 켜기/끄기 칸은 딱지 대상이 아니다.
 */
const GROUPS: Record<PointSettingsGroup, { fields: readonly PointSettingsField[]; offWhenZero: readonly PointSettingsNumberField[]; noLimitWhenZero?: readonly PointSettingsNumberField[] }> = {
    attendance: { fields: ['attendancePoints'], offWhenZero: ['attendancePoints'] },
    attendanceStreak: { fields: ['attendanceStreakDays', 'attendanceStreakBonus'], offWhenZero: ['attendanceStreakDays', 'attendanceStreakBonus'] },
    post: { fields: ['postPoints', 'postDailyCap'], offWhenZero: ['postPoints', 'postDailyCap'] },
    comment: { fields: ['commentPoints', 'commentDailyCap'], offWhenZero: ['commentPoints', 'commentDailyCap'] },
    earnConditions: { fields: ['postMinLength', 'commentMinLength', 'writeIntervalSeconds', 'commentOwnPostExcluded', 'commentOncePerPost'], offWhenZero: [] },
    dailyTotal: { fields: ['dailyTotalCap'], offWhenZero: [], noLimitWhenZero: ['dailyTotalCap'] },
    popular: { fields: ['popularRewardPoints', 'popularRewardTopN'], offWhenZero: ['popularRewardPoints', 'popularRewardTopN'] },
    likeBonus: { fields: ['likeBonusThreshold', 'likeBonusPoints', 'likeBonusDailyLimit'], offWhenZero: ['likeBonusPoints', 'likeBonusDailyLimit'] },
    dealEnd: { fields: ['dealEndRequestPoints', 'dealEndRequestDailyLimit'], offWhenZero: ['dealEndRequestPoints', 'dealEndRequestDailyLimit'] },
    signup: { fields: ['signupPoints'], offWhenZero: ['signupPoints'] },
    adjust: { fields: ['adjustLargeThreshold'], offWhenZero: [] },
};

/** 칸 하나로 꺼지는 규칙 — 적립 조건의 정수 칸(0 = 그 조건 없음) */
const FIELD_OFF_WHEN_ZERO: readonly PointSettingsNumberField[] = ['postMinLength', 'commentMinLength', 'writeIntervalSeconds'];

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
    // 커머스 1-1·1-2 — 3단계·공구 사유(`CHARGE` 등)는 기능이 생길 때 더한다
    'POINT_SHOP_PURCHASE',
    'POINT_SHOP_REFUND',
    'REVOKE_DEBT_OFFSET',
    'EXPIRE',
];

const KINDS: readonly PointLedgerKind[] = ['EARN', 'USE', 'CANCEL_USE', 'REVOKE', 'EXPIRE', 'REFUND'];

export default class UtilPoint {
    static readonly FIELDS = FIELDS;
    static readonly NUMBER_FIELDS = NUMBER_FIELDS;
    static readonly SWITCH_FIELDS = SWITCH_FIELDS;
    static readonly DEFAULTS = DEFAULTS;
    static readonly REASON_CODES = REASON_CODES;
    static readonly KINDS = KINDS;
    static readonly ADJUST_MODES: readonly PointAdjustMode[] = ['GRANT', 'DEDUCT'];
    /** 조정 한 번의 금액 범위 — 서버 `PointService.adjust` 는 delta −100000~100000(0 불가, 커머스 1-1) */
    static readonly AMOUNT_MIN = 1;
    static readonly AMOUNT_MAX = 100000;
    /** 조정 사유 최대 글자 수 */
    static readonly REASON_MAX = 100;

    /** 정수 칸의 입력 범위 [최소, 최대] */
    static limitsOf(field: PointSettingsNumberField): readonly [number, number] {
        return LIMITS[field];
    }

    /** 켜기/끄기 칸인가 */
    static isSwitch(field: PointSettingsField): field is PointSettingsSwitchField {
        return (SWITCH_FIELDS as readonly string[]).includes(field);
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

    /** 정수 칸 하나의 문제 — 비었나 · 정수인가 · 범위 안인가 */
    static settingsProblem(field: PointSettingsNumberField, text: string): PointSettingsProblem | null {
        if (!text.trim()) return 'REQUIRED';
        const value = UtilPoint.parse(text);
        if (value === null) return 'NOT_INTEGER';
        const [min, max] = LIMITS[field];
        return value < min || value > max ? 'OUT_OF_RANGE' : null;
    }

    /** 모든 칸의 문제 — 문제가 있는 칸만 담는다(켜기/끄기 칸은 늘 맞다) */
    static validateSettings(form: PointSettingsForm): Partial<Record<PointSettingsField, PointSettingsProblem>> {
        const out: Partial<Record<PointSettingsField, PointSettingsProblem>> = {};
        for (const field of NUMBER_FIELDS) {
            const problem = UtilPoint.settingsProblem(field, form[field]);
            if (problem) out[field] = problem;
        }
        return out;
    }

    /** 서버 값 → 입력(정수 칸은 글자, 켜기/끄기 칸은 그대로) */
    static formOf(values: PointSettingsValues): PointSettingsForm {
        const numbers = Object.fromEntries(NUMBER_FIELDS.map((field) => [field, String(values[field])]));
        const switches = Object.fromEntries(SWITCH_FIELDS.map((field) => [field, values[field] === true]));
        return { ...numbers, ...switches } as PointSettingsForm;
    }

    /** 입력 → 요청(모든 칸). 정수 칸을 하나라도 읽을 수 없으면 null */
    static toRequest(form: PointSettingsForm): PointSettingsValues | null {
        const out: Record<string, number | boolean> = {};
        for (const field of NUMBER_FIELDS) {
            const value = UtilPoint.parse(form[field]);
            if (value === null) return null;
            out[field] = value;
        }
        for (const field of SWITCH_FIELDS) out[field] = form[field];
        return out as unknown as PointSettingsValues;
    }

    /** 저장된 값과 다른 칸 — 정수 칸은 숫자로 읽어 비교한다(「010」과 「10」은 같다, 읽을 수 없는 글자는 바뀐 것), 켜기/끄기 칸은 그대로 */
    static changedFields(form: PointSettingsForm, saved: PointSettingsValues): PointSettingsField[] {
        return FIELDS.filter((field) => (UtilPoint.isSwitch(field) ? form[field] !== saved[field] : UtilPoint.parse(form[field]) !== saved[field]));
    }

    /** 묶음이 꺼졌나 — 입력 중인 값 기준(읽을 수 없는 칸은 판정하지 않는다) */
    static groupOff(group: PointSettingsGroup, form: PointSettingsForm): boolean {
        return GROUPS[group].offWhenZero.some((field) => UtilPoint.parse(form[field]) === 0);
    }

    /** 묶음 딱지 — 꺼짐이 먼저, 다음은 한도 없음(하루 총 한도 0). 없으면 null */
    static groupTag(group: PointSettingsGroup, form: PointSettingsForm): PointSettingsTag | null {
        if (UtilPoint.groupOff(group, form)) return 'OFF';
        return GROUPS[group].noLimitWhenZero?.some((field) => UtilPoint.parse(form[field]) === 0) ? 'NO_LIMIT' : null;
    }

    /** 칸 하나가 그 조건을 끄고 있나 — 적립 조건의 정수 칸만(최소 글자 수·작성 간격 0) */
    static fieldOff(field: PointSettingsField, form: PointSettingsForm): boolean {
        return (FIELD_OFF_WHEN_ZERO as readonly string[]).includes(field) && UtilPoint.parse(form[field as PointSettingsNumberField]) === 0;
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

    /** 종류 딱지 색 — 지급 초록 · 사용 취소 파랑 · 회수 빨강 · 환불 주황 · 사용·소멸 회색 */
    static kindSeverity(kind: PointLedgerKind): 'success' | 'info' | 'warn' | 'secondary' | 'danger' {
        if (kind === 'EARN') return 'success';
        if (kind === 'CANCEL_USE') return 'info';
        if (kind === 'REVOKE') return 'danger';
        if (kind === 'REFUND') return 'warn';
        return 'secondary';
    }

    /** 증감 글자 색 — 소멸은 회색(빨강 회수와 구분), 그 밖에는 +초록 · −빨강 · 0 회색 */
    static deltaClass(kind: PointLedgerKind, delta: number): string {
        if (kind === 'EXPIRE' || delta === 0) return 'text-muted-color';
        return delta > 0 ? 'text-green-700 dark:text-green-400' : 'text-red-600 dark:text-red-400';
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

    /**
     * 금액이 고액 조정 문턱(정책 `adjustLargeThreshold` — 0 이면 모든 조정)을 넘나. 읽을 수 없는 글자는 넘지 않은 것으로 본다.
     * 문턱을 모르면(`null` — 정책을 아직 못 읽었다) 미리 판정하지 않는다(false — 서버 403 `POINT_ADJUST_LARGE_REQUIRED` 에 맡긴다).
     */
    static isLarge(amountText: string, threshold: number | null): boolean {
        if (threshold === null) return false;
        const value = UtilPoint.parse(amountText);
        return value !== null && value > threshold;
    }

    /**
     * 처리 후 예상 — 조정은 **적립(무상) 포인트만** 바꾸므로 `balance` 에는 적립 잔액(`freeBalance`)을 넣는다.
     * 차감은 적립 0P 아래로 내려가지 않는다(서버 `PointService.adjust` 의 0P 바닥 — 충전 포인트는 건드리지 않는다).
     */
    static preview(balance: number, delta: number): PointAdjustPreview {
        if (delta >= 0) return { applied: delta, after: balance + delta, clipped: false };
        const applied = -Math.min(-delta, balance);
        return { applied, after: balance + applied, clipped: applied !== delta };
    }
}
