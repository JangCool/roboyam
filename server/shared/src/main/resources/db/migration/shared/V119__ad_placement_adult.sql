-- 광고 노출 자리 설정 넷째 묶음 「19금 게시판에도 광고 보이기」(adult.enabled) — 사용자 결정 2026-11-01(2차). 기본 끔.
-- 끄면 19금(allow_adult) 게시판의 게시판 맥락 광고(GET /api/ads?board=, 날개 포함)는 빈 목록, 켜면 낸다. 숨김(ads_hidden)·소모임 게시판은 이 칸과 상관없이 늘 제외.
-- 이미 저장한 행은 끔으로 채운다(저장 전과 같은 동작).
ALTER TABLE ad_placement_settings ADD COLUMN adult_enabled boolean NOT NULL DEFAULT false;
