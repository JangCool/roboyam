package com.community.rewardevent.dto.response;

import com.community.rewardevent.dto.RewardMemberRef;
import com.community.rewardevent.dto.RewardOperatorRef;
import java.time.Instant;
import java.util.List;

/** 추첨 기록(admin-web {@code RewardDraw}) — 추첨 번호({@code seed}, 16자리 16진수)와 후보 목록이 같으면 같은 결과 */
public record RewardDrawView(String id, Instant at, RewardOperatorRef operator, String postId, String postTitle, String tierId, String tierName, int candidateCount,
                             int picked, String seed, List<RewardMemberRef> members) {
}
