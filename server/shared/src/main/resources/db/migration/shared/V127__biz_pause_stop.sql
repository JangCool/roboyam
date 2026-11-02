-- 광고·사업자 핫딜 「일시 정지 / 중단 + 중도 해지 수수료」(docs/features/biz-pause-contract.md 규칙 1~11 — 화면 확정 2026-11-02).
--
-- 상태 모델: 정지·중단은 새 상태가 아니라 기존 TAKEN_DOWN + 새 내림 까닭이다.
--   · ADVERTISER_PAUSE — 광고주가 잠깐 멈춤([다시 켜기] → SCHEDULED, 끝 시각 그대로 · 보상 없음). 한 건에 정지 횟수 한도(설정 roboyam.biz.pause-limit, 기본 3).
--   · ADVERTISER_STOP  — 광고주가 끝냄(되돌리기 없음). 일할 환불 STOP_PRORATED(− 중도 해지 수수료)가 심사 대기로 같이 생긴다(0원이면 줄 없음). 승인 → CANCELED(까닭은 남는다).
--
-- ① biz_applications
--    · takedown_cause 넓힘(varchar 10 → 20 — ADVERTISER_PAUSE 16자) + CHECK ∪ ADVERTISER_PAUSE·ADVERTISER_STOP(기존 SUSPENDED·EMERGENCY 그대로)
--    · pause_count(쓴 정지 횟수 — 정지할 때 센다) · paused_at(지금 정지의 시작 — 다시 켜기·중단 때 비운다) · paused_minutes(끝난 정지의 멈춘 분 합, 안내용 — 기간은 늘지 않는다)
--    · takedown_actor(광고주 요청 내림을 누가 — MEMBER 사업자 · OPERATOR 운영자 대신) · takedown_channel(운영자 대신이면 요청 경로 PHONE·EMAIL·OTHER) · takedown_memo(그 메모 1~200자)
--      — 운영자 대신이면 기존 taken_down_by 에 운영자 ID
--    · stopped_at(중단 시각) · refund_from(일할 기준 시각 — 정지 중 전환이면 멈춘 시각, 아니면 중단 시각)
--    · stop_fee_rate(중도 해지 수수료율 % — 신청 건을 낼 때 그때 설정 비율을 고정한다, 규칙 7·8). 이미 있는 줄은 0(수수료 없는 2026-10-30 규정에 동의했으므로),
--      새 줄은 NOT NULL(기본값 없음 — 넣는 쪽이 반드시 정한다)
-- ② biz_refunds — 종류 ∪ STOP_PRORATED, 제안 내역 칸(prorated_amount 일할 · fee_rate · fee_amount 수수료 · basis_at 기준 시각 — 환불 예상액 = proposed_amount = 일할 − 수수료)
-- ③ posts.hidden_reason ∪ SELLER_PAUSED(정지 숨김) · SELLER_STOPPED(중단 숨김) — 운영 숨김 STAFF 와 가른다(V80 목록 REPORTED·ILLEGAL·RIGHTS·STAFF 그대로)
-- ④ ad_placement_settings.stop_fee_rate — 「광고 기본 설정」 중도 해지 수수료율(0~30 정수 %, 기본 10). 행이 없으면 서버 기본값 10(AdPlacementStore)
-- 처리 기록 action(PAUSED·RESUMED·STOPPED)·회원 알림 kind·감사 작업 이름은 DB CHECK 가 없어 고치지 않는다. 배치는 없다(정지한 채 끝나면 상태 그대로 — 화면이 끝 시각으로 읽는다).

-- ── ① 신청 건 ──
ALTER TABLE biz_applications ALTER COLUMN takedown_cause TYPE varchar(20);
ALTER TABLE biz_applications DROP CONSTRAINT ck_ba_takedown_cause;
ALTER TABLE biz_applications ADD CONSTRAINT ck_ba_takedown_cause
    CHECK (takedown_cause IS NULL OR takedown_cause IN ('SUSPENDED', 'EMERGENCY', 'ADVERTISER_PAUSE', 'ADVERTISER_STOP'));

ALTER TABLE biz_applications
    ADD COLUMN pause_count       smallint     NOT NULL DEFAULT 0,
    ADD COLUMN paused_at         timestamptz,
    ADD COLUMN paused_minutes    integer      NOT NULL DEFAULT 0,
    ADD COLUMN takedown_actor    varchar(10),
    ADD COLUMN takedown_channel  varchar(10),
    ADD COLUMN takedown_memo     varchar(200),
    ADD COLUMN stopped_at        timestamptz,
    ADD COLUMN refund_from       timestamptz,
    ADD COLUMN stop_fee_rate     smallint     NOT NULL DEFAULT 0;
