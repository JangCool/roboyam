-- 관리자 앱 로그인 세션 저장소 (Spring Session JDBC, PostgreSQL) — docs/features/roles-contract.md 가정, docs/domain/operator-permissions.md "추가 확정"
--
-- 공개 사이트의 세션 표(spring_session, shared V2)와 따로 둔다. 쿠키 이름(ADMIN_SESSION ↔ SESSION)만 갈라서는, 한쪽 쿠키 값을 다른 앱에 내밀어도 같은 표에서
-- 세션이 찾아져 직렬화된 로그인 주체(회원 AuthUser ↔ 운영자 OperatorPrincipal)를 읽다 실패하기 때문이다. 표가 다르면 서로의 세션을 아예 찾지 못한다.
-- admin 앱의 spring.session.jdbc.table-name 이 이 표를 가리킨다. 스키마는 여기서 만들고 Spring 은 만들지 않는다(initialize-schema=never).
CREATE TABLE admin_spring_session (
    primary_id            char(36)     NOT NULL,
    session_id            char(36)     NOT NULL,
    creation_time         bigint       NOT NULL,
    last_access_time      bigint       NOT NULL,
    max_inactive_interval int          NOT NULL,
    expiry_time           bigint       NOT NULL,
    principal_name        varchar(100),
    CONSTRAINT admin_spring_session_pk PRIMARY KEY (primary_id)
);
CREATE UNIQUE INDEX admin_spring_session_ix1 ON admin_spring_session (session_id);
CREATE INDEX admin_spring_session_ix2 ON admin_spring_session (expiry_time);
CREATE INDEX admin_spring_session_ix3 ON admin_spring_session (principal_name);

CREATE TABLE admin_spring_session_attributes (
    session_primary_id char(36)     NOT NULL,
    attribute_name     varchar(200) NOT NULL,
    attribute_bytes    bytea        NOT NULL,
    CONSTRAINT admin_spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name),
    CONSTRAINT admin_spring_session_attributes_fk FOREIGN KEY (session_primary_id)
        REFERENCES admin_spring_session (primary_id) ON DELETE CASCADE
);
