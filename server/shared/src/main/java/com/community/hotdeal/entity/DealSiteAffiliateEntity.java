package com.community.hotdeal.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 사이트 제휴 몰 한 줄(D5) — V122 {@code deal_site_affiliates}. {@code SITE} 모드에서 구매 링크를 이 몰의 변환으로 바꾼다({@code SiteAffiliateLinker}).
 * 관리자 「사이트 제휴 몰」(3단계)이 이 엔티티로 고친다. 켬/끔은 줄 전체 PUT.
 */
@Entity
@Table(name = "deal_site_affiliates")
public class DealSiteAffiliateEntity {

    public static final String TEMPLATE = "TEMPLATE";
    public static final String QUERY = "QUERY";
    /** 틀에 꼭 있어야 하는 자리표시자 */
    public static final String URL_PLACEHOLDER = "{url}";
    public static final int TEMPLATE_MAX = 1000;
    public static final int QUERY_MAX = 300;
    public static final int PROGRAM_NAME_MAX = 40;

    @Id
    private Long id;

    private String domain;
    private String programName;
    private String transformType;
    private String template;
    private String query;
    private boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;
    private Long updatedBy;

    protected DealSiteAffiliateEntity() {
    }

    public static DealSiteAffiliateEntity create(long id, String domain, String programName, String transformType, String template, String query, boolean enabled,
                                                 Long operatorId, Instant now) {
        DealSiteAffiliateEntity s = new DealSiteAffiliateEntity();
        s.id = id;
        s.createdAt = now;
        s.change(domain, programName, transformType, template, query, enabled, operatorId, now);
        return s;
    }

    public void change(String domain, String programName, String transformType, String template, String query, boolean enabled, Long operatorId, Instant now) {
        this.domain = domain;
        this.programName = programName;
        this.transformType = transformType;
        this.template = TEMPLATE.equals(transformType) ? template : null;
        this.query = QUERY.equals(transformType) ? query : null;
        this.enabled = enabled;
        this.updatedBy = operatorId;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getDomain() {
        return domain;
    }

    public String getProgramName() {
        return programName;
    }

    public String getTransformType() {
        return transformType;
    }

    public String getTemplate() {
        return template;
    }

    public String getQuery() {
        return query;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }
}
