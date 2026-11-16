package com.community.aimember.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 뉴스 수집 항목 한 줄 — {@code ai_news_items}(shared V228). 제목 · 원문 링크(고유) · 짧은 요약(RSS 설명을 자른 것) · 출처 이름 · 발행 시각만 둔다 — 본문 전체·사진은 받지 않는다
 * (ai-news-reporter-contract 「저작권·인용 판단」). AI 기자가 소재로 쓰면 {@link #use} 로 표시해 다시 쓰지 않는다. 7일 지나면 수집이 지운다.
 */
@Entity
@Table(name = "ai_news_items")
public class AiNewsItemEntity {

    public static final int TITLE_MAX = 300;
    public static final int LINK_MAX = 1000;
    /** 요약은 짧게 자른다(RSS 설명이 본문 전체인 피드도 있다 — 원문 전재가 되지 않게) */
    public static final int SUMMARY_MAX = 300;
    public static final int SOURCE_NAME_MAX = 100;

    @Id
    private Long id;

    private Long sourceId;
    private String title;
    private String link;
    private String summary;
    private String sourceName;
    private Instant publishedAt;
    private Instant fetchedAt;
    private Instant usedAt;
    private Long usedDraftId;

    protected AiNewsItemEntity() {
    }

    public static AiNewsItemEntity create(long id, long sourceId, String title, String link, String summary, String sourceName, Instant publishedAt, Instant now) {
        AiNewsItemEntity e = new AiNewsItemEntity();
        e.id = id;
        e.sourceId = sourceId;
        e.title = cut(title, TITLE_MAX - 1);
        e.link = link;
        e.summary = summary == null || summary.isBlank() ? null : cut(summary, SUMMARY_MAX);
        e.sourceName = cut(sourceName, SOURCE_NAME_MAX - 1);
        e.publishedAt = publishedAt;
        e.fetchedAt = now;
        return e;
    }

    /** AI 기자가 소재로 썼다(생성 줄) */
    public void use(long draftId, Instant now) {
        this.usedAt = now;
        this.usedDraftId = draftId;
    }

    /** 앞뒤 공백을 걷고 {@code max} 자(코드 포인트)를 넘으면 잘라 「…」 */
    static String cut(String s, int max) {
        String v = s == null ? "" : s.strip();
        return v.codePointCount(0, v.length()) > max ? v.substring(0, v.offsetByCodePoints(0, max)).strip() + "…" : v;
    }

    public Long getId() {
        return id;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public String getTitle() {
        return title;
    }

    public String getLink() {
        return link;
    }

    public String getSummary() {
        return summary;
    }

    public String getSourceName() {
        return sourceName;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public Long getUsedDraftId() {
        return usedDraftId;
    }
}
