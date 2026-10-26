import { ApiError } from '~/composables/common/useApiClient';

/**
 * 기능 제한 종류(commerce.md 12-3 — 서버 `member_restrictions.type`). 계정 정지(`users.status`)와 따로 걸린다.
 * - `POINT_FREEZE` 포인트 동결: 적립·사용·충전을 막는다(충전 포인트 환불 신청은 된다)
 * - `GROUP_BUY_BAN` 공동구매 참여 제한: 새 참여를 막는다(이미 결제한 주문의 배송·취소·반품·조회는 된다)
 * - `CHARGE_BAN` 충전 제한: 새 충전을 막는다(기존 포인트 사용·환불은 된다)
 */
export type FeatureRestrictionType = 'POINT_FREEZE' | 'GROUP_BUY_BAN' | 'CHARGE_BAN';

/** 막힌 기능을 쓰려 할 때 서버가 주는 제한 한 건 — 403 `MEMBER_RESTRICTED` 의 오류 본문 최상위 `restriction`(`ApiError.extras.restriction`, 서버 `MemberRestrictedException`). 운영 메모는 오지 않는다 */
export interface FeatureRestriction {
    type: FeatureRestrictionType;
    /** 끝나는 순간(ISO-8601 UTC). 무기한이면 null */
    until: string | null;
    /** 회원에게 공개하는 사유 */
    reason: string;
}

const TYPES: readonly FeatureRestrictionType[] = ['POINT_FREEZE', 'GROUP_BUY_BAN', 'CHARGE_BAN'];

/** 기능 제한 안내(`FeatureRestrictionNotice`)가 쓰는 규칙 — 서버 오류에서 제한을 꺼내고 종류별 아이콘을 고른다 */
export default class UtilFeatureRestriction {
    /** 막힌 기능을 쓰려 할 때의 오류 코드(403) */
    static readonly ERROR_CODE = 'MEMBER_RESTRICTED';
    static readonly TYPES = TYPES;

    /**
     * 잡은 오류가 기능 제한(403 `MEMBER_RESTRICTED`)이면 그 제한을 꺼낸다.
     * @param {unknown} e - 잡은 오류.
     * @returns {FeatureRestriction | null} 제한. 기능 제한 오류가 아니거나 모양이 틀리면 null(그때는 서버 문구를 그대로 보인다).
     */
    static fromError(e: unknown): FeatureRestriction | null {
        if (!(e instanceof ApiError) || e.code !== UtilFeatureRestriction.ERROR_CODE) return null;
        const raw = e.extras?.restriction as Partial<FeatureRestriction> | undefined;
        if (!raw || !TYPES.includes(raw.type as FeatureRestrictionType)) return null;
        return {
            type: raw.type as FeatureRestrictionType,
            until: typeof raw.until === 'string' ? raw.until : null,
            reason: typeof raw.reason === 'string' ? raw.reason : '',
        };
    }

    /**
     * 종류별 아이콘 — 글자와 함께 쓴다(색만으로 가르지 않는다).
     * @param {FeatureRestrictionType} type - 종류.
     * @returns {string} PrimeIcons 클래스.
     */
    static icon(type: FeatureRestrictionType): string {
        if (type === 'POINT_FREEZE') return 'pi pi-lock';
        if (type === 'GROUP_BUY_BAN') return 'pi pi-shopping-bag';
        return 'pi pi-wallet';
    }
}
