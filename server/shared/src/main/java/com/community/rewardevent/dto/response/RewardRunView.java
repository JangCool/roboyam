package com.community.rewardevent.dto.response;

import com.community.rewardevent.dto.RewardOperatorRef;
import java.time.Instant;

/**
 * 지급 실행 한 번(admin-web {@code RewardRun}) — 선착순 자동은 날마다 한 줄({@code day} 표시 시간대 날짜, 운영자 null). 진행률 {@code progress} 는 끝났으면 done = total.
 * 합계: 받는 사람마다 실패·일부 실패 → failed, 모두 건너뜀 → skipped, 그 밖 → success. 포인트·아이템은 지급(회수는 회수)한 양.
 */
public record RewardRunView(String id, Instant at, RewardOperatorRef operator, String kind, String status, String day, int targetCount, int success, int failed, int skipped,
                            long points, int items, Progress progress, String reason) {

    public record Progress(int done, int total) {
    }
}
