-- 운영자 이름(operator_name) 유일 — 대소문자를 무시하고 같은 이름은 둘 이상 못 쓴다 (docs/features/roles-contract.md 「6단계 구현 결과」)
--
-- 앱은 이미 내 정보 닉네임 변경과 초대 수락에서만 대소문자 무시 중복을 막았고(어드바이저리 잠금 7340003), 운영자 직접 추가·수정은 막지 않았다.
-- 이제 모든 입구가 같은 규칙을 쓰고 이 유일 인덱스가 마지막 방어다. 앱의 사전 검사도 같은 lower() 로 견준다(OperatorRepositoryQDSL.existsOtherWithName).
--
-- 이미 같은 이름이 들어 있는 DB 를 위해 인덱스를 만들기 전에 중복을 해소한다:
--   · 같은 이름(lower 기준) 묶음에서 가장 먼저 만든 행(created_at, id 순)은 그대로 둔다.
--   · 나머지는 이름 뒤에 공백과 숫자를 붙인다: 「이름 2」, 「이름 3」 … 20자 제한 안에 맞춰 이름 앞부분을 잘라낸다(자른 뒤 끝 공백은 지운다).
--     붙인 이름이 다른 운영자의 이름(대소문자 무시)과 겹치면 숫자를 올려 빈 이름을 찾는다.
--   · 이름이 바뀐 행마다 감사 기록 OPERATOR_NAME_DEDUP 을 SYSTEM 이 남긴다(대상 = 그 운영자, detail = 바뀌기 전·후 이름). 해당 운영자에게 알리는 방법은 없다.
--   · 감사 기록 ID 는 앱의 Snowflake 와 같은 모양(시각 ms 41비트 | 서버 번호 10비트 | 순번 12비트)으로 만든다. 서버 번호는 마이그레이션 전용으로 1023 을 쓴다.
DO $$
DECLARE
    r        record;
    n        int;
    suffix   text;
    cand     text;
    changed  int := 0;
    base_ms  bigint := floor(extract(epoch FROM clock_timestamp()) * 1000)::bigint - 1767225600000;
BEGIN
    FOR r IN
        SELECT d.id, d.operator_name
          FROM (SELECT id, operator_name, created_at,
                       row_number() OVER (PARTITION BY lower(operator_name) ORDER BY created_at, id) AS rn
                  FROM operators) d
         WHERE d.rn > 1
         ORDER BY d.created_at, d.id
    LOOP
        n := 2;
        LOOP
            suffix := ' ' || n;
            cand := rtrim(left(r.operator_name, 20 - length(suffix))) || suffix;
            EXIT WHEN NOT EXISTS (SELECT 1 FROM operators o WHERE lower(o.operator_name) = lower(cand) AND o.id <> r.id);
            n := n + 1;
        END LOOP;

        UPDATE operators SET operator_name = cand, updated_at = now() WHERE id = r.id;

        INSERT INTO audit_logs (id, occurred_at, actor_type, actor_id, action, result, target_type, target_id, ip, request_id, detail)
        VALUES (((base_ms + changed / 4096) << 22) | (1023::bigint << 12) | (changed % 4096), now(), 'SYSTEM', NULL, 'OPERATOR_NAME_DEDUP', 'SUCCESS', 'OPERATOR', r.id::text, NULL, NULL,
                jsonb_build_object('before', jsonb_build_object('operatorName', r.operator_name), 'after', jsonb_build_object('operatorName', cand))::text);
        changed := changed + 1;
    END LOOP;
    RAISE NOTICE 'V30: 중복된 운영자 이름 % 건을 고쳤다', changed;
END
$$;

CREATE UNIQUE INDEX ux_operators_name_lower ON operators (lower(operator_name));
