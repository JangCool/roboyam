-- 올린 로고 묶음의 유형 (docs/features/site-logo-library-contract.md 「올린 묶음의 유형」)
--
-- 운영자가 올린 묶음도 유형(CHARACTER = 캐릭터 · WORDMARK = 글자)을 고른다 — 기념일 로고 유형이 AUTO 일 때 기본 로고·성별 로고의 유형에 맞는 기념일 벌을 고르는 데 쓴다.
-- NULL = 고른 적 없음 = 캐릭터로 본다(이미 올린 묶음의 기존 동작 그대로).
-- 기본 제공 묶음은 built_in_key 가 유형이다 — 이 칸은 채우지 않고 NULL 로 둔다(원천을 하나로: 유효 유형 = built_in_key, 없으면 style, 없으면 CHARACTER — SiteLogoBundleEntity.effectiveStyle).
ALTER TABLE site_logo_bundles ADD COLUMN style varchar(16);
ALTER TABLE site_logo_bundles ADD CONSTRAINT ck_site_logo_bundles_style CHECK (style IS NULL OR style IN ('CHARACTER', 'WORDMARK'));
