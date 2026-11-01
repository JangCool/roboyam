package com.community.hotdeal.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * OG 이미지 사본(D1) — V122 {@code deal_og_images}. link-check 가 가져와 우리 저장소에 둔 사본 한 장. {@code originUrl} 은 원본 이미지 주소(저작권 요청 때 지운다 —
 * 공개 응답에 싣지 않는다). 같은 사본을 여러 글이 쓸 수 있다({@code post_deals.og_image_id}) — {@code postId} 는 처음 붙은 글. 정리(딜 종료·글 삭제 90일 뒤, 붙지 않은 사본)는
 * 2단계 배치가 하고, 지울 때는 그 사본을 가리키는 딜이 없는지 본다.
 */
@Entity
@Table(name = "deal_og_images")
public class DealOgImageEntity {

    @Id
    private Long id;

    private String originUrl;
    private String originHost;
    private String storageKey;
    private String publicUrl;
    private String contentType;
    private int sizeBytes;
    private int width;
    private int height;
    private Long createdBy;
    private Long postId;
    private Instant createdAt;
    private Instant attachedAt;
    private Instant removedAt;

    protected DealOgImageEntity() {
    }

    public static DealOgImageEntity create(long id, String originUrl, String originHost, String storageKey, String publicUrl, String contentType, int sizeBytes,
                                           int width, int height, Long createdBy, Instant now) {
        DealOgImageEntity e = new DealOgImageEntity();
        e.id = id;
        e.originUrl = originUrl != null && originUrl.length() > 2000 ? originUrl.substring(0, 2000) : originUrl;
        e.originHost = originHost;
        e.storageKey = storageKey;
        e.publicUrl = publicUrl;
        e.contentType = contentType;
        e.sizeBytes = sizeBytes;
        e.width = width;
        e.height = height;
        e.createdBy = createdBy;
        e.createdAt = now;
        return e;
    }

    /** 글에 붙인다 — 처음 붙은 글만 남긴다 */
    public void attach(long postId, Instant now) {
        if (this.postId == null) {
            this.postId = postId;
            this.attachedAt = now;
        }
    }

    /** 저작권 요청 — 원본 주소를 지운다 */
    public void forgetOrigin() {
        this.originUrl = null;
    }

    /** 파일을 지웠다(정리 배치) */
    public void markRemoved(Instant now) {
        this.removedAt = now;
    }

    public boolean isRemoved() {
        return removedAt != null;
    }

    public Long getId() {
        return id;
    }

    public String getOriginUrl() {
        return originUrl;
    }

    public String getOriginHost() {
        return originHost;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getPublicUrl() {
        return publicUrl;
    }

    public String getContentType() {
        return contentType;
    }

    public int getSizeBytes() {
        return sizeBytes;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public Long getPostId() {
        return postId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getAttachedAt() {
        return attachedAt;
    }

    public Instant getRemovedAt() {
        return removedAt;
    }
}
