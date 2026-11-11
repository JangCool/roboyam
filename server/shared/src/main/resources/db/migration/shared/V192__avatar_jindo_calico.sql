-- 동물 아바타 4종 — 진돗개 백구 · 삼색 고양이(수컷/암컷). 그림 원본 robotikon/avatar-pets.mjs(로보티콘 그림 방식).
--   · 품목: PROFILE_AVATAR · 영구 · 1,000P — V86 의 animal 카테고리와 같은 값, code avatar-<id>.
--   · 카탈로그 manifest.json 맨 뒤에 더했으므로 sort_order 는 유료 안 차례 317..320
--     (V86 1..306 · V157 307..314 · V183 315·316 다음 — AvatarCatalogTest 가 지킨다).
-- ID 는 그 시각의 Snowflake(EPOCH 2026-01-01 UTC, 노드 1023 — V86·V157·V183 과 같은 마이그레이션용 노드) + 순번.
DO $$
DECLARE
    v_base bigint := ((floor(extract(epoch FROM clock_timestamp()) * 1000)::bigint - 1767225600000) << 22) | (1023::bigint << 12);
BEGIN
    INSERT INTO point_shop_items (id, code, kind, usage_type, name, description, description_en, avatar_id, price_point, sort_order)
    SELECT v_base + a.seq, 'avatar-' || a.avatar_id, 'PROFILE_AVATAR', 'PERMANENT', a.name, '프로필 동그라미에 이 아바타를 보여요.', 'Shows this avatar in your profile circle.',
           a.avatar_id, 1000, a.sort_order
      FROM (VALUES
        (1, 'animal-jindo-m', '백구 수컷', 317),
        (2, 'animal-jindo-f', '백구 암컷', 318),
        (3, 'animal-calico-m', '삼색이 수컷', 319),
        (4, 'animal-calico-f', '삼색이 암컷', 320)) AS a(seq, avatar_id, name, sort_order)
     WHERE NOT EXISTS (SELECT 1 FROM point_shop_items i WHERE i.code = 'avatar-' || a.avatar_id);
END $$;
