package com.community.site.entity;

import java.util.Arrays;
import java.util.Optional;

/**
 * 푸터 안내 문구를 언제 보일까(V177 {@code site_operator.footer_notice_scope}) — 개인 단계에서만(기본) · 늘(사업자 정보 표가 있어도).
 */
public enum SiteFooterNoticeScope {

    INDIVIDUAL_ONLY,
    ALWAYS;

    /** 이름으로 찾는다. 모르면 비어 있다 */
    public static Optional<SiteFooterNoticeScope> of(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(scope -> scope.name().equals(name)).findFirst();
    }
}
