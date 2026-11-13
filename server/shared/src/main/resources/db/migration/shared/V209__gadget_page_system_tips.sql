-- /tips 레이아웃 편집 — docs/features/tips-contract.md 「/tips 레이아웃 편집」(사용자 2026-11-13), gadget-page-contract.md.
-- 번호: shared 마지막 V208(상품 가격 예약 변경) 다음. 다른 브랜치에 V209 가 없음을 2026-11-13 확인(합칠 때 다시 본다).
--
-- 무엇이 생기나
--   · gadget_pages.system_page — 사이트 화면의 배치를 갖는 시스템 페이지 표시. 지금 값은 'TIPS'(팁·강좌·사용기 매거진 /tips) 하나뿐이고 한 행만 가질 수 있다.
--     운영자가 만든 페이지는 NULL. 시스템 페이지는 지울 수 없고 영문 주소를 바꿀 수 없다(앱 — GadgetPageAdminService).
--   · 시스템 페이지 행 하나(id 1 — 앱 ID(Snowflake, 2026-01-01 기준 밀리초 << 22)와 겹치지 않는 고정 값, 설치 구조 파일 irobit.structure.json 에도 같은 행).
--     공개 주소는 /p/tips-magazine 이 아니라 /tips 다(메뉴 대상 해석·관리자 목록). 배치·가젯은 다른 가젯 페이지처럼 테마마다 gadget_page_layouts·home_gadgets.page_id.
--     initial_layout = 처음 배치(GadgetPageInitial.read 가 읽는 모양 {columns, items[{gadgetId,x,y,w,h}], gadgets[{id,type,title,icon,config}]}, 24칸) —
--     매거진 가젯 4종(TIPS_HEADLINE·TIPS_LATEST·TIPS_POPULAR·TIPS_TAGS — 내용 설정 없음). 꾸미기는 읽을 때 테마 기본 + 아이콘(아이로빗은 흰 카드·장식 없는 머리·옅은 네모 아이콘 바탕).
--   · 이미 같은 id·키의 행이 있으면 넘어간다(ON CONFLICT DO NOTHING).

ALTER TABLE gadget_pages
    ADD COLUMN system_page varchar(20),
    ADD CONSTRAINT ck_gadget_pages_system_page CHECK (system_page IN ('TIPS'));

CREATE UNIQUE INDEX ux_gadget_pages_system_page ON gadget_pages (system_page) WHERE system_page IS NOT NULL;

INSERT INTO gadget_pages (id, page_key, name, name_en, description, folder_id, write_button, initial_layout, system_page, created_by, created_at, updated_by, updated_at)
VALUES (1, 'tips-magazine', '팁·강좌·사용기', 'Tips & Reviews', '따라 하기 좋은 팁과 강좌, 직접 써 본 사용기를 모아 봐요', NULL, false,
        '{"columns":24,"items":[{"gadgetId":"seed-1","x":0,"y":0,"w":24,"h":20},{"gadgetId":"seed-2","x":0,"y":20,"w":17,"h":40},{"gadgetId":"seed-3","x":17,"y":20,"w":7,"h":14},{"gadgetId":"seed-4","x":17,"y":34,"w":7,"h":8}],"gadgets":[{"id":"seed-1","type":"TIPS_HEADLINE","title":"이번 주 추천","icon":"pi-star","config":{}},{"id":"seed-2","type":"TIPS_LATEST","title":"최신글","icon":"pi-clock","config":{}},{"id":"seed-3","type":"TIPS_POPULAR","title":"많이 본 글","icon":"pi-eye","config":{}},{"id":"seed-4","type":"TIPS_TAGS","title":"태그","icon":"pi-hashtag","config":{}}]}'::jsonb,
        'TIPS', NULL, now(), NULL, now())
ON CONFLICT DO NOTHING;
