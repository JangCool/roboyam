-- 게시판 개인정보 보유기간 적용(board-privacy-retention 배치의 전체 스위치)을 기본으로 켠다 — 사용자 지시 2026-11-07.
-- 개인정보처리방침(기본 문안)의 보관 기간(작성 IP 365일 · 비회원 정보 · 삭제된 원문 30일 · 신고·처리 기록)과 실제 처리를 맞추려는 것이다.
-- 새 설치: 기본값 true. 이미 있는 행: 운영자가 한 번도 저장하지 않은 행(updated_at IS NULL)만 켠다 — 일부러 끈 설정은 그대로 둔다.
ALTER TABLE board_privacy_settings ALTER COLUMN retention_enabled SET DEFAULT true;
UPDATE board_privacy_settings SET retention_enabled = true WHERE id = 1 AND updated_at IS NULL;
