-- 회원 주소록(배송지 관리 — docs/features/address-book-contract.md). 회원이 「내 설정」에 배송지를 최대 10개 두고 하나를 기본으로 정한다.
-- · 받는 분·전화·주소·상세 주소·메모는 DataCipher 암호문(ROBOYAM_DATA_KEY, 용도 member.address.* — 공구 주문 배송지 groupbuy.address.* 와 따로), 우편번호·별칭만 평문.
--   주문의 배송지는 주문마다 따로 저장한 사본이다(commerce.md 10-9) — 주소록을 고치거나 지워도 이미 낸 주문은 바뀌지 않는다.
-- · 회원당 기본은 하나(부분 유일 색인). 배송지가 있으면 기본이 꼭 하나인 것과 10개 한도는 앱이 회원 단위 어드바이저리 잠금 아래에서 지킨다(동시 요청 포함).
-- · 지우면 바로 삭제(보관 없음). 탈퇴 완료 때 즉시 삭제(WithdrawalEraseStore). 운영자 화면·API 에는 없다.

CREATE TABLE member_addresses (
    id            bigint        PRIMARY KEY,
    user_id       bigint        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    label         varchar(20),
    recipient_enc varchar(500)  NOT NULL,
    phone_enc     varchar(500)  NOT NULL,
    postcode      varchar(5)    NOT NULL,
    address1_enc  varchar(1000) NOT NULL,
    address2_enc  varchar(1000),
    memo_enc      varchar(500),
    is_default    boolean       NOT NULL DEFAULT false,
    created_at    timestamptz   NOT NULL,
    updated_at    timestamptz   NOT NULL,
    CONSTRAINT ck_member_addresses_label CHECK (label IS NULL OR char_length(btrim(label)) BETWEEN 1 AND 20),
    CONSTRAINT ck_member_addresses_postcode CHECK (postcode ~ '^[0-9]{5}$')
);
-- 회원당 기본 배송지 하나 — 동시 요청은 회원 잠금이 먼저 막고, 이 색인이 마지막으로 막는다
CREATE UNIQUE INDEX ux_member_addresses_default ON member_addresses (user_id) WHERE is_default;
-- 내 목록(기본 → 최근 고친 순)·개수·기본을 지웠을 때 넘길 대상
CREATE INDEX ix_member_addresses_user ON member_addresses (user_id, updated_at DESC, id DESC);
