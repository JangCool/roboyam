package com.community.jjal.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 짤을 쓰는 글·댓글 — V261 {@code jjal_uses}. 글 본문이면 {@code commentId} null. 글·댓글을 저장할 때 본문의 짤 토큰으로 다시 쓴다({@code JjalGuard#recordUses}).
 * 파일 정리(쓰는 곳이 없을 때)와 담기 검사(그 글·댓글에 쓰인 짤인가)가 본다. 파티션 표(posts·comments)를 가리키는 외래 키는 없다.
 */
@Entity
@Table(name = "jjal_uses")
public class JjalUseEntity {

    @Id
    private Long id;
    private Long fileId;
    private Long postId;
    private Long commentId;

    protected JjalUseEntity() {
    }

    public static JjalUseEntity create(long id, long fileId, long postId, Long commentId) {
        JjalUseEntity u = new JjalUseEntity();
        u.id = id;
        u.fileId = fileId;
        u.postId = postId;
        u.commentId = commentId;
        return u;
    }

    public Long getId() {
        return id;
    }

    public Long getFileId() {
        return fileId;
    }
}
