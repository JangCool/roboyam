package com.community.highlight.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 하이라이트 — V75__highlights.sql 의 {@code highlights}. 관리자 앱이 만들고 고치며, 공개 서버는 글 상세의 {@code highlighted}(어느 곳이든 노출 중인 하이라이트 글)·
 * 하이라이트 가젯·팁 묶음 「이번 주 추천」을 그릴 때 읽는다.
 * 놓일 곳 {@link #getPlace() place}(V161 — {@code home}·{@code tips}·가젯 페이지 키, {@link com.community.highlight.service.HighlightPlaces})마다 목록·순서를 따로 갖고, 같은 글은 곳마다 한 번.
 * 「노출 중」 = {@link #isShowingAt}(노출 켬 + 기간 안) 이고 글이 지금 볼 수 있을 때다(글 상태는 부르는 쪽이 본다).
 * 하이라이트는 토론을 갖지 않는다 — 찬반 토론은 글의 것이다({@code post_debates}, V79 — docs/features/post-debate-contract.md).
 */
@Entity
@Table(name = "highlights")
public class HighlightEntity {

    @Id
    private Long id;

    private String place;
    private Long postId;
    private String title;
    private String summary;
    private Long imageFileId;
    private boolean visible;
    private Instant showFrom;
    private Instant showUntil;
    private int sortOrder;
    private Long createdBy;
    private Instant createdAt;
    private Long updatedBy;
    private Instant updatedAt;

    protected HighlightEntity() {
    }

    public static HighlightEntity create(long id, String place, long postId, String title, String summary, Long imageFileId, boolean visible, Instant showFrom,
                                         Instant showUntil, int sortOrder, long operatorId, Instant now) {
        HighlightEntity row = new HighlightEntity();
        row.id = id;
        row.place = place;
        row.postId = postId;
        row.title = title;
        row.summary = summary;
        row.imageFileId = imageFileId;
        row.visible = visible;
        row.showFrom = showFrom;
        row.showUntil = showUntil;
        row.sortOrder = sortOrder;
        row.createdBy = operatorId;
        row.createdAt = now;
        row.updatedBy = operatorId;
        row.updatedAt = now;
        return row;
    }

    /** 칸을 고친다(대상 글은 바꾸지 않는다) */
    public void change(String title, String summary, Long imageFileId, boolean visible, Instant showFrom, Instant showUntil, long operatorId, Instant now) {
        this.title = title;
        this.summary = summary;
        this.imageFileId = imageFileId;
        this.visible = visible;
        this.showFrom = showFrom;
        this.showUntil = showUntil;
        this.updatedBy = operatorId;
        this.updatedAt = now;
    }

    public void changeVisible(boolean visible, long operatorId, Instant now) {
        this.visible = visible;
        this.updatedBy = operatorId;
        this.updatedAt = now;
    }

    public void moveTo(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    /** 노출을 켰고 지금이 노출 기간 안인가(시작 포함·끝 미포함, 비어 있으면 상시) — 글 상태는 보지 않는다 */
    public boolean isShowingAt(Instant now) {
        return visible && (showFrom == null || !showFrom.isAfter(now)) && (showUntil == null || showUntil.isAfter(now));
    }

    public Long getId() {
        return id;
    }

    /** 놓일 곳 키({@code home}·{@code tips}·가젯 페이지 키) */
    public String getPlace() {
        return place;
    }

    public Long getPostId() {
        return postId;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public Long getImageFileId() {
        return imageFileId;
    }

    public boolean isVisible() {
        return visible;
    }

    public Instant getShowFrom() {
        return showFrom;
    }

    public Instant getShowUntil() {
        return showUntil;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
