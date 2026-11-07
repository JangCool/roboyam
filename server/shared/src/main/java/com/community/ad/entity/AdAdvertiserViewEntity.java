package com.community.ad.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 광고주 연락처 평문 열람 기록 한 줄 — shared V115 {@code ad_advertiser_views}(V131 사업자 광고주 — 키 = 사업자 신원 · 원천 {@code source}). 쌓기만 한다.
 * 대상은 광고주 줄({@link #direct}) 또는 사업자 신원({@link #business}) 중 하나.
 */
@Entity
@Table(name = "ad_advertiser_views")
public class AdAdvertiserViewEntity {

    /** 광고 담당자 칸(광고주 줄) */
    public static final String SOURCE_ADVERTISER = "ADVERTISER";

    @Id
    private Long id;

    private Long advertiserId;
    private Long businessProfileId;
    /** {@code ADVERTISER}(광고 담당자 칸) · {@code BUSINESS}(신원 대표자·연락처·이메일) */
    private String source;
    private Long operatorId;
    private String reason;
    private String ip;
    private Instant createdAt;

    protected AdAdvertiserViewEntity() {
    }

    /** 직접 등록 광고주 줄의 열람 */
    public static AdAdvertiserViewEntity direct(long id, long advertiserId, long operatorId, String reason, String ip, Instant now) {
        AdAdvertiserViewEntity v = base(id, SOURCE_ADVERTISER, operatorId, reason, ip, now);
        v.advertiserId = advertiserId;
        return v;
    }

    /** 사업자 광고주의 열람(키 = 사업자 신원) */
    public static AdAdvertiserViewEntity business(long id, long profileId, String source, long operatorId, String reason, String ip, Instant now) {
        AdAdvertiserViewEntity v = base(id, source, operatorId, reason, ip, now);
        v.businessProfileId = profileId;
        return v;
    }

    private static AdAdvertiserViewEntity base(long id, String source, long operatorId, String reason, String ip, Instant now) {
        AdAdvertiserViewEntity v = new AdAdvertiserViewEntity();
        v.id = id;
        v.source = source;
        v.operatorId = operatorId;
        v.reason = reason;
        v.ip = ip;
        v.createdAt = now;
        return v;
    }
}
