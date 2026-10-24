package com.community.block.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 내 차단 단어 — V71__blocks_memos.sql 의 {@code blocked_words}. 글 제목·댓글 본문에 들어 있으면(대소문자 무시·부분 일치) 화면이 접는다.
 * 단어는 다듬은 값(앞뒤 공백 자름·연속 공백 한 칸)으로 저장하고, 같은 회원의 같은 단어(대소문자 무시)는 한 번만이다.
 */
@Entity
@Table(name = "blocked_words")
public class BlockedWordEntity {

    @Id
    private Long id;

    private Long userId;
    private String word;
    private Instant createdAt;

    protected BlockedWordEntity() {
    }

    public static BlockedWordEntity create(long id, long userId, String word, Instant now) {
        BlockedWordEntity entity = new BlockedWordEntity();
        entity.id = id;
        entity.userId = userId;
        entity.word = word;
        entity.createdAt = now;
        return entity;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getWord() {
        return word;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
