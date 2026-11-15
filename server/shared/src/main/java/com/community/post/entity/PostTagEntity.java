package com.community.post.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 글에 단 태그 하나(V215 {@code post_tags} — docs/features/post-tags-contract.md). 글 표는 파티션 표라 별도 표에 두고 외래 키를 두지 않는다
 * (지운·가린 글의 행은 남아도 목록·추천 질의가 글 표 조건으로 뺀다). 글자 규칙은 {@code PostTags}, 쓰기는 {@code PostTagWriter}.
 *
 * <ul>
 *   <li>{@code tagKey}: 비교 열쇠(영문 소문자) — 같은 글 안에서 하나.</li>
 *   <li>{@code tag}: 처음 쓴 모양(보이는 글자).</li>
 *   <li>{@code sortOrder}: 글쓴이가 단 차례(0부터).</li>
 * </ul>
 */
@Entity
@Table(name = "post_tags")
@IdClass(PostTagId.class)
public class PostTagEntity {

    @Id
    private Long postId;

    @Id
    private String tagKey;

    private String tag;

    private short sortOrder;

    private Instant createdAt;

    protected PostTagEntity() {
    }

    public static PostTagEntity create(long postId, String tagKey, String tag, int sortOrder, Instant now) {
        PostTagEntity e = new PostTagEntity();
        e.postId = postId;
        e.tagKey = tagKey;
        e.tag = tag;
        e.sortOrder = (short) sortOrder;
        e.createdAt = now;
        return e;
    }

    /** 남은 태그의 보이는 모양·차례를 고친다(열쇠는 그대로) */
    public void change(String tag, int sortOrder) {
        this.tag = tag;
        this.sortOrder = (short) sortOrder;
    }

    public Long getPostId() {
        return postId;
    }

    public String getTagKey() {
        return tagKey;
    }

    public String getTag() {
        return tag;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
