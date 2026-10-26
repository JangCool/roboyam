package com.community.group.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * 소모임을 스스로 해산한 모임장이 해산 패널티 기간 안에 개설 신청했다(403 {@code GROUP_DISSOLVE_PENALTY}, V96) — 오류 본문 최상위 {@code retryAfterSeconds}(끝까지 남은 초)와
 * {@code until}(끝 시각 ISO-8601)
 */
public class GroupDissolvePenaltyException extends CommonException {

    public GroupDissolvePenaltyException(Instant until, Instant now) {
        super(HttpStatus.FORBIDDEN, "GROUP_DISSOLVE_PENALTY", new MessageRef("error.GROUP_DISSOLVE_PENALTY"), Map.of(),
                Math.max(1, Duration.between(now, until).toSeconds()));
        extra("until", until.toString());
    }
}
