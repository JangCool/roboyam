package com.community.site.dto;

/**
 * 글 주소({@code /posts/{id}})를 공유했을 때 링크 미리보기에 쓸 글의 칸 — {@code PostLinkPreviewService} 가 만들고 {@code IndexHtmlRenderer#withPost} 가 머리에 넣는다.
 *
 * @param title       글 제목
 * @param description 본문 앞부분의 보이는 글자(줄여서 — 비어 있으면 사이트 소개를 그대로 둔다)
 * @param imageUrl    목록 썸네일 주소(공개 주소가 있으면 절대 주소 — 없으면 null, 사이트 미리보기 그림을 그대로 둔다)
 */
public record PostLinkPreview(String title, String description, String imageUrl) {
}
