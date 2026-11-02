-- 광고주 관리(docs/features/ad-advertiser-contract.md — admin-web /ad-advertisers) — V115 광고주 표에
--   · 보관(직접 등록 광고주만 — 보관 시각·보관한 운영자)과 연락처 파기 표시(R__batch_35 가 담당자·연락처·이메일 암호문·가린 값을 비우고 찍는다, 규칙 9)
--   · 연락처 평문 열람 기록에 사업자 신원 키(사업자 광고주 = biz-<신원 id> — 광고주 줄이 없어도 신원 대표자·연락처·이메일을 열람할 수 있다, 규칙 8)
--   · 처리 기록 표(만들기·고치기 — 바뀐 칸 이름만, 값은 남기지 않는다 · 보관 · 보관 풀기, 규칙 10)
-- 를 더한다. 광고주 키(사업자 한 줄 합치기 biz- · adv- 별칭)는 앱이 신청 건·광고주 줄에서 계산한다(표 없음).

-- ── 보관·파기 ──
ALTER TABLE ad_advertisers
    ADD COLUMN archived_at        timestamptz,
    ADD COLUMN archived_by        bigint,
    ADD COLUMN contact_purged_at  timestamptz;
ALTER TABLE ad_advertisers
    -- 보관은 직접 등록 광고주만(사업자 광고주는 사업자 정지·탈퇴를 따른다)
    ADD CONSTRAINT ck_aa_archive_direct CHECK (archived_at IS NULL OR business_profile_id IS NULL),
    ADD CONSTRAINT ck_aa_archived_by CHECK ((archived_at IS NULL) = (archived_by IS NULL));
-- 파기 배치가 훑는 줄(보관했고 아직 파기하지 않은 것)
CREATE INDEX ix_aa_purge ON ad_advertisers (archived_at, id) WHERE archived_at IS NOT NULL AND contact_purged_at IS NULL;

-- ── 열람 기록 — 광고주 줄(직접 등록 · 예전 기록) 또는 사업자 신원(사업자 광고주) 중 하나 ──
ALTER TABLE ad_advertiser_views ALTER COLUMN advertiser_id DROP NOT NULL;
ALTER TABLE ad_advertiser_views
    ADD COLUMN business_profile_id bigint REFERENCES business_profiles (id),
    -- 무엇을 열었나 — ADVERTISER 광고 담당자 칸 · BUSINESS 사업자 신원 대표자·연락처·이메일(사업자 화면 business.reveal 과 별개 기록). 예전 줄은 ADVERTISER
    ADD COLUMN source varchar(10) NOT NULL DEFAULT 'ADVERTISER';
ALTER TABLE ad_advertiser_views
    ADD CONSTRAINT ck_aav_target CHECK (num_nonnulls(advertiser_id, business_profile_id) = 1),
    ADD CONSTRAINT ck_aav_source CHECK (source IN ('ADVERTISER', 'BUSINESS'));
CREATE INDEX ix_aav_business ON ad_advertiser_views (business_profile_id, created_at DESC) WHERE business_profile_id IS NOT NULL;

-- ── 처리 기록 — fields = 바뀐 칸 이름(쉼표, name·contactName·contactPhone·email·memo 중) ──
CREATE TABLE ad_advertiser_events (
    id             bigint        PRIMARY KEY,
    advertiser_id  bigint        NOT NULL REFERENCES ad_advertisers (id),
    action         varchar(10)   NOT NULL,
    actor_id       bigint,
    fields         varchar(100),
    created_at     timestamptz   NOT NULL,
    CONSTRAINT ck_aae_action CHECK (action IN ('CREATE', 'UPDATE', 'ARCHIVE', 'UNARCHIVE')),
    CONSTRAINT ck_aae_fields CHECK (fields IS NULL OR fields ~ '^[A-Za-z]+(,[A-Za-z]+)*$')
);
CREATE INDEX ix_aae_advertiser ON ad_advertiser_events (advertiser_id, created_at DESC, id DESC);

-- 이미 있는 광고주의 만들기 기록 — 처리 기록 ID 는 광고주 ID 를 그대로 쓴다(Snowflake 라 앞으로 발급할 ID 와 겹치지 않는다)
INSERT INTO ad_advertiser_events (id, advertiser_id, action, actor_id, fields, created_at)
SELECT a.id, a.id, 'CREATE', a.created_by, NULL, a.created_at FROM ad_advertisers a;
