package com.community.publicdata.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 공공데이터포털 설정 칸 한 줄 — V125 {@code public_data_settings}(칸마다 한 줄인 키-값 표). 값은 암호문({@code v1:})으로만 오가고 {@code toString} 을 두지 않는다.
 * 값 저장·연결 시험 기록은 {@code PublicDataSettingStore} 의 upsert({@code ON CONFLICT}), 값 지우기는 조건부 UPDATE.
 */
@Entity
@Table(name = "public_data_settings")
public class PublicDataSettingEntity {

    @Id
    @Column(name = "key")
    private String settingKey;

    private String valueCipher;
    private Instant updatedAt;
    private Long updatedBy;
    private String testedResult;
    private Instant testedAt;
    private Long testedBy;

    protected PublicDataSettingEntity() {
    }
}
