package com.community.avatarupload.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 아바타 올리기 신청 한 줄 — V157 {@code avatar_upload_requests}. 닉네임 이미지 신청(V72)을 본뜬 상태 모델: 대기({@link AvatarUploadStatus#PENDING})로 생기고
 * 승인·반려·해제·취소·지움으로 끝난다. 대기·승인은 회원당 하나(부분 유일 색인). 상태 전이는 {@code AvatarUploadStore} 의 조건부 UPDATE(바꾼 줄 수로 판정)로만 한다.
 * 파일은 저장소({@code ImageStorage}) 키로 가리키고 지운 신청은 키가 없다. {@code toString} 을 두지 않는다.
 */
@Entity
@Table(name = "avatar_upload_requests")
public class AvatarUploadRequestEntity {

    @Id
    private Long id;

    private Long userId;

    @Enumerated(EnumType.STRING)
    private AvatarUploadStatus status;

    private String imageKey;
    /** 움직이는 GIF 의 첫 장면(PNG) — 정지 그림이면 null */
    private String stillKey;
    private String contentType;
    /** 올린 파일 크기(300KB 한도가 재는 것) */
    private int bytes;
    private int width;
    private int height;
    /** GIF 장면 수(그 밖 1) */
    private int frames;
    private String rejectReason;
    private String revokeReason;
    private Long reviewedBy;
    private Instant reviewedAt;
    private Long revokedBy;
    private Instant revokedAt;
    private Instant endedAt;
    private Instant createdAt;

    protected AvatarUploadRequestEntity() {
    }

    /** 새 대기 신청 */
    public static AvatarUploadRequestEntity pending(long id, long userId, String imageKey, String stillKey, String contentType, int bytes, int width, int height,
                                                    int frames, Instant now) {
        AvatarUploadRequestEntity row = new AvatarUploadRequestEntity();
        row.id = id;
        row.userId = userId;
        row.status = AvatarUploadStatus.PENDING;
        row.imageKey = imageKey;
        row.stillKey = stillKey;
        row.contentType = contentType;
        row.bytes = bytes;
        row.width = width;
        row.height = height;
        row.frames = frames;
        row.createdAt = now;
        return row;
    }
}
