package com.community.nicknameimage.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 닉네임 그림 신청 조건(행 하나, {@code id = 1}) — V72__nickname_images.sql 의 {@code nickname_image_settings}. 행은 마이그레이션이 만든다.
 * 회원 화면(api)이 요청마다 읽어 판정하고, 관리자(admin)가 {@link #change} 로만 바꾼다. {@code gradeKey} 는 등급 외래 키가 아니다(지워진 등급이면 등급 조건을 채우지 못한다).
 */
@Entity
@Table(name = "nickname_image_settings")
public class NicknameImageSettingEntity {

    public static final int ROW_ID = 1;

    @Id
    private Integer id;

    @Enumerated(EnumType.STRING)
    private NicknameImageRequirementMode mode;

    private String gradeKey;
    private int minDays;
    private int minActivity;
    private int minPoints;
    private boolean requireEmailVerified;
    private Instant updatedAt;
    private Long updatedBy;

    protected NicknameImageSettingEntity() {
    }

    /** 저장한다 — GRADE 면 이메일 인증은 늘 true 로 둔다(CUSTOM 칸은 받은 값 그대로) */
    public void change(NicknameImageRequirementMode mode, String gradeKey, int minDays, int minActivity, int minPoints, boolean requireEmailVerified,
                       long operatorId, Instant now) {
        this.mode = mode;
        this.gradeKey = gradeKey;
        this.minDays = minDays;
        this.minActivity = minActivity;
        this.minPoints = minPoints;
        this.requireEmailVerified = mode == NicknameImageRequirementMode.GRADE || requireEmailVerified;
        this.updatedAt = now;
        this.updatedBy = operatorId;
    }

    public NicknameImageRequirementMode getMode() {
        return mode;
    }

    public String getGradeKey() {
        return gradeKey;
    }

    public int getMinDays() {
        return minDays;
    }

    public int getMinActivity() {
        return minActivity;
    }

    public int getMinPoints() {
        return minPoints;
    }

    /** GRADE 방식이면 늘 true(저장 때 맞춘다) */
    public boolean isRequireEmailVerified() {
        return mode == NicknameImageRequirementMode.GRADE || requireEmailVerified;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }
}
