package com.community.dm.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 두 회원 사이의 쪽지 대화(짝마다 하나) — shared V70 의 {@code dm_threads}. 짝은 작은 ID·큰 ID 로 둔다(유일 제약 {@code ux_dm_threads_pair}).
 * 줄은 api {@code DmQueries#createThread}({@code ON CONFLICT DO NOTHING} — 두 사람이 동시에 첫 쪽지를 보내도 대화는 하나)가 만들고, 보낸 뒤 칸은 대량 UPDATE 로 고친다.
 */
@Entity
@Table(name = "dm_threads")
public class DmThreadEntity {

    @Id
    private Long id;

    private Long userLowId;
    private Long userHighId;

    /** 그 쪽 회원이 이 대화에서 쪽지를 보낸 적이 있다(링크·연락처 허용 — 한 번 열리면 닫히지 않는다) */
    private boolean lowSent;
    private boolean highSent;

    private Long lastMessageId;
    private Instant lastMessageAt;
    private Instant createdAt;

    protected DmThreadEntity() {
    }

    public Long getId() {
        return id;
    }
}
