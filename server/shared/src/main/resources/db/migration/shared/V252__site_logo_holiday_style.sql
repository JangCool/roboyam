-- 기념일 로고 유형 (docs/features/site-logo-library-contract.md 「기념일 로고 유형」)
--
-- 기념일 로고는 날마다 두 벌(캐릭터·글자, V251)이다. 운영자가 어느 벌을 보일지 한 번에 고른다.
-- AUTO(기본) = 그 회원에게 평소 보일 로고(성별 로고가 있으면 그것, 없으면 기본 로고)의 유형에 맞춤 · CHARACTER = 모두에게 캐릭터 벌 · WORDMARK = 모두에게 글자 벌.
-- 규칙이 기본 제공 기념일 묶음을 가리킬 때만 뜻이 있다 — 운영자가 올린 묶음을 가리키는 규칙은 그대로다(SiteLogoResolver.matchStyle).
ALTER TABLE site_settings ADD COLUMN holiday_logo_style varchar(16) NOT NULL DEFAULT 'AUTO';
ALTER TABLE site_settings ADD CONSTRAINT ck_site_settings_holiday_logo_style CHECK (holiday_logo_style IN ('AUTO', 'CHARACTER', 'WORDMARK'));
