-- 쪽지(회원 1:1) — docs/features/member-social-contract.md 「쪽지」. 한도·보관은 서버 상수(하루 20통 · 새 상대 5명 · 가입 7일 뒤 · 연속 3초 · 본문 500자 · 1년 보관,
-- 「하루」는 한국 시간 0시에 시작한다 — 앱 com.community.dm.service.DmRules).
--
-- · dm_threads: 두 회원 사이의 대화는 하나다(누가 먼저 보냈든). 짝은 작은 ID(user_low_id)·큰 ID(user_high_id)로 둔다 — 같은 짝의 대화가 둘 생기지 않게 유일 제약.
--   low_sent·high_sent: 그 쪽 회원이 이 대화에서 쪽지를 한 번이라도 보냈는가(링크·연락처 허용 판정 contactAllowed — 한 번 열리면 닫히지 않는다).
--   last_message_id·last_message_at: 마지막 쪽지(보관 정리 배치가 「쪽지가 하나도 안 남은 대화」를 고른다).
-- · dm_boxes: 회원마다 자기 쪽지함 한 줄(대화를 만들 때 두 줄). 대화 삭제는 내 줄의 cleared_up_to 만 올린다(그 쪽지까지 내 쪽지함에서 안 보인다 — 상대 쪽지함은 그대로).
--   read_up_to: 내가 읽은 마지막 쪽지 ID(내려가지 않는다). last_message_id: 대화의 마지막 쪽지(쪽지함 줄 세우기 색인용 사본 — 보낼 때 두 줄을 함께 고친다).
--   안 읽은 대화 = last_message_id > greatest(read_up_to, cleared_up_to) (내가 보내면 내 read_up_to 가 그 쪽지까지 올라가므로 마지막이 내 쪽지면 안 읽음이 아니다).
-- · dm_messages: 쪽지 원문(일반 글자 500자 이하 — HTML 아님). 양쪽 쪽지함에서 모두 지운 대화는 앱이 원문을 지운다. 1년이 지난 쪽지는 DB 배치 dm-retention 이 지운다.
-- · dm_user_states: 회원마다 한 줄 — 수신 설정(accept_dm, 줄이 없으면 받기)과 오늘(한국 시간)의 한도 사용량·마지막 전송 시각. 보낼 때 이 줄을 잠가
--   간격·한도 판정과 차감을 한 트랜잭션에서 한다(서버가 여러 대여도 한도를 넘지 못한다).
-- 회원 행이 지워지면(users 행 삭제) 같이 지운다. 탈퇴(status = WITHDRAWN)한 회원의 대화는 상대 쪽지함에 「탈퇴한 회원」으로 남는다.
CREATE TABLE dm_threads (
    id              bigint      PRIMARY KEY,
    user_low_id     bigint      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    user_high_id    bigint      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    low_sent        boolean     NOT NULL DEFAULT false,
    high_sent       boolean     NOT NULL DEFAULT false,
    last_message_id bigint,
    last_message_at timestamptz,
    created_at      timestamptz NOT NULL,
    CONSTRAINT ck_dm_threads_pair CHECK (user_low_id < user_high_id),
    CONSTRAINT ux_dm_threads_pair UNIQUE (user_low_id, user_high_id)
);
CREATE INDEX ix_dm_threads_high ON dm_threads (user_high_id);
CREATE INDEX ix_dm_threads_last_at ON dm_threads (last_message_at);

CREATE TABLE dm_boxes (
    thread_id       bigint NOT NULL REFERENCES dm_threads (id) ON DELETE CASCADE,
    user_id         bigint NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    partner_id      bigint NOT NULL,
    last_message_id bigint NOT NULL DEFAULT 0,
    read_up_to      bigint NOT NULL DEFAULT 0,
    cleared_up_to   bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (thread_id, user_id)
);
-- 내 쪽지함(마지막 쪽지가 최근인 대화가 위, 커서 = 마지막 쪽지 ID) · 두 사람 사이의 대화 찾기
CREATE INDEX ix_dm_boxes_inbox ON dm_boxes (user_id, last_message_id DESC);
CREATE UNIQUE INDEX ux_dm_boxes_partner ON dm_boxes (user_id, partner_id);

CREATE TABLE dm_messages (
    id         bigint       PRIMARY KEY,
    thread_id  bigint       NOT NULL REFERENCES dm_threads (id) ON DELETE CASCADE,
    sender_id  bigint       NOT NULL,
    body       varchar(500) NOT NULL,
    created_at timestamptz  NOT NULL
);
-- 대화방(최근 쪽지가 앞, 커서 = 쪽지 ID) · 안 읽은 수 · 보관 정리
CREATE INDEX ix_dm_messages_thread ON dm_messages (thread_id, id DESC);
CREATE INDEX ix_dm_messages_created ON dm_messages (created_at);

CREATE TABLE dm_user_states (
    user_id            bigint      PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    accept_dm          boolean     NOT NULL DEFAULT true,
    quota_day          date,
    sent_today         integer     NOT NULL DEFAULT 0,
    new_partners_today integer     NOT NULL DEFAULT 0,
    last_sent_at       timestamptz,
    updated_at         timestamptz NOT NULL,
    CONSTRAINT ck_dm_user_states_counts CHECK (sent_today >= 0 AND new_partners_today >= 0)
);
