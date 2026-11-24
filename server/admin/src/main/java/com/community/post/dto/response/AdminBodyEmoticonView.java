package com.community.post.dto.response;

/**
 * 본문 로보티콘 토큰 {@code :emo-ID:} 하나의 그림 — 관리자 글·신고 서랍(admin-web {@code AdminPostEmoticonView}). 공개 카탈로그 줄({@code EmoticonPublicView.CatalogItem})의
 * {@code id}·{@code label}·{@code url} 과 같은 값이다(판매 중단 팩 포함).
 *
 * @param id    로보티콘 ID(토큰의 숫자)
 * @param label 이름표 — 그림의 {@code alt}·{@code title}
 * @param url   그림 주소
 */
public record AdminBodyEmoticonView(String id, String label, String url) {
}
