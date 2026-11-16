package com.community.point.dto.response;

import com.community.restriction.exception.MemberRestrictedException;

/**
 * 포인트를 막는 기능 제한(포인트 동결) — {@code GET /api/me/points} 의 {@code restriction}. 오류 본문 {@code restriction}(403 {@code MEMBER_RESTRICTED})과 같은 모양이라
 * user-web {@code FeatureRestrictionNotice} 를 그대로 쓴다. 운영 메모는 싣지 않는다.
 *
 * @param type   제한 종류({@code POINT_FREEZE})
 * @param until  같은 종류 중 가장 늦은 끝(ISO-8601) — 무기한이면 null
 * @param reason 회원에게 공개하는 사유
 */
public record PointRestrictionView(String type, String until, String reason) {

    public static PointRestrictionView of(MemberRestrictedException blocking) {
        return new PointRestrictionView(blocking.type().name(), blocking.until() == null ? null : blocking.until().toString(), blocking.reason());
    }
}
