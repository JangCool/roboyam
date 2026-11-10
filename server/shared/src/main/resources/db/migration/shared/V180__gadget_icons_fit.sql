-- 가젯 제목 아이콘 맞추기(docs/features/gadget-page-contract.md 「가젯 제목 아이콘」, 사용자 2026-11-10 「홈 가젯에도 어울리는 아이콘 — 프라임뷰·타블러에서 찾아서」).
--
-- 1) 보이기: 로보얌 테마의 제목 아이콘 기본을 「제목만」(숨김)에서 「보이기」로 바꿨다(사용자 2026-11-10 「로보얌 기본 보이기」). 이것은 코드의 테마 기본값이라
--    (user-web UtilGadget.titleIcon · admin UtilGadgetStyle · 서버 GadgetKinds.baseStyle) 여기서 따로 할 일이 없다 — 저장 가젯에는 iconShow 칸이 없어 테마 기본으로 읽는다.
--    운영자가 직접 끈 가젯(iconShow false)은 그대로 꺼져 있다.
-- 2) 아이콘: 로보얌 테마의 저장 가젯 중 **처음(시드) 아이콘을 그대로 둔 것만** 이름에 어울리는 아이콘으로 바꾼다 — 종류·PrimeIcons 이름(게시판 카드는 가젯 제목도)이 같고,
--    출처가 PRIME 이고 Tabler 를 고른 적이 없는 가젯. 운영자가 바꾼 아이콘·다른 테마(다음·네이버·기본 — 아이콘 값 없음)는 건드리지 않는다.
--    Tabler 는 그림 조각(path)을 그대로 담는다(Tabler Icons 3.49.0 outline, MIT — 관리자 고르기 창이 저장하는 모양과 같다). PrimeIcons 이름 칸(icon)은 그대로 둬서
--    출처를 PRIME 으로 되돌리면 이전 아이콘이 보인다. 게시판 카드의 제목 줄은 지금 데모·설치 데이터의 게시판 이름이다(없는 설치에서는 바뀌는 행이 없다).
-- 3) 가젯 페이지의 처음 배치(gadget_pages.initial_layout — 「처음 배치로 되돌리기」·아직 저장 안 한 테마가 읽는다)에도 인기글 → flame · 갤러리 → photo-video 이름을 단다
--    (GadgetPageInitial 의 새 칸 tabler — 그림 조각은 서버 GadgetTablerIcons 가 채운다).
-- POST_LIST: pi-chart-line → Tabler flame
UPDATE home_gadgets SET style = style || jsonb_build_object('iconSource', 'TABLER', 'iconTabler', '{"name":"flame","paths":[{"d":"M12 10.941c2.333 -3.308 .167 -7.823 -1 -8.941c0 3.395 -2.235 5.299 -3.667 6.706c-1.43 1.408 -2.333 3.294 -2.333 5.588c0 3.704 3.134 6.706 7 6.706c3.866 0 7 -3.002 7 -6.706c0 -1.712 -1.232 -4.403 -2.333 -5.588c-2.084 3.353 -3.257 3.353 -4.667 2.235"}]}'::jsonb)
 WHERE theme = 'roboyam' AND type = 'POST_LIST' AND style->>'icon' = 'pi-chart-line'
   AND COALESCE(style->>'iconSource', 'PRIME') = 'PRIME' AND COALESCE(style->'iconTabler', 'null'::jsonb) = 'null'::jsonb;
-- GALLERY: pi-images → Tabler photo-video
UPDATE home_gadgets SET style = style || jsonb_build_object('iconSource', 'TABLER', 'iconTabler', '{"name":"photo-video","paths":[{"d":"M9 15h-3a3 3 0 0 1 -3 -3v-6a3 3 0 0 1 3 -3h6a3 3 0 0 1 3 3v3"},{"d":"M9 12a3 3 0 0 1 3 -3h6a3 3 0 0 1 3 3v6a3 3 0 0 1 -3 3h-6a3 3 0 0 1 -3 -3l0 -6"},{"d":"M3 12l2.296 -2.296a2.41 2.41 0 0 1 3.408 0l.296 .296"},{"d":"M14 13.5v3l2.5 -1.5l-2.5 -1.5"},{"d":"M7 6v.01"}]}'::jsonb)
 WHERE theme = 'roboyam' AND type = 'GALLERY' AND style->>'icon' = 'pi-images'
   AND COALESCE(style->>'iconSource', 'PRIME') = 'PRIME' AND COALESCE(style->'iconTabler', 'null'::jsonb) = 'null'::jsonb;
