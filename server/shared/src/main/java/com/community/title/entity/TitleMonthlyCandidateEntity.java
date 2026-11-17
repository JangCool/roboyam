package com.community.title.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 운영자가 이달의 작가 후보로 넣은 글 — {@code title_monthly_candidates}(V240). 달·글마다 하나, 글쓴이는 넣을 때의 글쓴이 */
@Entity
@Table(name = "title_monthly_candidates")
public class TitleMonthlyCandidateEntity {

    @Id
    private Long id;
    private String month;
    private Long postId;
    private Long userId;
    private Long addedBy;
    private Instant addedAt;

    protected TitleMonthlyCandidateEntity() {
    }

    public static TitleMonthlyCandidateEntity add(long id, String month, long postId, long userId, long operatorId, Instant now) {
        TitleMonthlyCandidateEntity e = new TitleMonthlyCandidateEntity();
        e.id = id;
        e.month = month;
        e.postId = postId;
        e.userId = userId;
        e.addedBy = operatorId;
        e.addedAt = now;
        return e;
    }

    public Long getId() {
        return id;
    }

    public String getMonth() {
        return month;
    }

    public Long getPostId() {
        return postId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getAddedBy() {
        return addedBy;
    }

    public Instant getAddedAt() {
        return addedAt;
    }
}
