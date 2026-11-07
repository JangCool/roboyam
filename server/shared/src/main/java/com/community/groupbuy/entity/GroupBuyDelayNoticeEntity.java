package com.community.groupbuy.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.DynamicInsert;

/**
 * 발송 지연 공지 — shared V101.
 * 읽기는 저장소의 한 줄 모양(프로젝션), 상태 전이·수량은 저장소의 조건부 UPDATE 다 — 이 엔티티는 새 줄 넣기({@code persist})와 질의 경로(Q 클래스)에 쓴다.
 * {@code @DynamicInsert}: 값을 주지 않은 칸은 INSERT 에서 빠져 DB 기본값을 받는다(예전 INSERT 문장의 칸 목록과 같다).
 */
@Entity
@DynamicInsert
@Table(name = "group_buy_delay_notices")
public class GroupBuyDelayNoticeEntity {

    @Id
    private Long id;
    private Long groupBuyId;
    private LocalDate previousPlannedAt;
    private LocalDate newPlannedAt;
    private String reason;
    private Long operatorId;
    private Instant createdAt;

    protected GroupBuyDelayNoticeEntity() {
    }

    public static GroupBuyDelayNoticeEntity create(long id, long groupBuyId, LocalDate previous, LocalDate next, String reason, Long operatorId, Instant now) {
        GroupBuyDelayNoticeEntity d = new GroupBuyDelayNoticeEntity();
        d.id = id;
        d.groupBuyId = groupBuyId;
        d.previousPlannedAt = previous;
        d.newPlannedAt = next;
        d.reason = reason;
        d.operatorId = operatorId;
        d.createdAt = now;
        return d;
    }
}
