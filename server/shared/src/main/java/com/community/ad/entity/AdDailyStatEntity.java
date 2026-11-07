package com.community.ad.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 광고 일별 집계 한 줄 — shared V118 {@code ad_daily_stats}(건 · 소재 버전 · KST 날짜 · 자리마다 하나). 쌓기는 공개 노출 API 의 한 문장
 * ({@code PublicAdStore.count} — 하루 1번 판정 {@code ON CONFLICT} + 집계 upsert, 네이티브)뿐이라 만들기 메서드가 없다 — 읽기·질의용 매핑이다.
 */
@Entity
@Table(name = "ad_daily_stats")
@IdClass(AdDailyStatEntity.Key.class)
public class AdDailyStatEntity {

    @Id
    private Long applicationId;
    @Id
    private Integer version;
    @Id
    private LocalDate day;
    @Id
    private String placement;

    private Long impressions;
    private Long clicks;
    private Instant updatedAt;

    protected AdDailyStatEntity() {
    }

    /** 복합 키(건 · 버전 · 날짜 · 자리) */
    public static class Key implements Serializable {

        private Long applicationId;
        private Integer version;
        private LocalDate day;
        private String placement;

        protected Key() {
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(applicationId, k.applicationId) && Objects.equals(version, k.version) && Objects.equals(day, k.day)
                    && Objects.equals(placement, k.placement);
        }

        @Override
        public int hashCode() {
            return Objects.hash(applicationId, version, day, placement);
        }
    }
}
