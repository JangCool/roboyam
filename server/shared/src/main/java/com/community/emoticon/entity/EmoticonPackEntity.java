package com.community.emoticon.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 로보티콘 팩 — shared V142 {@code emoticon_packs}(+ V147 판매 중단 종류). 상점에 걸린 판({@code liveRevisionId})은 판 표를 가리키는 지연 외래 키
 * ({@code fk_emoticon_packs_live} — 커밋 때 본다)라 같은 트랜잭션에서 팩을 먼저 넣고 판을 뒤에 넣어도 된다. 유료 가격은 트리거가 설정의 열린 단계인지 보고,
 * 작가 탈퇴로 멈춘 팩은 트리거가 되살리지 못하게 막는다.
 * 읽기는 저장소의 한 줄 모양(프로젝션), 상태 전이·카운터는 저장소의 조건부 UPDATE 다 — 이 엔티티는 넣기({@code persist})·질의 경로(Q 클래스)·행 잠금에 쓴다.
 */
@Entity
@Table(name = "emoticon_packs")
public class EmoticonPackEntity {

    @Id
    private Long id;
    @Enumerated(EnumType.STRING)
    private EmoticonPackSource source;
    private Long creatorId;
    @Enumerated(EnumType.STRING)
    private EmoticonPackStatus status;
    private int price;
    private Long liveRevisionId;
    private int acquireCount;
    private Instant publishedAt;
    private String suspendReason;
    private Instant suspendedAt;
    private Long suspendedBy;
    private Instant createdAt;
    private Instant updatedAt;
    @Enumerated(EnumType.STRING)
    private EmoticonSuspendKind suspendKind;

    protected EmoticonPackEntity() {
    }

    /** 새 팩 — 무료(0), 받은 수 0, 판매 중단 기록 없음 */
    public static EmoticonPackEntity create(long id, EmoticonPackSource source, Long creatorId, EmoticonPackStatus status, Long liveRevisionId, Instant publishedAt,
                                            Instant now) {
        EmoticonPackEntity p = new EmoticonPackEntity();
        p.id = id;
        p.source = source;
        p.creatorId = creatorId;
        p.status = status;
        p.price = 0;
        p.liveRevisionId = liveRevisionId;
        p.acquireCount = 0;
        p.publishedAt = publishedAt;
        p.createdAt = now;
        p.updatedAt = now;
        return p;
    }
}
