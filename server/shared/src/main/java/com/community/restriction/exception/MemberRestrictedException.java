package com.community.restriction.exception;

import com.community.restriction.entity.RestrictionType;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * 403 — 기능 제한에 걸린 기능을 쓰려 했다(commerce.md 12-3). 오류 본문 최상위 {@code restriction} = {@code { type, until, reason }} — {@code until} 은 같은 종류 중 가장 늦은 끝
 * (무기한이 있으면 null), {@code reason} 은 회원에게 공개하는 사유. <b>운영 메모는 싣지 않는다.</b>
 */
public class MemberRestrictedException extends CommonException {

    public MemberRestrictedException(RestrictionType type, Instant until, String reason) {
        super(HttpStatus.FORBIDDEN, "MEMBER_RESTRICTED", new MessageRef("error.MEMBER_RESTRICTED"));
        Map<String, Object> restriction = new LinkedHashMap<>();
        restriction.put("type", type.name());
        restriction.put("until", until == null ? null : until.toString());
        restriction.put("reason", reason);
        extra("restriction", restriction);
    }
}
