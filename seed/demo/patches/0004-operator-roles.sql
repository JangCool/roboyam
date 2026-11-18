-- 데모 팩 패치 0004 — 운영자 역할 38개(관리자 QA 2026-11-16 의 시험 역할 ROLE_QA_* 를 권한 유형별로 더 나눠 실제 운영 이름으로)
-- (docs/qa/admin-qa-scenarios.md 4-1 · docs/domain/operator-permissions.md, 2026-11-18)
--
-- 변수: :'demo_now' · :demo_id_base — 새 행 ID 는 demo_id_base + 4001 부터(역할 권한 줄).
-- 원고도 이 파일이다 — seed-demo.mjs 의 roles 단계가 같은 파일을 demo_now = 지금 · demo_id_base = 그때의 Snowflake 로 돌린다(처음부터 다시 만들 때도 들어간다).
-- 여러 번 돌려도 된다(있는 역할·권한은 그대로).
--
-- 나누는 규칙 — 권한 유형(PermissionType, role_type_id = op_<type_id>)마다:
--   「담당」 = 그 유형의 조회(READ)·처리(MANAGE)·설정(CONFIG) 권한, 「총괄」 = 담당 + 민감 조치(SENSITIVE — 개인정보 보기·환불·삭제·지급 등).
--   민감 조치가 없는 유형은 역할 하나, 권한이 적은 유형(정산·스텝 활동)은 하나로 묶었다. 화면 구성만 「보기」(조회만)를 따로 둔다(QA 역할 그대로).
--   유형에 걸치는 것은 부관리자(운영자 권한 전부)·전체 열람(조회 권한 전부) 둘뿐 — 종합 관리 유형(op_general, 최고 관리자만 고칠 수 있다).
--   권한 목록을 적지 않고 돌릴 때의 권한 카탈로그(permissions 표)에서 유형·분류로 고른다(팩에는 그때의 목록이 굳는다 — 뒤에 생긴 권한은 설치한 곳에서 더한다).
--   조회 권한이 늘 함께 들어가 「쓰기 고르면 같은 유형의 조회 자동 체크」(Permission.requiredRead, 2026-11-16)와 맞는다.
--
-- 역할만 넣는다 — 운영자 계정에 붙이지 않는다(데모 팩의 운영자는 설치한 곳의 루트 하나뿐이다). 설치한 운영자가 운영자 화면에서 사람을 초대하며 고른다.
-- 팩에는 운영자 축(OPERATOR) 역할 중 ROLE_DEMO_SEED 를 뺀 것만 담긴다(build-demo-pack.mjs ROW_FILTER).
-- 아이디는 내장 회원 역할(ROLE_BOARD_MANAGER「게시판 매니저」·ROLE_BOARD_STAFF·ROLE_GRADE_*·ROLE_MEMBER_*)과 겹치지 않게 지었다 — 회원 역할은 운영자에게 붙일 수 없다
-- (ck_roles_type: 운영자 역할은 op_* 유형). 같은 아이디의 회원 역할이 있으면 멈춘다.

CREATE TEMP TABLE demo_role (
    role_id      varchar(30) PRIMARY KEY,
    role_type_id varchar(40) NOT NULL,
    role_name    varchar(50) NOT NULL,
    description  varchar(200) NOT NULL,
    sort         int NOT NULL,
    categories   text[] NOT NULL       -- 고를 권한 분류(READ·MANAGE·CONFIG·SENSITIVE) — op_general 이면 모든 유형에서
) ON COMMIT DROP;

