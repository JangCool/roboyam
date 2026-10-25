package com.community.terms.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 게시한 약관 본문 한 버전 — V62__terms.sql 의 {@code terms_versions}. 게시한 뒤에는 고치지 않는다(바꾸는 메서드가 없다). {@code (kind, version)} 은 유일하다.
 * {@link #toString()} 은 본문을 싣지 않는다.
 */
@Entity
@Table(name = "terms_versions")
public class TermsVersionEntity {

    @Id
    private Long id;

    @Enumerated(EnumType.STRING)
    private TermsKind kind;

    private Integer version;

    /** 정화된 HTML({@code TermsHtml}) */
    private String body;

    /** 시행일(사이트 표시 시간대 기준 날짜) */
    private LocalDate effectiveDate;

    /** 변경 요지(0~200자). 없으면 null */
    private String changeNote;

    private Instant publishedAt;

    /** 게시한 운영자({@code operators.id}). FK 없음 — 운영자가 지워져도 남는다 */
    private Long publishedBy;

    protected TermsVersionEntity() {
    }

    /** 새 버전을 게시한다 — 값 검사(시행일·변경 요지·본문 정화)는 부르는 쪽이 끝냈다 */
    public static TermsVersionEntity publish(long id, TermsKind kind, int version, String body, LocalDate effectiveDate, String changeNote,
            long operatorId, Instant now) {
        TermsVersionEntity entity = new TermsVersionEntity();
        entity.id = id;
        entity.kind = kind;
        entity.version = version;
        entity.body = body;
        entity.effectiveDate = effectiveDate;
        entity.changeNote = changeNote;
        entity.publishedAt = now;
        entity.publishedBy = operatorId;
        return entity;
    }

    public Long getId() {
        return id;
    }

    public TermsKind getKind() {
        return kind;
    }

    public Integer getVersion() {
        return version;
    }

    public String getBody() {
        return body;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public String getChangeNote() {
        return changeNote;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Long getPublishedBy() {
        return publishedBy;
    }

    @Override
    public String toString() {
        return "TermsVersionEntity[id=" + id + ", kind=" + kind + ", version=" + version + ", effectiveDate=" + effectiveDate + "]";
    }
}
