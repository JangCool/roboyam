import type { BizChoice, DeliveredChoice, PaidPointChoice, WithdrawalGroupChoice, WithdrawalPendingItem, WithdrawalView } from '~/composables/account/api/useWithdrawalApi.types';

/** 화면이 들고 있는 선택 값 — 서버 규칙 이름: 탈퇴 신청(`commerce.md` 17-1, 서버 `WithdrawalService.request`) */
export interface WithdrawalChoices {
    paidPointChoice: PaidPointChoice;
    deliveredChoice: DeliveredChoice;
    bizChoice: BizChoice;
    groupChoices: WithdrawalGroupChoice[];
    paidForfeitConsent: boolean;
    bizForfeitConsent: boolean;
}

/**
 * 회원 탈퇴 화면의 순수 계산 — 탈퇴가 바로 끝나는지(17-1), 제출 가능 여부.
 * 화면 검사는 미리 알려 주는 용도이고 판정은 서버다(환불 예상액·수수료·재가입 가능일은 서버가 계산해 `WithdrawalView` 로 준다).
 */
export default class UtilWithdrawal {
    /**
     * 지금 선택대로 신청하면 기다려야 하는 일 — 이미 남은 일 + 「기다리기」를 고른 것(배송완료 주문·광고·핫딜) + 「환불받고」를 고른 충전 포인트.
     * @param {WithdrawalView} view - 탈퇴 정보.
     * @param {WithdrawalChoices} choices - 고른 것.
     * @returns {WithdrawalPendingItem[]} 남을 일. 비어 있으면 즉시 완료.
     */
    static pendingAfter(view: WithdrawalView, choices: WithdrawalChoices): WithdrawalPendingItem[] {
        const out: WithdrawalPendingItem[] = [...view.pending];
        if (view.delivered.length > 0 && choices.deliveredChoice === 'WAIT') {
            for (const order of view.delivered) out.push({ code: 'DELIVERED_RETURN_WINDOW', label: order.orderNo, link: order.link, waitUntil: order.returnDeadline });
        }
        if (view.business && view.business.running.length > 0 && choices.bizChoice === 'WAIT') {
            for (const run of view.business.running) out.push({ code: 'BIZ_APPLICATION', label: run.label, link: run.link, waitUntil: run.endsAt });
        }
        if (view.money && choices.paidPointChoice === 'REFUND') {
            out.push({ code: 'POINT_CHARGE_REFUND', label: null, link: '/me/points', waitUntil: null });
        }
        return out;
    }

    /**
     * 포기 동의가 아직 안 된 것이 있는가 — 「포기하고 탈퇴」를 골랐는데 체크가 없으면 true.
     * @param {WithdrawalView} view - 탈퇴 정보.
     * @param {WithdrawalChoices} choices - 고른 것.
     * @returns {boolean} 동의가 더 필요하면 true.
     */
    static consentMissing(view: WithdrawalView, choices: WithdrawalChoices): boolean {
        if (view.money && choices.paidPointChoice === 'FORFEIT' && !choices.paidForfeitConsent) return true;
        if (view.business && view.business.running.length > 0 && choices.bizChoice === 'FORFEIT' && !choices.bizForfeitConsent) return true;
        return false;
    }

    /**
     * 「넘기기」를 골랐는데 받을 사람을 아직 안 고른 모임이 있는가.
     * @param {readonly WithdrawalGroupChoice[]} groupChoices - 모임마다의 선택.
     * @returns {boolean} 있으면 true.
     */
    static transferTargetMissing(groupChoices: readonly WithdrawalGroupChoice[]): boolean {
        return groupChoices.some((choice) => choice.action === 'TRANSFER' && !choice.toMemberId);
    }

    /**
     * 본인 확인이 됐는가 — 비밀번호 계정은 비밀번호를 적었는지, 소셜 전용은 최근 로그인인지.
     * @param {WithdrawalView} view - 탈퇴 정보.
     * @param {string} password - 비밀번호 칸 값.
     * @returns {boolean} 됐으면 true.
     */
    static verified(view: WithdrawalView, password: string): boolean {
        return view.verification.method === 'PASSWORD' ? password.length > 0 : view.verification.recentLoginOk;
    }
}
