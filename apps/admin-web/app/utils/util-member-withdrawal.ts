import UtilTime from '#layers/display-time/utils/util-time';
import UtilMember, { type TagSeverity } from '~/utils/util-member';
import type {
    ForceWithdrawReasonKind,
    MemberWithdrawalQuery,
    WithdrawalCancelChannel,
    WithdrawalEvent,
    WithdrawalSource,
    WithdrawalPendingCode,
    WithdrawalReason,
    WithdrawalReasonCount,
    WithdrawalStatus,
} from '~/composables/member/api/useMemberWithdrawalApi.types';

/** 탈퇴 관리 화면의 조회 조건 — 날짜는 달력 글자(`YYYY-MM-DD`), 보낼 때 사이트 시간대 경계로 바꾼다. 상태 ''는 전체 */
export interface MemberWithdrawalFilter {
    status: WithdrawalStatus | '';
    memberNo: string;
    requestedFrom: string;
    requestedTo: string;
    completedFrom: string;
    completedTo: string;
}

/** 사유 분포 막대의 한 토막 — 비율(%)·색 */
export interface ReasonBarSegment {
    reason: WithdrawalReason | null;
    count: number;
    percent: number;
    colorClass: string;
}

/**
 * 회원 탈퇴 관리 화면이 쓰는 계산(판정은 서버 — 여기는 보여 주기 위한 값). 서버 enum `WithdrawalStatus`·`WithdrawalPendingCode`·`member_withdrawals.reason` 과 같은 값.
 */
export default class UtilMemberWithdrawal {
    static readonly STATUSES: readonly WithdrawalStatus[] = ['SCHEDULED', 'COMPLETED', 'CANCELED'];
    /** 남은 일 종류 표시 차례 */
    static readonly PENDING_CODES: readonly WithdrawalPendingCode[] = ['GROUP_BUY_ORDER', 'DELIVERED_RETURN_WINDOW', 'RETURN_REQUEST', 'PAYMENT_CANCEL', 'POINT_CHARGE_REFUND', 'POINT_SHOP_REFUND', 'BIZ_APPLICATION'];
    /** 사유 6종 + 고르지 않음(null) — 분포 막대 차례 */
    static readonly REASONS: readonly (WithdrawalReason | null)[] = ['NOT_USING', 'PRIVACY', 'INCONVENIENT', 'OTHER_SERVICE', 'REJOIN', 'OTHER', null];
    /** 강제 탈퇴 사유 종류(서버 `MemberWithdrawalAdminRequest.Force.reasonKind`) */
    static readonly FORCE_REASON_KINDS: readonly ForceWithdrawReasonKind[] = ['UNDER_14', 'POLICY_VIOLATION', 'ON_BEHALF', 'OTHER'];
    /** 신청 뒤 이 날수를 넘겨도 진행 중이면 주의 색(서버 배치의 10일 안내와 같은 기준) */
    static readonly OVERDUE_DAYS = 10;
    /** 서버가 받는 정렬 칸 */
    static readonly SORT_FIELDS: readonly string[] = ['requestedAt', 'expectedCompleteAt', 'completedAt', 'canceledAt', 'pendingCount'];
    static readonly DEFAULT_SORT = 'requestedAt,desc';

    static emptyFilter(): MemberWithdrawalFilter {
        return { status: 'SCHEDULED', memberNo: '', requestedFrom: '', requestedTo: '', completedFrom: '', completedTo: '' };
    }

    /** 주소 쿼리(`?status=&completedFrom=…`) → 조건. 모르는 값은 기본값 */
    static filterFromQuery(query: Record<string, unknown>): MemberWithdrawalFilter {
        const str = (key: string): string => (typeof query[key] === 'string' ? (query[key] as string) : '');
        const status = str('status');
        return {
            // `ALL` 은 전체, 아는 상태면 그 상태, 없거나 모르는 값이면 기본(진행 중)
            status: status === 'ALL' ? '' : (UtilMemberWithdrawal.STATUSES as readonly string[]).includes(status) ? (status as WithdrawalStatus) : 'SCHEDULED',
            memberNo: str('memberNo'),
            requestedFrom: str('requestedFrom'),
            requestedTo: str('requestedTo'),
            completedFrom: str('completedFrom'),
            completedTo: str('completedTo'),
        };
    }

