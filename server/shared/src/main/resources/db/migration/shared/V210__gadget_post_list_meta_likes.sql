-- 글 목록 가젯(POST_LIST)의 글 줄 정보에 「추천 수」(LIKES) 옵션을 더한다(사용자 2026-11-14).
-- 예전에는 추천 수가 옵션 없이 코드 규칙으로 붙었다 — 순위 모양(rowStyle RANK)·추천 많은 순(sort LIKES)이면 늘 보였다(인기 정렬은 직전 커밋에서 뺐다).
-- 이제 화면은 meta 에 LIKES 가 있을 때만 보이므로, 보이던 가젯이 그대로 보이게 그 가젯들의 meta 에 LIKES 를 넣는다.
-- meta 가 없는 가젯(처음 값으로 그리던 것)은 처음 값(BOARD·PREFIX·COMMENTS·TIME)에 LIKES 를 더한다. 이미 있으면 그대로 둔다.
UPDATE home_gadgets
SET config = jsonb_set(config, '{meta}',
                       COALESCE(config -> 'meta', '["BOARD", "PREFIX", "COMMENTS", "TIME"]'::jsonb) || '["LIKES"]'::jsonb)
WHERE type = 'POST_LIST'
  AND (config ->> 'rowStyle' = 'RANK' OR config ->> 'sort' = 'LIKES')
  AND NOT COALESCE(config -> 'meta', '[]'::jsonb) ? 'LIKES';
