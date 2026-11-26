package com.community.post.dto.response;

/**
 * 글이 가리키는 말머리(user-web {@code PostPrefixRef}).
 *
 * @param counsel AI 연애상담소 말머리(V274 — ai-love-counsel-contract) — 글 보기가 AI 상담 칸을 그린다
 */
public record PostPrefixRefView(String id, String name, boolean staffOnly, String color, boolean counsel) {
}
