package com.community.dashboard.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 운영자 대시보드 구성(운영자당 한 행) — admin 스트림 V5 의 {@code operator_dashboards}. 행이 없으면 「저장한 적 없음」이다.
 * 저장은 {@code DashboardLayoutStore#save} 의 {@code ON CONFLICT} upsert 한 문장이다(같은 운영자의 동시 저장도 행 하나로 끝난다 — 나중 것이 이긴다).
 * 칸 검사·정리는 저장하는 쪽({@code DashboardLayoutService})이 끝낸다.
 */
@Entity
@Table(name = "operator_dashboards")
public class OperatorDashboardEntity {

    @Id
    private Long operatorId;

    /** 저장 모양 판 */
    private int version;

    /** 격자 칸 수 */
    private int columns;

    /** 가젯 배열 JSON — {@code [{id, type, x, y, w, h, config}]} */
    @JdbcTypeCode(SqlTypes.JSON)
    private String gadgets;

    private Instant updatedAt;

    protected OperatorDashboardEntity() {
    }
}
