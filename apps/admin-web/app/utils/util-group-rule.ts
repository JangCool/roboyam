import { ApiError } from '~/composables/common/useApiClient';
import type { GroupRuleRange } from '~/composables/group/api/useGroupSettingsApi.types';

/**
 * 소모임 설정 네 구획(개설 규칙 · 매니저 · 공개 범위 · 운영 한도)이 같이 쓰는 입력 규칙 — 순수 함수만.
 * 서버(community `GroupCreationRuleService`·`GroupManagerRuleService`·`GroupVisibilityPolicy`·`GroupPolicyRuleService` — 로보얌 서버 단계에서 같은 이름)와 같은 규칙으로 미리 알려 주는 용도다. 판정은 서버다.
 *
 * 폼 값: 숫자 칸은 입력 그대로의 글자(`"7"`), 켜기는 불리언, 고르기는 값 글자. 숫자는 0 이상 정수만 받는다(소수·음수·빈칸은 범위 문제).
 */

/** 폼 값 — 칸 이름 → 글자(숫자·고르기) 또는 불리언(켜기) */
export type GroupRuleForm = Record<string, string | boolean>;

/** 칸 하나의 문제 — 범위 밖(빈칸·소수 포함) · 순서(작은 값 > 큰 값) */
export type GroupRuleProblem = { kind: 'RANGE'; min: number; max: number } | { kind: 'ORDER' };

/** 두 칸의 크기 순서 규칙 — `low` ≤ `high` 가 아니면 `on` 칸에 문제를 단다 */
export interface GroupRuleOrder {
    low: string;
    high: string;
    on: string;
}

export default class UtilGroupRule {
    /**
     * 숫자 칸의 글자를 수로 — 0 이상 정수(6자리까지)만. 아니면 null.
     * @param {string | boolean | undefined} text - 입력 글자.
     * @returns {number | null} 수.
     */
    static numberOf(text: string | boolean | undefined): number | null {
        if (typeof text !== 'string') return null;
        const trimmed = text.trim();
        return /^\d{1,6}$/.test(trimmed) ? Number(trimmed) : null;
    }

    /**
     * 서버 값을 폼 값으로 — 수는 글자로 바꾸고 나머지는 그대로.
     * @param {object} values - 서버의 `values`(또는 `defaults`).
     * @returns {GroupRuleForm} 폼 값.
     */
    static formOf(values: object): GroupRuleForm {
        const out: GroupRuleForm = {};
        for (const [key, value] of Object.entries(values)) out[key] = typeof value === 'number' ? String(value) : (value as string | boolean);
        return out;
    }

    /**
     * 폼의 한 칸이 그 값과 같은가 — 숫자 칸은 수로 견준다(`"07"` 과 7 은 같다).
     * @param {string | boolean | undefined} formValue - 폼 값.
     * @param {unknown} value - 견줄 서버 값.
     * @returns {boolean} 같으면 true.
     */
    static same(formValue: string | boolean | undefined, value: unknown): boolean {
        if (typeof value === 'number') return UtilGroupRule.numberOf(formValue) === value;
        return formValue === value;
    }

    /**
     * 저장된 값과 다른 칸들(칸 차례 그대로).
     * @param {object} saved - 저장된 값.
     * @param {GroupRuleForm} form - 폼 값.
     * @returns {string[]} 바뀐 칸 이름.
     */
    static changedFields(saved: object, form: GroupRuleForm): string[] {
        return Object.entries(saved)
            .filter(([key, value]) => !UtilGroupRule.same(form[key], value))
            .map(([key]) => key);
    }

    /**
     * 칸마다의 문제 — 숫자 칸의 범위, 그다음 순서 규칙(두 칸이 모두 범위 안일 때만).
     * @param {GroupRuleForm} form - 폼 값.
     * @param {Record<string, GroupRuleRange>} ranges - 숫자 칸의 범위(서버 `limits`).
     * @param {readonly GroupRuleOrder[]} [orders] - 크기 순서 규칙.
     * @returns {Partial<Record<string, GroupRuleProblem>>} 문제가 있는 칸만.
     */
    static problems(form: GroupRuleForm, ranges: Record<string, GroupRuleRange>, orders: readonly GroupRuleOrder[] = []): Partial<Record<string, GroupRuleProblem>> {
        const out: Partial<Record<string, GroupRuleProblem>> = {};
        for (const [key, range] of Object.entries(ranges)) {
            const value = UtilGroupRule.numberOf(form[key]);
            if (value === null || value < range.min || value > range.max) out[key] = { kind: 'RANGE', min: range.min, max: range.max };
        }
        for (const order of orders) {
            if (out[order.low] || out[order.high]) continue;
            const low = UtilGroupRule.numberOf(form[order.low]);
            const high = UtilGroupRule.numberOf(form[order.high]);
            if (low !== null && high !== null && low > high) out[order.on] = { kind: 'ORDER' };
        }
        return out;
    }

    /**
     * 폼 값을 서버 본문으로 — 저장된 값과 같은 칸 모양(수는 수로). 문제가 있으면 null.
     * @param {T} shape - 칸 모양을 알려 줄 값(저장된 값).
     * @param {GroupRuleForm} form - 폼 값.
     * @param {Partial<Record<string, GroupRuleProblem>>} problems - `problems()` 의 결과.
     * @returns {T | null} 본문.
     */
    static toValues<T extends object>(shape: T, form: GroupRuleForm, problems: Partial<Record<string, GroupRuleProblem>>): T | null {
        if (Object.keys(problems).length > 0) return null;
        const out: Record<string, unknown> = {};
        for (const [key, value] of Object.entries(shape)) out[key] = typeof value === 'number' ? UtilGroupRule.numberOf(form[key]) : form[key];
        return out as T;
    }

    /**
     * 서버가 거절한 칸(`400 VALIDATION` 의 `fields.<칸>`)만 — 이 구획의 칸 이름에 있는 것.
     * @param {unknown} e - 잡은 오류.
     * @param {readonly string[]} keys - 이 구획의 칸 이름.
     * @returns {{ fields: Record<string, string>; rest: string } | null} 칸 문구와 칸에 붙지 않는 서버 문구. 400 이 아니면 null.
     */
    static fieldErrorsOf(e: unknown, keys: readonly string[]): { fields: Record<string, string>; rest: string } | null {
        if (!(e instanceof ApiError) || e.status !== 400 || !e.fields) return null;
        const fields: Record<string, string> = {};
        const rest: string[] = [];
        for (const [key, message] of Object.entries(e.fields)) {
            if (keys.includes(key)) fields[key] = message;
            else rest.push(message);
        }
        return { fields, rest: rest.join(' ') };
    }

    /**
     * 매니저 규칙 — 입력한 최대 인원보다 큰 상한을 정해 둔 소모임 수(community `groupsAbove`).
     * @param {{ limit: number; groupCount: number }[]} limitCounts - 상한별 소모임 수.
     * @param {string | boolean | undefined} limitMaxText - 입력한 최대 인원.
     * @returns {number} 소모임 수(입력이 수가 아니면 0).
     */
    static groupsAbove(limitCounts: readonly { limit: number; groupCount: number }[], limitMaxText: string | boolean | undefined): number {
        const max = UtilGroupRule.numberOf(limitMaxText);
        if (max === null) return 0;
        return limitCounts.filter((c) => c.limit > max).reduce((sum, c) => sum + c.groupCount, 0);
    }
}
