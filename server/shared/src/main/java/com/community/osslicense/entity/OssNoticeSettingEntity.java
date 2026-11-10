package com.community.osslicense.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 오픈소스 라이선스 고지의 설정 한 줄 — V180__oss_notice.sql 의 {@code oss_notice_settings}(id = 1, docs/features/oss-licenses-contract.md).
 * {@code intro} 는 공개 화면 {@code /licenses} 제목 아래 설명(NULL = 화면 기본 문구). 관리자 앱만 고친다.
 */
@Entity
@Table(name = "oss_notice_settings")
public class OssNoticeSettingEntity {

    public static final int ROW_ID = 1;

    @Id
    private Integer id;

    private String intro;
    private Long updatedBy;
    private Instant updatedAt;

    protected OssNoticeSettingEntity() {
    }

    /** 안내 문구를 바꾼다(정리한 값 — 비우면 null) */
    public void changeIntro(String intro, long operatorId, Instant now) {
        this.intro = intro;
        this.updatedBy = operatorId;
        this.updatedAt = now;
    }

    public Integer getId() {
        return id;
    }

    public String getIntro() {
        return intro;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
