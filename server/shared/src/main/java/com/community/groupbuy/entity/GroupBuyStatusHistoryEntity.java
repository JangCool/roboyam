package com.community.groupbuy.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicInsert;

/**
 * 공구 상태 이력 — shared V101.
 * 읽기는 저장소의 한 줄 모양(프로젝션), 상태 전이·수량은 저장소의 조건부 UPDATE 다 — 이 엔티티는 새 줄 넣기({@code persist})와 질의 경로(Q 클래스)에 쓴다.
 * {@code @DynamicInsert}: 값을 주지 않은 칸은 INSERT 에서 빠져 DB 기본값을 받는다(예전 INSERT 문장의 칸 목록과 같다).
 */
@Entity
@DynamicInsert
@Table(name = "group_buy_status_history")
public class GroupBuyStatusHistoryEntity {

    @Id
    private Long id;
    private Long groupBuyId;
    @Enumerated(EnumType.STRING)
    private GroupBuyStatus fromStatus;
    @Enumerated(EnumType.STRING)
    private GroupBuyStatus toStatus;
    private String reason;
    private Long actorId;
    private Instant createdAt;

    protected GroupBuyStatusHistoryEntity() {
    }

    public static GroupBuyStatusHistoryEntity create(long id, long groupBuyId, GroupBuyStatus from, GroupBuyStatus to, String reason, Long actorId, Instant now) {
        GroupBuyStatusHistoryEntity h = new GroupBuyStatusHistoryEntity();
        h.id = id;
        h.groupBuyId = groupBuyId;
        h.fromStatus = from;
        h.toStatus = to;
        h.reason = reason;
        h.actorId = actorId;
        h.createdAt = now;
        return h;
    }
}
