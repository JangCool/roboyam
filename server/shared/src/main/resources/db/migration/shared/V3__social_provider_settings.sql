-- 소셜 로그인 키 설정 화면 — docs/api-admin-social.md, docs/decisions.md "소셜 로그인 키 설정 화면"
--
-- 운영자(ADMIN)가 설정 화면에서 네이버·카카오·구글의 CLIENT_ID·CLIENT_SECRET 을 등록한다. 공급자마다 한 행이다.
--   * 행이 있으면 그 값이 환경 변수(NAVER_CLIENT_ID …)보다 먼저 쓰인다. enabled = false 면 "등록은 했지만 꺼 둠"이라
--     환경 변수 값이 있어도 그 공급자는 꺼진다. 행을 지우면(DELETE) 환경 변수 값으로 돌아간다.
--   * 시크릿은 평문으로 저장하지 않는다. 앱이 AES-256-GCM 으로 암호화한 문자열("v1:" + base64)만 둔다.
--     키는 환경 변수 COMMUNITY_CRYPTO_KEY(base64 32바이트). 키를 잃으면 이 값은 복호화할 수 없으므로 설정 화면에서 다시 입력한다.
--   * 앱 서버 2대가 같은 테이블을 본다 — 저장한 서버는 즉시, 다른 서버는 주기 확인(community.social.refresh-millis, 기본 15초)으로 반영한다.
CREATE TABLE social_provider_settings (
    provider          varchar(16)  PRIMARY KEY,                -- NAVER · KAKAO · GOOGLE
    client_id         varchar(200) NOT NULL,                   -- 공개값(로그인 주소에 실린다)
    client_secret_enc text         NOT NULL,                   -- 암호문 "v1:" + base64(iv ‖ 암호문 ‖ 태그). 평문 시크릿은 어디에도 저장하지 않는다
    enabled           boolean      NOT NULL DEFAULT true,
    updated_by        bigint       NOT NULL REFERENCES users (id),   -- 마지막으로 저장한 운영자
    updated_at        timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_social_provider_settings_provider CHECK (provider IN ('NAVER', 'KAKAO', 'GOOGLE'))
);
