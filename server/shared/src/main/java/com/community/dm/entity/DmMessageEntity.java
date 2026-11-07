package com.community.dm.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 쪽지 한 통 — shared V70 의 {@code dm_messages}(일반 글자 500자 이하 — HTML 아님). 고치지 않는다 — 양쪽 쪽지함에서 모두 지운 대화의 원문은 앱이, 1년 지난 것은 DB 배치
 * {@code dm-retention} 이 지운다. {@code toString} 은 기본(본문을 싣지 않는다)이다.
 */
@Entity
@Table(name = "dm_messages")
public class DmMessageEntity {

    @Id
    private Long id;

    private Long threadId;
    private Long senderId;
    private String body;
    private Instant createdAt;

    protected DmMessageEntity() {
    }

    public static DmMessageEntity of(long id, long threadId, long senderId, String body, Instant createdAt) {
        DmMessageEntity m = new DmMessageEntity();
        m.id = id;
        m.threadId = threadId;
        m.senderId = senderId;
        m.body = body;
        m.createdAt = createdAt;
        return m;
    }
}
