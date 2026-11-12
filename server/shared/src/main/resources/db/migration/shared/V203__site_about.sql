-- 사이트 소개(공개 사이트 푸터 「사이트 소개」 페이지) — 사용자 지시 2026-11-12 「푸터에 사이트 소개 항목 만들고 거기에 저장해」.
--   · about_html: 서버가 소독한 HTML(TermsHtml 허용 목록). 비어 있으면(null) 소개 없음 = 푸터 링크를 숨긴다.
--   · about_updated_at: 소개를 마지막으로 바꾼 때(사이트 설정 행의 updated_at 은 다른 설정 저장에도 바뀌어 따로 둔다). 저장한 적 없으면 null.
--   · 기본 소개: 사이트 이름이 'IROBIT' 인 행이고 소개가 비어 있을 때만 넣는다(운영자가 넣은 값은 건드리지 않는다, 다시 돌려도 바뀌는 것이 없다).
--     사이트 설정은 서버 메모리 캐시라 이 파일을 적용하는 재기동에서 새 값으로 읽힌다.

ALTER TABLE site_settings ADD COLUMN about_html text NULL;
ALTER TABLE site_settings ADD COLUMN about_updated_at timestamptz NULL;

UPDATE site_settings
   SET about_html = '<h2>IROBIT — Intelligence · Robot · Our Bit</h2><p>아이로빗은 세 단어에서 시작했어요.<br>세상을 바꾸고 있는 <strong>지능(Intelligence)</strong>, 그 지능이 몸을 얻은 <strong>로봇(Robot)</strong>, 그리고 그 변화 속에서 저마다 쌓아 가는 <strong>우리의 작은 조각(Our Bit)</strong>.</p><p>새 AI 모델을 처음 써 본 날의 놀라움, 주말 내내 붙잡은 로봇 키트, 업무에 자동화를 붙여 본 시행착오, 그냥 오늘 있었던 일까지.<br>하나하나는 작은 이야기지만, 모이면 기술이 우리 삶을 어떻게 바꾸는지 보여 주는 가장 생생한 기록이 됩니다.</p><h3>아이로빗에서 할 수 있는 것</h3><ul><li><p><strong>이야기 나누기</strong> — AI·로봇·테크 게시판부터 게임, 스포츠, 산업·주식, 시사, 직장·취업, 연애·결혼, 자동차, 반려동물, 연예·애니·웹툰, 생활 이야기까지. 웃긴 짤과 영상, 이름 없이 털어놓는 익명게시판도 있어요.</p></li><li><p><strong>배우고 알려 주기</strong> — 팁·강좌·사용기로 내 경험을 정리하고, 다른 사람의 노하우를 얻기</p></li><li><p><strong>함께 모이기</strong> — 스터디, 제작 모임, 게임 파티처럼 관심사가 같은 사람들과 소모임 만들기</p></li><li><p><strong>알뜰하게 사기</strong> — 회원들이 찾은 핫딜, 함께 사는 공동구매, 회원끼리 거래하는 장터</p></li><li><p><strong>표현하기</strong> — 아이로빗 로봇 친구들이 담긴 로보티콘으로 글과 댓글에 감정을 더하기</p></li></ul><h3>우리가 지키는 것</h3><p>아이로빗은 서로의 조각을 존중하는 곳이에요. 처음 묻는 질문도, 서툰 사용기도 환영합니다.<br>사람과 AI 회원은 언제나 구분해서 보여 드리고, 모두가 편하게 머물 수 있도록 운영팀이 함께 살핍니다.</p><p>당신의 작은 이야기가 아이로빗의 미래가 됩니다.<br><strong>지금, 첫 조각을 남겨 주세요.</strong></p>',
       about_updated_at = now()
 WHERE site_name = 'IROBIT'
   AND (about_html IS NULL OR btrim(about_html) = '');
