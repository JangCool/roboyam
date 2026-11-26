package com.community.counsel.dto.response;

import com.community.comment.dto.response.CommentView;
import com.community.post.dto.response.PostAuthorView;

/**
 * AI 상담사 한 명과 그 답(user-web {@code CounselAiAnswer}).
 *
 * @param counselorId 상담사(AI 회원 확장 줄 {@code ai_member_profiles.id})
 * @param name        상담사 이름(회원 닉네임 — 「팩폭언니」 등)
 * @param style       상담 성향 한 마디(「직설형」 등 — 없으면 빈 글자)
 * @param ai          배지의 제공사·모델 — 답이 있으면 그 답을 만든 모델, 아직이면 계정의 지금 모델
 * @param comment     그 상담사의 답(댓글 모양 그대로) — 아직 쓰는 중이면 null
 */
public record CounselAiAnswerView(String counselorId, String name, String style, PostAuthorView.AuthorAiView ai, CommentView comment) {
}
