package com.community.point.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/**
 * 403 — 조정 크기가 큰 금액 문턱({@code point_settings.adjust_large_threshold})을 넘는데 운영자에게 {@code point.adjust.large} 가 없다(commerce-stage1-contract 「1-1」 API,
 * commerce.md 13-2). 화면은 이 문구를 금액 칸 아래에 그대로 보인다. 승인 대기(4단계)는 아직 없다 — 거절만 한다(결정 11).
 */
public class PointAdjustLargeRequiredException extends CommonException {

    /** @param threshold 문턱(이 크기를 넘으면 고액) — 문구에 천 단위 쉼표로 넣는다 */
    public PointAdjustLargeRequiredException(int threshold) {
        super(HttpStatus.FORBIDDEN, "POINT_ADJUST_LARGE_REQUIRED", new MessageRef("error.POINT_ADJUST_LARGE_REQUIRED", String.format("%,d", threshold)));
    }
}
