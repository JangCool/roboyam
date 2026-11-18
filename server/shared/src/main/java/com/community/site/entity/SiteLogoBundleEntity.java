package com.community.site.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 로고 묶음 한 벌(= 4칸 + 이름 + 「로고에 사이트 이름이 들어 있음」) — V249 {@code site_logo_bundles}, docs/features/site-logo-library-contract.md.
 * 칸({@link SiteLogoVariant})마다 <b>그림 파일 하나</b>({@code files.id}, 용도 SITE_LOGO) 또는 <b>SVG 소스 하나</b>(검사를 마치고 xmlns 를 맞춘 것)이고 둘 다 없으면 빈 칸이다.
 * 빈 칸을 어느 칸이 대신할지는 화면 규칙({@code UtilSiteLogo.resolve})이다.
 *
 * <p>기본 제공 묶음 둘({@link #CHARACTER_ID} 「캐릭터 로고」·{@link #WORDMARK_ID} 「글자 로고」)은 마이그레이션이 넣어 모든 설치에 늘 있고 고치지 못한다(서버가 거절).
 * 기념일 로고 묶음 56개(28일 × 두 벌, id 101~128·201~228 — V251)도 기본 제공이다({@code dayKey} 가 그날, {@code builtInKey} 가 벌).
 * 올린 묶음의 ID 는 앱이 매기고(Snowflake), 지우지 않는다(보관해 두고 나중에 다시 고른다).
 */
@Entity
@Table(name = "site_logo_bundles")
public class SiteLogoBundleEntity {

    /** 기본 제공 「캐릭터 로고」 — 설치 직후의 평소 로고 */
    public static final long CHARACTER_ID = 1L;
    /** 기본 제공 「글자 로고」 */
    public static final long WORDMARK_ID = 2L;

    /** 벌 — 캐릭터 로고 계열({@code built_in_key}) */
    public static final String STYLE_CHARACTER = "CHARACTER";
    /** 벌 — 글자 로고 계열 */
    public static final String STYLE_WORDMARK = "WORDMARK";

    /** 이름 길이 한도 */
    public static final int NAME_MAX = 30;

    @Id
    private Long id;

    private String name;

    /**
     * 기본 제공 묶음의 <b>벌</b> — {@link #STYLE_CHARACTER}(캐릭터 로고 계열 · 기념일의 귀여운 버전) · {@link #STYLE_WORDMARK}(글자 로고 계열 · 기념일의 일반 버전).
     * 올린 묶음은 null
     */
    private String builtInKey;

    /**
     * 올린 묶음의 유형(V255) — {@link #STYLE_CHARACTER} · {@link #STYLE_WORDMARK}. 고른 적 없으면 null(캐릭터로 본다). 기본 제공 묶음은 늘 null 이고
     * {@code builtInKey} 가 유형이다 — 읽을 때는 {@link #effectiveStyle()} 하나만 쓴다
     */
    private String style;

    /**
     * 기념일 로고 묶음이면 그날의 키({@code newyear}·{@code seollal}·{@code samil} … — V251). 평소 로고·올린 묶음은 null.
     * 같은 날 키의 두 묶음(벌이 다른 짝)이 있고, 기념일 규칙은 평소 로고의 벌에 맞는 쪽을 보인다({@code SiteLogoResolver}).
     */
    private String dayKey;

    /** 로고 그림에 사이트 이름이 들어 있는가 — 켜져 있으면 공개 헤더가 로고 옆 이름 글자를 붙이지 않는다 */
    private boolean nameInLogo;

    private Long koLightFileId;
    private String koLightSvg;
    private Long koDarkFileId;
    private String koDarkSvg;
    private Long enLightFileId;
    private String enLightSvg;
    private Long enDarkFileId;
    private String enDarkSvg;

    /** 올린 운영자({@code operators.id}). 기본 제공·옮긴 묶음은 null 일 수 있다 */
    private Long createdBy;

    private Instant createdAt;
    private Instant updatedAt;

    protected SiteLogoBundleEntity() {
    }

    /** 새로 올린 묶음 — 칸은 비어 있다({@link #putFile}·{@link #putSvg} 로 채운다) */
    public static SiteLogoBundleEntity create(long id, String name, boolean nameInLogo, long operatorId, Instant now) {
        SiteLogoBundleEntity bundle = new SiteLogoBundleEntity();
        bundle.id = id;
        bundle.name = name;
        bundle.nameInLogo = nameInLogo;
        bundle.createdBy = operatorId;
        bundle.createdAt = now;
        bundle.updatedAt = now;
        return bundle;
    }

    /** 이름과 「이름 포함」을 바꾼다 */
    public void change(String name, boolean nameInLogo, Instant now) {
        this.name = name;
        this.nameInLogo = nameInLogo;
        this.updatedAt = now;
    }

    /** 올린 묶음의 유형을 바꾼다(허용 값인지·기본 제공이 아닌지는 부르는 쪽이 확인했다) */
    public void changeStyle(String style, Instant now) {
        this.style = style;
        this.updatedAt = now;
    }

    /** 유효 유형 — 기본 제공이면 {@code builtInKey}, 아니면 고른 유형, 고른 적 없으면 캐릭터. 유형을 읽는 곳은 모두 이것을 쓴다 */
    public String effectiveStyle() {
        if (builtInKey != null) {
            return builtInKey;
        }
        return style == null ? STYLE_CHARACTER : style;
    }

    /** 한 칸을 그림 파일로 — 그 칸의 SVG 는 비운다 */
    public void putFile(SiteLogoVariant variant, long fileId, Instant now) {
        put(variant, fileId, null);
        this.updatedAt = now;
    }

    /** 한 칸을 SVG 소스로 — 그 칸의 그림 파일은 비운다 */
    public void putSvg(SiteLogoVariant variant, String svgSource, Instant now) {
        put(variant, null, svgSource);
        this.updatedAt = now;
    }

    /** 한 칸을 비운다 */
    public void clear(SiteLogoVariant variant, Instant now) {
        put(variant, null, null);
        this.updatedAt = now;
    }

    /** 한 칸의 그림 파일 번호(없으면 null) */
    public Long fileId(SiteLogoVariant variant) {
        return switch (variant) {
            case KO_LIGHT -> koLightFileId;
            case KO_DARK -> koDarkFileId;
            case EN_LIGHT -> enLightFileId;
            case EN_DARK -> enDarkFileId;
        };
    }

    /** 한 칸의 SVG 소스(없으면 null) */
    public String svg(SiteLogoVariant variant) {
        return switch (variant) {
            case KO_LIGHT -> koLightSvg;
            case KO_DARK -> koDarkSvg;
            case EN_LIGHT -> enLightSvg;
            case EN_DARK -> enDarkSvg;
        };
    }

    /** 그 칸에 그림이 있는가(파일이든 SVG 든) */
    public boolean filled(SiteLogoVariant variant) {
        return fileId(variant) != null || svg(variant) != null;
    }

    private void put(SiteLogoVariant variant, Long fileId, String svg) {
        switch (variant) {
            case KO_LIGHT -> {
                this.koLightFileId = fileId;
                this.koLightSvg = svg;
            }
            case KO_DARK -> {
                this.koDarkFileId = fileId;
                this.koDarkSvg = svg;
            }
            case EN_LIGHT -> {
                this.enLightFileId = fileId;
                this.enLightSvg = svg;
            }
            case EN_DARK -> {
                this.enDarkFileId = fileId;
                this.enDarkSvg = svg;
            }
        }
    }

    /** 기본 제공 묶음인가 — 고치지 못한다 */
    public boolean isBuiltIn() {
        return builtInKey != null;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getBuiltInKey() {
        return builtInKey;
    }

    public String getDayKey() {
        return dayKey;
    }

    public boolean isNameInLogo() {
        return nameInLogo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