-- GROUPS: pi-users → Tabler users-group
UPDATE home_gadgets SET style = style || jsonb_build_object('iconSource', 'TABLER', 'iconTabler', '{"name":"users-group","paths":[{"d":"M10 13a2 2 0 1 0 4 0a2 2 0 0 0 -4 0"},{"d":"M8 21v-1a2 2 0 0 1 2 -2h4a2 2 0 0 1 2 2v1"},{"d":"M15 5a2 2 0 1 0 4 0a2 2 0 0 0 -4 0"},{"d":"M17 10h2a2 2 0 0 1 2 2v1"},{"d":"M5 5a2 2 0 1 0 4 0a2 2 0 0 0 -4 0"},{"d":"M3 13v-1a2 2 0 0 1 2 -2h2"}]}'::jsonb)
 WHERE theme = 'roboyam' AND type = 'GROUPS' AND style->>'icon' = 'pi-users'
   AND COALESCE(style->>'iconSource', 'PRIME') = 'PRIME' AND COALESCE(style->'iconTabler', 'null'::jsonb) = 'null'::jsonb;
-- BOARD_CARDS 「팁과강좌」: pi-th-large → pi-lightbulb
UPDATE home_gadgets SET style = jsonb_set(style, '{icon}', '"pi-lightbulb"')
 WHERE theme = 'roboyam' AND type = 'BOARD_CARDS' AND style->>'icon' = 'pi-th-large' AND title = '팁과강좌'
   AND COALESCE(style->>'iconSource', 'PRIME') = 'PRIME' AND COALESCE(style->'iconTabler', 'null'::jsonb) = 'null'::jsonb;
-- BOARD_CARDS 「사용기」: pi-th-large → Tabler message-star
UPDATE home_gadgets SET style = style || jsonb_build_object('iconSource', 'TABLER', 'iconTabler', '{"name":"message-star","paths":[{"d":"M8 9h8"},{"d":"M8 13h4.5"},{"d":"M10.325 19.605l-2.325 1.395v-3h-2a3 3 0 0 1 -3 -3v-8a3 3 0 0 1 3 -3h12a3 3 0 0 1 3 3v4.5"},{"d":"M17.8 20.817l-2.172 1.138a.392 .392 0 0 1 -.568 -.41l.415 -2.411l-1.757 -1.707a.389 .389 0 0 1 .217 -.665l2.428 -.352l1.086 -2.193a.392 .392 0 0 1 .702 0l1.086 2.193l2.428 .352a.39 .39 0 0 1 .217 .665l-1.757 1.707l.414 2.41a.39 .39 0 0 1 -.567 .411l-2.172 -1.138"}]}'::jsonb)
 WHERE theme = 'roboyam' AND type = 'BOARD_CARDS' AND style->>'icon' = 'pi-th-large' AND title = '사용기'
   AND COALESCE(style->>'iconSource', 'PRIME') = 'PRIME' AND COALESCE(style->'iconTabler', 'null'::jsonb) = 'null'::jsonb;
-- BOARD_CARDS 「자유게시판」: pi-th-large → pi-comments
UPDATE home_gadgets SET style = jsonb_set(style, '{icon}', '"pi-comments"')
 WHERE theme = 'roboyam' AND type = 'BOARD_CARDS' AND style->>'icon' = 'pi-th-large' AND title = '자유게시판'
   AND COALESCE(style->>'iconSource', 'PRIME') = 'PRIME' AND COALESCE(style->'iconTabler', 'null'::jsonb) = 'null'::jsonb;
