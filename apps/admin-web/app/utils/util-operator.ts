import type { OperatorRoleOption } from '~/components/operator/OperatorInfoForm.types';
import type { OperatorRoleRef, OperatorView } from '~/composables/operator/api/useOperatorApi.types';

/** 상태 딱지 하나 — 왜 막혔는지를 하나씩 짚는다("막힘" 한 덩어리로는 무엇을 풀어야 할지 모른다) */
export type OperatorStatusKey = 'pending' | 'locked' | 'expired' | 'disabled' | 'normal';

export type OperatorTagSeverity = 'success' | 'warn' | 'danger' | 'secondary';

/**
 * 운영자 화면이 쓰는 계산. 서버 규칙(로그인 조건: 승인 · 잠기지 않음 · 사용 중 · 기간 안 — `docs/domain/operator-permissions.md` 6번)을 미리 알려 주는 용도이고 판정은 서버다.
 */
export default class UtilOperator {
    /** 목록에서 역할 딱지를 몇 개까지 보이고 나머지를 "+N" 으로 줄이는가 */
    static readonly ROLE_TAGS_SHOWN = 2;

    /** 초대 링크의 유효기간(시간) — 가정(정책 문서 "추가 확정": 기본 72시간, 구현 때 조정 가능). 서버가 정한 값이 오면 그것을 쓴다 */
    static readonly INVITE_VALID_HOURS = 72;

    /** 반려 사유의 최대 길이 — 가정 */
    static readonly REJECT_REASON_MAX = 200;

    /**
     * 이 운영자의 상태 딱지들. 어느 것도 아니면 `normal` 하나.
     * @param {OperatorView} o - 운영자.
     * @returns {OperatorStatusKey[]} 딱지 키들.
     */
    static statusKeys(o: OperatorView): OperatorStatusKey[] {
        const out: OperatorStatusKey[] = [];
        if (!o.approved) out.push('pending');
        if (o.locked) out.push('locked');
        if (o.expired || o.periodOver) out.push('expired');
        if (!o.enabled) out.push('disabled');
        if (!out.length) out.push('normal');
        return out;
    }

    static statusSeverity(key: OperatorStatusKey): OperatorTagSeverity {
        return { pending: 'warn', locked: 'danger', expired: 'danger', disabled: 'secondary', normal: 'success' }[key] as OperatorTagSeverity;
    }

    /** 색만으로 구분하지 않도록 딱지에 함께 붙이는 아이콘 */
    static statusIcon(key: OperatorStatusKey): string {
        return { pending: 'pi pi-clock', locked: 'pi pi-lock', expired: 'pi pi-calendar-times', disabled: 'pi pi-ban', normal: 'pi pi-check-circle' }[key];
    }

    /**
     * 목록의 역할 칸 — 앞의 몇 개만 딱지로, 나머지는 "+N" 으로.
     * @param {OperatorRoleRef[]} roles - 받은 역할들.
     * @returns {{ shown: OperatorRoleRef[]; rest: OperatorRoleRef[] }} 보일 것과 줄일 것.
     */
    static splitRoles(roles: OperatorRoleRef[]): { shown: OperatorRoleRef[]; rest: OperatorRoleRef[] } {
        return { shown: roles.slice(0, UtilOperator.ROLE_TAGS_SHOWN), rest: roles.slice(UtilOperator.ROLE_TAGS_SHOWN) };
    }

    /**
     * 시작이 끝보다 뒤인가(거꾸로) — 서버는 `OPERATOR_PERIOD_REVERSED` 로 거절한다.
     * @param {string} start - 시작 (UTC ISO, 비면 '').
     * @param {string} end - 종료 (UTC ISO, 비면 '').
     * @returns {boolean} 거꾸로면 true.
     */
    static isPeriodReversed(start: string, end: string): boolean {
        return !!start && !!end && Date.parse(start) > Date.parse(end);
    }

    /**
     * 역할 고르개의 항목을 역할 유형(탭) 이름별 묶음으로 — 처음 나온 차례를 지킨다. 고르개가 묶음 소제목으로 보인다.
     * @param {OperatorRoleOption[]} options - 고를 수 있는 운영자 역할들.
     * @returns {{ label: string; items: OperatorRoleOption[] }[]} 유형 이름별 묶음.
     */
    static groupRoleOptions(options: OperatorRoleOption[]): { label: string; items: OperatorRoleOption[] }[] {
        const out: { label: string; items: OperatorRoleOption[] }[] = [];
        for (const option of options) {
            const group = out.find((g) => g.label === option.group);
            if (group) group.items.push(option);
            else out.push({ label: option.group, items: [option] });
        }
        return out;
    }

    /** 이메일 형식 — 초대 목록에 넣기 전에 알려 주는 용도(서버가 다시 판정한다) */
    static isEmail(value: string): boolean {
        return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
    }
}
