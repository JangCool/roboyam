package com.community.bizapplication.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 신청 건 처리 기록 한 줄 — shared V112 {@code biz_application_events}. 쌓기만 한다. 행위({@code action})는 {@link BizEventAction} 이름이지만 글자 칸으로 둔다
 * (광고 성과의 멈춘 구간 판정 · 처리 기록 화면이 글자로 읽는다).
 */
@Entity
@Table(name = "biz_application_events")
public class BizApplicationEventEntity {

    @Id
    private Long id;

    private Long applicationId;
    private String action;
    @Enumerated(EnumType.STRING)
    private BizActorType actorType;
    private Long actorId;
    private String note;
    private Instant createdAt;

    protected BizApplicationEventEntity() {
    }

    /** {@code note} 는 부르는 쪽이 300자로 자른다 */
    public static BizApplicationEventEntity create(long id, long applicationId, BizEventAction action, BizActorType actorType, Long actorId, String note, Instant now) {
        BizApplicationEventEntity e = new BizApplicationEventEntity();
        e.id = id;
        e.applicationId = applicationId;
        e.action = action.name();
        e.actorType = actorType;
        e.actorId = actorId;
        e.note = note;
        e.createdAt = now;
        return e;
    }
}
