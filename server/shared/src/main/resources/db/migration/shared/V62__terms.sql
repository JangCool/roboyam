-- 약관(이용약관·개인정보처리방침) — docs/features/terms-contract.md
--
-- ① terms_versions: 게시한 약관 본문. 종류(TERMS·PRIVACY)마다 버전 1, 2, 3… 게시한 행은 고치지 않는다(새 버전을 낸다).
--    body 는 서버가 허용 목록으로 정화한 HTML(TermsHtml — p br strong em u s a ul ol li blockquote h2 h3 hr, 정화 후 200,000자 이하).
--    effective_date 는 사이트 표시 시간대(site_settings.display_zone_id) 기준 날짜. 같은 종류 안에서 버전이 커질수록 같거나 늦다(서비스가 검사).
--    「지금 약관」= 시행일이 오늘 이전인 가장 큰 버전, 시행일이 미래면 「시행 예정」.
--    published_by: 게시한 운영자(operators.id). 운영자가 지워져도 게시본은 남도록 FK 를 걸지 않는다(audit_logs.actor_id 와 같다).
-- ② terms_drafts: 종류마다 초안 한 행(없을 수 있다). 게시하면 지운다.
-- ③ site_settings.terms_links_visible: 가입·로그인 화면에 약관 「보기」 링크를 보일지. 새 설치에는 게시본이 없어 기본은 끔.
CREATE TABLE terms_versions (
    id             bigint       PRIMARY KEY,                -- Snowflake(앱 발급)
    kind           varchar(10)  NOT NULL,
    version        integer      NOT NULL,
    body           text         NOT NULL,
    effective_date date         NOT NULL,
    change_note    varchar(200),
    published_at   timestamptz  NOT NULL,
    published_by   bigint,
    CONSTRAINT ck_terms_versions_kind CHECK (kind IN ('TERMS', 'PRIVACY')),
    CONSTRAINT ck_terms_versions_version CHECK (version >= 1),
    CONSTRAINT ux_terms_versions_kind_version UNIQUE (kind, version)
);

CREATE TABLE terms_drafts (
    kind        varchar(10)  PRIMARY KEY,
    body        text         NOT NULL,
    updated_at  timestamptz  NOT NULL,
    updated_by  bigint,
    CONSTRAINT ck_terms_drafts_kind CHECK (kind IN ('TERMS', 'PRIVACY'))
);

ALTER TABLE site_settings ADD COLUMN terms_links_visible boolean NOT NULL DEFAULT false;
