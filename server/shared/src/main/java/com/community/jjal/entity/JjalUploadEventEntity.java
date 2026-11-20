package com.community.jjal.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 올리기 한 번 — V261 {@code jjal_upload_events}(1분·하루 속도 한도를 DB 에서 센다 — 보관함 줄을 지워도 줄지 않는다). 정리 배치가 지난 줄을 지운다 */
@Entity
@Table(name = "jjal_upload_events")
public class JjalUploadEventEntity {

    @Id
    private Long id;
    private Long userId;
    private Instant createdAt;

    protected JjalUploadEventEntity() {
    }

    public static JjalUploadEventEntity create(long id, long userId, Instant now) {
        JjalUploadEventEntity e = new JjalUploadEventEntity();
        e.id = id;
        e.userId = userId;
        e.createdAt = now;
        return e;
    }

    public Long getId() {
        return id;
    }
}
