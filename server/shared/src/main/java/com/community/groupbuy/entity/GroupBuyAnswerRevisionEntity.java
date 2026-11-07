package com.community.groupbuy.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 답변 수정 이력(고치기 전 내용) — shared V106 {@code group_buy_answer_revisions}. 넣기만 한다.
 */
@Entity
@Table(name = "group_buy_answer_revisions")
public class GroupBuyAnswerRevisionEntity {

    @Id
    private Long id;
    private Long questionId;
    private String body;
    private Long writtenBy;
    private Instant writtenAt;
    private Long replacedBy;
    private Instant replacedAt;

    protected GroupBuyAnswerRevisionEntity() {
    }

    public static GroupBuyAnswerRevisionEntity of(long id, long questionId, String body, long writtenBy, Instant writtenAt, long replacedBy, Instant replacedAt) {
        GroupBuyAnswerRevisionEntity r = new GroupBuyAnswerRevisionEntity();
        r.id = id;
        r.questionId = questionId;
        r.body = body;
        r.writtenBy = writtenBy;
        r.writtenAt = writtenAt;
        r.replacedBy = replacedBy;
        r.replacedAt = replacedAt;
        return r;
    }
}
