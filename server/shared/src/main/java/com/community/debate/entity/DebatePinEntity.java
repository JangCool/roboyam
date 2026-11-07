package com.community.debate.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 운영자가 맨 앞에 고정한 의견 — V76 {@code debate_pins}(댓글마다 한 줄). 고정은 멱등이라 {@code DebateQueries#pin}({@code ON CONFLICT DO NOTHING})이 넣는다.
 * {@code pinnedBy} 는 고정한 운영자(회원이 고정 요청을 승인했으면 null — 누가 승인했는지는 요청 행에 남는다).
 */
@Entity
@Table(name = "debate_pins")
public class DebatePinEntity {

    @Id
    private Long commentId;

    private Long postId;
    private Long pinnedBy;
    private Instant createdAt;

    protected DebatePinEntity() {
    }
}
