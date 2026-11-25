package com.community.groupbuy.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 배송 조회 설정(1행, id = 1) — shared V272, 계약 결정 90. 자동 조회 켜기·주기 · 발송 뒤 N일 자동 배송완료 · 스마트택배 API 키(SecretEncryptor 암호문) · 키 오류 알림 상태.
 * 읽기는 저장소의 한 줄 모양(프로젝션), 쓰기는 저장소의 UPDATE 다 — 이 엔티티는 질의 경로(Q 클래스)에 쓴다. {@code toString} 을 두지 않는다(암호문).
 */
@Entity
@Table(name = "commerce_tracking_settings")
public class CommerceTrackingSettingsEntity {

    /** 한 행의 키(CHECK id = 1) */
    public static final int ROW_ID = 1;

    @Id
    private Integer id;
    private Boolean enabled;
    private Integer intervalMinutes;
    private Boolean autoDeliverEnabled;
    private Integer autoDeliverDays;
    private String apiKeyCipher;
    private Instant keyUpdatedAt;
    private Long keyUpdatedBy;
    private String alertCode;
    private Instant alertAt;
    private Instant updatedAt;
    private Long updatedBy;

    protected CommerceTrackingSettingsEntity() {
    }
}