-- 이미 있는 줄만 0 — 새 줄은 넣는 쪽이 신청 때 설정 비율을 반드시 넣는다
ALTER TABLE biz_applications ALTER COLUMN stop_fee_rate DROP DEFAULT;

ALTER TABLE biz_applications
    ADD CONSTRAINT ck_ba_pause_count CHECK (pause_count BETWEEN 0 AND 100),
    ADD CONSTRAINT ck_ba_paused_minutes CHECK (paused_minutes >= 0),
    ADD CONSTRAINT ck_ba_stop_fee_rate CHECK (stop_fee_rate BETWEEN 0 AND 30),
    ADD CONSTRAINT ck_ba_takedown_actor CHECK (takedown_actor IS NULL OR takedown_actor IN ('MEMBER', 'OPERATOR')),
    ADD CONSTRAINT ck_ba_takedown_channel CHECK (takedown_channel IS NULL OR takedown_channel IN ('PHONE', 'EMAIL', 'OTHER')),
    -- 운영자 대신 처리는 경로·메모가 함께 있고, 경로가 있으면 운영자다
    ADD CONSTRAINT ck_ba_takedown_on_behalf CHECK ((takedown_channel IS NULL) = (takedown_memo IS NULL)
                                                   AND (takedown_channel IS NULL OR takedown_actor = 'OPERATOR')),
    -- 광고주 요청 내림은 누가 했는지 남는다
    ADD CONSTRAINT ck_ba_advertiser_takedown CHECK (takedown_cause IS NULL OR takedown_cause NOT IN ('ADVERTISER_PAUSE', 'ADVERTISER_STOP') OR takedown_actor IS NOT NULL),
    -- 정지 중(내림 · 정지 까닭)이면 멈춘 시각이 있다
    ADD CONSTRAINT ck_ba_paused CHECK (status <> 'TAKEN_DOWN' OR takedown_cause IS DISTINCT FROM 'ADVERTISER_PAUSE' OR paused_at IS NOT NULL),
    -- 중단이면 중단 시각과 일할 기준 시각이 있다(기준 ≤ 중단)
    ADD CONSTRAINT ck_ba_stopped CHECK (takedown_cause IS DISTINCT FROM 'ADVERTISER_STOP'
                                        OR (stopped_at IS NOT NULL AND refund_from IS NOT NULL AND refund_from <= stopped_at));

-- ── ② 환불 ──
ALTER TABLE biz_refunds DROP CONSTRAINT ck_br_type;
ALTER TABLE biz_refunds ADD CONSTRAINT ck_br_type CHECK (type IN ('FULL_AUTO', 'PRORATED', 'SUSPEND_PRORATED', 'DOWNTIME', 'STOP_PRORATED'));
ALTER TABLE biz_refunds
    ADD COLUMN prorated_amount  bigint,
    ADD COLUMN fee_rate         smallint,
    ADD COLUMN fee_amount       bigint,
    ADD COLUMN basis_at         timestamptz;
ALTER TABLE biz_refunds
    -- 중단 일할은 내역이 모두 있고 예상액 = 일할 − 수수료, 그 밖 종류는 내역이 비어 있다
    ADD CONSTRAINT ck_br_stop_detail CHECK (
        (type = 'STOP_PRORATED' AND prorated_amount IS NOT NULL AND fee_rate IS NOT NULL AND fee_amount IS NOT NULL AND basis_at IS NOT NULL
            AND prorated_amount >= 0 AND fee_rate BETWEEN 0 AND 30 AND fee_amount BETWEEN 0 AND prorated_amount
            AND proposed_amount = prorated_amount - fee_amount)
        OR (type <> 'STOP_PRORATED' AND prorated_amount IS NULL AND fee_rate IS NULL AND fee_amount IS NULL AND basis_at IS NULL));

-- ── ③ 글 숨김 까닭 ──
ALTER TABLE posts DROP CONSTRAINT ck_posts_hidden_reason;
ALTER TABLE posts ADD CONSTRAINT ck_posts_hidden_reason
    CHECK (hidden_reason IS NULL OR hidden_reason IN ('REPORTED', 'ILLEGAL', 'RIGHTS', 'STAFF', 'SELLER_PAUSED', 'SELLER_STOPPED'));

-- ── ④ 광고 기본 설정 — 중도 해지 수수료율 ──
ALTER TABLE ad_placement_settings ADD COLUMN stop_fee_rate smallint NOT NULL DEFAULT 10;
ALTER TABLE ad_placement_settings ADD CONSTRAINT ck_aps_stop_fee_rate CHECK (stop_fee_rate BETWEEN 0 AND 30);
