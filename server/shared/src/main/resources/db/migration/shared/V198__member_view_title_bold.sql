-- 회원 보기 설정 — 글 목록 제목 굵게(사용자 2026-11-12). docs/features/mobile-nav-prefix-style-contract.md 「7. 보기 설정 — 제목 굵게」
--
-- title_bold : 글 목록(게시판·인기글·최신글 — 모바일·데스크톱)의 제목을 굵게 볼지. 기본 false(지금 모양 — 보통 굵기).
--              행이 없는 회원의 기본값은 서버 코드(MemberViewSettingEntity.DEFAULT_TITLE_BOLD = false)가 정한다.
ALTER TABLE member_view_settings
    ADD COLUMN title_bold boolean NOT NULL DEFAULT false;
