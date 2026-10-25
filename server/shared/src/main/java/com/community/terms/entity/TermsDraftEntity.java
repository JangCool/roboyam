package com.community.terms.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 약관 초안 — V62__terms.sql 의 {@code terms_drafts}. 종류마다 최대 한 행이고 키가 종류 이름({@code TERMS}·{@code PRIVACY})이다. 게시하면 지운다.
 * {@link #toString()} 은 본문을 싣지 않는다.
 */
@Entity
@Table(name = "terms_drafts")
public class TermsDraftEntity {

    /** 종류 이름({@link TermsKind#name()}) */
    @Id
    private String kind;

    /** 정화된 HTML */
    private String body;

    private Instant updatedAt;

    /** 마지막으로 저장한 운영자({@code operators.id}). FK 없음 */
    private Long updatedBy;

    protected TermsDraftEntity() {
    }

    public static TermsDraftEntity create(TermsKind kind, String body, long operatorId, Instant now) {
        TermsDraftEntity draft = new TermsDraftEntity();
        draft.kind = kind.name();
        draft.body = body;
        draft.updatedBy = operatorId;
        draft.updatedAt = now;
        return draft;
    }

    /** 본문을 바꾼다(정화는 부르는 쪽이 끝냈다) */
    public void changeBody(String body, long operatorId, Instant now) {
        this.body = body;
        this.updatedBy = operatorId;
        this.updatedAt = now;
    }

    public TermsKind getKind() {
        return TermsKind.valueOf(kind);
    }

    public String getBody() {
        return body;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    @Override
    public String toString() {
        return "TermsDraftEntity[kind=" + kind + ", updatedAt=" + updatedAt + "]";
    }
}
