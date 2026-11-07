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

    protected AdPlacementSettingEntity() {
    }

    /** 처음 저장하는 행 */
    public static AdPlacementSettingEntity first(boolean postInlineEnabled, String postInlineType, String postInlinePosition, boolean wingLeft, boolean wingRight,
                                                 String[] wingScreens, boolean boardListEnabled, int boardListAfter, int boardListCount, int boardListGap,
                                                 boolean adultEnabled, int stopFeeRate, long operatorId, Instant now) {
        AdPlacementSettingEntity s = new AdPlacementSettingEntity();
        s.id = SINGLE_ID;
        s.postInlineEnabled = postInlineEnabled;
        s.postInlineType = postInlineType;
        s.postInlinePosition = postInlinePosition;
        s.wingLeft = wingLeft;
        s.wingRight = wingRight;
        s.wingScreens = wingScreens;
        s.boardListEnabled = boardListEnabled;
        s.boardListAfter = boardListAfter;
        s.boardListCount = boardListCount;
        s.boardListGap = boardListGap;
        s.adultEnabled = adultEnabled;
        s.stopFeeRate = stopFeeRate;
        s.updatedAt = now;
        s.updatedBy = operatorId;
        return s;
    }
}
