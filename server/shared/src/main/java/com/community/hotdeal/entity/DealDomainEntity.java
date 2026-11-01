package com.community.hotdeal.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;

/**
 * 링크 규칙·쇼핑몰 이름 표 한 줄 — V122 {@code deal_domains}. 공개 쪽은 {@code DealRuleBook}(JDBC 사본)으로 읽고, 관리자 「링크 규칙」(3단계)이 이 엔티티로 고친다.
 *
 * @see com.community.hotdeal.service.DealLinkJudge 판정 규칙(접미사 일치 · 더 구체적인 규칙 우선 · 체인의 모든 호스트 · 차단 > 제휴)
 */
@Entity
@Table(name = "deal_domains")
public class DealDomainEntity {

    public static final String BLOCK = "BLOCK";
    public static final String AFFILIATE = "AFFILIATE";
    /** 같은 딜 키에 남길 쿼리 이름 최대 수 */
    public static final int KEY_PARAMS_MAX = 10;

    @Id
    private Long id;

    private String domain;
    private String rule;
    private boolean common;
    private boolean shortener;
    private String[] keyParams = new String[0];
    private String storeName;
    private String note;
    private Instant createdAt;
    private Instant updatedAt;
    private Long updatedBy;

    protected DealDomainEntity() {
    }

    /** @param domain 정규화한 호스트({@code DealHosts.normalizeHost}) */
    public static DealDomainEntity create(long id, String domain, String rule, boolean common, boolean shortener, List<String> keyParams, String storeName,
                                          String note, Long operatorId, Instant now) {
        DealDomainEntity d = new DealDomainEntity();
        d.id = id;
        d.domain = domain;
        d.createdAt = now;
        d.change(rule, common, shortener, keyParams, storeName, note, operatorId, now);
        return d;
    }

    public void change(String rule, boolean common, boolean shortener, List<String> keyParams, String storeName, String note, Long operatorId, Instant now) {
        this.rule = rule;
        this.common = common;
        this.shortener = BLOCK.equals(rule) && shortener;
        this.keyParams = keyParams == null ? new String[0] : keyParams.toArray(String[]::new);
        this.storeName = storeName;
        this.note = note;
        this.updatedBy = operatorId;
        this.updatedAt = now;
    }

    /** 관리자 고치기의 도메인 바꾸기(정규화·유일 검사는 부르는 쪽) — 규칙 칸은 {@link #change} */
    public void rename(String domain) {
        this.domain = domain;
    }

    public Long getId() {
        return id;
    }

    public String getDomain() {
        return domain;
    }

    public String getRule() {
        return rule;
    }

    public boolean isCommon() {
        return common;
    }

    public boolean isShortener() {
        return shortener;
    }

    public List<String> getKeyParams() {
        return keyParams == null ? List.of() : List.of(keyParams);
    }

    public String getStoreName() {
        return storeName;
    }

    public String getNote() {
        return note;
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