-- BOARD_CARDS 「유머게시판」: pi-th-large → Tabler mood-happy
UPDATE home_gadgets SET style = style || jsonb_build_object('iconSource', 'TABLER', 'iconTabler', '{"name":"mood-happy","paths":[{"d":"M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0"},{"d":"M9 9l.01 0"},{"d":"M15 9l.01 0"},{"d":"M8 13a4 4 0 1 0 8 0h-8"}]}'::jsonb)
 WHERE theme = 'roboyam' AND type = 'BOARD_CARDS' AND style->>'icon' = 'pi-th-large' AND title = '유머게시판'
   AND COALESCE(style->>'iconSource', 'PRIME') = 'PRIME' AND COALESCE(style->'iconTabler', 'null'::jsonb) = 'null'::jsonb;
-- BOARD_CARDS 「AI·로봇·테크」: pi-th-large → Tabler robot
UPDATE home_gadgets SET style = style || jsonb_build_object('iconSource', 'TABLER', 'iconTabler', '{"name":"robot","paths":[{"d":"M6 6a2 2 0 0 1 2 -2h8a2 2 0 0 1 2 2v4a2 2 0 0 1 -2 2h-8a2 2 0 0 1 -2 -2l0 -4"},{"d":"M12 2v2"},{"d":"M9 12v9"},{"d":"M15 12v9"},{"d":"M5 16l4 -2"},{"d":"M15 14l4 2"},{"d":"M9 18h6"},{"d":"M10 8v.01"},{"d":"M14 8v.01"}]}'::jsonb)
 WHERE theme = 'roboyam' AND type = 'BOARD_CARDS' AND style->>'icon' = 'pi-th-large' AND title = 'AI·로봇·테크'
   AND COALESCE(style->>'iconSource', 'PRIME') = 'PRIME' AND COALESCE(style->'iconTabler', 'null'::jsonb) = 'null'::jsonb;
-- BOARD_CARDS 「시사」: pi-th-large → Tabler news
UPDATE home_gadgets SET style = style || jsonb_build_object('iconSource', 'TABLER', 'iconTabler', '{"name":"news","paths":[{"d":"M16 6h3a1 1 0 0 1 1 1v11a2 2 0 0 1 -4 0v-13a1 1 0 0 0 -1 -1h-10a1 1 0 0 0 -1 1v12a3 3 0 0 0 3 3h11"},{"d":"M8 8l4 0"},{"d":"M8 12l4 0"},{"d":"M8 16l4 0"}]}'::jsonb)
 WHERE theme = 'roboyam' AND type = 'BOARD_CARDS' AND style->>'icon' = 'pi-th-large' AND title = '시사'
   AND COALESCE(style->>'iconSource', 'PRIME') = 'PRIME' AND COALESCE(style->'iconTabler', 'null'::jsonb) = 'null'::jsonb;
-- BOARD_CARDS 「산업·주식」: pi-th-large → Tabler chart-candle
UPDATE home_gadgets SET style = style || jsonb_build_object('iconSource', 'TABLER', 'iconTabler', '{"name":"chart-candle","paths":[{"d":"M4 7a1 1 0 0 1 1 -1h2a1 1 0 0 1 1 1v3a1 1 0 0 1 -1 1h-2a1 1 0 0 1 -1 -1l0 -3"},{"d":"M6 4l0 2"},{"d":"M6 11l0 9"},{"d":"M10 15a1 1 0 0 1 1 -1h2a1 1 0 0 1 1 1v3a1 1 0 0 1 -1 1h-2a1 1 0 0 1 -1 -1l0 -3"},{"d":"M12 4l0 10"},{"d":"M12 19l0 1"},{"d":"M16 6a1 1 0 0 1 1 -1h2a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1h-2a1 1 0 0 1 -1 -1l0 -4"},{"d":"M18 4l0 1"},{"d":"M18 11l0 9"}]}'::jsonb)
 WHERE theme = 'roboyam' AND type = 'BOARD_CARDS' AND style->>'icon' = 'pi-th-large' AND title = '산업·주식'
   AND COALESCE(style->>'iconSource', 'PRIME') = 'PRIME' AND COALESCE(style->'iconTabler', 'null'::jsonb) = 'null'::jsonb;
