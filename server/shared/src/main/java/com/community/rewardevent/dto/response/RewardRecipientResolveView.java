package com.community.rewardevent.dto.response;

import com.community.rewardevent.dto.RewardMemberRef;
import java.util.List;

/** 붙여 넣은 줄 → 찾은 회원 / 못 찾은 줄 / 이미 이 이벤트에 있는 회원 / 넣을 수 없는 회원(admin-web {@code RewardRecipientResolveResult}) */
public record RewardRecipientResolveView(List<RewardMemberRef> found, List<String> notFound, List<RewardMemberRef> alreadyIn, List<Excluded> excluded) {

    /** @param reason {@code SUSPENDED} · {@code WITHDRAWN} · {@code OPERATOR} */
    public record Excluded(RewardMemberRef member, String reason) {
    }
}
