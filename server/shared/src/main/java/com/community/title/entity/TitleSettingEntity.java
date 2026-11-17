package com.community.title.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 작가 칭호 설정 — {@code title_settings}(V240, 행 하나 id = 1). 이달의 작가 한 달 최대 인원 · 명예 작가가 되는 이달의 작가 횟수 · 거절 뒤 다시 신청까지 일수 */
@Entity
@Table(name = "title_settings")
public class TitleSettingEntity {

    public static final int ID = 1;

    @Id
    private Integer id;
    private int monthlyMax;
    private int honoraryCount;
    private int reapplyDays;
    private Instant updatedAt;
    private Long updatedBy;

    protected TitleSettingEntity() {
    }

    public void change(int monthlyMax, int honoraryCount, int reapplyDays, long operatorId, Instant now) {
        this.monthlyMax = monthlyMax;
        this.honoraryCount = honoraryCount;
        this.reapplyDays = reapplyDays;
        this.updatedAt = now;
        this.updatedBy = operatorId;
    }

    public Integer getId() {
        return id;
    }

    public int getMonthlyMax() {
        return monthlyMax;
    }

    public int getHonoraryCount() {
        return honoraryCount;
    }

    public int getReapplyDays() {
        return reapplyDays;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }
}
