-- 설치 때 고른 「데모 데이터 포함 / 구조만」과 데모를 넣은 결과를 설치 표에 남긴다 (docs/features/install-contract.md 「데모 데이터 선택 (2026-11-08)」).
--
-- demo_data    설치 마법사가 데모 데이터 포함을 골랐는가(구조만 = false). 이 칸이 생기기 전에 설치된 곳은 false.
-- demo_status  데모를 넣은 결과 — APPLIED(넣음) · FAILED(넣지 못해 건너뜀 — 설치는 끝났고 구조만 있다). 고르지 않았으면 NULL.
-- demo_done_at 그 결과를 적은 시각.
-- 데모는 설치가 커밋된 뒤 따로 넣으므로(실패해도 설치를 깨지 않는다) 결과 칸이 따로 있다. 순수 expand — 칸 추가뿐이라 이전 버전 앱은 이 칸을 모르고 그대로 돈다.
ALTER TABLE installation ADD COLUMN demo_data boolean NOT NULL DEFAULT false;
ALTER TABLE installation ADD COLUMN demo_status varchar(16);
ALTER TABLE installation ADD COLUMN demo_done_at timestamptz;
ALTER TABLE installation ADD CONSTRAINT ck_installation_demo_status CHECK (
    demo_status IS NULL OR demo_status IN ('APPLIED', 'FAILED')
);
