package com.community.jjal.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 보관함 줄 — V261 {@code jjal_box_items}. (회원, 파일) 유일. 순서 값 {@code sortKey} 가 작을수록 앞이다(새 줄은 지금 가장 작은 값 − 1 = 맨 앞, 「순서 바꾸기」는 0부터 다시 매긴다).
 * 담은 줄에 원래 올린 사람을 남기지 않는다(출처 표시 없음 — my-jjal-contract 정책 6).
 */
@Entity
@Table(name = "jjal_box_items")
public class JjalBoxItemEntity {

    @Id
    private Long id;
    private Long userId;
    private Long fileId;
    @Enumerated(EnumType.STRING)
    private JjalSource source;
    private long sortKey;
    private Instant createdAt;

    protected JjalBoxItemEntity() {
    }

    public static JjalBoxItemEntity create(long id, long userId, long fileId, JjalSource source, long sortKey, Instant now) {
        JjalBoxItemEntity item = new JjalBoxItemEntity();
        item.id = id;
        item.userId = userId;
        item.fileId = fileId;
        item.source = source;
        item.sortKey = sortKey;
        item.createdAt = now;
        return item;
    }

    /** 「순서 바꾸기」 저장 — 새 자리 */
    public void moveTo(long sortKey) {
        this.sortKey = sortKey;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getFileId() {
        return fileId;
    }

    public JjalSource getSource() {
        return source;
    }

    public long getSortKey() {
        return sortKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
