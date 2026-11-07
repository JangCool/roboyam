package com.community.bizapplication.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 신청 건 소재 버전 한 줄 — shared V112 {@code biz_creative_versions}(건 · 버전마다 하나, ux_biz_creatives). 내용은 jsonb 글자({@code content}), 승인 때 그 버전에 승인 시각을
 * 적는다(저장소의 UPDATE).
 */
@Entity
@Table(name = "biz_creative_versions")
public class BizCreativeVersionEntity {

    @Id
    private Long id;

    private Long applicationId;
    private Integer version;
    @JdbcTypeCode(SqlTypes.JSON)
    private String content;
    /** {@link BizActorType} 이름 */
    private String createdByType;
    private Long createdBy;
    private Instant createdAt;
    private Instant approvedAt;

    protected BizCreativeVersionEntity() {
    }

    public static BizCreativeVersionEntity create(long id, long applicationId, int version, String contentJson, BizActorType byType, Long by, Instant now) {
        BizCreativeVersionEntity c = new BizCreativeVersionEntity();
        c.id = id;
        c.applicationId = applicationId;
        c.version = version;
        c.content = contentJson;
        c.createdByType = byType.name();
        c.createdBy = by;
        c.createdAt = now;
        return c;
    }
}
