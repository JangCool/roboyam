-- 푸터 안내 문구(docs/features/terms-operator-contract.md 「푸터 안내 문구 (2026-11-10)」) — 운영 주체 행(site_operator, V158)에 칸 둘.
-- footer_notice: 공개 푸터의 안내 줄(줄바꿈 \n 으로 이은 글, 최대 3줄 · 줄마다 120자 — 서비스 SiteOperatorService 가 본다). NULL = 안내 숨김.
--   {사이트} 는 화면이 사이트 이름으로 바꾸고 바로 뒤 조사(은/는·이/가·을/를)를 이름에 맞춘다(그래서 기본 문구는 「{사이트}은」 꼴로 둔다).
-- footer_notice_scope: INDIVIDUAL_ONLY(개인 단계에서만 보이기, 기본) · ALWAYS(늘 보이기 — 사업자 정보 표가 있어도).
-- 기본값은 새 설치·기존 사이트 모두 같다(ADD COLUMN ... DEFAULT 가 기존 행도 채운다). 관리자가 비우면 NULL 로 저장된다.
ALTER TABLE site_operator
    ADD COLUMN footer_notice       text        DEFAULT E'{사이트}은 개인이 운영하는 커뮤니티입니다.\n사이트 운영을 위해 광고가 표시될 수 있습니다.',
    ADD COLUMN footer_notice_scope varchar(20) NOT NULL DEFAULT 'INDIVIDUAL_ONLY',
    ADD CONSTRAINT ck_site_operator_footer_notice_scope CHECK (footer_notice_scope IN ('INDIVIDUAL_ONLY', 'ALWAYS'));
