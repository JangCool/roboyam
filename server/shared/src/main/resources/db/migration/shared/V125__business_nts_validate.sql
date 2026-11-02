-- 사업자 국세청 진위확인 · 공공데이터포털 설정(docs/features/business-contract.md 「국세청 진위확인 · 공공데이터 설정」, 사용자 결정 2026-11-02)
--   · business_profiles.opened_on — 개업일(사업자등록증 「개업연월일」, 가리지 않는다). 진위확인에 번호·상호·대표자명과 함께 보낸다.
--     이미 들어온 신청·승인된 사업자는 비워 둔다(진위 미확인 — 고칠 때 개업일 필수).
--   · business_nts_checks.validity — 진위확인 결과. MATCH(일치) · MISMATCH(불일치 — 승인 잠금) · UNCHECKED(진위 미확인 — 개업일 없이 상태조회만) ·
--     UNAVAILABLE(조회 불가 — 장애·제한 시간·키 없음). 이미 있는 줄은 상태조회만 한 것이라 UNCHECKED.
--   · public_data_settings — 공공데이터포털 서비스 키(국세청 상태조회·진위확인이 같이 쓴다)를 관리자 화면에서 넣는다(portone_settings 와 같은 규칙).

ALTER TABLE business_profiles ADD COLUMN opened_on date;

ALTER TABLE business_nts_checks ADD COLUMN validity varchar(12) NOT NULL DEFAULT 'UNCHECKED';
-- 새 줄은 서버가 늘 결과를 적는다 — 기본값은 이미 있는 줄을 채우는 데만 쓴다
ALTER TABLE business_nts_checks ALTER COLUMN validity DROP DEFAULT;
ALTER TABLE business_nts_checks ADD CONSTRAINT ck_bnc_validity CHECK (validity IN ('MATCH', 'MISMATCH', 'UNCHECKED', 'UNAVAILABLE'));

-- ── 공공데이터포털 설정 (칸마다 한 줄 — 지금은 NTS_SERVICE_KEY 하나) ──────────────────
-- value_cipher: SecretEncryptor(ROBOYAM_CRYPTO_KEY) 암호문. 지우면 NULL 이 되고 바꾼 시각·사람은 남는다(화면의 「마지막 변경」). 값은 응답·감사·로그에 싣지 않는다.
--   키 읽는 차례: 이 칸 → 환경 변수 ROBOYAM_NTS_SERVICE_KEY → 없음(조회 불가 NO_KEY). 서버는 조회 때마다 읽는다(바꾸면 재기동 없이 반영).
-- updated_at·updated_by: 화면에서 마지막으로 저장·지운 순간 — 연결 시험만 한 줄이면 NULL.
-- tested_*: 마지막 연결 시험(저장된 키로 실제 국세청 상태조회 한 번). 키를 저장·지우면 지운다(옛 키의 결과라서).
CREATE TABLE public_data_settings (
    key            varchar(40)  PRIMARY KEY,
    value_cipher   text,
    updated_at     timestamptz,
    updated_by     bigint,
    tested_result  varchar(16),
    tested_at      timestamptz,
    tested_by      bigint,
    CONSTRAINT ck_public_data_settings_cipher CHECK (value_cipher IS NULL OR value_cipher LIKE 'v1:%'),
    CONSTRAINT ck_public_data_settings_updated CHECK (value_cipher IS NULL OR updated_at IS NOT NULL),
    CONSTRAINT ck_public_data_settings_test CHECK (tested_result IS NULL OR tested_result IN ('OK', 'KEY_INVALID', 'NO_KEY', 'TIMEOUT', 'ERROR')),
    CONSTRAINT ck_public_data_settings_tested CHECK ((tested_result IS NULL) = (tested_at IS NULL))
);
