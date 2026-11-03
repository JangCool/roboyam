-- 관리자 대시보드 구성(운영자마다 따로) — docs/features/admin-dashboard-contract.md 「구성(저장 값)」
--
-- admin 스트림에 두는 이유: 관리자 앱(admin 서버)만 읽고 쓴다 — 공개 사이트 서버(api)는 관리자 대시보드를 모른다.
-- · 운영자당 한 행(운영자 ID = PK). 행이 없으면 「저장한 적 없음」(GET 이 gadgets: null — 화면이 권한으로 기본 구성을 만든다).
-- · gadgets = [{id, type, x, y, w, h, config}] 배열(jsonb). 칸 검사·정리(종류별 최소 폭 · 한 개만 되는 종류 · 설정 칸)는 저장하는 쪽(DashboardLayoutService)이 끝낸다.
-- · version = 저장 모양 판(지금 2 — 행 12px), columns = 격자 칸 수(지금 48). 옛 판·칸 수를 늘려 읽는 일은 화면이 한다.
-- 개인 화면 설정이라 감사 기록·이력 표가 없다. 운영자가 지워지면 함께 지운다.
CREATE TABLE operator_dashboards (
    operator_id bigint      PRIMARY KEY REFERENCES operators (id) ON DELETE CASCADE,
    version     integer     NOT NULL,
    columns     integer     NOT NULL,
    gadgets     jsonb       NOT NULL,
    updated_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_operator_dashboards_gadgets CHECK (jsonb_typeof(gadgets) = 'array')
);
