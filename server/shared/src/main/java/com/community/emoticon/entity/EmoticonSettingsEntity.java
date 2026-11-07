package com.community.emoticon.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 로보티콘 설정 한 행 — shared V142(받기 보상·개수·대기 상한) · V143(유료 판매·수수료·정산일·최소 지급액·환불 기간·가격 단계 {@code integer[]}).
 * 행은 마이그레이션이 넣는다(id = 1). 읽기는 저장소의 한 줄 모양(프로젝션), 저장은 저장소의 UPDATE 다 — 이 엔티티는 질의 경로(Q 클래스)와 행 잠금에 쓴다.
 * 가격 단계를 바꾸면 쓰는 단계를 빼지 못하게 트리거({@code tr_emoticon_settings_tiers})가 본다.
 */
@Entity
@Table(name = "emoticon_settings")
public class EmoticonSettingsEntity {

    /** 한 행의 ID */
    public static final int ROW_ID = 1;

    @Id
    private Integer id;
    private int rewardPerAcquire;
    private int rewardDailyCap;
    private int itemMin;
    private int itemMax;
    private int pendingLimit;
    private Instant updatedAt;
    private Long updatedBy;
    private boolean paidSalesEnabled;
    private int feeRatePercent;
    private int settlementDay;
    private int payoutMinimum;
    private int refundDays;
    @JdbcTypeCode(SqlTypes.ARRAY)
    private Integer[] priceTiers;

    protected EmoticonSettingsEntity() {
    }
}
