package com.community.ad.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 광고주 처리 기록 한 줄 — shared V131 {@code ad_advertiser_events}. 쌓기만 한다(광고주를 지울 때 함께 지운다). {@code fields} 는 바뀐 칸 이름을 쉼표로 이은 글자 */
@Entity
@Table(name = "ad_advertiser_events")
public class AdAdvertiserEventEntity {

    @Id
    private Long id;

    private Long advertiserId;
    private String action;
    private Long actorId;
    private String fields;
    private Instant createdAt;

    protected AdAdvertiserEventEntity() {
    }

    public static AdAdvertiserEventEntity create(long id, long advertiserId, String action, Long actorId, String fields, Instant now) {
        AdAdvertiserEventEntity e = new AdAdvertiserEventEntity();
        e.id = id;
        e.advertiserId = advertiserId;
        e.action = action;
        e.actorId = actorId;
        e.fields = fields;
        e.createdAt = now;
        return e;
    }
}
