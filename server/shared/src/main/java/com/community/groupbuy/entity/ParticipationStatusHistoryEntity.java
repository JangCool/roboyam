package com.community.groupbuy.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicInsert;

/**
 * 주문 상태 이력 — shared V101.
 * 읽기는 저장소의 한 줄 모양(프로젝션), 상태 전이·수량은 저장소의 조건부 UPDATE 다 — 이 엔티티는 새 줄 넣기({@code persist})와 질의 경로(Q 클래스)에 쓴다.
 * {@code @DynamicInsert}: 값을 주지 않은 칸은 INSERT 에서 빠져 DB 기본값을 받는다(예전 INSERT 문장의 칸 목록과 같다).
 */
@Entity
@DynamicInsert
@Table(name = "participation_status_history")
public class ParticipationStatusHistoryEntity {

    @Id
    private Long id;
    private Long participationId;
    private Long groupBuyId;
    private Long userId;
    @Enumerated(EnumType.STRING)
    private ParticipationStatus fromStatus;
    @Enumerated(EnumType.STRING)
    private ParticipationStatus toStatus;
    private String cause;
    private Long actorId;
    private Instant createdAt;

    protected ParticipationStatusHistoryEntity() {
    }

    public static ParticipationStatusHistoryEntity create(long id, long participationId, long groupBuyId, long userId, ParticipationStatus from, ParticipationStatus to,
                                                          String cause, Long actorId, Instant now) {
        ParticipationStatusHistoryEntity h = new ParticipationStatusHistoryEntity();
        h.id = id;
        h.participationId = participationId;
        h.groupBuyId = groupBuyId;
        h.userId = userId;
        h.fromStatus = from;
        h.toStatus = to;
        h.cause = cause;
        h.actorId = actorId;
        h.createdAt = now;
        return h;
    }
}
