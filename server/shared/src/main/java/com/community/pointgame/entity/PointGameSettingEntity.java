package com.community.pointgame.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 포인트 게임 설정(행 하나) — {@code point_game_settings}(V234, point-games-contract 「당첨 확인 링크」). 행은 마이그레이션이 넣는다(없으면 저장소가
 * {@code ON CONFLICT DO NOTHING} 으로 만든다). 저장은 저장소 {@code PointGameSettingStore} 의 UPDATE(행을 잠근 뒤 — 감사와 한 트랜잭션). 복권 사업자가 바뀔 수 있어 설정값이다.
 */
@Entity
@Table(name = "point_game_settings")
public class PointGameSettingEntity {

    /** 행은 하나 */
    public static final int ROW_ID = 1;
    public static final String DEFAULT_PROVIDER_NAME = "동행복권";
    public static final String DEFAULT_CHECK_URL = "https://dhlottery.co.kr";
    public static final int PROVIDER_NAME_MAX = 30;
    public static final int CHECK_URL_MAX = 300;

    @Id
    private Integer id;

    private String lotteryProviderName;
    private String lotteryCheckUrl;
    private Instant updatedAt;
    private Long updatedBy;

    protected PointGameSettingEntity() {
    }

    public Integer getId() {
        return id;
    }

    public String getLotteryProviderName() {
        return lotteryProviderName;
    }

    public String getLotteryCheckUrl() {
        return lotteryCheckUrl;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }
}
