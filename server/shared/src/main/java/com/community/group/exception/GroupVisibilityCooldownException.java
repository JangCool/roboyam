package com.community.group.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 공개 범위를 바꾼 지 쿨다운 일수가 지나지 않아 아직 공개로 바꿀 수 없다(409) — 코드 {@code GROUP_VISIBILITY_COOLDOWN}, {@code retryAfterSeconds} */
public class GroupVisibilityCooldownException extends CommonException {

    public GroupVisibilityCooldownException(long retryAfterSeconds) {
        super(HttpStatus.CONFLICT, "GROUP_VISIBILITY_COOLDOWN", new MessageRef("error.GROUP_VISIBILITY_COOLDOWN"), Map.of(), Math.max(1, retryAfterSeconds));
    }
}
