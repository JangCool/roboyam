package com.community.poll.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 글의 투표 하나 — V81 {@code polls}(글 하나에 하나, {@code post_id} 유일). 글 표는 파티션 표라 {@code post_id} 외래 키가 없다.
 * 선택지({@link PollOptionEntity})·표({@link PollVoteEntity})는 이 행을 지우면 DB 가 함께 지운다(ON DELETE CASCADE).
 * 읽기·잠금·고치기는 {@code PollQueries} 가 한다(첫 표 잠금 규칙 — 고치는 쪽 쓰기 잠금, 투표하는 쪽 공유 잠금).
 */
@Entity
@Table(name = "polls")
public class PollEntity {

    @Id
    private Long id;

    private Long postId;
    private String question;
    private boolean multiple;
    private boolean changeAllowed;
    /** 마감 시각(null = 마감 없음) */
    private Instant closesAt;
    private Instant createdAt;
    private Instant updatedAt;

    protected PollEntity() {
    }

    public static PollEntity create(long id, long postId, String question, boolean multiple, boolean changeAllowed, Instant closesAt, Instant now) {
        PollEntity poll = new PollEntity();
        poll.id = id;
        poll.postId = postId;
        poll.question = question;
        poll.multiple = multiple;
        poll.changeAllowed = changeAllowed;
        poll.closesAt = closesAt;
        poll.createdAt = now;
        poll.updatedAt = now;
        return poll;
    }

    public Long getId() {
        return id;
    }

    public Long getPostId() {
        return postId;
    }

    public String getQuestion() {
        return question;
    }

    public boolean isMultiple() {
        return multiple;
    }

    public boolean isChangeAllowed() {
        return changeAllowed;
    }

    public Instant getClosesAt() {
        return closesAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
