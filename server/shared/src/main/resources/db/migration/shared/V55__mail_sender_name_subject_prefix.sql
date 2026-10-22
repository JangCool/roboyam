-- 메일 서버 설정에 보내는 이름 · 제목 머리말 — docs/features/mail-settings-contract.md
--
--   * sender_name    보내는 사람 표시 이름. 있으면 From 이 "보내는 이름" <from_address>(RFC 5322, UTF-8 인코딩), 없으면 주소만. 1~50자, 줄바꿈·제어 문자 없음(앱이 가린다).
--   * subject_prefix 모든 메일 제목 앞에 "{머리말} {제목}" 으로 붙는다. 1~30자, 줄바꿈·제어 문자 없음(앱이 가린다).
--   둘 다 비우면 null(지금 동작 그대로). 관리자 화면에서만 넣는다(설치 마법사는 받지 않는다). 이미 있는 행은 null 로 둔다.
ALTER TABLE mail_settings ADD COLUMN sender_name varchar(50);
ALTER TABLE mail_settings ADD COLUMN subject_prefix varchar(30);
