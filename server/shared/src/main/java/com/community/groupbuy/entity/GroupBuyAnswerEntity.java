package com.community.groupbuy.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicInsert;

/**
 * 공구 질문의 답변(질문 하나에 하나) — shared V106 {@code group_buy_answers}. 고치면 이전 내용은 {@link GroupBuyAnswerRevisionEntity}.
 * 읽기는 저장소의 한 줄 모양(프로젝션), 고치기는 저장소의 UPDATE 다 — 이 엔티티는 새 줄 넣기({@code persist})와 질의 경로(Q 클래스)에 쓴다.
 */
@Entity
@DynamicInsert
@Table(name = "group_buy_answers")
public class GroupBuyAnswerEntity {

    @Id
    private Long questionId;
    private String body;
    private Long answeredBy;
    private Instant answeredAt;
    private Long updatedBy;
    private Instant updatedAt;

    protected GroupBuyAnswerEntity() {
    }

    /** 첫 답변 */
    public static GroupBuyAnswerEntity first(long questionId, String body, long answeredBy, Instant answeredAt) {
        GroupBuyAnswerEntity a = new GroupBuyAnswerEntity();
        a.questionId = questionId;
        a.body = body;
        a.answeredBy = answeredBy;
        a.answeredAt = answeredAt;
        return a;
    }
}
