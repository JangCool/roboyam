package com.community.emoticon.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 로보티콘 작가 정산 정보 — shared V143 {@code emoticon_payout_accounts}(작가마다 한 줄). 원문 칸({@code *Enc})은 {@code DataCipher} 암호문이고 화면용 가린 값은 따로 둔다.
 * 넣기·바꾸기는 저장소의 {@code ON CONFLICT} upsert 다(처음 등록 시각을 남긴다) — 이 엔티티는 질의 경로(Q 클래스)에 쓴다. {@code toString} 을 두지 않는다.
 */
@Entity
@Table(name = "emoticon_payout_accounts")
public class EmoticonPayoutAccountEntity {

    @Id
    private Long userId;
    private String payeeType;
    private String businessNumberEnc;
    private String businessNumberMasked;
    private String bankCode;
    private String accountNumberEnc;
    private String accountLast4;
    private String holderNameEnc;
    private String holderMasked;
    private Instant createdAt;
    private Instant updatedAt;

    protected EmoticonPayoutAccountEntity() {
    }
}
