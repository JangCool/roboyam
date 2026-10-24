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
