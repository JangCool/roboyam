-- 회원 탈퇴(docs/features/member-withdrawal-contract.md, docs/domain/commerce.md 17절 2026-11-03 개정 D1~D6, docs/plans/member-withdrawal/00-analysis.md 4절).
--   · users.status 에 WITHDRAWING(탈퇴 진행 중 — 남은 일이 끝나면 배치가 완료) 을 더한다. isGone() 에는 넣지 않는다(로그인·남은 일 처리·탈퇴 취소는 된다).
--   · profiles.nickname NOT NULL 해제 — 탈퇴 완료 때 NULL(익명화). lower(nickname) 유일 색인은 NULL 을 여럿 허용한다. 화면은 gone 판정으로 「탈퇴한 회원」을 그린다.
--   · member_withdrawals — 탈퇴 신청 기록(사유·고른 것·포기 동의 금액·시각·남은 일 스냅샷·완료·취소 시각). 돈 포기 증빙이라 5년 둔다(일반 거래 기록 파기와 같은 기준).
--     개인정보(이메일·닉네임)는 넣지 않는다 — 회원 번호만.

ALTER TABLE users DROP CONSTRAINT ck_users_status;
ALTER TABLE users ADD CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'BANNED', 'WITHDRAWING', 'WITHDRAWN'));

ALTER TABLE profiles ALTER COLUMN nickname DROP NOT NULL;

CREATE TABLE member_withdrawals (
    id                      bigint        PRIMARY KEY,
    user_id                 bigint        NOT NULL REFERENCES users (id),
    status                  varchar(10)   NOT NULL,
    -- 신청 전 계정 상태(ACTIVE·SUSPENDED·BANNED) — 취소하면 이 상태로 돌아가고, 완료 때 재가입 제한(정지면 상한)을 센다
    prior_status            varchar(16)   NOT NULL DEFAULT 'ACTIVE',
    reason                  varchar(20),
    reason_note             varchar(200),
    paid_point_choice       varchar(10),
    delivered_choice        varchar(10),
    biz_choice              varchar(10),
    group_choices           jsonb,
    forfeit_paid_points     bigint,
    forfeit_refund_estimate bigint,
    forfeit_biz_amount      bigint,
    forfeit_consented_at    timestamptz,
    pending_snapshot        jsonb,
    pending_count           integer       NOT NULL DEFAULT 0,
    expected_complete_at    timestamptz,
    rejoin_until            timestamptz,
    has_payment_history     boolean,
    progress_notified_at    timestamptz,
    requested_at            timestamptz   NOT NULL,
    completed_at            timestamptz,
    canceled_at             timestamptz,
    CONSTRAINT ck_mw_status CHECK (status IN ('SCHEDULED', 'COMPLETED', 'CANCELED')),
    CONSTRAINT ck_mw_prior_status CHECK (prior_status IN ('ACTIVE', 'SUSPENDED', 'BANNED')),
    CONSTRAINT ck_mw_reason CHECK (reason IS NULL OR reason IN ('NOT_USING', 'PRIVACY', 'INCONVENIENT', 'OTHER_SERVICE', 'REJOIN', 'OTHER')),
    CONSTRAINT ck_mw_paid_choice CHECK (paid_point_choice IS NULL OR paid_point_choice IN ('REFUND', 'FORFEIT')),
    CONSTRAINT ck_mw_delivered_choice CHECK (delivered_choice IS NULL OR delivered_choice IN ('WAIT', 'NOW')),
    CONSTRAINT ck_mw_biz_choice CHECK (biz_choice IS NULL OR biz_choice IN ('WAIT', 'FORFEIT')),
    CONSTRAINT ck_mw_completed CHECK ((status = 'COMPLETED') = (completed_at IS NOT NULL)),
    CONSTRAINT ck_mw_canceled CHECK ((status = 'CANCELED') = (canceled_at IS NOT NULL)),
    CONSTRAINT ck_mw_pending_count CHECK (pending_count >= 0)
);
-- 회원당 진행 중 신청은 하나
CREATE UNIQUE INDEX ux_member_withdrawals_scheduled ON member_withdrawals (user_id) WHERE status = 'SCHEDULED';
-- 자동 완료·진행 안내 배치가 훑는 줄
CREATE INDEX ix_member_withdrawals_scheduled ON member_withdrawals (requested_at, id) WHERE status = 'SCHEDULED';
CREATE INDEX ix_member_withdrawals_user ON member_withdrawals (user_id, requested_at DESC);

-- 탈퇴·반려 사업자의 90일 뒤 암호문·등록증 파기 배치(R__batch_36)가 훑는 줄 — 아직 파기하지 않은 탈퇴·반려 신원
CREATE INDEX ix_business_profiles_erase_due ON business_profiles (updated_at) WHERE erased_at IS NULL AND status IN ('REJECTED', 'WITHDRAWN');
-- 탈퇴 회원 글·댓글의 접속 IP 90일 정리(R__batch_36)가 훑는 줄
CREATE INDEX ix_posts_author_ip ON posts (author_id) WHERE author_ip IS NOT NULL;
CREATE INDEX ix_comments_author_ip ON comments (author_id) WHERE author_ip IS NOT NULL;
