package com.community.aimember.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * AI 기자 뉴스 출처 한 줄 — {@code ai_news_sources}(shared V228, ai-news-reporter-contract 「소재 수집」). 관리자 AI 회원 설정 「뉴스 출처」에서 더하고·고치고·지운다.
 * 수집({@code AiNewsCollector} — 배치 {@code ai-news-collect} · 「지금 수집」)이 {@link #fetched}·{@link #failed} 로 마지막 수집 결과를 적는다.
 */
@Entity
@Table(name = "ai_news_sources")
public class AiNewsSourceEntity {

    public static final int NAME_MAX = 60;
    public static final int ADDRESS_MAX = 500;
    public static final int ERROR_MAX = 300;

    @Id
    private Long id;

    @Enumerated(EnumType.STRING)
    private AiNewsSourceKind kind;

    private String name;
    private String address;
    private boolean enabled;
    private int sortOrder;
    private Instant lastFetchedAt;
    private Integer lastItemCount;
    private String lastError;
    private Instant lastErrorAt;
    private Instant createdAt;
    private Long createdBy;
    private Instant updatedAt;
    private Long updatedBy;

    protected AiNewsSourceEntity() {
    }

    public static AiNewsSourceEntity create(long id, AiNewsSourceKind kind, String name, String address, boolean enabled, int sortOrder, long operatorId, Instant now) {
        AiNewsSourceEntity e = new AiNewsSourceEntity();
        e.id = id;
        e.sortOrder = sortOrder;
        e.createdAt = now;
        e.createdBy = operatorId;
        e.change(kind, name, address, enabled, operatorId, now);
        return e;
    }

    /** 고치기 — 종류·주소가 바뀌면 지난 오류는 지운다 */
    public void change(AiNewsSourceKind kind, String name, String address, boolean enabled, long operatorId, Instant now) {
        boolean moved = this.kind != kind || this.address == null || !this.address.equals(address);
        this.kind = kind;
        this.name = name;
        this.address = address;
        this.enabled = enabled;
        if (moved) {
            this.lastError = null;
            this.lastErrorAt = null;
        }
        this.updatedAt = now;
        this.updatedBy = operatorId;
    }

    /** 수집 성공 — 피드에서 읽은 항목 수(새로 넣은 수가 아니다) */
    public void fetched(int items, Instant now) {
        this.lastFetchedAt = now;
        this.lastItemCount = items;
        this.lastError = null;
        this.lastErrorAt = null;
    }

    /** 수집 실패 — 사유 한 줄(앞 {@value #ERROR_MAX}자) */
    public void failed(String error, Instant now) {
        String e = error == null ? "" : error.replaceAll("\\s+", " ").strip();
        this.lastError = e.length() > ERROR_MAX ? e.substring(0, ERROR_MAX) : e;
        this.lastErrorAt = now;
    }

    public Long getId() {
        return id;
    }

    public AiNewsSourceKind getKind() {
        return kind;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public Instant getLastFetchedAt() {
        return lastFetchedAt;
    }

    public Integer getLastItemCount() {
        return lastItemCount;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getLastErrorAt() {
        return lastErrorAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
