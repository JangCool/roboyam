package com.community.emoticon.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 로보티콘 팩의 판 — shared V142 {@code emoticon_pack_revisions}. {@code version} 이 없으면 아직 내지 않은 임시 판(팩마다 하나 — 부분 유일 색인), 낸 판은 트리거가 고치지 못하게 막는다.
 * 태그는 {@code text[]}. 읽기는 저장소의 한 줄 모양(프로젝션), 임시 판 고치기·내기는 저장소의 조건부 UPDATE 다 — 이 엔티티는 넣기({@code persist})와 질의 경로(Q 클래스)에 쓴다.
 */
@Entity
@Table(name = "emoticon_pack_revisions")
public class EmoticonPackRevisionEntity {

    @Id
    private Long id;
    private Long packId;
    private Integer version;
    private String name;
    private String description;
    @JdbcTypeCode(SqlTypes.ARRAY)
    private String[] tags;
    private Long coverImageId;
    @Enumerated(EnumType.STRING)
    private EmoticonSaleWish saleWish;
    private String priceRequestMemo;
    private Instant createdAt;
    private Instant submittedAt;

    protected EmoticonPackRevisionEntity() {
    }

    /** 새 임시 판(아직 내지 않음) */
    public static EmoticonPackRevisionEntity draft(long id, long packId, String name, String description, List<String> tags, Long coverImageId, EmoticonSaleWish saleWish,
                                                   String priceRequestMemo, Instant now) {
        EmoticonPackRevisionEntity r = new EmoticonPackRevisionEntity();
        r.id = id;
        r.packId = packId;
        r.name = name;
        r.description = description;
        r.tags = tags.toArray(String[]::new);
        r.coverImageId = coverImageId;
        r.saleWish = saleWish;
        r.priceRequestMemo = priceRequestMemo;
        r.createdAt = now;
        return r;
    }
}
