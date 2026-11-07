package com.community.commerce.sales.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicInsert;

/**
 * 커머스 고유 사업자 안내 칸(1행, id = 1) — shared V104 · V158(겹치는 칸은 운영 주체 표로 옮겼다).
 * 읽기는 저장소의 한 줄 모양(프로젝션), 상태 전이·수량은 저장소의 조건부 UPDATE 다 — 이 엔티티는 새 줄 넣기({@code persist})와 질의 경로(Q 클래스)에 쓴다.
 * {@code @DynamicInsert}: 값을 주지 않은 칸은 INSERT 에서 빠져 DB 기본값을 받는다(예전 INSERT 문장의 칸 목록과 같다).
 */
@Entity
@DynamicInsert
@Table(name = "commerce_business_info")
public class CommerceBusinessInfoEntity {

    @Id
    private Integer id;
    private String businessHours;
    private String shippingRefundPolicy;
    private Instant updatedAt;
    private Long updatedBy;

    protected CommerceBusinessInfoEntity() {
    }

    /** 한 행의 키(CHECK id = 1) */
    public static final int ROW_ID = 1;
}
