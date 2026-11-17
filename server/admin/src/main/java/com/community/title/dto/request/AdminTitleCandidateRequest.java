package com.community.title.dto.request;

/** 이달의 작가 후보 넣기·빼기({@code POST·DELETE /api/admin/titles/monthly-candidates}) — {@code postId} 글 ID(글자), {@code month} {@code YYYY-MM}(없으면 이번 달, 한국 시간) */
public record AdminTitleCandidateRequest(String postId, String month) {
}
