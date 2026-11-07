package com.community.rewardevent.dto.response;

import com.community.rewardevent.dto.RewardMemberRef;
import com.community.rewardevent.dto.RewardOperatorRef;
import java.time.Instant;
import java.util.List;

/** 받는 사람 한 명(admin-web {@code RewardRecipient}) — 선정 운영자는 선착순·일괄 자동이면 null */
public record RewardRecipientView(String id, RewardMemberRef member, String tierId, String tierName, String method, Instant selectedAt, RewardOperatorRef selectedBy,
                                  String drawId, String status, List<Result> results, String revokeReason) {

    /** 보상 줄 하나의 결과(admin-web {@code RewardLineResult}) — 회수한 포인트(0P 바닥으로 덜 회수했을 수 있다) · 직접 전달 완료 기록 */
    public record Result(String lineId, String kind, String status, String reason, Instant at, Integer revokedPoints, Delivered delivered) {
    }

    public record Delivered(Instant at, RewardOperatorRef operator, String memo) {
    }
}
