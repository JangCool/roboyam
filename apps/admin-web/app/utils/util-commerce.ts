import type { RequestLimitAction, RequestLimitItem } from '~/composables/commerce/api/useCommerceSettingsApi.types';
import { REQUEST_LIMIT_ACTIONS } from '~/composables/commerce/api/useCommerceSettingsApi.types';

/** 요청 제한 칸 하나의 문제 — 비었나 · 정수인가 · 0~100 안인가 */
export type RequestLimitProblem = 'REQUIRED' | 'NOT_INTEGER' | 'OUT_OF_RANGE';

/** 입력 중인 값 — 숫자 칸은 글자로 들고 저장할 때 바꾼다(`InputNumber` 는 범위 밖 값을 말없이 고친다) */
export type RequestLimitForm = Record<RequestLimitAction, string>;

/**
 * 커머스 설정 화면의 순수 규칙 — 판정은 서버다(서버 `CommerceRequestLimitService.check` — 같은 범위 0~100, `RequestAction` 의 기본값).
 * 요청 제한: 종류별 회원 1분 한도, 0~100 정수, 0 = 제한 없음(`docs/domain/commerce.md` 12-4).
 */
export default class UtilCommerce {
    static readonly ACTIONS = REQUEST_LIMIT_ACTIONS;
    static readonly REQUEST_LIMIT = { min: 0, max: 100 } as const;

    /**
     * 입력 글자를 0 이상 정수로 읽는다 — 앞뒤 공백은 허용한다.
     * @returns 정수. 비었거나 정수가 아니면 null.
     */
    static parse(text: string): number | null {
        const cleaned = text.trim();
        return /^\d{1,9}$/.test(cleaned) ? Number(cleaned) : null;
    }

    /** 칸 하나의 문제 */
    static limitProblem(text: string): RequestLimitProblem | null {
        if (!text.trim()) return 'REQUIRED';
        const value = UtilCommerce.parse(text);
        if (value === null) return 'NOT_INTEGER';
        return value < UtilCommerce.REQUEST_LIMIT.min || value > UtilCommerce.REQUEST_LIMIT.max ? 'OUT_OF_RANGE' : null;
    }

    /** 서버 값 → 입력 글자 */
    static formOf(items: readonly RequestLimitItem[]): RequestLimitForm {
        const out = {} as RequestLimitForm;
        for (const action of UtilCommerce.ACTIONS) out[action] = String(items.find((item) => item.action === action)?.limitPerMinute ?? '');
        return out;
    }

    /** 입력 글자 → 요청 값(네 종류 모두). 하나라도 읽을 수 없으면 null */
    static toLimits(form: RequestLimitForm): Record<RequestLimitAction, number> | null {
        const out = {} as Record<RequestLimitAction, number>;
        for (const action of UtilCommerce.ACTIONS) {
            const value = UtilCommerce.parse(form[action]);
            if (value === null) return null;
            out[action] = value;
        }
        return out;
    }
}
