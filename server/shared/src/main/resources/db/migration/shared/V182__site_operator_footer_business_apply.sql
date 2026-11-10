-- 푸터 「고객 지원」 끝의 「사업자 신청」 링크 보이기(사용자 2026-11-10 — 계정 메뉴에서 푸터로 옮기고 관리자가 켜고 끈다, terms-operator-contract 「푸터」).
-- 운영 주체 행(site_operator, V158)에 칸 하나. 기본 켬 — 새 설치·기존 사이트 모두(ADD COLUMN ... DEFAULT 가 기존 행도 채운다).
ALTER TABLE site_operator
    ADD COLUMN footer_business_apply boolean NOT NULL DEFAULT true;
