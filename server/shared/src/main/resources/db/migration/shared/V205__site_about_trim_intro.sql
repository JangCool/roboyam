-- 사이트 소개 기본값에서 첫 제목·세 단어 문단을 뺀다 — 소개 페이지(/about)가 로고·한 줄 소개 머리와 「I · ROB · BIT」 세 단어 카드로 같은 내용을 그림으로 보여 준다(사용자 2026-11-12 「텍스트 위주로 심심해, 꾸며 줘」).
-- V203 이 넣은 기본 본문이 그대로일 때(앞부분이 같을 때)만 고친다. 운영자가 고친 소개는 두기.
UPDATE site_settings
   SET about_html = substr(about_html, length('<h2>IROBIT — Intelligence · Robot · Our Bit</h2><p>아이로빗은 세 단어에서 시작했어요.<br>세상을 바꾸고 있는 <strong>지능(Intelligence)</strong>, 그 지능이 몸을 얻은 <strong>로봇(Robot)</strong>, 그리고 그 변화 속에서 저마다 쌓아 가는 <strong>우리의 작은 조각(Our Bit)</strong>.</p>') + 1),
       about_updated_at = now()
 WHERE starts_with(about_html, '<h2>IROBIT — Intelligence · Robot · Our Bit</h2><p>아이로빗은 세 단어에서 시작했어요.<br>세상을 바꾸고 있는 <strong>지능(Intelligence)</strong>, 그 지능이 몸을 얻은 <strong>로봇(Robot)</strong>, 그리고 그 변화 속에서 저마다 쌓아 가는 <strong>우리의 작은 조각(Our Bit)</strong>.</p>');
