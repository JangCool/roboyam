package com.community.withdrawal.dto;

import com.community.withdrawal.entity.WithdrawalSource;
import java.util.Map;

/**
 * 탈퇴 신청 한 건의 처리 명령 — 회원 본인 신청(api {@code WithdrawalService} 가 검사·본인 확인을 끝낸 뒤)과 운영자 강제 탈퇴(admin)가 <b>같은 처리</b>
 * ({@code WithdrawalRequestWriter#apply})를 부를 때 넘긴다.
 *
 * @param userId     탈퇴할 회원
 * @param source     회원 본인 · 운영자 강제
 * @param reason     탈퇴 사유 6종 또는 null(강제 탈퇴는 null)
 * @param reasonNote 탈퇴 사유 메모(정규화 뒤, 없으면 null)
 * @param choices    고른 것 — 강제 탈퇴는 회원에게 유리한 기본값으로 고정({@link WithdrawalChoices#DEFAULT})
 * @param groupPlans 내가 모임장인 소모임마다 처리(빠진 모임은 AUTO) — 강제 탈퇴는 모두 AUTO
 * @param consent    포기 동의 금액(검사한 값, 동의할 것이 없으면 {@link Consent#NONE})
 * @param force      운영자 강제 탈퇴 정보({@code source = OPERATOR} 일 때만, 아니면 null)
 * @param ip         요청자 IP(감사)
 */
public record WithdrawalApplication(long userId, WithdrawalSource source, String reason, String reasonNote, WithdrawalChoices choices, Map<Long, GroupPlan> groupPlans,
                                    Consent consent, Force force, String ip) {

    public WithdrawalApplication {
        groupPlans = groupPlans == null ? Map.of() : Map.copyOf(groupPlans);
        consent = consent == null ? Consent.NONE : consent;
        if ((source == WithdrawalSource.OPERATOR) != (force != null)) {
            throw new IllegalArgumentException("운영자 강제 탈퇴는 force 가 있어야 하고 회원 신청은 없어야 한다");
        }
    }

    /** 소모임 하나의 처리 — {@code AUTO} · {@code TRANSFER}(대상 멤버) · {@code DISSOLVE} */
    public record GroupPlan(String action, Long toMemberId) {

        public static final GroupPlan AUTO = new GroupPlan("AUTO", null);
    }

    /** 포기 동의 — 검사한 금액(동의가 필요 없으면 null) */
    public record Consent(Long paidPoints, Long refundEstimate, Long bizForfeit) {

        public static final Consent NONE = new Consent(null, null, null);

        public boolean consented() {
            return paidPoints != null || bizForfeit != null;
        }
    }

    /**
     * 운영자 강제 탈퇴(member-withdrawal-contract 「관리자」) — 사유 종류 {@code UNDER_14 · POLICY_VIOLATION · ON_BEHALF · OTHER} + 운영 메모 5~500자(검사는 admin 이 끝냈다).
     */
    public record Force(long operatorId, String reasonKind, String memo) {
    }

    /** 회원 본인 신청 */
    public static WithdrawalApplication byMember(long userId, String reason, String reasonNote, WithdrawalChoices choices, Map<Long, GroupPlan> groupPlans, Consent consent,
                                                 String ip) {
        return new WithdrawalApplication(userId, WithdrawalSource.MEMBER, reason, reasonNote, choices, groupPlans, consent, null, ip);
    }

    /** 운영자 강제 탈퇴 — 선택값 고정(배송·광고 기다리기 · 충전 환불 · 모임 자동), 사유·포기 동의 없음 */
    public static WithdrawalApplication byOperator(long userId, Map<Long, GroupPlan> groupPlans, Force force, String ip) {
        return new WithdrawalApplication(userId, WithdrawalSource.OPERATOR, null, null, WithdrawalChoices.DEFAULT, groupPlans, Consent.NONE, force, ip);
    }
}
