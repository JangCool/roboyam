package com.community.groupbuy.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.DynamicInsert;

/**
 * 공구 질문(2b-6 Q&A) — shared V106 {@code group_buy_questions}. 상태 {@code OPEN}(답변 대기)·{@code ANSWERED}.
 * 읽기는 저장소의 한 줄 모양(프로젝션), 상태 전이·숨김·지우기는 저장소의 조건부 UPDATE 다 — 이 엔티티는 새 줄 넣기({@code persist})와 질의 경로(Q 클래스)에 쓴다.
 * {@code @DynamicInsert}: 값을 주지 않은 칸은 INSERT 에서 빠져 DB 기본값을 받는다(예전 INSERT 문장의 칸 목록과 같다).
 */
@Entity
@DynamicInsert
@Table(name = "group_buy_questions")
public class GroupBuyQuestionEntity {

    @Id
    private Long id;
    private Long groupBuyId;
    private Long userId;
    private String body;
    private Boolean secret;
    private Boolean forcedSecret;
    private String status;
    private Instant hiddenAt;
    private Long hiddenBy;
    private String hiddenReason;
    private Instant deletedAt;
    private Instant updatedAt;
    private Instant createdAt;
    /** 보관 만료일 — 마지막 답변일(없으면 작성일) + 3년 */
    private LocalDate retainUntil;

    protected GroupBuyQuestionEntity() {
    }

    /** 새 질문(답변 대기) */
    public static GroupBuyQuestionEntity asked(long id, long groupBuyId, long userId, String body, boolean secret, boolean forcedSecret, Instant now, LocalDate retainUntil) {
        GroupBuyQuestionEntity q = new GroupBuyQuestionEntity();
        q.id = id;
        q.groupBuyId = groupBuyId;
        q.userId = userId;
        q.body = body;
        q.secret = secret;
        q.forcedSecret = forcedSecret;
        q.status = "OPEN";
        q.createdAt = now;
        q.retainUntil = retainUntil;
        return q;
    }
}
