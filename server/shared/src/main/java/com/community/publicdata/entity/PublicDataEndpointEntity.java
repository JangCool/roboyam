package com.community.publicdata.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 공공데이터 API 주소 한 줄 — V150 {@code public_data_endpoints}(API 마다 한 줄: {@code NTS}·{@code HOLIDAY}). 빈 칸(null)은 코드의 공식 기본값.
 * 저장·연결 시험 기록은 {@code PublicDataEndpointStore} 의 upsert({@code ON CONFLICT} — 처음 저장할 때 줄이 없다)가 한다.
 */
@Entity
@Table(name = "public_data_endpoints")
public class PublicDataEndpointEntity {

    @Id
    private String api;

    private String baseUrl;
    private String queryTemplate;
    private String dateField;
    private String nameField;
    private Instant updatedAt;
    private Long updatedBy;
    private String testedResult;
    private Instant testedAt;
    private Long testedBy;

    protected PublicDataEndpointEntity() {
    }
}
