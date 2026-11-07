package com.community.emoticon.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 로보티콘 보유 — shared V142 {@code emoticon_acquisitions}(+ V144 출처 {@code kind}·구매). (회원, 팩) 하나에 줄 하나, 빼거나 환불하면 {@code removedAt} 만 찍는다.
 * 출처 {@code kind} 는 {@code FREE} · {@code PURCHASE}(구매 {@code purchaseId}) — 표의 CHECK 가 짝을 본다.
 * 읽기는 저장소의 한 줄 모양(프로젝션), 빼기·다시 가지기는 저장소의 조건부 UPDATE 다 — 이 엔티티는 넣기({@code persist})와 질의 경로(Q 클래스)에 쓴다.
 */
@Entity
@Table(name = "emoticon_acquisitions")
public class EmoticonAcquisitionEntity {

    @Id
    private Long id;
    private Long userId;
    private Long packId;
    private String kind;
    private Long purchaseId;
    private Instant firstAcquiredAt;
    private Instant acquiredAt;
    private Instant removedAt;

    protected EmoticonAcquisitionEntity() {
    }

    /** 새 보유 줄 — 무료 받기({@code purchaseId} 없음) 또는 유료 구매 */
    public static EmoticonAcquisitionEntity create(long id, long userId, long packId, String kind, Long purchaseId, Instant now) {
        EmoticonAcquisitionEntity a = new EmoticonAcquisitionEntity();
        a.id = id;
        a.userId = userId;
        a.packId = packId;
        a.kind = kind;
        a.purchaseId = purchaseId;
        a.firstAcquiredAt = now;
        a.acquiredAt = now;
        return a;
    }
}
