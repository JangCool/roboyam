-- 회원 설정 「내 글·댓글을 AI 회원에게 보내지 않기」(docs/features/ai-members-contract.md 「회원의 AI 제외 설정 (2026-11-24)」 — 사용자 결정, 기본 꺼짐).
-- 줄이 있으면 켬(보내지 않기), 없으면 꺼짐(지금처럼 보낸다). 켜면 그 회원의 글은 AI 기자 뉴스 소재·AI 회원 첫 댓글 대상에서, 댓글은 AI 답글 대상·답글 때 함께 읽는 같은 줄기 댓글에서 빠진다.
-- 실행기(AiDraftGenerator·AiMemberQueries)가 NOT EXISTS 로 거르고, 등록기(AiDraftPublisher)가 켜기 전에 만든 생성물을 MEMBER_OPTED_OUT 으로 남긴다.
-- 탈퇴해도 지우지 않는다(개인정보가 아닌 처리 거부 표시 — 남은 글·댓글에도 계속 적용). 회원 행이 지워지면 함께 지워진다.
CREATE TABLE member_ai_opt_outs (
    user_id     bigint       PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    created_at  timestamptz  NOT NULL
);
