package com.community.groupbuy.service;

import com.community.groupbuy.dto.GroupBuyBuyer;
import com.community.groupbuy.exception.GroupBuyBlockedException;
import com.community.groupbuy.exception.GroupBuyException;
import com.community.groupbuy.repository.master.GroupBuyMemberStore;
import com.community.identity.service.IdentityVerificationService;
import com.community.restriction.entity.RestrictedFeature;
import com.community.restriction.service.MemberRestrictionService;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * 공구 새 주문 자격(commerce.md 10-5 「참여 자격」) — 차례대로: 로그인(없거나 탈퇴 401) → 정상 회원(정지·영구 정지 403 {@code ACCOUNT_RESTRICTED}) → 이메일 인증
 * ({@code EMAIL_NOT_VERIFIED}) → 회원 권한 {@code commerce.purchase}({@code PERMISSION_DENIED}) → 본인인증·만 19세({@code IDENTITY_REQUIRED}·{@code IDENTITY_UNDERAGE}) →
 * 공구 참여 제한({@code MEMBER_RESTRICTED} — 선점 반복 이탈 자동 제한 포함) → 판매 스위치({@code GROUP_BUY_SALES_CLOSED}).
 * 정지 회원은 새 주문만 막고 진행 중 주문(결제 확정·취소·조회)은 된다 — 이 검사는 새 주문에만 쓴다.
 */
@Component
public class GroupBuyEligibility {

    private final GroupBuyMemberStore members;
    private final IdentityVerificationService identity;
    private final MemberRestrictionService restrictions;
    private final GroupBuySalesSwitch salesSwitch;

    public GroupBuyEligibility(GroupBuyMemberStore members, IdentityVerificationService identity, MemberRestrictionService restrictions, GroupBuySalesSwitch salesSwitch) {
        this.members = members;
        this.identity = identity;
        this.restrictions = restrictions;
        this.salesSwitch = salesSwitch;
    }

    public void assertCanJoin(GroupBuyBuyer buyer) {
        GroupBuyMemberStore.MemberState state = members.state(buyer.userId()).filter(s -> !s.gone()).orElseThrow(() -> new CommonException(ErrorCode.UNAUTHENTICATED));
        if (state.suspended()) {
            throw new GroupBuyBlockedException(GroupBuyBlockedException.ACCOUNT_RESTRICTED);
        }
        if (!state.emailVerified()) {
            throw new GroupBuyBlockedException(GroupBuyBlockedException.EMAIL_NOT_VERIFIED);
        }
        if (!buyer.purchasePermitted()) {
            throw new GroupBuyBlockedException(GroupBuyBlockedException.PERMISSION_DENIED);
        }
        identity.requireAdult(buyer.userId());
        restrictions.assertAllowed(buyer.userId(), RestrictedFeature.GROUP_BUY_JOIN);
        if (!salesSwitch.groupBuyOpen()) {
            throw GroupBuyException.salesClosed();
        }
    }
}
