package com.community.notificationtemplate.catalog;

/**
 * 대상·채널 하나가 쓸 수 있는 변수 — 코드가 정한다. 문법은 {@code {{이름}}} 만(조건·반복 없음).
 * 설명과 견본 값은 메시지 번들 {@code notificationTemplate.var.<id>.desc} · {@code .sample} 이다(같은 이름이라도 대상마다 뜻이 다르면 id 가 다르다 — 예: {@code operatorName} 은
 * 초대 수락·직접 추가에서는 {@code operatorName}, 로그인 잠금에서는 {@code lockedOperatorName}).
 *
 * @param name     {@code {{이름}}} 의 이름(영문자·숫자)
 * @param id       설명·견본 값 번들 키의 가운데 조각
 * @param required 보안 메일의 필수 변수 — 제목·본문·버튼 링크 어디에도 없으면 저장 거절
 * @param link     링크 변수 — 이메일 「버튼」 블록의 주소로 고를 수 있는 것은 이것뿐이고, 렌더링은 http·https 값만 주소로 쓴다
 * @param sample   견본 값을 어디서 가져오는가
 */
public record MessageVariable(String name, String id, boolean required, boolean link, Sample sample) {

    /** 견본 값의 출처 — 미리보기·시험 발송에 쓴다 */
    public enum Sample {
        /** 번들 {@code notificationTemplate.var.<id>.sample} */
        BUNDLE,
        /** 사이트 설정의 사이트 이름 */
        SITE_NAME,
        /** 사이트 설정의 공개 주소(없으면 번들 견본) */
        SITE_URL,
        /** 관리자 앱 주소 {@code ADMIN_URL}(없으면 번들 견본) */
        ADMIN_URL,
        /** 이 대상의 기본 앱 알림 제목에 견본 값을 채운 것 */
        IN_APP_TITLE,
        /** 이 대상의 기본 앱 알림 본문에 견본 값을 채운 것 */
        IN_APP_BODY,
        /** 받는 사람의 사이트 주소 + 대상의 견본 경로 */
        LINK
    }

    public static final String SITE_NAME = "siteName";
    public static final String SITE_URL = "siteUrl";
    public static final String ADMIN_URL = "adminUrl";
    public static final String RECIPIENT_NAME = "recipientName";
    /** 템플릿 틀의 본문 자리 — 틀에서만 쓴다 */
    public static final String CONTENT = "content";
    /** 이메일 · Slack · Mattermost 의 「앱 알림 제목」 — 렌더링 엔진이 그 대상의 앱 알림 문구를 먼저 그려 채운다(부르는 쪽이 값을 주면 그것) */
    public static final String TITLE = "title";
    /** 이메일 · Slack · Mattermost 의 「앱 알림 본문」(위와 같다) */
    public static final String BODY = "body";
    /** 이벤트 화면 주소 — Slack · Mattermost 템플릿의 「열기」 버튼도 이 값이다 */
    public static final String LINK = "link";
    public static final String OCCURRED_AT = "occurredAt";

    public String descriptionKey() {
        return "notificationTemplate.var." + id + ".desc";
    }

    public String sampleKey() {
        return "notificationTemplate.var." + id + ".sample";
    }

    static MessageVariable of(String name, String id) {
        return new MessageVariable(name, id, false, false, Sample.BUNDLE);
    }

    static MessageVariable required(String name, String id) {
        return new MessageVariable(name, id, true, false, Sample.BUNDLE);
    }

    static MessageVariable requiredLink(String name, String id) {
        return new MessageVariable(name, id, true, true, Sample.BUNDLE);
    }
}