    /** 조건 → 주소 쿼리(빈 값은 빼고, 전체 상태는 `ALL`) */
    static filterToQuery(filter: MemberWithdrawalFilter): Record<string, string> {
        const out: Record<string, string> = { status: filter.status || 'ALL' };
        for (const key of ['memberNo', 'requestedFrom', 'requestedTo', 'completedFrom', 'completedTo'] as const) if (filter[key]) out[key] = filter[key];
        return out;
    }

    /**
     * 끝 날짜(`YYYY-MM-DD`)의 **다음 날 0시** 순간 — 서버 기간 조건이 반열림 `[from, to)` 라 끝 날짜를 통째로 넣으려면 다음 날 시작을 보낸다(`UtilMember.dayEnd` 의 23:59:59.999 는 마지막 1ms 가 빠진다).
     * 읽을 수 없으면 undefined.
     */
    static dayAfterStart(ymd: string, zoneId: string): string | undefined {
        const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(ymd);
        if (!m) return undefined;
        // 달·해 넘김은 Date.UTC 가 정리한다(10월 31일 + 1 = 11월 1일)
        const next = new Date(Date.UTC(Number(m[1]), Number(m[2]) - 1, Number(m[3]) + 1));
        return UtilTime.fromWall(next.getUTCFullYear(), next.getUTCMonth() + 1, next.getUTCDate(), 0, 0, zoneId) ?? undefined;
    }

    /** 화면 조건 → 서버 조회 조건(쪽·정렬은 부르는 쪽이 얹는다). 기간은 `[그 날 0시, 끝 날 다음 날 0시)` */
    static toQuery(filter: MemberWithdrawalFilter, zoneId: string): MemberWithdrawalQuery {
        const q: MemberWithdrawalQuery = {};
        if (filter.status) q.status = filter.status;
        const memberNo = filter.memberNo.trim();
        if (memberNo) q.memberNo = memberNo;
        const from = (ymd: string) => (ymd ? UtilMember.dayStart(ymd, zoneId) : undefined);
        const to = (ymd: string) => (ymd ? UtilMemberWithdrawal.dayAfterStart(ymd, zoneId) : undefined);
        q.requestedFrom = from(filter.requestedFrom);
        q.requestedTo = to(filter.requestedTo);
        q.completedFrom = from(filter.completedFrom);
        q.completedTo = to(filter.completedTo);
        for (const key of Object.keys(q) as (keyof MemberWithdrawalQuery)[]) if (q[key] === undefined) delete q[key];
        return q;
    }

    static statusSeverity(status: WithdrawalStatus): TagSeverity {
        return { SCHEDULED: 'warn', COMPLETED: 'secondary', CANCELED: 'info' }[status] as TagSeverity;
    }

    static statusIcon(status: WithdrawalStatus): string {
        return { SCHEDULED: 'pi pi-hourglass', COMPLETED: 'pi pi-sign-out', CANCELED: 'pi pi-undo' }[status];
    }

    static pendingIcon(code: WithdrawalPendingCode): string {
        return {
            GROUP_BUY_ORDER: 'pi pi-shopping-bag',
            DELIVERED_RETURN_WINDOW: 'pi pi-box',
            RETURN_REQUEST: 'pi pi-replay',
            PAYMENT_CANCEL: 'pi pi-credit-card',
            POINT_CHARGE_REFUND: 'pi pi-wallet',
            POINT_SHOP_REFUND: 'pi pi-gift',
            BIZ_APPLICATION: 'pi pi-megaphone',
        }[code];
    }

    /**
     * 남은 일 한 건의 관리자 화면 주소 — 기존 화면의 서랍 쿼리를 연다. 대상 ID 가 없거나 화면이 없는 종류(충전 환불 — 커머스 3단계 전)는 null.
     * 공구 주문 `/group-buy-orders?order=` · 반품 요청 `/group-buy-requests?request=` · 결제 취소 `/payments/orders?payment=` · 포인트샵 환불 `/point-shop/purchases?refundRequest=` · 광고·핫딜 신청 `/ad-reviews?id=`.
     */
    static pendingLink(code: WithdrawalPendingCode, refId: string | null): { path: string; query: Record<string, string> } | null {
        if (!refId) return null;
        switch (code) {
            case 'GROUP_BUY_ORDER':
            case 'DELIVERED_RETURN_WINDOW':
                return { path: '/group-buy-orders', query: { order: refId } };
            case 'RETURN_REQUEST':
                return { path: '/group-buy-requests', query: { request: refId } };
            case 'PAYMENT_CANCEL':
                return { path: '/payments/orders', query: { payment: refId } };
            case 'POINT_SHOP_REFUND':
                return { path: '/point-shop/purchases', query: { refundRequest: refId } };
            case 'BIZ_APPLICATION':
                return { path: '/ad-reviews', query: { id: refId } };
            default:
                return null;
        }
    }

