-- 닉네임 그림 신청·검토 — docs/features/member-social-contract.md 「닉네임 그림」. 회원이 올리고(api) 운영자가 승인·반려·해제한다(admin, 권한 nickname-image.read·manage).
--
-- 한 줄 = 신청 한 번. status:
--   PENDING   검토 대기 — 회원당 하나(부분 유일 색인). 새로 올리면 앞 대기 신청은 CANCELED 가 된다.
--   APPROVED  지금 적용 중인 그림 — 회원당 하나(부분 유일 색인). 새 신청이 승인되면 앞 그림은 REMOVED.
--   REJECTED  반려(reject_reason, reapply_at = 반려 + 72시간). 파일은 다음 신청 때까지 둔다(반려 화면이 보인다).
--   REVOKED   운영자가 적용 중인 그림을 해제(revoke_reason, reapply_at = 해제 + 7일).
--   CANCELED  회원이 대기 신청을 취소했거나 새 신청으로 대체됨(파일은 지운다).
--   REMOVED   회원이 적용 중인 그림을 해제했거나 다른 그림이 승인돼 바뀜(파일은 지운다).
-- image_key·still_key: 저장소(ImageStorage) 키 — 파일을 지운 신청은 NULL. still_key 는 움직이는 GIF 의 첫 장면(PNG)만.
-- bytes 는 올린 파일의 크기(200KB 한도가 재는 것), width·height 는 저장한 그림의 크기, frames 는 GIF 의 장면 수(그 밖 1).
-- 회원 행이 지워지면(users 행 삭제) 같이 지운다(파일은 남는다 — 회원 탈퇴 흐름이 생기면 그때 지운다).
CREATE TABLE nickname_image_requests (
    id            bigint       PRIMARY KEY,
    user_id       bigint       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status        varchar(10)  NOT NULL,
    image_key     varchar(200),
    still_key     varchar(200),
    content_type  varchar(40)  NOT NULL,
    bytes         integer      NOT NULL,
    width         integer      NOT NULL,
    height        integer      NOT NULL,
    frames        integer      NOT NULL DEFAULT 1,
    reject_reason varchar(200),
    revoke_reason varchar(200),
    reviewed_by   bigint       REFERENCES operators (id) ON DELETE SET NULL,
    reviewed_at   timestamptz,
    revoked_by    bigint       REFERENCES operators (id) ON DELETE SET NULL,
    revoked_at    timestamptz,
    reapply_at    timestamptz,
    ended_at      timestamptz,
    created_at    timestamptz  NOT NULL,
    CONSTRAINT ck_nickname_image_requests_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'REVOKED', 'CANCELED', 'REMOVED')),
    CONSTRAINT ck_nickname_image_requests_size CHECK (bytes > 0 AND width > 0 AND height > 0 AND frames >= 1)
);
CREATE UNIQUE INDEX ux_nickname_image_requests_pending ON nickname_image_requests (user_id) WHERE status = 'PENDING';
CREATE UNIQUE INDEX ux_nickname_image_requests_approved ON nickname_image_requests (user_id) WHERE status = 'APPROVED';
-- 내 최근 신청 · 오늘 올린 수(속도 제한) · 관리자 목록(상태별)
CREATE INDEX ix_nickname_image_requests_user ON nickname_image_requests (user_id, id DESC);
CREATE INDEX ix_nickname_image_requests_status ON nickname_image_requests (status, id);

-- 회원 알림 종류에 DM(쪽지 받음)·NICKNAME_IMAGE_REVIEWED(닉네임 그림 검토 결과)를 더한다(MemberNotificationType 과 같은 값).
-- ⚠ 합칠 때 합집합 확인 — 다른 세션도 이 CHECK 를 다시 만들 수 있다(나중에 적용되는 쪽이 모든 종류를 담아야 한다).
ALTER TABLE member_notifications DROP CONSTRAINT ck_member_notifications_type;
ALTER TABLE member_notifications ADD CONSTRAINT ck_member_notifications_type
    CHECK (type IN ('COMMENT', 'REPLY', 'POINTS_REVOKED', 'POST_HIDDEN', 'COMMENT_HIDDEN', 'DM', 'NICKNAME_IMAGE_REVIEWED'));

-- 닉네임 그림 신청 조건(행 하나) — 관리자 「닉네임 이미지 설정」(admin, 조회 nickname-image.read · 저장 nickname-image.manage). 회원 화면(api)은 요청마다 이 행을 읽어 판정한다.
--   mode = GRADE   등급 기준: grade_key(최소 등급) 이상 + 이메일 인증(늘 필요 — require_email_verified 는 true 로 저장된다).
--   mode = CUSTOM  사용자 정의: grade_key(없으면 등급 조건 없음) · 가입 후 일수 · 글+댓글 수 · 보유 포인트 · 이메일 인증(끌 수 있음)을 모두 채워야 한다. 0 인 칸은 조건이 아니다.
--   GRADE 로 저장해도 CUSTOM 칸(min_*)은 받은 값을 그대로 둔다(다시 CUSTOM 으로 바꿀 때 앞 값이 보이게).
-- grade_key 는 grades 를 외래 키로 걸지 않는다(등급을 지워도 설정이 남는다) — 없는 등급이면 등급 조건은 채우지 못한 것으로 보고 관리자 화면에 gradeMissing 으로 알린다.
-- 이미 적용 중인 그림은 조건이 바뀌거나 등급이 내려가도 그대로 둔다(판정은 새 신청 때만).
-- 기본 행: GRADE · 베테랑(VETERAN — 없으면 가장 높은 등급) · 나머지 0 · 이메일 인증 필요.
CREATE TABLE nickname_image_settings (
    id                      integer      PRIMARY KEY,
    mode                    varchar(10)  NOT NULL,
    grade_key               varchar(19),
    min_days                integer      NOT NULL DEFAULT 0,
    min_activity            integer      NOT NULL DEFAULT 0,
    min_points              integer      NOT NULL DEFAULT 0,
    require_email_verified  boolean      NOT NULL DEFAULT true,
    updated_at              timestamptz,
    updated_by              bigint       REFERENCES operators (id) ON DELETE SET NULL,
    CONSTRAINT ck_nickname_image_settings_single_row CHECK (id = 1),
    CONSTRAINT ck_nickname_image_settings_mode CHECK (mode IN ('GRADE', 'CUSTOM')),
    CONSTRAINT ck_nickname_image_settings_grade CHECK (mode <> 'GRADE' OR (grade_key IS NOT NULL AND require_email_verified)),
    CONSTRAINT ck_nickname_image_settings_ranges CHECK (min_days BETWEEN 0 AND 3650 AND min_activity BETWEEN 0 AND 100000 AND min_points BETWEEN 0 AND 100000000)
);
INSERT INTO nickname_image_settings (id, mode, grade_key)
VALUES (1, 'GRADE', COALESCE((SELECT grade_key FROM grades WHERE grade_key = 'VETERAN'),
                             (SELECT grade_key FROM grades ORDER BY level DESC LIMIT 1),
                             'VETERAN'));
