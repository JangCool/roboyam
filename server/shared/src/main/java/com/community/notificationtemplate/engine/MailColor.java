package com.community.notificationtemplate.engine;

import java.util.Arrays;
import java.util.Optional;

/**
 * 메일 템플릿 머리말 띠 색 팔레트(계약 「미확정」 4 — 8색 가정). 메일 앱은 CSS 변수를 못 읽으므로 실제 색 값으로 그린다 — 흰 글씨가 잘 읽히는 진한 색만 둔다
 * (admin-web {@code UtilMailTemplate} 과 같은 값).
 */
public enum MailColor {

    BLUE("#1c5fcf"),
    INDIGO("#4f46e5"),
    TEAL("#0f766e"),
    GREEN("#15803d"),
    AMBER("#b45309"),
    ORANGE("#c2410c"),
    RED("#b91c1c"),
    SLATE("#334155");

    private final String hex;

    MailColor(String hex) {
        this.hex = hex;
    }

    public String hex() {
        return hex;
    }

    /** 색 이름의 번들 키(이력 표시) */
    public String nameKey() {
        return "notificationTemplate.mailColor." + name();
    }

    public static Optional<MailColor> of(String name) {
        return Arrays.stream(values()).filter(color -> color.name().equals(name)).findFirst();
    }
}
