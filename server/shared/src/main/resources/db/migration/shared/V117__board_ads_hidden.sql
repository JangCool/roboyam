-- 게시판 「이 게시판에 광고 숨김」(ads_hidden) — 켜면 그 게시판의 목록 한 줄·글 보기·핫딜 노출 광고가 없다. docs/features/ad-contract.md 「공개 노출 목업」 확정 결정(사용자 2026-11-01).
-- 기본 끔. 19금(allow_adult)·소모임(kind = 'GROUP') 게시판은 이 칸과 상관없이 늘 제외한다(서버 GET /api/ads?board= 판정). 게시판마다의 값이다(기본 설정에서 물려받지 않는다).
ALTER TABLE boards ADD COLUMN ads_hidden boolean NOT NULL DEFAULT false;
