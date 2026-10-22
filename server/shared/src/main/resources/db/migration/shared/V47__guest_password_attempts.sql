-- 비회원 글·댓글 비밀번호 시도 제한을 서버 메모리에서 DB 로 옮긴다 — 서버가 여러 대여도 한곳에서 세고, 서버를 다시 띄워도 이어진다.
-- 규칙(GuestPasswordGuard): 같은 IP 가 같은 대상(글·댓글)에 10분 안에 5번 틀리면 처음 틀린 때부터 10분 동안 막는다. 맞히면 그 줄을 지운다.
-- attempt_key 는 sha256("<IP>|<대상>") 의 16진수 — IP 원문을 남기지 않는다. 시각은 서버마다 시계가 달라도 같도록 DB 의 now() 로만 쓴다.
-- 오래된 줄은 DB 배치 `board-post-upkeep`(R__batch_19)이 지운다. 순수 expand — 이전 버전 앱은 이 표를 모르고 메모리로 센다.
CREATE TABLE guest_password_attempts (
    attempt_key        char(64)    PRIMARY KEY,
    failures           int         NOT NULL CHECK (failures >= 0),
    window_started_at  timestamptz NOT NULL,
    updated_at         timestamptz NOT NULL
);
CREATE INDEX ix_guest_password_attempts_window ON guest_password_attempts (window_started_at);
