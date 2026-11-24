-- 설치 구성의 AI 기본 계정(IROBIT — install-presets/irobit.ai-members.json, 11계정 · 꺼진 채)을 넣은 시각 (docs/features/install-contract.md 「AI 기본 계정 (2026-11-24)」).
--
-- ai_members_seeded_at  공개 서버가 그 구성의 AI 기본 계정을 넣은(또는 넣을 것이 없어 건너뛴) 시각. NULL 이면 아직 — 설치 직후(「구조만」은 설치가 커밋된 뒤,
--                       「데모 포함」은 데모를 넣은 뒤) 또는 서버가 뜰 때 한 번 넣고 채운다. 이미 같은 닉네임의 회원이 있는 계정은 건너뛴다(데모로 이미 있는 곳).
-- 이 칸이 생기기 전에 설치된 곳(운영 — 「구조만」, AI 계정 0개)도 NULL 이라 다음 기동에서 한 번 채워진다. 순수 expand — 칸 추가뿐이라 이전 버전 앱은 그대로 돈다.
ALTER TABLE installation ADD COLUMN ai_members_seeded_at timestamptz;
