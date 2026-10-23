package com.community.notificationtemplate.catalog;

import java.util.Arrays;
import java.util.Optional;

/**
 * 회원 이벤트 코드 — <b>임시 정의</b>(한 곳). 통합 회원 이벤트 카탈로그(다른 작업이 동시에 만든다)가 생기면 그것으로 바꾸고 이 enum 을 지운다 —
 * 코드명은 admin-web 목업 {@code notification/api/mock/member-event-mock.ts} 의 것이 정본이다(계약 「개정 3」 회원 이벤트 통합 목록).
 * 지금 실제로 일어나는 5종({@code featureReady}, 회원 알림 {@code MemberNotificationType} 과 같은 이름)만 문구 대상이고, 나머지는 「준비 중」이라 문구가 없다.
 */
public enum MemberEvent {

    COMMENT("content", true),
    REPLY("content", true),
    POINTS_REVOKED("member", true),
    POST_HIDDEN("safety", true),
    COMMENT_HIDDEN("safety", true),
    MENTION("content", false),
    POST_POPULAR("content", false),
    OPERATOR_ACTION("safety", false),
    STAFF_ACTION("safety", false),
    REPORT_RESULT("safety", false);

    private final String area;
    private final boolean featureReady;

    MemberEvent(String area, boolean featureReady) {
        this.area = area;
        this.featureReady = featureReady;
    }

    public String code() {
        return name();
    }

    public String area() {
        return area;
    }

    /** 실제로 일어나는가 — false 면 문구 대상이 아니다 */
    public boolean featureReady() {
        return featureReady;
    }

    public String nameKey() {
        return "notificationTemplate.memberEvent." + name() + ".name";
    }

    public String descriptionKey() {
        return "notificationTemplate.memberEvent." + name() + ".description";
    }

    public static Optional<MemberEvent> ofCode(String code) {
        return Arrays.stream(values()).filter(event -> event.name().equals(code)).findFirst();
    }
}
