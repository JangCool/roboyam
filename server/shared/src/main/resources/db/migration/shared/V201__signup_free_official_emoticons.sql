-- 무료 운영 로보티콘을 처음부터 가진다(사용자 결정 2026-11-12 「어차피 무료잖아」 — 상점에서 「무료 받기」를 따로 누르지 않아도 된다).
--   · 새 가입: 이메일·소셜 가입이 회원을 만든 뒤 EmoticonAcquireService.grantSignupPacks 가 판매 중인 무료 운영 팩(source OFFICIAL · APPROVED · price 0)을
--     상점 받기와 같은 길로 준다(받기 줄 kind FREE · 받은 수 + 1 · 운영 팩이라 받기 보상 없음). 가입 뒤 새로 나온 운영 팩은 주지 않는다(가입 때만).
--   · 이 파일: 이미 가입한 회원에게 한 번만 같은 줄을 넣는다. 그 팩의 받기 줄이 이미 있는 회원(받았거나 · 스스로 뺐거나 · 이벤트로 받은 줄)은 건드리지 않는다.
--     넣지 않는 회원 = 탈퇴(WITHDRAWN 또는 deleted_at) · AI 회원(ai_member_profiles) · 운영자 회원 계정(operator_member_accounts — 판매 중 팩을 이미 다 쓴다).
--     정지 회원은 넣는다(가입한 회원이다 — 정지가 풀리면 쓴다).
--   · 상점의 「무료 받기」 단추는 그대로다(가진 팩은 「받음」, 뺀 회원은 다시 받기).
-- ID 는 앱 Snowflake 와 같은 모양(2026-01-01 기준 ms << 22 | 서버 번호 1023(마이그레이션 몫) << 12 | 순번) — 4096 줄마다 1ms 씩 앞으로 넘긴다.

DO $grant$
DECLARE
    v_ms  bigint := greatest(0, (extract(epoch FROM clock_timestamp()) * 1000)::bigint - 1767225600000);
    v_now timestamptz := now();
BEGIN
    CREATE TEMP TABLE v201_grants ON COMMIT DROP AS
    SELECT row_number() OVER (ORDER BY p.id, u.id) - 1 AS n, u.id AS user_id, p.id AS pack_id
      FROM emoticon_packs p
      CROSS JOIN users u
     WHERE p.source = 'OFFICIAL' AND p.status = 'APPROVED' AND p.price = 0
       AND u.status <> 'WITHDRAWN' AND u.deleted_at IS NULL
       AND NOT EXISTS (SELECT 1 FROM ai_member_profiles a WHERE a.user_id = u.id)
       AND NOT EXISTS (SELECT 1 FROM operator_member_accounts o WHERE o.user_id = u.id)
       AND NOT EXISTS (SELECT 1 FROM emoticon_acquisitions e WHERE e.user_id = u.id AND e.pack_id = p.id);

    INSERT INTO emoticon_acquisitions (id, user_id, pack_id, kind, first_acquired_at, acquired_at)
    SELECT ((v_ms + g.n / 4096) << 22) | (1023::bigint << 12) | (g.n % 4096), g.user_id, g.pack_id, 'FREE', v_now, v_now
      FROM v201_grants g;

    UPDATE emoticon_packs p SET acquire_count = p.acquire_count + c.cnt
      FROM (SELECT pack_id, count(*)::int AS cnt FROM v201_grants GROUP BY pack_id) c
     WHERE p.id = c.pack_id;
END
$grant$;
