package com.community.groupbuy.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicInsert;

/**
 * 요청 사진(먼저 올리고 나중에 붙인다) — shared V102.
 * 읽기는 저장소의 한 줄 모양(프로젝션), 상태 전이·수량은 저장소의 조건부 UPDATE 다 — 이 엔티티는 새 줄 넣기({@code persist})와 질의 경로(Q 클래스)에 쓴다.
 * {@code @DynamicInsert}: 값을 주지 않은 칸은 INSERT 에서 빠져 DB 기본값을 받는다(예전 INSERT 문장의 칸 목록과 같다).
 */
@Entity
@DynamicInsert
@Table(name = "group_buy_request_photos")
public class GroupBuyRequestPhotoEntity {

    @Id
    private Long id;
    private Long requestId;
    private String ownerKind;
    private Long ownerId;
    private String imageKey;
    private Instant createdAt;
    private Instant attachedAt;

    protected GroupBuyRequestPhotoEntity() {
    }

    /** 아직 요청에 붙지 않은 사진(올린 사람 MEMBER · OPERATOR) */
    public static GroupBuyRequestPhotoEntity loose(long id, String ownerKind, long ownerId, String imageKey, Instant now) {
        GroupBuyRequestPhotoEntity p = new GroupBuyRequestPhotoEntity();
        p.id = id;
        p.ownerKind = ownerKind;
        p.ownerId = ownerId;
        p.imageKey = imageKey;
        p.createdAt = now;
        return p;
    }
}
