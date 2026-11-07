package com.community.portone.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 포트원 설정 칸 한 줄 — V84 {@code portone_settings}(칸마다 한 줄인 키-값 표). 값은 암호문({@code v1:})으로만 오가고 {@code toString} 을 두지 않는다.
 * 값 저장·local 시드는 {@code PortOneSettingStore} 의 upsert({@code ON CONFLICT}), 값 지우기는 조건부 UPDATE.
 */
@Entity
@Table(name = "portone_settings")
public class PortOneSettingEntity {

    @Id
    @Column(name = "key")
    private String settingKey;

    private String valueCipher;
    private Instant updatedAt;
    private Long updatedBy;

    protected PortOneSettingEntity() {
    }
}
