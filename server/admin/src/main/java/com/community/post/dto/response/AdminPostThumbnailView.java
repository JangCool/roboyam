package com.community.post.dto.response;

import java.util.List;

/**
 * 글 상세의 대표 썸네일(admin-web {@code AdminPostThumbnailView}, list-thumbnail-contract 「2단계」). 비공개 소모임 글·원문 파기 글은 상세에서 null.
 *
 * @param mode     AUTO · IMAGE · UPLOAD · NONE — 고른 이미지를 본문에서 뺐으면 AUTO 로 보인다
 * @param setBy    AUTHOR · OPERATOR, 자동이면 null
 * @param imageId  IMAGE 일 때 고른 본문 이미지 ID
 * @param fileId   UPLOAD 일 때 올린 파일 ID
 * @param url      지금 목록·가젯에 보이는 썸네일 주소(없으면 null)
 * @param autoUrl  자동이면 쓰일 그림(첫 본문 이미지 → 첫 YouTube 영상), 없으면 null
 * @param autoFrom IMAGE · VIDEO · null
 * @param images   글에 붙은 본문 이미지(본문 차례 — 주소는 목록 썸네일, 없으면 원본)
 */
public record AdminPostThumbnailView(String mode, String setBy, String imageId, String fileId, String url, String autoUrl, String autoFrom, List<Image> images) {

    /** 고르기 격자의 이미지 하나 */
    public record Image(String imageId, String url) {
    }
}
