package com.community.post.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 태그가 바뀐 글의 「작성일(한국 날짜) · 게시판」 표시 한 줄 — shared V216 {@code post_tag_dirty_days}(docs/features/post-tags-contract.md 「집계」).
 * {@code PostTagStore#replace} 가 태그를 실제로 바꿀 때(넣기·빼기·표기 변경) 넣기만 하고, DB 배치({@code R__batch_44_post_tag_daily.sql})가 그 날을 다시 세며 지운다.
 * 넣기만 하는 줄이라(일련번호 키) 같은 날·게시판을 여러 글이 동시에 고쳐도 키가 부딪치지 않는다 — 글 저장 트랜잭션을 실패시키지 않는다.
 */
@Entity
@Table(name = "post_tag_dirty_days")
public class PostTagDirtyDayEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate day;

    private Long boardId;

    private Instant createdAt;

    protected PostTagDirtyDayEntity() {
    }

    public static PostTagDirtyDayEntity create(LocalDate day, long boardId, Instant now) {
        PostTagDirtyDayEntity e = new PostTagDirtyDayEntity();
        e.day = day;
        e.boardId = boardId;
        e.createdAt = now;
        return e;
    }

    public Long getId() {
        return id;
    }

    public LocalDate getDay() {
        return day;
    }

    public Long getBoardId() {
        return boardId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
