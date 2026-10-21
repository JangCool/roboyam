package com.community.site.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 사이트 설정(행이 하나뿐이다, {@code id = 1}) — V4__installation.sql, V5__site_tagline.sql, V12__site_media.sql.
 * 사이트 이름과 공개 주소(서버가 밖으로 내보내는 링크의 기준), 슬로건, 로고·사이트 아이콘·링크 미리보기 이미지, 표시 기본값이다.
 * 행은 설치 때 처음 생기고, 이후 관리자 앱의 설정 > 일반 화면이 고친다(공개 주소는 그 화면이 건드리지 않는다).
 */
@Entity
@Table(name = "site_settings")
public class SiteSettingEntity {

    /** 행 번호 — 행은 하나뿐이라 항상 1 이다 */
    public static final int ROW_ID = 1;

    /** 표시 기본값의 처음 값(설치 직후) — V12 의 칸 기본값과 같다 */
    public static final String DEFAULT_LANGUAGE = "ko";
    public static final String DEFAULT_ZONE_ID = "Asia/Seoul";
    public static final String DEFAULT_TIME_FORMAT = "H24";
    public static final String DEFAULT_DATE_FORMAT = "YMD_DASH";

    @Id
    private Integer id;

    private String siteName;

    private String publicUrl;

    /** 사이트 슬로건(한 줄 소개). 없으면(null) 문구 없음 */
    private String tagline;

    /** 로고로 지정한 그림 파일({@code files.id}). 로고 SVG 와 함께 있을 수 없다 */
    private Long logoFileId;

    /** 로고로 지정한 SVG 소스(검사를 마치고 xmlns 를 맞춘 것). 로고 그림 파일과 함께 있을 수 없다 */
    private String logoSvg;

    /** 사이트 아이콘의 버전 토큰(저장소 키 {@code site-icon/{버전}/…}). 없으면(null) 기본 아이콘 */
    private String iconToken;

    /** 링크 미리보기 이미지의 버전 토큰(저장소 키 {@code site-og/{버전}/…}). 없으면(null) 기본 이미지 */
    private String ogToken;

    private String displayLanguage;

    private String displayZoneId;

    private String displayTimeFormat;

    private String displayDateFormat;

    /** 마지막으로 저장한 운영자(회원 ID). 설치 때는 루트 계정 */
    private Long updatedBy;

    private Instant updatedAt;

    protected SiteSettingEntity() {
    }

    /** 설치 때 처음 만든다 — 로고·아이콘·이미지는 없고 표시 기본값은 처음 값이다 */
    public static SiteSettingEntity create(String siteName, String publicUrl, String tagline, long updatedBy, Instant now) {
        SiteSettingEntity setting = new SiteSettingEntity();
        setting.id = ROW_ID;
        setting.siteName = siteName;
        setting.publicUrl = publicUrl;
        setting.tagline = tagline;
        setting.displayLanguage = DEFAULT_LANGUAGE;
        setting.displayZoneId = DEFAULT_ZONE_ID;
        setting.displayTimeFormat = DEFAULT_TIME_FORMAT;
        setting.displayDateFormat = DEFAULT_DATE_FORMAT;
        setting.updatedBy = updatedBy;
        setting.updatedAt = now;
        return setting;
    }

    /** 사이트 이름과 한 줄 소개를 바꾼다(공개 주소는 건드리지 않는다) */
    public void changeBasic(String siteName, String tagline, long operatorId, Instant now) {
        this.siteName = siteName;
        this.tagline = tagline;
        touch(operatorId, now);
    }

    /** 로고를 그림 파일로 바꾼다 — SVG 로고는 비운다 */
    public void changeLogoFile(long fileId, long operatorId, Instant now) {
        this.logoFileId = fileId;
        this.logoSvg = null;
        touch(operatorId, now);
    }

    /** 로고를 SVG 소스로 바꾼다 — 그림 파일 로고는 비운다 */
    public void changeLogoSvg(String svgSource, long operatorId, Instant now) {
        this.logoSvg = svgSource;
        this.logoFileId = null;
        touch(operatorId, now);
    }

    /** 로고를 뗀다 */
    public void clearLogo(long operatorId, Instant now) {
        this.logoFileId = null;
        this.logoSvg = null;
        touch(operatorId, now);
    }

    /** 사이트 아이콘의 버전을 바꾼다. {@code null} 이면 기본 아이콘으로 되돌린다 */
    public void changeIconToken(String token, long operatorId, Instant now) {
        this.iconToken = token;
        touch(operatorId, now);
    }

    /** 링크 미리보기 이미지의 버전을 바꾼다. {@code null} 이면 기본 이미지로 되돌린다 */
    public void changeOgToken(String token, long operatorId, Instant now) {
        this.ogToken = token;
        touch(operatorId, now);
    }

    /** 표시 기본값을 바꾼다(값 검사는 부르는 쪽이 끝냈다) */
    public void changeDisplay(String language, String zoneId, String timeFormat, String dateFormat, long operatorId, Instant now) {
        this.displayLanguage = language;
        this.displayZoneId = zoneId;
        this.displayTimeFormat = timeFormat;
        this.displayDateFormat = dateFormat;
        touch(operatorId, now);
    }

    private void touch(long operatorId, Instant now) {
        this.updatedBy = operatorId;
        this.updatedAt = now;
    }

    public Integer getId() {
        return id;
    }

    public String getSiteName() {
        return siteName;
    }

    public String getPublicUrl() {
        return publicUrl;
    }

    public String getTagline() {
        return tagline;
    }

    public Long getLogoFileId() {
        return logoFileId;
    }

    public String getLogoSvg() {
        return logoSvg;
    }

    public String getIconToken() {
        return iconToken;
    }

    public String getOgToken() {
        return ogToken;
    }

    public String getDisplayLanguage() {
        return displayLanguage;
    }

    public String getDisplayZoneId() {
        return displayZoneId;
    }

    public String getDisplayTimeFormat() {
        return displayTimeFormat;
    }

    public String getDisplayDateFormat() {
        return displayDateFormat;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
