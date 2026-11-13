package com.community.post.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 글의 대표 썸네일 선택(V206 {@code post_thumbnails} — docs/features/list-thumbnail-contract.md 「2단계」). <b>행이 없으면 자동</b>(본문 첫 이미지, 없으면 첫 YouTube 영상 —
 * 대부분의 글). 글 표는 파티션 표라 칸을 더하지 않고 글 ID 를 키로 하는 별도 표에 둔다(외래 키 없음).
 *
 * <ul>
 *   <li>{@code mode}: {@link #IMAGE}(그 글 본문 이미지 하나 — 파일 용도 POST_IMAGE) · {@link #UPLOAD}(따로 올린 그림 — POST_THUMBNAIL) · {@link #NONE}(목록에 그림 없음).</li>
 *   <li>{@code setBy}: {@link #AUTHOR}(글쓴이) · {@link #OPERATOR}(운영자 — 운영자가 정한 동안 글쓴이는 바꿀 수 없다, 409 THUMBNAIL_LOCKED).
 *       글 하나에 한 행이라 운영자가 정하면 글쓴이 것을 덮어쓰고, 「자동으로 되돌리기」는 행을 지워 둘 다 없앤다(사용자 결정 A안 2026-11-13).</li>
 *   <li>{@code fileId}: IMAGE·UPLOAD 의 파일. 그 파일이 그 글에 쓰이는 중(IN_USE + 같은 글)이 아니면 목록은 자동으로 본다(본문에서 뺀 이미지 등).</li>
 * </ul>
 */
@Entity
@Table(name = "post_thumbnails")
public class PostThumbnailEntity {

    public static final String IMAGE = "IMAGE";
    public static final String UPLOAD = "UPLOAD";
    public static final String NONE = "NONE";
    /** 행 없음(요청·응답에서만 쓰는 값) */
    public static final String AUTO = "AUTO";
    public static final String AUTHOR = "AUTHOR";
    public static final String OPERATOR = "OPERATOR";

    @Id
    private Long postId;

    private String mode;

    private Long fileId;

    private String setBy;

    /** 정한 사람 — AUTHOR 면 회원 ID, OPERATOR 면 운영자 ID(외래 키 없음) */
    private Long setById;

    private Instant updatedAt;

    protected PostThumbnailEntity() {
    }

    public static PostThumbnailEntity create(long postId, String mode, Long fileId, String setBy, Long setById, Instant now) {
        PostThumbnailEntity e = new PostThumbnailEntity();
        e.postId = postId;
        e.change(mode, fileId, setBy, setById, now);
        return e;
    }

    /** 고른 것을 바꾼다(NONE 이면 파일 없음) */
    public void change(String mode, Long fileId, String setBy, Long setById, Instant now) {
        this.mode = mode;
        this.fileId = NONE.equals(mode) ? null : fileId;
        this.setBy = setBy;
        this.setById = setById;
        this.updatedAt = now;
    }

    public boolean byOperator() {
        return OPERATOR.equals(setBy);
    }

    public Long getPostId() {
        return postId;
    }

    public String getMode() {
        return mode;
    }

    public Long getFileId() {
        return fileId;
    }

    public String getSetBy() {
        return setBy;
    }

    public Long getSetById() {
        return setById;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
