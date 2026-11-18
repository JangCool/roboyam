-- 19금 게시판 접근 설정·핀번호 (docs/features/adult-board-pin-contract.md)
--
-- 회원마다 최대 한 줄 — 행이 없으면 꺼짐이다(이미 성인 전용 게시판을 보던 회원도 모두 꺼짐으로 시작한다).
--   · enabled       「19금 게시판 접근」. 켜려면 계정 비밀번호 확인 + 핀번호 6자리 — 핀 없이 켜진 상태는 없다(ck_…_pin).
--   · pin_hash      핀번호 해시(계정 비밀번호와 같은 PasswordEncoder). 원문은 어디에도 두지 않는다. 끄면 지운다.
--   · pin_set_at    핀번호를 정한 때 — 이 기기(로그인 세션)의 핀 확인에 같이 담아, 값이 달라지면(핀 바꾸기·끄기) 다른 기기의 확인도 무효가 된다.
--   · relock_mode   핀번호를 다시 묻는 기준 — IDLE(마지막으로 본 뒤 1시간, 기본) · FIXED(핀을 넣은 뒤 1시간). 꺼도 남는다.
--   · failed_count  연속으로 틀린 횟수(회원 단위 — 기기를 바꿔도 같이 센다). 5번이면 locked_until 까지 10분 잠금.
-- 핀 확인 상태(열림·끝나는 때)는 여기가 아니라 로그인 세션 속성이다(기기마다 따로, 로그아웃하면 풀린다).
CREATE TABLE member_adult_board_access (
    user_id       bigint       PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    enabled       boolean      NOT NULL DEFAULT false,
    pin_hash      varchar(100),
    pin_set_at    timestamptz,
    relock_mode   varchar(5)   NOT NULL DEFAULT 'IDLE',
    failed_count  integer      NOT NULL DEFAULT 0,
    locked_until  timestamptz,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_member_adult_board_access_relock_mode CHECK (relock_mode IN ('IDLE', 'FIXED')),
    CONSTRAINT ck_member_adult_board_access_pin CHECK (NOT enabled OR (pin_hash IS NOT NULL AND pin_set_at IS NOT NULL)),
    CONSTRAINT ck_member_adult_board_access_failed_count CHECK (failed_count >= 0)
);
