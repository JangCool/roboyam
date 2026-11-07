package com.community.groupbuy.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicInsert;

/**
 * 배송비 정책 기간 줄 — shared V101(기간 겹침 배제 제약 · 적용 시작한 줄 불변 트리거).
 * 읽기는 저장소의 한 줄 모양(프로젝션), 상태 전이·수량은 저장소의 조건부 UPDATE 다 — 이 엔티티는 새 줄 넣기({@code persist})와 질의 경로(Q 클래스)에 쓴다.
 * {@code @DynamicInsert}: 값을 주지 않은 칸은 INSERT 에서 빠져 DB 기본값을 받는다(예전 INSERT 문장의 칸 목록과 같다).
 */
@Entity
@DynamicInsert
@Table(name = "shipping_policies")
public class ShippingPolicyEntity {

    @Id
    private Long id;
    private String code;
    private String name;
    private Long baseFee;
    private Long freeShippingOver;
    private Long returnFee;
    private String returnFeeMode;
    private Instant effectiveFrom;
    private Instant effectiveTo;
    private Long createdBy;
    private Instant createdAt;

    protected ShippingPolicyEntity() {
    }

    /** 새 기간 줄(끝 null = 열림) — 기간 겹침은 DB 배제 제약이 막는다 */
    public static ShippingPolicyEntity create(long id, String code, String name, long baseFee, Long freeShippingOver, long returnFee, String returnFeeMode,
                                              Instant effectiveFrom, Instant effectiveTo, Long createdBy, Instant now) {
        ShippingPolicyEntity p = new ShippingPolicyEntity();
        p.id = id;
        p.code = code;
        p.name = name;
        p.baseFee = baseFee;
        p.freeShippingOver = freeShippingOver;
        p.returnFee = returnFee;
        p.returnFeeMode = returnFeeMode;
        p.effectiveFrom = effectiveFrom;
        p.effectiveTo = effectiveTo;
        p.createdBy = createdBy;
        p.createdAt = now;
        return p;
    }
}
