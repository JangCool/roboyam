package com.community.rewardevent.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/**
 * 403 — 1인 포인트 합이 고액 문턱({@code point_settings.adjust_large_threshold})을 넘거나 일괄 방식에 포인트 보상이 있는 지급인데 고액 권한({@code point.adjust.large})이 없다.
 * 포인트 수동 조정과 같은 코드·문구다(event-admin-contract 「보상 줄」).
 */
public class RewardLargeRequiredException extends CommonException {

    public RewardLargeRequiredException(int threshold) {
        super(HttpStatus.FORBIDDEN, "POINT_ADJUST_LARGE_REQUIRED", new MessageRef("error.POINT_ADJUST_LARGE_REQUIRED", String.format("%,d", threshold)));
    }
}
