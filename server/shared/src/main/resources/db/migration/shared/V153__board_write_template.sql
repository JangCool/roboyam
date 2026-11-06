-- 게시판 글 양식(사용자 결정 2026-11-06) — 새 글을 쓸 때 본문 입력칸에 미리 채워 두는 평문 양식(예: 회원장터 「품명·가격·상태·거래 방식」, 사용기 「제품명·구매처」).
-- 비면 양식 없음. 줄바꿈은 그대로 두고, 에디터 게시판은 화면이 줄마다 문단으로 바꿔 넣는다. 보이는 글자 2,000자까지(안내문 500자보다 길다 — 항목이 여럿이라).
ALTER TABLE boards ADD COLUMN write_template text;
ALTER TABLE boards ADD CONSTRAINT ck_boards_write_template CHECK (write_template IS NULL OR char_length(write_template) <= 2000);
