package com.community.grade.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 등급 옵션(행이 하나뿐이다, {@code id = 1}) — V9__grades.sql 의 {@code grade_settings}. 수동으로 올린 회원의 이후 자동 승급 옵션과
 * 새 등급 키({@code GRADE_<번호>})의 다음 번호를 둔다. 행은 마이그레이션이 만든다.
 */
@Entity
@Table(name = "grade_settings")
public class GradeSettingEntity {

    /** 행 번호 — 행은 하나뿐이라 항상 1 이다 */
    public static final int ROW_ID = 1;

    @Id
    private Integer id;

    @Enumerated(EnumType.STRING)
    private GradeManualPolicy manualPolicy;

    private int nextSerial;

    /** 레벨 곡선 기준값(V196, 1~1000, 기본 10) — 레벨 L 에 필요한 누적 경험치 = ceil(base × (L−1)^지수) */
    private int xpCurveBase = 10;

    /** 레벨 곡선 지수(V245, 1.0·1.5·2.0·2.5·3.0 뿐, 기본 2.0) — 셈은 {@code XpCurve} */
    private BigDecimal xpCurveExponent = new BigDecimal("2.0");

    /** 마지막으로 저장한 운영자 */
    private Long updatedBy;

    private Instant updatedAt;

    protected GradeSettingEntity() {
    }

    /** 저장한다 — 옵션과 다음 번호, 레벨 곡선(기준값·지수), 저장한 운영자·시각 */
    public void change(GradeManualPolicy manualPolicy, int nextSerial, int xpCurveBase, BigDecimal xpCurveExponent, long updatedBy, Instant now) {
        this.manualPolicy = manualPolicy;
        this.nextSerial = nextSerial;
        this.xpCurveBase = xpCurveBase;
        this.xpCurveExponent = xpCurveExponent;
        this.updatedBy = updatedBy;
        this.updatedAt = now;
    }

    public GradeManualPolicy getManualPolicy() {
        return manualPolicy;
    }

    public int getNextSerial() {
        return nextSerial;
    }

    public int getXpCurveBase() {
        return xpCurveBase;
    }

    public BigDecimal getXpCurveExponent() {
        return xpCurveExponent;
    }
}