-- BOARD_CARDS 「게임」: pi-th-large → Tabler device-gamepad-2
UPDATE home_gadgets SET style = style || jsonb_build_object('iconSource', 'TABLER', 'iconTabler', '{"name":"device-gamepad-2","paths":[{"d":"M12 5h3.5a5 5 0 0 1 0 10h-5.5l-4.015 4.227a2.3 2.3 0 0 1 -3.923 -2.035l1.634 -8.173a5 5 0 0 1 4.904 -4.019h3.4"},{"d":"M14 15l4.07 4.284a2.3 2.3 0 0 0 3.925 -2.023l-1.6 -8.232"},{"d":"M8 9v2"},{"d":"M7 10h2"},{"d":"M14 10h2"}]}'::jsonb)
 WHERE theme = 'roboyam' AND type = 'BOARD_CARDS' AND style->>'icon' = 'pi-th-large' AND title = '게임'
   AND COALESCE(style->>'iconSource', 'PRIME') = 'PRIME' AND COALESCE(style->'iconTabler', 'null'::jsonb) = 'null'::jsonb;
-- BOARD_CARDS 「스포츠」: pi-th-large → Tabler ball-football
UPDATE home_gadgets SET style = style || jsonb_build_object('iconSource', 'TABLER', 'iconTabler', '{"name":"ball-football","paths":[{"d":"M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0"},{"d":"M12 7l4.76 3.45l-1.76 5.55h-6l-1.76 -5.55l4.76 -3.45"},{"d":"M12 7v-4m3 13l2.5 3m-.74 -8.55l3.74 -1.45m-11.44 7.05l-2.56 2.95m.74 -8.55l-3.74 -1.45"}]}'::jsonb)
 WHERE theme = 'roboyam' AND type = 'BOARD_CARDS' AND style->>'icon' = 'pi-th-large' AND title = '스포츠'
   AND COALESCE(style->>'iconSource', 'PRIME') = 'PRIME' AND COALESCE(style->'iconTabler', 'null'::jsonb) = 'null'::jsonb;
-- BOARD_CARDS 「연애·결혼」: pi-th-large → pi-heart
UPDATE home_gadgets SET style = jsonb_set(style, '{icon}', '"pi-heart"')
 WHERE theme = 'roboyam' AND type = 'BOARD_CARDS' AND style->>'icon' = 'pi-th-large' AND title = '연애·결혼'
   AND COALESCE(style->>'iconSource', 'PRIME') = 'PRIME' AND COALESCE(style->'iconTabler', 'null'::jsonb) = 'null'::jsonb;
-- BOARD_CARDS 「직장·취업」: pi-th-large → pi-briefcase
UPDATE home_gadgets SET style = jsonb_set(style, '{icon}', '"pi-briefcase"')
 WHERE theme = 'roboyam' AND type = 'BOARD_CARDS' AND style->>'icon' = 'pi-th-large' AND title = '직장·취업'
   AND COALESCE(style->>'iconSource', 'PRIME') = 'PRIME' AND COALESCE(style->'iconTabler', 'null'::jsonb) = 'null'::jsonb;
-- 처음 배치: 인기글(POST_LIST pi-chart-line) → tabler flame · 갤러리(GALLERY pi-images) → tabler photo-video (이미 tabler 칸이 있으면 그대로)
UPDATE gadget_pages p
   SET initial_layout = jsonb_set(p.initial_layout, '{gadgets}', (
           SELECT jsonb_agg(CASE
                                WHEN e.g ? 'tabler' THEN e.g
                                WHEN e.g->>'type' = 'POST_LIST' AND e.g->>'icon' = 'pi-chart-line' THEN e.g || '{"tabler": "flame"}'::jsonb
                                WHEN e.g->>'type' = 'GALLERY' AND e.g->>'icon' = 'pi-images' THEN e.g || '{"tabler": "photo-video"}'::jsonb
                                ELSE e.g
                            END ORDER BY e.ord)
             FROM jsonb_array_elements(p.initial_layout->'gadgets') WITH ORDINALITY AS e(g, ord)))
 WHERE jsonb_typeof(p.initial_layout->'gadgets') = 'array'
   AND jsonb_array_length(p.initial_layout->'gadgets') > 0;