INSERT INTO demo_role VALUES
 -- 종합
 ('ROLE_SUB_ADMIN', 'op_general', '부관리자', '운영자 권한 전부 — 최고 관리자 전용 화면(로그인 보안 등)은 빼고', 1, '{READ,MANAGE,CONFIG,SENSITIVE}'),
 ('ROLE_VIEWER', 'op_general', '전체 열람', '모든 관리 화면을 볼 수만 있다 — 저장·처리는 못 한다(감사·인수인계용)', 2, '{READ}'),
 -- 콘텐츠
 ('ROLE_NOTICE_MANAGER', 'op_notice', '공지·주요글 담당', '공지·하이라이트·인기글 관리와 설정', 10, '{READ,MANAGE,CONFIG}'),
 ('ROLE_POST_MANAGER', 'op_post', '게시글 담당', '게시글 처리·주요글 지정·AI 회원 관리', 11, '{READ,MANAGE,CONFIG}'),
 ('ROLE_POST_ADMIN', 'op_post', '게시글 총괄', '게시글 담당 + 삭제 글 복구·일괄 삭제·AI 회원 API 키', 12, '{READ,MANAGE,CONFIG,SENSITIVE}'),
 ('ROLE_BOARD_OPERATOR', 'op_board', '게시판 담당', '게시판 만들기·설정·기본값, 매니저/스텝 지정·한도 — 게시판 삭제는 못 한다', 13, '{READ,MANAGE,CONFIG}'),
 ('ROLE_BOARD_ADMIN', 'op_board', '게시판 총괄', '게시판 담당 + 게시판 삭제', 14, '{READ,MANAGE,CONFIG,SENSITIVE}'),
 ('ROLE_SITE_DESIGNER', 'op_siteLayout', '화면 구성 담당', '메뉴·테마·가젯 페이지·홈 배치·관리자 메뉴 고치기', 15, '{READ,MANAGE,CONFIG}'),
 ('ROLE_SITE_DESIGN_VIEWER', 'op_siteLayout', '화면 구성 보기', '메뉴·테마·가젯 페이지를 볼 수만 있다', 16, '{READ}'),
 -- 신고·제재
 ('ROLE_REPORT_MANAGER', 'op_report', '신고 담당', '신고 확인·처리', 20, '{READ,MANAGE,CONFIG}'),
 ('ROLE_SANCTION_MANAGER', 'op_sanction', '제재 담당', '회원 제재·이의제기 처리 — 영구 정지·익명 작성자 확인은 못 한다', 21, '{READ,MANAGE,CONFIG}'),
 ('ROLE_SANCTION_ADMIN', 'op_sanction', '제재 총괄', '제재 담당 + 영구 정지·익명 작성자 확인', 22, '{READ,MANAGE,CONFIG,SENSITIVE}'),
 ('ROLE_STAFF_AUDITOR', 'op_staffAudit', '스텝 활동 감사', '게시판 매니저·스텝의 처리 기록 보기와 되돌리기', 23, '{READ,MANAGE,CONFIG,SENSITIVE}'),
 -- 회원
 ('ROLE_USER_MANAGER', 'op_memberView', '회원 담당', '회원 조회·닉네임 변경 — 개인정보·탈퇴 처리는 못 한다', 30, '{READ,MANAGE,CONFIG}'),
 ('ROLE_USER_ADMIN', 'op_memberView', '회원 총괄', '회원 담당 + 개인정보 보기·탈퇴 처리·탈퇴 회원 연락처', 31, '{READ,MANAGE,CONFIG,SENSITIVE}'),
 ('ROLE_NICKNAME_IMAGE_REVIEWER', 'op_nicknameImage', '닉네임 이미지 심사', '닉네임 이미지 신청 심사', 32, '{READ,MANAGE,CONFIG}'),
 ('ROLE_POINT_MANAGER', 'op_gradePoint', '등급·포인트 담당', '등급·포인트 정책·상점·로보티콘·칭호·보상 이벤트·휴면 — 지급·환불·정산은 못 한다', 33, '{READ,MANAGE,CONFIG}'),
 ('ROLE_POINT_ADMIN', 'op_gradePoint', '등급·포인트 총괄', '등급·포인트 담당 + 포인트 직접 지급·환불·등급 직접 지정·로보티콘 매출·정산', 34, '{READ,MANAGE,CONFIG,SENSITIVE}'),
 -- 소모임·핫딜
 ('ROLE_GROUP_MANAGER', 'op_club', '소모임 담당', '소모임 개설 심사·관리와 설정', 40, '{READ,MANAGE,CONFIG}'),
 ('ROLE_HOTDEAL_MANAGER', 'op_hotdeal', '핫딜 담당', '핫딜 처리와 판매처 관리', 41, '{READ,MANAGE,CONFIG}'),
 -- 공동구매
 ('ROLE_GB_PRODUCT_MANAGER', 'op_gbPlan', '공구 상품 담당', '공구 상품·공구 만들기와 상품 설정 — 가격·재고 조정은 못 한다', 50, '{READ,MANAGE,CONFIG}'),
 ('ROLE_GB_PRODUCT_ADMIN', 'op_gbPlan', '공구 상품 총괄', '공구 상품 담당 + 가격 변경·재고 조정', 51, '{READ,MANAGE,CONFIG,SENSITIVE}'),
 ('ROLE_GB_ORDER_MANAGER', 'op_gbOrder', '공구 주문 담당', '공구 주문 확인·처리 — 환불·배송지 보기·공구 취소는 못 한다', 52, '{READ,MANAGE,CONFIG}'),
 ('ROLE_GB_ORDER_ADMIN', 'op_gbOrder', '공구 주문 총괄', '공구 주문 담당 + 환불 승인(큰 금액 포함)·배송지 보기·공구 취소', 53, '{READ,MANAGE,CONFIG,SENSITIVE}'),
 ('ROLE_GB_SETTLEMENT', 'op_gbSettle', '공구 정산', '결제·정산 보기, 대사 처리, 내보내기', 54, '{READ,MANAGE,CONFIG,SENSITIVE}'),
 ('ROLE_GB_SETTINGS', 'op_gbConfig', '공구 설정', '공동구매 판매 설정', 55, '{READ,MANAGE,CONFIG}'),
 -- 사업자·광고
 ('ROLE_BIZ_REVIEWER', 'op_bizReview', '사업자 심사 담당', '사업자 신청 심사 — 사업자 정보 보기·정지 해제·삭제는 못 한다', 60, '{READ,MANAGE,CONFIG}'),
 ('ROLE_BIZ_ADMIN', 'op_bizReview', '사업자 총괄', '사업자 심사 담당 + 사업자 정보 보기·정지 해제·삭제', 61, '{READ,MANAGE,CONFIG,SENSITIVE}'),
 ('ROLE_DEAL_ORDER_MANAGER', 'op_bizProduct', '사업자 상품 주문 담당', '사업자 상품 주문 처리와 설정 — 환불은 못 한다', 62, '{READ,MANAGE,CONFIG}'),
 ('ROLE_DEAL_ORDER_ADMIN', 'op_bizProduct', '사업자 상품 주문 총괄', '사업자 상품 주문 담당 + 환불', 63, '{READ,MANAGE,CONFIG,SENSITIVE}'),
 ('ROLE_AD_MANAGER', 'op_adPromo', '광고·체험단 담당', '광고·홍보·체험단 처리와 설정 — 연락처·배송지 보기·광고 내리기는 못 한다', 64, '{READ,MANAGE,CONFIG}'),
 ('ROLE_AD_ADMIN', 'op_adPromo', '광고·체험단 총괄', '광고·체험단 담당 + 광고주 연락처·체험단 배송지 보기·광고 내리기·되살리기', 65, '{READ,MANAGE,CONFIG,SENSITIVE}'),
 -- 시스템
 ('ROLE_OPERATOR_MANAGER', 'op_operatorRole', '운영자 관리 담당', '운영자 초대·팀·역할 만들기 — 운영자 잠그기·역할 주기는 못 한다', 70, '{READ,MANAGE,CONFIG}'),
 ('ROLE_OPERATOR_ADMIN', 'op_operatorRole', '운영자 관리 총괄', '운영자 관리 담당 + 운영자 잠그기·역할 주기(자기에게 없는 권한은 줄 수 없다)', 71, '{READ,MANAGE,CONFIG,SENSITIVE}'),
 ('ROLE_SYSTEM_MANAGER', 'op_auditBatch', '감사·배치 담당', '감사 로그·배치·캐시 보기와 관리 — 배치 지금 실행은 못 한다', 72, '{READ,MANAGE,CONFIG}'),
 ('ROLE_SYSTEM_ADMIN', 'op_auditBatch', '감사·배치 총괄', '감사·배치 담당 + 배치 지금 실행', 73, '{READ,MANAGE,CONFIG,SENSITIVE}'),
 ('ROLE_SITE_SETTINGS', 'op_siteIntegration', '사이트 설정 담당', '일반 설정·팝업·공지 띠·알림·메일 양식·약관 — 연동 키·비밀 칸은 못 본다', 74, '{READ,MANAGE,CONFIG}'),
 ('ROLE_SITE_INTEGRATION', 'op_siteIntegration', '사이트 연동 총괄', '사이트 설정 담당 + 소셜 로그인·결제·메일 서버 키 보기·바꾸기', 75, '{READ,MANAGE,CONFIG,SENSITIVE}');

