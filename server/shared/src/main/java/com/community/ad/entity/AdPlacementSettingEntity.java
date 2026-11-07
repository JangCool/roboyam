package com.community.ad.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 광고 노출 자리 설정 — shared V118 {@code ad_placement_settings}(한 행, id = 1 · V119 성인 · V127 중도 해지 수수료율 · V155 목록 한 줄 개수·간격). 행이 없으면 서버 기본값
 * ({@code AdPlacements.DEFAULTS} · 수수료율 10). 처음 저장은 {@link #first}, 그 뒤는 저장소의 UPDATE 가 고친다.
 */
@Entity
@Table(name = "ad_placement_settings")
public class AdPlacementSettingEntity {

    /** 한 행의 키 */
    public static final int SINGLE_ID = 1;

    @Id
    private Integer id;

    private Boolean postInlineEnabled;
    private String postInlineType;
    private String postInlinePosition;
    private Boolean wingLeft;
    private Boolean wingRight;
    /** {@code text[]} — 날개 배너를 띄우는 화면 */
    @JdbcTypeCode(SqlTypes.ARRAY)
    private String[] wingScreens;
    private Boolean boardListEnabled;
    private Integer boardListAfter;
    private Integer boardListCount;
    private Integer boardListGap;
    private Boolean adultEnabled;
    private Integer stopFeeRate;
    private Instant updatedAt;
    private Long updatedBy;

    /** 행은 {@code AdPlacementStore} 가 처음 저장 때 {@code ON CONFLICT} 로 넣고 QueryDSL 로 고친다 — 이 엔티티는 읽기·질의용이다 */
    protected AdPlacementSettingEntity() {
    }
}
