package com.community.site.entity;

import java.util.Arrays;
import java.util.Optional;

/**
 * 사이트 로고 칸(언어 × 바탕) — V237, docs/features/site-logo-variants-contract.md. API 경로·JSON 의 칸 이름은 {@link #key()}({@code koLight} …)다.
 * ① {@link #KO_LIGHT} 는 V12 의 단일 로고 칸({@code logo_file_id}·{@code logo_svg})이라 옛 단일 로고 API·관리자 me·메일 로고와 같은 값이다.
 * 빈 칸을 어느 칸이 대신할지는 화면 규칙({@code UtilSiteLogo.resolve})이고 서버는 원본 칸만 준다.
 */
public enum SiteLogoVariant {
    /** ① 한글·밝은 바탕용 */
    KO_LIGHT("koLight"),
    /** ② 한글·어두운 바탕용 */
    KO_DARK("koDark"),
    /** ③ 영문·밝은 바탕용 */
    EN_LIGHT("enLight"),
    /** ④ 영문·어두운 바탕용 */
    EN_DARK("enDark");

    private final String key;

    SiteLogoVariant(String key) {
        this.key = key;
    }

    /** API 경로·JSON 의 칸 이름 */
    public String key() {
        return key;
    }

    /** 칸 이름({@code koLight} …)으로 찾는다 — 모르는 이름이면 비어 있다 */
    public static Optional<SiteLogoVariant> fromKey(String key) {
        return Arrays.stream(values()).filter(v -> v.key.equals(key)).findFirst();
    }
}
