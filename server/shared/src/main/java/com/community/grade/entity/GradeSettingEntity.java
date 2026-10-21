package com.community.grade.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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

    /** 마지막으로 저장한 운영자 */
    private Long updatedBy;

    private Instant updatedAt;

    protected GradeSettingEntity() {
    }

    /** 저장한다 — 옵션과 다음 번호, 저장한 운영자·시각 */
    public void change(GradeManualPolicy manualPolicy, int nextSerial, long updatedBy, Instant now) {
        this.manualPolicy = manualPolicy;
        this.nextSerial = nextSerial;
        this.updatedBy = updatedBy;
        this.updatedAt = now;
    }

    public GradeManualPolicy getManualPolicy() {
        return manualPolicy;
    }

    public int getNextSerial() {
        return nextSerial;
    }
}
