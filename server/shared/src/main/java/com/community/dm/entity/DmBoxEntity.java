package com.community.dm.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * 회원마다 자기 쪽지함의 대화 한 줄 — shared V70 의 {@code dm_boxes}(키 = 대화 + 회원, 대화를 만들 때 두 줄). 대화 삭제는 내 줄의 {@code clearedUpTo} 만 올린다
 * (그 쪽지까지 내 쪽지함에서 안 보인다). {@code readUpTo}·{@code clearedUpTo} 는 내려가지 않는다 — 고치기는 {@code greatest} 대량 UPDATE 다.
 */
@Entity
@Table(name = "dm_boxes")
@IdClass(DmBoxId.class)
public class DmBoxEntity {

    @Id
    private Long threadId;

    @Id
    private Long userId;

    private Long partnerId;
    /** 대화의 마지막 쪽지(쪽지함 줄 세우기 색인용 사본, 없으면 0) */
    private long lastMessageId;
    private long readUpTo;
    private long clearedUpTo;

    protected DmBoxEntity() {
    }

    /** 새 대화의 쪽지함 줄 — 마지막 쪽지·읽음·지움 위치 0(표 기본값과 같다) */
    public static DmBoxEntity open(long threadId, long userId, long partnerId) {
        DmBoxEntity box = new DmBoxEntity();
        box.threadId = threadId;
        box.userId = userId;
        box.partnerId = partnerId;
        return box;
    }
}
