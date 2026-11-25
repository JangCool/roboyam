-- 공구 배송 상태 자동 반영 — 스마트택배(스윗트래커) 조회 API (commerce-groupbuy-contract 결정 90 · 고친 결정 7).
--   · 택배사(commerce_carriers)에 스마트택배 택배사 코드(sweettracker_code — 비면 그 택배사는 자동 조회 안 함). 시드 6개 값을 채운다(운영자가 고칠 수 있다).
--   · 발송 줄(group_buy_shipments)에 추적 칸 — 마지막 조회 단계(0~6)·완료 여부·이력(jsonb [{time,kind,where,level}] — 기사 이름·전화는 싣지 않는다)·
--     마지막/다음 조회 시각·연속 실패 횟수·마지막 오류 코드·하루 조회 수(KST 날짜 + 횟수 — 송장당 하루 20회)·택배사 완료 시각·자동 완료를 하지 않은 사유.
--   · 배송 조회 설정(commerce_tracking_settings 1행) — 자동 조회 켜기(기본 꺼짐)·주기(분, 기본 180)·발송 뒤 N일 자동 배송완료(기본 꺼짐, N 기본 10)·
--     API 키(SecretEncryptor 암호문 — 평문 금지)·키 오류 알림 상태(같은 오류 반복 알림 막기).

ALTER TABLE commerce_carriers ADD COLUMN sweettracker_code varchar(10) NULL;
ALTER TABLE commerce_carriers ADD CONSTRAINT ck_commerce_carriers_sweettracker CHECK (sweettracker_code IS NULL OR sweettracker_code ~ '^[0-9A-Za-z]{1,10}$');
UPDATE commerce_carriers SET sweettracker_code = CASE code
    WHEN 'EPOST' THEN '01'
    WHEN 'CJ' THEN '04'
    WHEN 'HANJIN' THEN '05'
    WHEN 'LOGEN' THEN '06'
    WHEN 'LOTTE' THEN '08'
    WHEN 'KDEXP' THEN '23'
END
WHERE code IN ('EPOST', 'CJ', 'HANJIN', 'LOGEN', 'LOTTE', 'KDEXP');

ALTER TABLE group_buy_shipments ADD COLUMN tracking_level smallint NULL;
ALTER TABLE group_buy_shipments ADD COLUMN tracking_complete boolean NOT NULL DEFAULT false;
ALTER TABLE group_buy_shipments ADD COLUMN tracking_events jsonb NULL;
ALTER TABLE group_buy_shipments ADD COLUMN tracking_checked_at timestamptz NULL;
ALTER TABLE group_buy_shipments ADD COLUMN tracking_next_at timestamptz NULL;
ALTER TABLE group_buy_shipments ADD COLUMN tracking_fail_count integer NOT NULL DEFAULT 0;
ALTER TABLE group_buy_shipments ADD COLUMN tracking_error_code varchar(20) NULL;
ALTER TABLE group_buy_shipments ADD COLUMN tracking_day date NULL;
ALTER TABLE group_buy_shipments ADD COLUMN tracking_day_count integer NOT NULL DEFAULT 0;
ALTER TABLE group_buy_shipments ADD COLUMN tracking_completed_at timestamptz NULL;
ALTER TABLE group_buy_shipments ADD COLUMN tracking_hold_reason varchar(30) NULL;
ALTER TABLE group_buy_shipments ADD CONSTRAINT ck_gbs_tracking_level CHECK (tracking_level IS NULL OR tracking_level BETWEEN 0 AND 6);
ALTER TABLE group_buy_shipments ADD CONSTRAINT ck_gbs_tracking_counts CHECK (tracking_fail_count >= 0 AND tracking_day_count >= 0);
-- 조회 배치가 집는 줄(배송완료 전·추적 완료 전) — 다음 조회 시각 차례
CREATE INDEX ix_gbs_tracking_due ON group_buy_shipments (tracking_next_at) WHERE delivered_at IS NULL AND tracking_complete = false;

CREATE TABLE commerce_tracking_settings (
    id                     integer      PRIMARY KEY,
    enabled                boolean      NOT NULL DEFAULT false,
    interval_minutes       integer      NOT NULL DEFAULT 180,
    auto_deliver_enabled   boolean      NOT NULL DEFAULT false,
    auto_deliver_days      integer      NOT NULL DEFAULT 10,
    api_key_cipher         text         NULL,
    key_updated_at         timestamptz  NULL,
    key_updated_by         bigint       NULL,
    alert_code             varchar(20)  NULL,
    alert_at               timestamptz  NULL,
    updated_at             timestamptz  NULL,
    updated_by             bigint       NULL,
    CONSTRAINT ck_commerce_tracking_settings_id CHECK (id = 1),
    CONSTRAINT ck_commerce_tracking_settings_interval CHECK (interval_minutes BETWEEN 60 AND 1440),
    CONSTRAINT ck_commerce_tracking_settings_days CHECK (auto_deliver_days BETWEEN 3 AND 30)
);
INSERT INTO commerce_tracking_settings (id) VALUES (1);
