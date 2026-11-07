package com.community.groupbuy.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicInsert;

/**
 * 반품 처리 방법 — shared V102.
 * 읽기는 저장소의 한 줄 모양(프로젝션), 상태 전이·수량은 저장소의 조건부 UPDATE 다 — 이 엔티티는 새 줄 넣기({@code persist})와 질의 경로(Q 클래스)에 쓴다.
 * {@code @DynamicInsert}: 값을 주지 않은 칸은 INSERT 에서 빠져 DB 기본값을 받는다(예전 INSERT 문장의 칸 목록과 같다).
 */
@Entity
@DynamicInsert
@Table(name = "group_buy_return_dispositions")
public class GroupBuyReturnDispositionEntity {

    @Id
    private Long id;
    private Long requestItemId;
    private String disposition;
    private Integer qty;
    private Long createdBy;
    private Instant createdAt;

    protected GroupBuyReturnDispositionEntity() {
    }

    public static GroupBuyReturnDispositionEntity create(long id, long requestItemId, String disposition, int qty, Long createdBy, Instant now) {
        GroupBuyReturnDispositionEntity d = new GroupBuyReturnDispositionEntity();
        d.id = id;
        d.requestItemId = requestItemId;
        d.disposition = disposition;
        d.qty = qty;
        d.createdBy = createdBy;
        d.createdAt = now;
        return d;
    }
}
