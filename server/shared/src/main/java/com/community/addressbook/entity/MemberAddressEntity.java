package com.community.addressbook.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 회원 주소록 한 줄 — V139 {@code member_addresses}. 개인정보 칸(받는 사람·전화·주소·메모)은 {@code DataCipher} 암호문({@code *_enc})으로만 담는다 — {@code toString} 을 두지 않는다.
 * 회원당 기본 배송지 하나(부분 유일 색인 {@code ux_member_addresses_default}) — 「앞 기본 끄기 → 새 기본 켜기」 차례는 {@code MemberAddressStore} 가 문장 순서로 지킨다.
 */
@Entity
@Table(name = "member_addresses")
public class MemberAddressEntity {

    @Id
    private Long id;

    private Long userId;
    /** 별칭(없으면 null) */
    private String label;
    private String recipientEnc;
    private String phoneEnc;
    private String postcode;
    private String address1Enc;
    private String address2Enc;
    private String memoEnc;
    private boolean isDefault;
    private Instant createdAt;
    private Instant updatedAt;

    protected MemberAddressEntity() {
    }

    public static MemberAddressEntity create(long id, long userId, String label, String recipientEnc, String phoneEnc, String postcode, String address1Enc,
                                             String address2Enc, String memoEnc, boolean isDefault, Instant now) {
        MemberAddressEntity row = new MemberAddressEntity();
        row.id = id;
        row.userId = userId;
        row.label = label;
        row.recipientEnc = recipientEnc;
        row.phoneEnc = phoneEnc;
        row.postcode = postcode;
        row.address1Enc = address1Enc;
        row.address2Enc = address2Enc;
        row.memoEnc = memoEnc;
        row.isDefault = isDefault;
        row.createdAt = now;
        row.updatedAt = now;
        return row;
    }
}