    /** 신청 뒤 10일을 넘겼는데 아직 진행 중인가 */
    static isOverdue(status: WithdrawalStatus, requestedAt: string, now: number = Date.now()): boolean {
        return status === 'SCHEDULED' && now - Date.parse(requestedAt) > UtilMemberWithdrawal.OVERDUE_DAYS * 86_400_000;
    }

    /** 신청 뒤 지난 날수(내림) */
    static daysSince(requestedAt: string, now: number = Date.now()): number {
        return Math.max(0, Math.floor((now - Date.parse(requestedAt)) / 86_400_000));
    }

    /** 종류별 개수 → 표시 차례의 칩 목록(0 은 뺀다) */
    static pendingChips(counts: Partial<Record<WithdrawalPendingCode, number>>): { code: WithdrawalPendingCode; count: number }[] {
        return UtilMemberWithdrawal.PENDING_CODES.filter((code) => (counts[code] ?? 0) > 0).map((code) => ({ code, count: counts[code]! }));
    }

    static eventIcon(kind: WithdrawalEvent['kind']): string {
        return { REQUESTED: 'pi pi-sign-out', PROGRESS_MAILED: 'pi pi-envelope', COMPLETED: 'pi pi-check-circle', CANCELED: 'pi pi-undo', OPERATOR_CANCELED: 'pi pi-headphones', FORCED: 'pi pi-user-minus' }[kind];
    }

    static eventSeverity(kind: WithdrawalEvent['kind']): TagSeverity {
        return ({ REQUESTED: 'warn', PROGRESS_MAILED: 'info', COMPLETED: 'secondary', CANCELED: 'info', OPERATOR_CANCELED: 'info', FORCED: 'danger' } as Record<WithdrawalEvent['kind'], TagSeverity>)[kind];
    }

    /** 운영자 대리 취소의 요청 경로 차례 */
    static readonly CANCEL_CHANNELS: readonly WithdrawalCancelChannel[] = ['CS_INQUIRY', 'PHONE', 'EMAIL', 'OTHER'];

    static cancelChannelIcon(channel: WithdrawalCancelChannel): string {
        return { CS_INQUIRY: 'pi pi-comments', PHONE: 'pi pi-phone', EMAIL: 'pi pi-envelope', OTHER: 'pi pi-ellipsis-h' }[channel];
    }

    /** 운영자가 대리 취소할 수 있는 기록인가 — 진행 중이고 회원이 신청한 것만(운영자 강제 탈퇴는 회원이 요청해도 불가) */
    static canOperatorCancel(status: WithdrawalStatus, source: WithdrawalSource): boolean {
        return status === 'SCHEDULED' && source === 'MEMBER';
    }

    static forceReasonIcon(kind: ForceWithdrawReasonKind): string {
        return { UNDER_14: 'pi pi-id-card', POLICY_VIOLATION: 'pi pi-ban', ON_BEHALF: 'pi pi-phone', OTHER: 'pi pi-ellipsis-h' }[kind];
    }

    /** 사유 분포 → 막대 토막(비율은 합이 100 이 되게 마지막 토막에서 맞춘다). 0 건은 뺀다 */
    static reasonSegments(items: readonly WithdrawalReasonCount[], total: number): ReasonBarSegment[] {
        if (total <= 0) return [];
        const colors: Record<string, string> = {
            NOT_USING: 'bg-sky-500',
            PRIVACY: 'bg-violet-500',
            INCONVENIENT: 'bg-amber-500',
            OTHER_SERVICE: 'bg-emerald-500',
            REJOIN: 'bg-rose-500',
            OTHER: 'bg-slate-500',
            NONE: 'bg-surface-300 dark:bg-surface-600',
        };
        const ordered = UtilMemberWithdrawal.REASONS.map((reason) => items.find((i) => i.reason === reason) ?? { reason, count: 0 }).filter((i) => i.count > 0);
        let used = 0;
        return ordered.map((item, index) => {
            const percent = index === ordered.length - 1 ? Math.max(0, 100 - used) : Math.round((item.count / total) * 100);
            used += percent;
            return { reason: item.reason, count: item.count, percent, colorClass: colors[item.reason ?? 'NONE']! };
        });
    }

    /** 사유 키(null → `NONE`) — i18n 키 조각 */
    static reasonKey(reason: WithdrawalReason | null): string {
        return reason ?? 'NONE';
    }
}
