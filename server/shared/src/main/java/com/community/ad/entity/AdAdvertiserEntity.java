package com.community.ad.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 계정 없는 광고주 한 줄 — shared V115 {@code ad_advertisers}(V131 보관·연락처 파기). 연락처는 {@code DataCipher} 암호문({@code *_enc})과 가린 값({@code *_masked})을 같이 두고,
 * 목록·검색은 가린 값만 읽는다. 이름은 대소문자·앞뒤 공백을 무시하고 유일(ux_ad_advertisers_name), 사업자 신원에는 하나만 붙는다(ux_ad_advertisers_profile).
 * 고치기·보관은 저장소의 UPDATE 가 한다. {@code toString} 을 두지 않는다(연락처 암호문).
 */
@Entity
@Table(name = "ad_advertisers")
public class AdAdvertiserEntity {

    @Id
    private Long id;

    private String name;
    private Long businessProfileId;
    private String contactNameEnc;
    private String contactPhoneEnc;
    private String emailEnc;
    private String contactNameMasked;
    private String contactPhoneMasked;
    private String emailMasked;
    private String memo;
    private Long createdBy;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant archivedAt;
    private Long archivedBy;
    private Instant contactPurgedAt;

    protected AdAdvertiserEntity() {
    }

    public static AdAdvertiserEntity create(long id, String name, Long businessProfileId, String contactNameEnc, String contactPhoneEnc, String emailEnc,
                                            String contactNameMasked, String contactPhoneMasked, String emailMasked, String memo, long createdBy, Instant now) {
        AdAdvertiserEntity a = new AdAdvertiserEntity();
        a.id = id;
        a.name = name;
        a.businessProfileId = businessProfileId;
        a.contactNameEnc = contactNameEnc;
        a.contactPhoneEnc = contactPhoneEnc;
        a.emailEnc = emailEnc;
        a.contactNameMasked = contactNameMasked;
        a.contactPhoneMasked = contactPhoneMasked;
        a.emailMasked = emailMasked;
        a.memo = memo;
        a.createdBy = createdBy;
        a.createdAt = now;
        a.updatedAt = now;
        return a;
    }
}
