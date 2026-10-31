package com.community.bizapplication.dto.request;

/**
 * 소재 바꾸기({@code PUT /api/business/applications/{id}/creative}) — 종류에 맞는 칸만 본다: 배너 {@code imageFileId}·{@code linkUrl}·{@code altText} ·
 * 한 줄 {@code text}·{@code linkUrl} · 핫딜 글 {@code title}·{@code deal}(노출 자리·기간은 바꿀 수 없다 — 다른 값이면 400 {@code fields.deal.placement}).
 */
public record BizCreativeRequest(String imageFileId, String linkUrl, String altText, String text, String title, BizDealInfo deal) {
}
