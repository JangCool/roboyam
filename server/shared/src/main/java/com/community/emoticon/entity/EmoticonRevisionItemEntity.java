package com.community.emoticon.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * 판의 차례 한 줄 — shared V142 {@code emoticon_revision_items}(판 + 차례가 키, 로보티콘·그림은 판 안에서 하나씩). 낸 판의 차례는 트리거가 넣기·고치기·지우기를 막는다.
 * 임시 판의 차례는 통째로 지우고 다시 넣는다 — 이 엔티티는 넣기({@code persist})와 질의 경로(Q 클래스)에 쓴다.
 */
@Entity
@Table(name = "emoticon_revision_items")
@IdClass(EmoticonRevisionItemId.class)
public class EmoticonRevisionItemEntity {

    @Id
    private Long revisionId;
    @Id
    private Integer position;
    private Long packId;
    private Long emoticonId;
    private Long imageId;
    private String label;

    protected EmoticonRevisionItemEntity() {
    }

    public static EmoticonRevisionItemEntity create(long revisionId, int position, long packId, long emoticonId, long imageId, String label) {
        EmoticonRevisionItemEntity i = new EmoticonRevisionItemEntity();
        i.revisionId = revisionId;
        i.position = position;
        i.packId = packId;
        i.emoticonId = emoticonId;
        i.imageId = imageId;
        i.label = label;
        return i;
    }
}
