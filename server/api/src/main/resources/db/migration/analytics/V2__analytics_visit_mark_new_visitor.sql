-- 방문자 표시에 「신규」 칸 — 서버 여러 대에서 신규 방문자가 빠지지 않게(QA 재현 2026-11-04, site-analytics-contract).
-- 첫 화면(신규 신호)은 서버 A, 다음 화면은 서버 B 가 받고 B 가 먼저 1분 몰아쓰기를 하면 B 의 표시가 이겨 A 의 신규 여부가 버려졌다.
-- 이제 늦게 온 신규 신호는 이미 있는 표시를 false → true 로 바꾸며 그때만 신규 +1 (AnalyticsWriteStore#VISITS). 옛 표시는 false(그날 이미 센 신규는 그대로).
ALTER TABLE analytics_visit_marks ADD COLUMN IF NOT EXISTS new_visitor boolean NOT NULL DEFAULT false;
