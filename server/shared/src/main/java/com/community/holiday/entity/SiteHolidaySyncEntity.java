package com.community.holiday.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 공공데이터 쉬는 날 받기의 마지막 결과 — V150 {@code site_holiday_sync}(1행, id = 1, 화면 표시용). 덮어쓰기는 {@code SiteHolidayStore#recordSync}({@code ON CONFLICT} upsert).
 * outcome: OK · PARTIAL · NO_KEY · FAILED.
 */
@Entity
@Table(name = "site_holiday_sync")
public class SiteHolidaySyncEntity {

    @Id
    private Integer id;

    private Instant ranAt;
    private String outcome;
    private Integer[] appliedYears = new Integer[0];
    private Integer[] failedYears = new Integer[0];
    private int changed;
    private Long ranBy;

    protected SiteHolidaySyncEntity() {
    }
}
