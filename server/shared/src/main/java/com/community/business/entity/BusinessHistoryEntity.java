package com.community.business.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 사업자 신원 처리 기록 한 줄 — shared V111 {@code business_history}(V130 서비스). 쌓기만 한다. 행위·기록한 사람 종류는 {@code BusinessStore} 의 글자 상수 */
@Entity
@Table(name = "business_history")
public class BusinessHistoryEntity {

    @Id
    private Long id;

    private Long profileId;
    private String action;
    private String actorType;
    private Long actorId;
    private String note;
    /** 서비스 하나에 대한 기록(서비스만 정지·해제)이면 그 서비스, 아니면 null */
    private String service;
    private Instant createdAt;

    protected BusinessHistoryEntity() {
    }

    /** {@code note} 는 부르는 쪽이 300자로 자른다 */
    public static BusinessHistoryEntity create(long id, long profileId, String action, String actorType, Long actorId, String note, String service, Instant now) {
        BusinessHistoryEntity h = new BusinessHistoryEntity();
        h.id = id;
        h.profileId = profileId;
        h.action = action;
        h.actorType = actorType;
        h.actorId = actorId;
        h.note = note;
        h.service = service;
        h.createdAt = now;
        return h;
    }
}
