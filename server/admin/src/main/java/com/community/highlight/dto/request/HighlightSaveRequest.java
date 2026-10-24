package com.community.highlight.dto.request;

import java.time.Instant;

/**
 * {@code POST /api/admin/highlights}(만들기) · {@code PUT /api/admin/highlights/{id}}(고치기 — 모든 칸을 보낸다).
 *
 * @param postId      대상 글 ID — 만들 때만 받고 고칠 때는 무시한다(대상 글은 바꾸지 않는다)
 * @param title       노출 제목(40자까지, 비우면 null — 화면은 글 제목을 쓴다)
 * @param summary     요약 문구(100자까지, 비우면 null — 화면은 요약을 그리지 않는다)
 * @param imageFileId 대표 이미지 — {@code POST /api/admin/files}(purpose HIGHLIGHT_IMAGE)로 올린 파일 ID. null 이면 대표 이미지 없음(고칠 때 null 이면 지운다), 지금 이미지와 같으면 그대로
 * @param visible     노출 켬 — 만들 때 비우면 true, 고칠 때 비우면 그대로
 * @param showFrom    노출 시작(포함, 비우면 바로)
 * @param showUntil   노출 끝(미포함, 비우면 상시) — {@code showFrom} 보다 늦어야 한다
 * @param debate      「찬성·반대 의견 받기」 — 만들 때 비우면 false, 고칠 때 비우면 그대로
 */
public record HighlightSaveRequest(String postId, String title, String summary, String imageFileId, Boolean visible, Instant showFrom, Instant showUntil,
                                   Boolean debate) {
}
