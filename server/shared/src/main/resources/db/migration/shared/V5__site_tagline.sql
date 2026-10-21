-- 사이트 슬로건(태그라인) 설정값화 (2026-10-11, docs/api-site-terms.md "슬로건")
--
-- 로고 밑 한 줄·검색엔진용 홈 제목 꼬리·홈 메타 설명·PWA manifest 설명에 쓰이는 사이트 한 줄 소개를 사이트 설정 행에 둔다. 코드에 문구를 박지 않는다 —
-- NULL = 문구 없음(화면은 그 줄을 그리지 않고, 홈 제목은 사이트 이름만 쓴다). 기존 행은 NULL 이라 로고 밑 문구가 사라지는 것 말고는 달라지지 않는다.
-- 규칙은 사이트 이름(UtilSitePolicy.nameViolation)과 같은 틀이다: 앞뒤 공백 없이 1~100자, '<'·'>' 와 제어 문자(U+0001~U+001F, U+007F~U+009F) 없음.
-- 빈 글자는 저장하지 않는다(앱이 NULL 로 바꿔 저장한다).
-- 순수 expand — 칸 추가뿐이라 이전 버전 앱은 이 칸을 모르고 그대로 돈다(docs/backend-guide.md 11-3). 이름·공개 주소·아이콘·미리보기 이미지를 저장하는 문장은 이 칸을 건드리지 않는다
-- (이름·공개 주소를 저장하는 새 버전의 upsert 만 이 칸을 함께 쓴다).
ALTER TABLE site_settings ADD COLUMN tagline varchar(100);
ALTER TABLE site_settings ADD CONSTRAINT ck_site_settings_tagline CHECK (
    tagline IS NULL OR (
        char_length(tagline) BETWEEN 1 AND 100
        AND tagline = btrim(tagline)
        AND tagline !~ '[<>\u0001-\u001f\u007f-\u009f]'
    )
);
