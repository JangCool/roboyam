-- 설치 때 고른 기본 게시판 구성(프리셋)의 아이디를 설치 표에 남긴다.
--
-- 설치 마법사의 "기본 게시판 구성" 단계가 고른 값(NEUTRAL · ROBOYAM …)이다. 게시판·메뉴 표가 아직 없어 이 값은 선택만 기록하고,
-- 게시판을 실제로 만드는 적용은 게시판 기능을 구현할 때 이 값을 읽어 한다.
-- 설치 전(PENDING)이거나 이 칸이 생기기 전에 설치된 곳은 NULL 이다. 값은 앱의 프리셋 데이터(install-presets/*.json)가 정하므로 DB 는 모양만 지킨다:
-- 영문 대문자로 시작하는 영문 대문자·숫자·밑줄 2~20자(프리셋 아이디 규칙).
-- 순수 expand — 칸 추가뿐이라 이전 버전 앱은 이 칸을 모르고 그대로 돈다.
ALTER TABLE installation ADD COLUMN board_preset varchar(32);
ALTER TABLE installation ADD CONSTRAINT ck_installation_board_preset CHECK (
    board_preset IS NULL OR board_preset ~ '^[A-Z][A-Z0-9_]{1,19}$'
);
