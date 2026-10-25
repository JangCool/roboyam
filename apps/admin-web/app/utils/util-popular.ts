import { ApiError } from '~/composables/common/useApiClient';
import type {
    PopularExcludeReason,
    PopularLimit,
    PopularNumberField,
    PopularPeriod,
    PopularSettingField,
    PopularSettingValues,
    PopularWeekStart,
} from '~/composables/content/api/usePopularApi.types';

/**
 * 인기글 설정·인기글 관리 화면의 순수 계산 — 서버 규칙을 **미리 알려 주는 용도**이고 판정은 서버다(`forms.md`).
 * 서버 규칙 이름: 설정 범위·소수 자리는 `PopularSettingsService`(응답 `limits` — 화면은 받은 `limits` 로 검사한다), 「세 가중치가 모두 0 이면 `fields.likeWeight`」도 같은 곳.
 * 빼기 사유·메모는 `PopularAdminService.exclude`(메모 100자, `OTHER` 는 메모 필수, 앞뒤 공백을 떼고 비면 null).
 */

/** 숫자 칸 하나의 입력 문제 */
export type PopularFieldProblem = 'REQUIRED' | 'NOT_NUMBER' | 'NOT_INTEGER' | 'TOO_MANY_DECIMALS' | 'OUT_OF_RANGE' | 'ALL_WEIGHTS_ZERO';

/** 설정 입력값 — 숫자 칸은 입력 글자, 나머지는 값 그대로 */
export type PopularSettingsForm = Record<PopularNumberField, string> & { excludeStaffBoards: boolean; weekStart: PopularWeekStart };

/** 빼기 메모 문제 */
export type PopularMemoProblem = 'REQUIRED' | 'TOO_LONG';

/** 숫자 칸 — 화면·요청 순서 */
const NUMBER_FIELDS: readonly PopularNumberField[] = ['likeWeight', 'commentWeight', 'viewWeight', 'gravity', 'ageOffsetHours', 'minLikes', 'minComments', 'minViews', 'maxPerAuthor'];

/** 모든 칸 — 서버 오류 `fields.<칸>` 을 칸에 붙일 때 쓴다 */
const FIELDS: readonly PopularSettingField[] = [...NUMBER_FIELDS, 'excludeStaffBoards', 'weekStart'];

const WEIGHT_FIELDS: readonly PopularNumberField[] = ['likeWeight', 'commentWeight', 'viewWeight'];

export default class UtilPopular {
    static readonly NUMBER_FIELDS = NUMBER_FIELDS;
    static readonly FIELDS = FIELDS;
    static readonly PERIODS: readonly PopularPeriod[] = ['REALTIME', 'YESTERDAY', 'WEEK'];
    /** 서버가 `reasons` 를 주지 않았을 때의 차례(서버 `PopularExcludeReason`) */
    static readonly REASONS: readonly PopularExcludeReason[] = ['INAPPROPRIATE', 'PROMOTION', 'MANIPULATION', 'DISPUTE', 'OTHER'];
    /** 빼기 메모 최대 글자 수 */
    static readonly MEMO_MAX = 100;
    /** 뺀 글 목록 최대 줄 수(서버) */
    static readonly EXCLUDED_LIMIT = 200;
    /** 「다시 계산 중」일 때 다시 읽기까지 기다리는 시간(ms) — 요청 집기 주기가 보통 몇 초다 */
    static readonly RECOMPUTE_POLL_MS = 4000;
    /** 다시 읽기 최대 횟수 — 그 뒤에는 운영자가 [새로고침]을 누른다 */
    static readonly RECOMPUTE_POLL_MAX = 8;

    /**
     * 입력 글자를 숫자로 읽는다 — 앞뒤 공백·천 단위 쉼표는 허용한다. 음수·지수 표기는 받지 않는다.
     * @returns 숫자와 소수 자리 수. 숫자가 아니면 null.
     */
    static parse(text: string): { value: number; decimals: number } | null {
        const cleaned = text.trim().replace(/,/g, '');
        const match = /^(\d{1,9})(?:\.(\d+))?$/.exec(cleaned);
        if (!match) return null;
        return { value: Number(cleaned), decimals: match[2]?.length ?? 0 };
    }

    /** 칸 하나의 문제 — 비었나 · 숫자인가 · 소수 자리 · 범위 */
    static fieldProblem(text: string, limit: PopularLimit): PopularFieldProblem | null {
        if (!text.trim()) return 'REQUIRED';
        const parsed = UtilPopular.parse(text);
        if (!parsed) return 'NOT_NUMBER';
        if (parsed.decimals > limit.decimals) return limit.decimals === 0 ? 'NOT_INTEGER' : 'TOO_MANY_DECIMALS';
        return parsed.value < limit.min || parsed.value > limit.max ? 'OUT_OF_RANGE' : null;
    }

