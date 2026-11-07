package com.community.groupbuy.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 공구 수정 이력(표시 기록 사본 — jsonb) — shared V101. 넣기는 저장소의 INSERT … SELECT(jsonb_agg 스냅숏) 한 문장.
 * 읽기는 저장소의 한 줄 모양(프로젝션), 상태 전이·수량은 저장소의 조건부 UPDATE 다 — 이 엔티티는 새 줄 넣기({@code persist})와 질의 경로(Q 클래스)에 쓴다.
 * {@code @DynamicInsert}: 값을 주지 않은 칸은 INSERT 에서 빠져 DB 기본값을 받는다(예전 INSERT 문장의 칸 목록과 같다).
 */
@Entity
@DynamicInsert
@Table(name = "group_buy_revisions")
public class GroupBuyRevisionEntity {

    @Id
    private Long id;
    private Long groupBuyId;
    private Integer revisionNo;
    private String title;
    private String productName;
    private String summary;
    private String spec;
    private String description;
    @JdbcTypeCode(SqlTypes.JSON)
    private String images;
    private Integer maxQty;
    private Integer addedQty;
    @JdbcTypeCode(SqlTypes.JSON)
    private String optionAxes;
    @JdbcTypeCode(SqlTypes.JSON)
    private String skus;
    @JdbcTypeCode(SqlTypes.JSON)
    private String notices;
    private String changeKind;
    private Long editedBy;
    private Instant createdAt;

    protected GroupBuyRevisionEntity() {
    }
}
