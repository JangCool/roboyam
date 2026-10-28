package com.community.home.dto.response.gadget;

import java.time.Instant;

/**
 * {@code GALLERY} 가젯의 한 칸 — user-web {@code GalleryItem}. 사진이 있는 보이는 글만.
 *
 * @param kind 움직이는 그림·영상 표시({@code GIF}·{@code VIDEO}) — 지금 서버는 가르지 않아 늘 null
 */
public record GalleryItemView(String postId, String title, String thumbnailUrl, String kind, BoardRef board, int likeCount, Instant createdAt) {

    public record BoardRef(String slug, String name, String nameEn) {
    }
}
