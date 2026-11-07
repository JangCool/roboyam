package com.community.emoticon.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 로보티콘 하나 — shared V142 {@code emoticons}. 토큰 {@code :emo-ID:} 의 ID 이고, 판이 바뀌어도 같은 로보티콘은 같은 ID 다(판의 차례·그림은 {@link EmoticonRevisionItemEntity}).
 * 넣기({@code persist})와 질의 경로(Q 클래스)에 쓴다. 고치지 않는다.
 */
@Entity
@Table(name = "emoticons")
public class EmoticonEntity {

    @Id
    private Long id;
    private Long packId;
    private Instant createdAt;

    protected EmoticonEntity() {
    }

    public static EmoticonEntity create(long id, long packId, Instant now) {
        EmoticonEntity e = new EmoticonEntity();
        e.id = id;
        e.packId = packId;
        e.createdAt = now;
        return e;
    }
}