-- 고른 권한(역할 × 권한)
CREATE TEMP TABLE demo_role_perm ON COMMIT DROP AS
SELECT d.role_id, d.sort, p.code
  FROM demo_role d
  JOIN permissions p ON p.axis = 'OPERATOR' AND p.category = ANY (d.categories)
   AND (d.role_type_id = 'op_general' OR d.role_type_id = 'op_' || p.type_id);

DO $$
DECLARE
    v_bad text;
BEGIN
    SELECT string_agg(r.role_id, ', ') INTO v_bad FROM roles r JOIN demo_role d USING (role_id) WHERE r.axis <> 'OPERATOR';
    IF v_bad IS NOT NULL THEN
        RAISE EXCEPTION '운영자 역할이 아닌 같은 아이디의 역할이 있다: %', v_bad;
    END IF;
    SELECT string_agg(d.role_id, ', ') INTO v_bad FROM demo_role d WHERE NOT EXISTS (SELECT 1 FROM demo_role_perm x WHERE x.role_id = d.role_id);
    IF v_bad IS NOT NULL THEN
        RAISE EXCEPTION '권한이 하나도 없는 역할: % — 권한 카탈로그(permissions)가 비었거나 유형이 바뀌었다', v_bad;
    END IF;
    -- 「총괄」은 같은 유형 「담당」보다 권한이 많아야 한다(그 유형에 민감 조치가 있다)
    SELECT string_agg(d.role_id, ', ') INTO v_bad FROM demo_role d
     WHERE d.role_name LIKE '%총괄' AND d.role_type_id <> 'op_general'
       AND (SELECT count(*) FROM demo_role_perm x WHERE x.role_id = d.role_id)
        <= (SELECT count(*) FROM demo_role_perm x JOIN demo_role m USING (role_id)
             WHERE m.role_type_id = d.role_type_id AND m.role_id <> d.role_id AND NOT ('SENSITIVE' = ANY (m.categories)));
    IF v_bad IS NOT NULL THEN
        RAISE EXCEPTION '총괄 역할이 담당보다 권한이 많지 않다: %', v_bad;
    END IF;
END $$;

INSERT INTO roles (role_id, role_type_id, axis, role_name, description, scope, grant_mode, builtin, created_by, created_at, updated_at)
SELECT d.role_id, d.role_type_id, 'OPERATOR', d.role_name, d.description, 'SITE', 'MANUAL', false,
       (SELECT id FROM operators WHERE is_root ORDER BY id LIMIT 1),
       :'demo_now'::timestamptz - make_interval(mins => 100 - d.sort), :'demo_now'::timestamptz - make_interval(mins => 100 - d.sort)
  FROM demo_role d
ON CONFLICT (role_id) DO NOTHING;

INSERT INTO role_permissions (id, role_id, permission_code, granted_at)
SELECT :demo_id_base + 4000 + row_number() OVER (ORDER BY x.sort, x.code), x.role_id, x.code,
       :'demo_now'::timestamptz - make_interval(mins => 100 - x.sort)
  FROM demo_role_perm x
 WHERE NOT EXISTS (SELECT 1 FROM role_permissions r WHERE r.role_id = x.role_id AND r.permission_code = x.code);