    /** 모든 숫자 칸의 문제 — 칸마다 문제가 없고 세 가중치가 모두 0 이면 `likeWeight` 에 붙인다(서버와 같은 칸) */
    static validate(form: PopularSettingsForm, limits: Record<PopularNumberField, PopularLimit>): Partial<Record<PopularNumberField, PopularFieldProblem>> {
        const out: Partial<Record<PopularNumberField, PopularFieldProblem>> = {};
        for (const field of NUMBER_FIELDS) {
            const problem = UtilPopular.fieldProblem(form[field], limits[field]);
            if (problem) out[field] = problem;
        }
        const weightsOk = WEIGHT_FIELDS.every((field) => !out[field]);
        if (weightsOk && WEIGHT_FIELDS.every((field) => UtilPopular.parse(form[field])?.value === 0)) out.likeWeight = 'ALL_WEIGHTS_ZERO';
        return out;
    }

    /** 서버 값 → 입력값 */
    static formOf(values: PopularSettingValues): PopularSettingsForm {
        const numbers = Object.fromEntries(NUMBER_FIELDS.map((field) => [field, String(values[field])])) as Record<PopularNumberField, string>;
        return { ...numbers, excludeStaffBoards: values.excludeStaffBoards, weekStart: values.weekStart };
    }

    /** 입력값 → 요청(11칸). 숫자 칸 하나라도 읽을 수 없으면 null */
    static toRequest(form: PopularSettingsForm): PopularSettingValues | null {
        const numbers: Partial<Record<PopularNumberField, number>> = {};
        for (const field of NUMBER_FIELDS) {
            const parsed = UtilPopular.parse(form[field]);
            if (!parsed) return null;
            numbers[field] = parsed.value;
        }
        return { ...(numbers as Record<PopularNumberField, number>), excludeStaffBoards: form.excludeStaffBoards, weekStart: form.weekStart };
    }

    /** 저장된 값과 다른 칸 — 숫자로 읽어 비교한다(「3.0」과 「3」은 같다), 읽을 수 없는 글자는 바뀐 것으로 본다 */
    static changedFields(form: PopularSettingsForm, saved: PopularSettingValues): PopularSettingField[] {
        return FIELDS.filter((field) => {
            if (field === 'excludeStaffBoards' || field === 'weekStart') return form[field] !== saved[field];
            return UtilPopular.parse(form[field])?.value !== saved[field];
        });
    }

    /** 기본값과 다른 칸이 있나 — [기본값으로 되돌리기] 를 켤지 */
    static differsFrom(form: PopularSettingsForm, defaults: PopularSettingValues): boolean {
        return UtilPopular.changedFields(form, defaults).length > 0;
    }

    /**
     * 서버가 거절한 칸(`400 VALIDATION` 의 `fields.<칸>`)과 칸에 붙지 않는 문구를 나눈다.
     * @returns 입력 오류가 아니면 null.
     */
    static fieldErrorsOf(e: unknown): { fields: Partial<Record<PopularSettingField, string>>; rest: string } | null {
        if (!(e instanceof ApiError) || !e.fields || Object.keys(e.fields).length === 0) return null;
        const fields: Partial<Record<PopularSettingField, string>> = {};
        const rest: string[] = [];
        for (const [key, message] of Object.entries(e.fields)) {
            if ((FIELDS as readonly string[]).includes(key)) fields[key as PopularSettingField] = message;
            else rest.push(message);
        }
        return { fields, rest: rest.join(' ') };
    }

    /** 점수 — 소수 둘째 자리까지 */
    static score(value: number, locale: string): string {
        return value.toLocaleString(locale, { minimumFractionDigits: 0, maximumFractionDigits: 2 });
    }

    /** 기간의 마지막 순간(끝 `to` 는 미포함이라 1밀리초 앞) */
    static lastInstant(to: string): string {
        return new Date(Date.parse(to) - 1).toISOString();
    }

    /** 빼기 메모 — 앞뒤 공백을 떼고 센다. `OTHER` 는 필수 */
    static memoProblem(reason: PopularExcludeReason, memo: string): PopularMemoProblem | null {
        const length = [...memo.trim()].length;
        if (length > UtilPopular.MEMO_MAX) return 'TOO_LONG';
        return reason === 'OTHER' && length === 0 ? 'REQUIRED' : null;
    }
}
