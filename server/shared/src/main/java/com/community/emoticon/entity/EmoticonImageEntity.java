package com.community.emoticon.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 올린 로보티콘 그림 한 장의 장부 — shared V142 {@code emoticon_images}. 파일 바이트는 저장소({@code storageKey}) 또는 앱 정적 파일({@code staticPath} — 시드)에 있다.
 * 고정 규격(크기·프레임·용량)은 표의 CHECK 가 한 번 더 막는다. 넣기({@code persist})와 질의 경로(Q 클래스)에 쓰고, 읽기는 저장소의 한 줄 모양(프로젝션)이다. 고치지 않는다.
 */
@Entity
@Table(name = "emoticon_images")
public class EmoticonImageEntity {

    @Id
    private Long id;
    @Enumerated(EnumType.STRING)
    private EmoticonImageKind kind;
    @Enumerated(EnumType.STRING)
    private EmoticonImageFormat format;
    private int width;
    private int height;
    private int bytes;
    private int frames;
    private boolean loopForever;
    private String contentHash;
    private String storageKey;
    private String staticPath;
    private Long uploadedByUser;
    private Long uploadedByOperator;
    private Instant createdAt;

    protected EmoticonImageEntity() {
    }

    public static EmoticonImageEntity create(long id, EmoticonImageKind kind, EmoticonImageFormat format, int width, int height, int bytes, int frames, boolean loopForever,
                                             String contentHash, String storageKey, String staticPath, Long uploadedByUser, Long uploadedByOperator, Instant createdAt) {
        EmoticonImageEntity e = new EmoticonImageEntity();
        e.id = id;
        e.kind = kind;
        e.format = format;
        e.width = width;
        e.height = height;
        e.bytes = bytes;
        e.frames = frames;
        e.loopForever = loopForever;
        e.contentHash = contentHash;
        e.storageKey = storageKey;
        e.staticPath = staticPath;
        e.uploadedByUser = uploadedByUser;
        e.uploadedByOperator = uploadedByOperator;
        e.createdAt = createdAt;
        return e;
    }
}
