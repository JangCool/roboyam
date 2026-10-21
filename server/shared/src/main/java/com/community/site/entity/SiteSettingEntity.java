package com.community.site.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 사이트 설정(행이 하나뿐이다, {@code id = 1}) — V4__installation.sql, V5__site_tagline.sql. 사이트 이름과 공개 주소(서버가 밖으로 내보내는 링크의 기준), 슬로건이다.
 * 행은 설치 때 처음 생긴다. 이후 운영자 설정 화면이 고치는 기능은 아직 없다.
 */
@Entity
@Table(name = "site_settings")
public class SiteSettingEntity {

    /** 행 번호 — 행은 하나뿐이라 항상 1 이다 */
    public static final int ROW_ID = 1;

    @Id
    private Integer id;

    private String siteName;

    private String publicUrl;

    /** 사이트 슬로건(한 줄 소개). 없으면(null) 문구 없음 */
    private String tagline;

    /** 마지막으로 저장한 운영자(회원 ID). 설치 때는 루트 계정 */
    private Long updatedBy;

    private Instant updatedAt;

    protected SiteSettingEntity() {
    }

    /** 설치 때 처음 만든다 */
    public static SiteSettingEntity create(String siteName, String publicUrl, String tagline, long updatedBy, Instant now) {
        SiteSettingEntity setting = new SiteSettingEntity();
        setting.id = ROW_ID;
        setting.siteName = siteName;
        setting.publicUrl = publicUrl;
        setting.tagline = tagline;
        setting.updatedBy = updatedBy;
        setting.updatedAt = now;
        return setting;
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

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
