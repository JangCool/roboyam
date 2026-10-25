import type { MemberRestriction, MemberRestrictionStatus, MemberRestrictionType } from '~/composables/member/api/useMemberRestrictionApi.types';
import { RESTRICTION_MEMO_MAX, RESTRICTION_REASON_MAX, RESTRICTION_REASON_MIN } from '~/composables/member/api/useMemberRestrictionApi.types';
import type { TagSeverity } from '~/utils/util-member';

/** 기간 고르기 — 1·3·7·30일(지금부터), 직접 끝 시각, 무기한 */
export type RestrictionDuration = 'D1' | 'D3' | 'D7' | 'D30' | 'CUSTOM' | 'FOREVER';

/** 사유 칸 문제 — 비었음 · 길이 */
export type RestrictionReasonProblem = 'REQUIRED' | 'LENGTH' | null;

const DAY = 24 * 60 * 60 * 1000;
const DAYS: Partial<Record<RestrictionDuration, number>> = { D1: 1, D3: 3, D7: 7, D30: 30 };

/**
 * 회원 기능 제한(회원 상세 「기능 제한」 구획·제한 걸기 대화상자)이 쓰는 계산. 판정(권한·중복·기간)은 서버 몫이고 여기는 미리 알려 주는 값만 만든다.
 * 서버 규칙(최종 판정은 서버): `MemberRestrictionService`(공개 사유·해제 사유 5~200자, 운영 메모 ~1000자, 끝 시각은 지금보다 뒤).
 */
export default class UtilMemberRestriction {
    static readonly TYPES: readonly MemberRestrictionType[] = ['POINT_FREEZE', 'GROUP_BUY_BAN', 'CHARGE_BAN'];
    static readonly DURATIONS: readonly RestrictionDuration[] = ['D1', 'D3', 'D7', 'D30', 'CUSTOM', 'FOREVER'];
    static readonly REASON_MIN = RESTRICTION_REASON_MIN;
    static readonly REASON_MAX = RESTRICTION_REASON_MAX;
    static readonly MEMO_MAX = RESTRICTION_MEMO_MAX;

    /** 종류 아이콘 — 글자와 함께 쓴다 */
    static typeIcon(type: MemberRestrictionType): string {
        if (type === 'POINT_FREEZE') return 'pi pi-lock';
        if (type === 'GROUP_BUY_BAN') return 'pi pi-shopping-bag';
        return 'pi pi-wallet';
    }

    static typeSeverity(type: MemberRestrictionType): TagSeverity {
        if (type === 'POINT_FREEZE') return 'info';
        if (type === 'GROUP_BUY_BAN') return 'warn';
        return 'contrast';
    }

    static statusIcon(status: MemberRestrictionStatus): string {
        if (status === 'ACTIVE') return 'pi pi-ban';
        if (status === 'LIFTED') return 'pi pi-unlock';
        return 'pi pi-clock';
    }

    static statusSeverity(status: MemberRestrictionStatus): TagSeverity {
        return status === 'ACTIVE' ? 'danger' : 'secondary';
    }

    /**
     * 고른 기간의 끝 순간.
     * @param {RestrictionDuration} duration - 기간.
     * @param {string} custom - 직접 고른 끝 순간(ISO) — `CUSTOM` 일 때.
     * @param {number} now - 지금(밀리초).
     * @returns {string | null} 끝 순간. 무기한이면 null, 직접인데 비었으면 ''.
     */
    static untilOf(duration: RestrictionDuration, custom: string, now: number = Date.now()): string | null {
        if (duration === 'FOREVER') return null;
        if (duration === 'CUSTOM') return custom;
        return new Date(now + (DAYS[duration] ?? 1) * DAY).toISOString();
    }

    /**
     * 같은 종류로 진행 중인 제한 중 가장 늦게 끝나는 것 — 새로 걸 때 경고에 쓴다(막지는 않는다).
     * @param {readonly MemberRestriction[]} items - 목록.
     * @param {MemberRestrictionType} type - 걸려는 종류.
     * @returns {MemberRestriction | null} 진행 중인 같은 종류. 없으면 null. 무기한이 있으면 그것.
     */
    static activeSameType(items: readonly MemberRestriction[], type: MemberRestrictionType): MemberRestriction | null {
        const same = items.filter((r) => r.status === 'ACTIVE' && r.type === type);
        if (!same.length) return null;
        return same.find((r) => r.until === null) ?? same.reduce((a, b) => (Date.parse(b.until ?? '') > Date.parse(a.until ?? '') ? b : a));
    }

    /** 공개 사유·해제 사유 검사(앞뒤 공백 뺀 길이) */
    static reasonProblem(reason: string): RestrictionReasonProblem {
        const length = reason.trim().length;
        if (length === 0) return 'REQUIRED';
        if (length < RESTRICTION_REASON_MIN || length > RESTRICTION_REASON_MAX) return 'LENGTH';
        return null;
    }

    /** 진행 중이 먼저, 그다음 최근에 시작한 것부터 — 서버 순서가 같지만 걸기·해제 뒤 화면이 직접 끼울 때도 같은 차례를 지킨다 */
    static sorted(items: readonly MemberRestriction[]): MemberRestriction[] {
        return [...items].sort((a, b) => Number(b.status === 'ACTIVE') - Number(a.status === 'ACTIVE') || Date.parse(b.startsAt) - Date.parse(a.startsAt));
    }
}
