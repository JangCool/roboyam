package com.community.emoticon.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 로보티콘 상품화 계약 동의 — shared V143 {@code emoticon_contract_consents}(회원 × 판 유일, 추가만 — 트리거가 고치기·지우기를 막는다). 넣기는 저장소의
 * {@code ON CONFLICT DO NOTHING}(같은 판에 이미 동의했으면 처음 시각을 남긴다) — 이 엔티티는 질의 경로(Q 클래스)에 쓴다.
 */
@Entity
@Table(name = "emoticon_contract_consents")
public class EmoticonContractConsentEntity {

    @Id
    private Long id;
    private Long userId;
    private int version;
    private Instant agreedAt;
    private Long packId;

    protected EmoticonContractConsentEntity() {
    }
}
