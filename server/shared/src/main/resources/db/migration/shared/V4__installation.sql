-- 설치 마법사 · 사이트 설정 · 메일 서버 설정 — docs/api-install.md, docs/decisions.md "설치형 구성 — 설치 마법사"
--
-- BOTBI 를 설치형으로도 쓴다. 서버를 처음 띄우면 설치 전(PENDING)이고 /install 마법사를 마쳐야 쓸 수 있다.
-- 세 테이블 모두 행이 하나뿐이다(id = 1). 사이트·메일 설정은 설치 때 처음 생기고 이후 운영자 설정 화면이 고친다.
--   * 설정 우선순위는 소셜 로그인 키(V3)와 같다: 설정(DB) > 환경 변수·yml > 기본값.
--   * 메일 비밀번호는 평문으로 저장하지 않는다. 앱이 AES-256-GCM 으로 암호화한 문자열("v1:" + base64)만 둔다(키 COMMUNITY_CRYPTO_KEY).

-- ── 설치 상태 ────────────────────────────────────────────────────────────────
CREATE TABLE installation (
    id           integer     PRIMARY KEY,
    status       varchar(16) NOT NULL,                       -- PENDING(설치 전) · COMPLETED(설치 끝)
    token_hash   char(64),                                   -- 설치 토큰의 SHA-256. 원문은 저장하지 않는다. 설치가 끝나면 NULL
    installed_at timestamptz,
    root_user_id bigint      REFERENCES users (id),          -- 설치 때 만든 루트 계정(첫 ADMIN). 이미 쓰던 DB 는 NULL
    CONSTRAINT ck_installation_single_row CHECK (id = 1),
    CONSTRAINT ck_installation_status CHECK (status IN ('PENDING', 'COMPLETED')),
    CONSTRAINT ck_installation_completed CHECK (status = 'PENDING' OR installed_at IS NOT NULL)
);

-- 이미 쓰던 DB 를 지킨다: ADMIN 회원이 하나라도 있으면 COMPLETED 로 시작한다(설치 전 상태로 돌아가 잠기지 않게).
-- 없으면 PENDING 이다. 설치 토큰은 서버가 뜰 때 만든다(token_hash).
INSERT INTO installation (id, status, installed_at)
SELECT 1,
       CASE WHEN EXISTS (SELECT 1 FROM users WHERE role = 'ADMIN') THEN 'COMPLETED' ELSE 'PENDING' END,
       CASE WHEN EXISTS (SELECT 1 FROM users WHERE role = 'ADMIN') THEN now() END;

-- ── 사이트 설정 ──────────────────────────────────────────────────────────────
-- 행이 없으면 환경 변수·yml(community.public-url · community.seo.site-name)이 쓰인다.
CREATE TABLE site_settings (
    id         integer      PRIMARY KEY,
    site_name  varchar(50)  NOT NULL,                        -- 제목 꼬리표·og:site_name 등에 쓰는 사이트 이름
    public_url varchar(200) NOT NULL,                        -- 서버가 밖으로 내보내는 링크의 기준(메일 링크·소셜 로그인 콜백·sitemap). http(s)://호스트[:포트], 끝 / 없음
    updated_by bigint       NOT NULL REFERENCES users (id),
    updated_at timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_site_settings_single_row CHECK (id = 1)
);

-- ── 메일 서버 설정 ───────────────────────────────────────────────────────────
-- 행이 없으면 환경 변수(spring.mail.* · community.mail.from)가 쓰인다. 둘 다 없으면 메일은 보내지 못한다(가입은 그대로 된다).
CREATE TABLE mail_settings (
    id           integer      PRIMARY KEY,
    host         varchar(255) NOT NULL,
    port         integer      NOT NULL,
    username     varchar(255),                               -- 비어 있으면 인증 없이 보낸다
    password_enc text,                                       -- 암호문 "v1:" + base64(iv ‖ 암호문 ‖ 태그). 사용자 이름이 있을 때만 있다
    from_address varchar(254) NOT NULL,
    security     varchar(8)   NOT NULL,                      -- NONE · STARTTLS · SSL
    updated_by   bigint       NOT NULL REFERENCES users (id),
    updated_at   timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_mail_settings_single_row CHECK (id = 1),
    CONSTRAINT ck_mail_settings_port CHECK (port BETWEEN 1 AND 65535),
    CONSTRAINT ck_mail_settings_security CHECK (security IN ('NONE', 'STARTTLS', 'SSL')),
    CONSTRAINT ck_mail_settings_auth CHECK (password_enc IS NULL OR username IS NOT NULL)
);
