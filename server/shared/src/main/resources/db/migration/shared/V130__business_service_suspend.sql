-- 사업자 서비스(HOTDEAL·AD)만 정지·해제(사용자 결정 2026-11-02 — business-contract 「서비스만 정지·해제」). V111 은 고치지 않는다(적용됨).
--  · business_services: 「서비스만 정지」 표시 = 사유 코드·글·시각·운영자(신원 정지를 따라간 정지는 비어 있다). 표시가 있는 줄은 신원 해제·재승인이 되살리지 않는다.
--  · business_history: action ∪ SERVICE_SUSPENDED·SERVICE_RESUMED(17자 — varchar 16 → 20) + 어느 서비스인지(service, 서비스 처리만).
ALTER TABLE business_services
    ADD COLUMN suspend_code varchar(20),
    ADD COLUMN suspend_text varchar(200),
    ADD COLUMN suspended_at timestamptz,
    ADD COLUMN suspended_by bigint REFERENCES operators (id) ON DELETE SET NULL,
    ADD CONSTRAINT ck_bs_suspend CHECK ((suspend_code IS NULL AND suspend_text IS NULL AND suspended_at IS NULL)
        OR (status = 'SUSPENDED' AND suspend_code IS NOT NULL AND suspend_text IS NOT NULL AND suspended_at IS NOT NULL)),
    ADD CONSTRAINT ck_bs_suspend_code CHECK (suspend_code IS NULL OR suspend_code IN ('POLICY_VIOLATION', 'FRAUD', 'NTS_CLOSED', 'OTHER'));

ALTER TABLE business_history ALTER COLUMN action TYPE varchar(20);
ALTER TABLE business_history DROP CONSTRAINT ck_bh_action;
ALTER TABLE business_history
    ADD CONSTRAINT ck_bh_action CHECK (action IN ('APPLIED', 'UPDATED', 'NTS_CHECKED', 'APPROVED', 'REJECTED', 'SUSPENDED', 'UNSUSPENDED', 'REVEALED', 'CERT_VIEWED',
                                                  'WITHDRAWN', 'SERVICE_SUSPENDED', 'SERVICE_RESUMED')),
    ADD COLUMN service varchar(10),
    ADD CONSTRAINT ck_bh_service CHECK ((service IS NULL) = (action NOT IN ('SERVICE_SUSPENDED', 'SERVICE_RESUMED')) AND (service IS NULL OR service IN ('HOTDEAL', 'AD')));
