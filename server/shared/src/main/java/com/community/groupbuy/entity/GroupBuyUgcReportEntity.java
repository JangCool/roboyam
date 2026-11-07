package com.community.groupbuy.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicInsert;

/**
 * 공구 질문·리뷰 신고 — shared V106 {@code group_buy_ugc_reports}. 유일 {@code ux_gbur_once(target_type, target_id, reporter_id)}. 상태 {@code OPEN}·{@code RESOLVED}.
 * 읽기는 저장소의 한 줄 모양(프로젝션), 처리는 저장소의 조건부 UPDATE 다 — 이 엔티티는 새 줄 넣기({@code persist})와 질의 경로(Q 클래스)에 쓴다.
 */
@Entity
@DynamicInsert
@Table(name = "group_buy_ugc_reports")
public class GroupBuyUgcReportEntity {

    @Id
    private Long id;
    private String targetType;
    private Long targetId;
    private Long reporterId;
    private String reason;
    private String detail;
    private String status;
    private Instant createdAt;
    private Instant resolvedAt;
    private Long resolvedBy;

    protected GroupBuyUgcReportEntity() {
    }

    /** 열린 신고 */
    public static GroupBuyUgcReportEntity open(long id, String targetType, long targetId, long reporterId, String reason, String detail, Instant now) {
        GroupBuyUgcReportEntity r = new GroupBuyUgcReportEntity();
        r.id = id;
        r.targetType = targetType;
        r.targetId = targetId;
        r.reporterId = reporterId;
        r.reason = reason;
        r.detail = detail;
        r.status = "OPEN";
        r.createdAt = now;
        return r;
    }
}
