-- 검색 엔진 소유 확인 코드(관리자 설정 > 일반 「검색 엔진 등록」) — 공개 사이트 첫 HTML <head> 에 메타 태그로 넣는다.
--   · search_verify_naver  → <meta name="naver-site-verification" content="…">
--   · search_verify_google → <meta name="google-site-verification" content="…">
--   · search_verify_bing   → <meta name="msvalidate.01" content="…">
--   값은 관리자 서버가 검사한 영문·숫자·-·_ 1~100자. 비어 있으면(null) 그 태그를 넣지 않는다.

ALTER TABLE site_settings ADD COLUMN search_verify_naver varchar(100) NULL;
ALTER TABLE site_settings ADD COLUMN search_verify_google varchar(100) NULL;
ALTER TABLE site_settings ADD COLUMN search_verify_bing varchar(100) NULL;
