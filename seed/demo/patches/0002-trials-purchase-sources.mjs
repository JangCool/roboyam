#!/usr/bin/env node
/**
 * 데모 팩 패치 0002 — 체험단(V241~V243) · 사용기 구입 경로 · 구매 인증 · 리뷰어 칭호(V240)
 * (docs/features/trial-group-contract.md · member-title-contract.md, 2026-11-17)
 *
 * .mjs 인 이유: 체험단 신청 배송지 사본(trial_applications.*_enc)과 구매 인증 사진(purchase_proofs.content)은 DataCipher 암호문이라 SQL 로 만들 수 없다.
 * 빌드 서버와 같은 키(local 개발 키 — irobit-shared-local.yml 의 IROBIT_DATA_KEY 기본값, 팩 manifest.dataKey 와 같다)로 서버와 같은 형식(AES-256-GCM, 용도 이름이 AAD,
 * 글자 = "d<버전>:" + base64(IV‖암호문‖태그) · 바이트 = "d<버전>:" ‖ IV ‖ 암호문 ‖ 태그)을 만들어 한 트랜잭션(psql -1)으로 넣는다. 설치 때 DemoDataInstaller 가 그곳의 키로 다시 잠근다.
 * 시각은 기준 시각(DEMO_NOW)에서 며칠 전으로, 새 행 ID 는 DEMO_ID_BASE + 1001 부터. 글만 예외 — 새 사용기 글 ID 는 같은 게시판에서 작성 시각 바로 앞 데모 글 ID + 101 로 둔다
 * (데모 글은 ID 차례 = 작성 차례라 목록이 ID 로 줄을 세운다 — 기준 시각 ID 를 쓰면 열흘 전 글이 목록 맨 위에 선다).
 *
 * 넣는 것(데모 사업자 아이로빗 = 광고주, 모집 3개 모두 제공형·0원 — 광고 신청 건 kind TRIAL 은 견적 0, 결제 흐름 밖):
 *  0) 광고주 줄: 사업자 아이로빗에 붙은 ad_advertisers(이름 = 상호, 연락처 칸 없음 — 사업자 신원의 연락처를 쓴다) + 처리 기록 CREATE.
 *  1) 끝난 모집 「아두이노 센서 패키지 45종」: 신청 6 → 뽑힘 3(광고주 발송·받음·기한 안 사용기 = 약속 지킴) · 안 뽑힘 3, 주소 모두 파기, 신청 건 CANCELED(0원 정리).
 *     뽑힌 셋의 체험단 사용기 글 3개(사용기 게시판, 구입 경로 TRIAL + 체험단 줄 연결 → 표시 상자).
 *  2) 진행 중 모집 「데스크 미니 로봇팔 키트」: 신청 7(취소 1) → 뽑힘 3(받음 1 · 송장만 1 · 발송 전 1) · 안 뽑힘 3. 받은 줄·안 뽑힘·취소는 주소 파기, 나머지 둘은 암호문 사본.
 *  3) 모집 중 「라인트레이서 입문 로봇 키트」(운영자 발송): 일반 신청 시작 2일 전 · 마감 5일 뒤, 신청 6(리뷰어 몫 2 · 일반 4, 광고주 추천 1) — 주소 암호문 사본.
 *  4) 사용기 게시판 데모 글 25개에 구입 경로(SELF 21 · SPONSORED 2 · BORROWED 2 · 위 체험단 글 3개는 TRIAL) — 원고 review.json 의 purchaseSource 와 같다.
 *  5) 구매 인증(SELF 글): 승인 3(리뷰어 조건 「구매 인증 1편」) · 확인 대기 1 — 사진은 seed/demo/images 의 자리표시 JPEG 를 암호문으로. 승인 보너스 포인트(원장 PURCHASE_PROOF)는 넣지 않는다.
 *  6) 리뷰어 칭호(신청·심사): 사용기 3편 + 구매 인증 + 정식 이상을 채운 셋 — 승인 2(칭호·대표 칭호) · 대기 1(관리자 「확인할 일」). 체험단 약속 지킴 1번이라 단계 전(NULL).
 *     모집 중 모집의 리뷰어 몫 신청자 = 승인된 두 리뷰어.
 */
import { execFileSync } from 'node:child_process';
import { createCipheriv, randomBytes } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..', '..', '..');
const DB_CONTAINER = process.env.BUILD_DB_CONTAINER ?? 'irobit-local-postgres';
const DB_NAME = process.env.BUILD_DB_NAME ?? 'irobit_demo_build';
const DB_USER = process.env.BUILD_DB_USER ?? 'irobit';
const DEMO_NOW = process.env.DEMO_NOW;
const DEMO_ID_BASE = process.env.DEMO_ID_BASE;
const DRY_RUN = process.argv.includes('--dry-run'); // 끝에서 되돌린다(시험용)
if (!DEMO_NOW || !DEMO_ID_BASE) throw new Error('DEMO_NOW · DEMO_ID_BASE 가 없다 — build-demo-pack.mjs refresh 가 넘긴다');
if (DB_NAME === 'irobit') throw new Error('사용자의 로컬 DB(irobit)에는 쓰지 않는다');

// ── 암호문(DataCipher 와 같은 형식) ──
const DATA_KEY = process.env.BUILD_DATA_KEY
    ?? readFileSync(join(ROOT, 'server', 'shared', 'src', 'main', 'resources', 'irobit-shared-local.yml'), 'utf8').match(/key:\s*\$\{IROBIT_DATA_KEY:([^}]+)\}/)?.[1];
const keyMatch = /^(\d{1,3}):(.+)$/.exec(DATA_KEY ?? '');
if (!keyMatch) throw new Error('빌드 서버의 데이터 키(IROBIT_DATA_KEY)를 읽지 못했다');
const KEY_VERSION = keyMatch[1];
const KEY = Buffer.from(keyMatch[2], 'base64');
if (KEY.length !== 32) throw new Error('데이터 키가 32바이트가 아니다');
const gcm = (purpose, plain) => {
    const iv = randomBytes(12);
    const c = createCipheriv('aes-256-gcm', KEY, iv);
    c.setAAD(Buffer.from(purpose, 'utf8'));
    return Buffer.concat([iv, c.update(plain), c.final(), c.getAuthTag()]);
};
const enc = (purpose, text) => (text == null ? null : `d${KEY_VERSION}:${gcm(purpose, Buffer.from(text, 'utf8')).toString('base64')}`);
const encBytes = (purpose, bytes) => Buffer.concat([Buffer.from(`d${KEY_VERSION}:`, 'ascii'), gcm(purpose, bytes)]);

// ── SQL 조각 ──
const q = (v) => (v == null ? 'NULL' : `'${String(v).replace(/'/g, "''")}'`);
const NOW_MS = Date.parse(DEMO_NOW);
/** 기준 시각 d일 전(분 단위로 내림 — 광고 신청 건 기간 CHECK ck_ba_period 가 분 단위를 요구한다) */
const ago = (d) => new Date(Math.floor((NOW_MS - d * 86_400_000) / 60_000) * 60_000).toISOString();
/** 기준 시각 d일 전(초 단위 그대로 — 글·신청 시각) */
const agoExact = (d) => new Date(Math.round(NOW_MS - d * 86_400_000)).toISOString();
const t = (iso) => (iso == null ? 'NULL' : `${q(iso)}::timestamptz`);
let seq = 1000n;
const nextId = () => (BigInt(DEMO_ID_BASE) + ++seq).toString();
const user = (nickname) => `(SELECT user_id FROM profiles WHERE nickname = ${q(nickname)})`;
const reviewPost = (title) => `(SELECT id FROM posts WHERE board_id = :review_board AND title = ${q(title)} AND deleted_at IS NULL)`;

// 가짜 배송지(데모 — 실제 사람·주소가 아니다)
const ADDRESSES = {
    '개발자K': ['이준호', '010-5550-0111', '13529', '경기도 성남시 분당구 판교역로 235', '에이치스퀘어 N동 6층', '경비실에 맡겨 주세요'],
    '미니멀라이프': ['박민지', '010-5550-0112', '04038', '서울특별시 마포구 양화로 45', '302호', null],
    '커피는아아': ['정하은', '010-5550-0113', '06236', '서울특별시 강남구 테헤란로 152', '12층 1203호', '문 앞에 두고 벨 눌러 주세요'],
    '드론날리기': ['최도윤', '010-5550-0114', '34126', '대전광역시 유성구 대학로 99', '101동 1502호', null],
    '처음왔어요': ['김서연', '010-5550-0115', '48058', '부산광역시 해운대구 센텀중앙로 79', '센텀스카이 2203호', null],
    '고양이집사': ['강지훈', '010-5550-0116', '61945', '광주광역시 서구 상무중앙로 110', '703호', '고양이가 있어 벨은 누르지 말아 주세요'],
    '배당재투자': ['윤서준', '010-5550-0117', '22382', '인천광역시 중구 공항로 272', '스카이빌 1004호', null],
    '요리하는공대': ['한유진', '010-5550-0118', '41940', '대구광역시 중구 동덕로 130', '201호', null],
};
const P = {
    RECIPIENT: 'trial.address.recipient', PHONE: 'trial.address.phone', ADDRESS1: 'trial.address.line1', ADDRESS2: 'trial.address.line2',
    MEMO: 'trial.address.memo', PROOF: 'trial.purchase-proof',
};

const sql = [];
sql.push(`SELECT id AS op FROM operators ORDER BY id LIMIT 1 \\gset
SELECT id AS review_board FROM boards WHERE slug = 'review' AND kind = 'GENERAL' \\gset
SELECT id AS kit_prefix FROM board_prefixes WHERE board_id = :review_board AND name = '로봇·키트' \\gset
SELECT bp.id AS bp_id, bp.user_id AS biz_user, bp.company_name AS company, bp.biz_no_last4 AS last4
  FROM business_profiles bp JOIN users u ON u.id = bp.user_id WHERE u.email = 'demo.irobit@example.com' AND bp.erased_at IS NULL \\gset
SELECT price_unit AS trial_unit, unit_price AS trial_price, shipping_fee_per_person AS trial_ship FROM biz_price_rules WHERE product = 'TRIAL' \\gset
DO $$ BEGIN
    IF (SELECT unit_price FROM biz_price_rules WHERE product = 'TRIAL') <> 0 OR (SELECT shipping_fee_per_person FROM biz_price_rules WHERE product = 'TRIAL') <> 0 THEN
        RAISE EXCEPTION 'TRIAL 요금표가 0원이 아니다 — 이 패치는 0원 체험단(결제 흐름 밖)만 넣는다';
    END IF;
END $$;`);

// ── 0) 광고주 줄 ──
const advId = nextId();
sql.push(`INSERT INTO ad_advertisers (id, name, business_profile_id, created_by, created_at, updated_at)
SELECT ${advId}, :'company', :bp_id, :op, ${t(ago(32))}, ${t(ago(32))}
 WHERE NOT EXISTS (SELECT 1 FROM ad_advertisers WHERE business_profile_id = :bp_id);
SELECT id AS adv FROM ad_advertisers WHERE business_profile_id = :bp_id \\gset
INSERT INTO ad_advertiser_events (id, advertiser_id, action, actor_id, created_at)
SELECT ${nextId()}, :adv, 'CREATE', :op, ${t(ago(32))} WHERE :adv = ${advId};`);

// ── 모집 3개 ──
const TRIALS = [
    {
        key: 'closed', status: 'CLOSED', title: '[체험단] 아두이노 센서 패키지 45종 + 보관함', product: '아두이노 센서 패키지 45종 + 보관함', price: 49000,
        shipper: 'ADVERTISER', recruit: 3, share: 0, created: 31, start: 30, end: 24, selected: 23, closed: 6, appStatus: 'CANCELED',
        description: '초음파·온습도·자이로·가스·적외선 등 수업과 프로젝트에 자주 쓰는 센서 45종을 칸막이 보관함에 담은 패키지입니다.\n'
            + '받으신 날부터 14일 안에 사용기 게시판에 사용기를 남겨 주세요(제공형 — 반납하지 않습니다). 아이로빗이 직접 보내 드립니다.',
        applicants: [
            ['커피는아아', 29.5, 'SELECTED'], ['드론날리기', 29.0, 'SELECTED'], ['댕댕이아빠', 28.2, 'SELECTED'],
            ['운동하는곰', 27.4, 'NOT_SELECTED'], ['레트로게이머', 26.3, 'NOT_SELECTED'], ['웹툰정주행', 25.1, 'NOT_SELECTED'],
        ],
        ship: { 커피는아아: [22, 20.5, '631204558812'], 드론날리기: [22, 20.0, '631204558829'], 댕댕이아빠: [22, 19.6, '631204558836'] },
    },
    {
        key: 'running', status: 'RUNNING', title: '[체험단] 데스크 미니 로봇팔 키트(4축) 써 보실 분', product: '데스크 미니 로봇팔 키트 4축', price: 45000,
        shipper: 'ADVERTISER', recruit: 4, share: 25, created: 17, start: 16, end: 10, selected: 9, closed: null, appStatus: 'SUBMITTED',
        description: '펜 플로터·흡착 그리퍼가 들어 있는 4축 데스크 로봇팔 키트입니다. 블록코딩과 파이썬 예제를 함께 드립니다.\n'
            + '받으신 날부터 14일 안에 사용기를 남겨 주세요(제공형). 리뷰어 몫 25%.',
        applicants: [
            ['자취9년차', 15.8, 'SELECTED'], ['개발자K', 15.0, 'SELECTED'], ['미니멀라이프', 14.1, 'SELECTED', 9.6],
            ['넷플중독', 13.2, 'NOT_SELECTED'], ['취준생J', 12.5, 'NOT_SELECTED'], ['초보집사', 11.6, 'CANCELLED'], ['프롬프트장인', 11.0, 'NOT_SELECTED'],
        ],
        ship: { 자취9년차: [6, 4.1, '552019340017'], 개발자K: [2.1, null, '552019340024'] },
    },
    {
        key: 'recruiting', status: 'RECRUITING', title: '[체험단] 라인트레이서 입문 로봇 키트 — 리뷰어 우선', product: '라인트레이서 입문 로봇 키트', price: 32000,
        shipper: 'OPERATOR', recruit: 5, share: 60, created: 3, start: 2, end: -5, selected: null, closed: null, appStatus: 'SUBMITTED',
        description: '적외선 센서 5개로 검은 선을 따라 달리는 입문용 2륜 로봇 키트입니다. 납땜 없이 1시간이면 조립해요.\n'
            + '리뷰어 칭호가 있으면 리뷰어 몫(60%)으로 먼저 뽑습니다. 사이트 운영팀이 보내 드리며 주소는 광고주에게 가지 않습니다.',
        applicants: [
            ['커피는아아', 1.95, 'APPLIED'], ['처음왔어요', 1.8, 'APPLIED'], ['드론날리기', 1.52, 'APPLIED'],
            ['고양이집사', 1.2, 'APPLIED', 0.5], ['배당재투자', 0.9, 'APPLIED'], ['요리하는공대', 0.31, 'APPLIED'],
        ],
        ship: {},
    },
];
const REVIEWERS_AT_RECRUITING = new Set(['커피는아아', '드론날리기']);

// 체험단 사용기 글(끝난 모집의 뽑힌 셋)
const TRIAL_POSTS = {
    커피는아아: {
        ago: 12.2, views: 412, likes: 9,
        title: '[체험단] 아두이노 센서 패키지 45종, 열흘 동안 하나씩 꽂아 본 후기',
        paragraphs: [
            '체험단으로 아두이노 센서 패키지 45종을 받아서 열흘 동안 퇴근 후에 하나씩 꽂아 봤어요. 제공받은 제품이라는 점 먼저 밝혀 둡니다.',
            '구성 — 칸막이 보관함에 센서가 칸마다 들어 있고 칸 뚜껑에 이름 스티커가 붙어 있어요. 이게 생각보다 편합니다. 쓰고 나서 제자리에 돌려놓기 쉬워요.',
            '써 본 것 — 초음파 거리, 온습도, 조도, 사운드, 적외선 장애물 센서로 책상 위 "퇴근 알리미"를 만들었어요. 의자에서 일어나면 초음파 값이 바뀌어서 LED 가 켜지는 단순한 장치인데 은근 재밌습니다.',
            '아쉬운 점 — 몇몇 센서는 핀 배치가 같은 종류의 다른 모듈과 달라서 자료실 배선도를 꼭 봐야 해요. 그냥 꽂았다가 값이 0 만 나와서 한참 헤맸습니다. 점퍼선이 조금 짧은 것도 아쉬워요.',
            '총평 — 처음 아두이노 시작하는 분께 센서를 따로따로 사는 것보다 훨씬 낫습니다. 보관함 덕분에 책상이 덜 어지러워진 게 제일 큰 장점이에요.',
        ],
    },
    드론날리기: {
        ago: 10.1, views: 538, likes: 12,
        title: '[체험단] 센서 45종 키트로 드론 고도계 흉내 내 본 사용기',
        paragraphs: [
            '체험단으로 받은 아두이노 센서 패키지 후기예요(제공받은 제품입니다). 드론 취미라 기압·자이로 센서부터 열어 봤어요.',
            '기압 센서 — 계단 오르내리면서 고도 값을 찍어 보니 한 층 차이가 대충 구분됩니다. 노이즈가 있어서 이동 평균을 걸어야 쓸 만해요.',
            '자이로·가속도 — 보드를 기울이면 각도 값이 바로 따라와서 수평계 만들기 좋았어요. 다만 드론에 바로 쓰기엔 갱신 속도가 아쉬워서 연습용이라고 보시면 됩니다.',
            '자료실 — 센서마다 배선도와 예제 코드가 있어서 복사해서 돌려 보기 편했어요. 예제 주석이 한글이라 좋았습니다.',
            '총평 — 센서를 골고루 만져 보고 싶은 입문자에게 추천. 드론·로봇에 바로 얹을 부품을 찾는다면 필요한 센서만 좋은 걸로 따로 사는 게 낫습니다.',
        ],
    },
    댕댕이아빠: {
        ago: 8.3, views: 377, likes: 7,
        title: '[체험단] 초등 아이랑 방학 숙제로 써 본 아두이노 센서 45종',
        paragraphs: [
            '체험단으로 센서 패키지를 받아서 초등 4학년 아이랑 주말마다 갖고 놀았어요. 제공받은 제품이에요.',
            '아이 반응 — 사운드 센서로 손뼉 치면 불 켜지는 걸 만들었더니 한 시간 내내 손뼉만 쳤습니다ㅋㅋ 온습도 센서로 강아지 집 온도 재기도 했어요.',
            '부모 입장 — 보관함에 칸이 나뉘어 있어서 아이가 스스로 꺼내고 정리하기 좋아요. 다만 가스 센서처럼 뜨거워지는 모듈은 어른이 옆에서 봐야 합니다.',
            '아쉬운 점 — 설명서가 센서 이름과 사진 위주라 아이 혼자 보기는 어려워요. 자료실 예제를 같이 보면서 해야 합니다.',
            '총평 — 방학 과학 숙제용으로 충분했고, 끝나고도 아이가 계속 꺼내 노는 걸 보니 본전은 뽑은 느낌이에요.',
        ],
    },
};

const trialIds = {};
for (const tr of TRIALS) {
    const id = nextId();
    const appId = nextId();
    trialIds[tr.key] = id;
    const created = ago(tr.created);
    const start = ago(tr.start);
    const end = ago(tr.end);
    const selected = tr.selected == null ? null : ago(tr.selected);
    const closed = tr.closed == null ? null : ago(tr.closed);
    const selectedCount = tr.selected == null ? null : tr.applicants.filter((a) => a[2] === 'SELECTED').length;
    const updated = closed ?? selected ?? created;
    // 광고 신청 건(kind TRIAL — BizApplicationEntity.trial 과 같은 칸) · 화면 번호는 시퀀스로
    sql.push(`INSERT INTO biz_applications (id, application_no, kind, status, source, business_profile_id, user_id, advertiser_name, company_name, biz_no_last4, title,
        start_at, end_at, preferred_method, quote_supply, quote_vat, quote_total, version, created_at, updated_at, advertiser_id, created_by, weight, stop_fee_rate, closed_at)
VALUES (${appId}, 'BA-' || to_char(${t(created)} AT TIME ZONE 'Asia/Seoul', 'YYYY') || '-' || lpad(nextval('biz_application_no_seq')::text, 5, '0'), 'TRIAL',
        ${q(tr.appStatus)}, 'OPERATOR', :bp_id, NULL, (SELECT name FROM ad_advertisers WHERE id = :adv), :'company', :'last4', ${q(tr.title)},
        ${t(start)}, ${t(end)}, 'MANUAL', 0, 0, 0, ${tr.closed != null ? 2 : tr.selected != null ? 1 : 0}, ${t(created)}, ${t(updated)}, :adv, :op, 5, 0,
        ${tr.appStatus === 'CANCELED' ? t(closed) : 'NULL'});
INSERT INTO biz_application_items (id, application_id, product, unit_price, units, supply, start_at, end_at, created_at)
VALUES (${nextId()}, ${appId}, 'TRIAL', 0, ${Math.max(1, selectedCount ?? 1)}, 0, ${t(start)}, ${t(end)}, ${t(selected ?? created)});`);
    if (selected) {
        sql.push(`INSERT INTO biz_application_events (id, application_id, action, actor_type, actor_id, note, created_at) VALUES (${nextId()}, ${appId}, 'TRIAL_QUOTED', 'OPERATOR', :op, '0', ${t(selected)});`);
    }
    if (tr.appStatus === 'CANCELED') {
        sql.push(`INSERT INTO biz_application_events (id, application_id, action, actor_type, actor_id, note, created_at) VALUES (${nextId()}, ${appId}, 'CANCELED', 'OPERATOR', :op, 'TRIAL_CLOSED', ${t(closed)});`);
    }
    sql.push(`INSERT INTO trials (id, advertiser_id, biz_application_id, title, product_name, product_price, form, review_board_id, recruit_count, reviewer_share,
        reviewer_gold_hours, reviewer_silver_hours, apply_start_at, apply_end_at, review_days, return_days, shipper, status, description, image_key,
        price_unit, unit_price, shipping_fee_per_person, selected_count, amount, created_by, created_at, updated_at, selected_at, selected_by, closed_at)
VALUES (${id}, :adv, ${appId}, ${q(tr.title)}, ${q(tr.product)}, ${tr.price}, 'GIVE', :review_board, ${tr.recruit}, ${tr.share}, 48, 24, ${t(start)}, ${t(end)}, 14, NULL,
        ${q(tr.shipper)}, ${q(tr.status)}, ${q(tr.description)}, NULL, :'trial_unit', :trial_price, :trial_ship, ${selectedCount ?? 'NULL'}, ${selectedCount == null ? 'NULL' : 0},
        :op, ${t(created)}, ${t(updated)}, ${t(selected)}, ${selected ? ':op' : 'NULL'}, ${t(closed)});`);

    const consents = tr.shipper === 'ADVERTISER' ? '["PROVIDE_CONTACT"]' : '[]';
    for (const [nickname, appliedAgo, status, recommendedAgo] of tr.applicants) {
        const aId = nextId();
        const applied = agoExact(appliedAgo);
        const shipping = tr.ship[nickname];
        const received = shipping?.[1] == null ? null : agoExact(shipping[1]);
        // 주소 사본: 안 뽑힘·취소는 결정 때, 제공형 받음은 다음 날 파기 배치, 끝난 모집은 모두 파기 — 남는 것은 신청 대기·뽑혔는데 아직 받지 않은 줄
        let purgedAt = null;
        if (status === 'NOT_SELECTED') purgedAt = selected;
        else if (status === 'CANCELLED') purgedAt = agoExact(appliedAgo - 0.9);
        else if (status === 'SELECTED' && received) purgedAt = agoExact(shipping[1] - 0.9);
        else if (tr.status === 'CLOSED') purgedAt = closed;
        const a = purgedAt ? null : ADDRESSES[nickname];
        if (!purgedAt && !a) throw new Error(`${nickname} 의 가짜 배송지가 없다`);
        const quota = tr.key === 'recruiting' && REVIEWERS_AT_RECRUITING.has(nickname) ? 'REVIEWER' : 'GENERAL';
        const decided = status === 'SELECTED' || status === 'NOT_SELECTED' ? selected : null;
        sql.push(`INSERT INTO trial_applications (id, trial_id, user_id, status, quota, reviewer_tier, recipient_enc, phone_enc, postcode, address1_enc, address2_enc, memo_enc,
        address_purged_at, consents, recommended_at, created_at, decided_at, cancelled_at)
VALUES (${aId}, ${id}, ${user(nickname)}, ${q(status)}, ${q(quota)}, NULL, ${q(a && enc(P.RECIPIENT, a[0]))}, ${q(a && enc(P.PHONE, a[1]))}, ${q(a && a[2])},
        ${q(a && enc(P.ADDRESS1, a[3]))}, ${q(a && enc(P.ADDRESS2, a[4]))}, ${q(a && enc(P.MEMO, a[5]))}, ${t(purgedAt)}, ${q(consents)}::jsonb,
        ${t(recommendedAgo == null ? null : agoExact(recommendedAgo))}, ${t(applied)}, ${t(decided)}, ${t(status === 'CANCELLED' ? agoExact(appliedAgo - 0.9) : null)});`);
        if (status !== 'SELECTED') continue;

        const pId = nextId();
        const shipped = shipping ? agoExact(shipping[0]) : null;
        const post = tr.key === 'closed' ? TRIAL_POSTS[nickname] : null;
        sql.push(`INSERT INTO trial_participants (id, trial_id, application_id, user_id, quota, carrier, tracking_no, shipped_at, shipped_by_type, shipped_by, received_at, received_by,
        review_post_id, review_posted_at, review_due_at, kept, kept_at, created_at)
VALUES (${pId}, ${id}, ${aId}, ${user(nickname)}, 'GENERAL', ${shipped ? q('CJ대한통운') : 'NULL'}, ${q(shipping?.[2] ?? null)}, ${t(shipped)}, ${shipped ? "'ADVERTISER'" : 'NULL'},
        ${shipped ? ':biz_user' : 'NULL'}, ${t(received)}, ${received ? "'MEMBER'" : 'NULL'}, NULL, NULL,
        ${received ? `${t(received)} + interval '14 days'` : 'NULL'}, ${post ? 'true' : 'NULL'}, ${post ? t(agoExact(post.ago)) : 'NULL'}, ${t(selected)});`);
        if (!post) continue;
        // 체험단 사용기 글 — ID 는 같은 게시판에서 작성 시각 바로 앞 사람 데모 글의 ID + 101(목록 차례 = 작성 차례 — 뒤에 만든 공지·AI 글 ID 는 크므로 게시판으로 좁힌다). 구입 경로 TRIAL + 체험단 줄
        const posted = agoExact(post.ago);
        const html = post.paragraphs.map((p) => `<p>${p.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')}</p>`).join('');
        sql.push(`SELECT (SELECT max(p.id) FROM posts p WHERE p.board_id = :review_board AND p.created_at <= ${t(posted)} AND p.id < :demo_id_base
                   AND NOT EXISTS (SELECT 1 FROM ai_member_profiles am WHERE am.user_id = p.author_id)) + 101 AS post_id \\gset
INSERT INTO posts (id, board_id, prefix_id, author_id, author_ip, title, content, status, created_at, updated_at, has_image, view_count, like_count, comment_count,
                   content_format, content_text, purchase_source, trial_participant_id)
VALUES (:post_id, :review_board, :kit_prefix, ${user(nickname)}, '0:0:0:0:0:0:0:1', ${q(post.title)}, ${q(html)}, 'PUBLISHED', ${t(posted)}, ${t(posted)}, false,
        ${post.views}, 0, 0, 'HTML', ${q(post.paragraphs.join('\n'))}, 'TRIAL', ${pId});
UPDATE trial_participants SET review_post_id = :post_id, review_posted_at = ${t(posted)} WHERE id = ${pId};
-- 추천: 글쓴이·AI 회원·운영팀·사업자 계정을 뺀 사람 데모 회원 중 글마다 정해진 차례로
INSERT INTO post_likes (post_id, user_id, created_at)
SELECT :post_id, u.id, ${t(posted)} + (row_number() OVER (ORDER BY md5(u.id::text || ${q(post.title)}))) * interval '37 minutes'
  FROM users u
 WHERE u.status = 'ACTIVE' AND u.email LIKE 'demo%@example.com' AND u.id <> ${user(nickname)} AND u.id <> :biz_user
   AND NOT EXISTS (SELECT 1 FROM ai_member_profiles am WHERE am.user_id = u.id)
   AND NOT EXISTS (SELECT 1 FROM operator_member_accounts o WHERE o.user_id = u.id)
 ORDER BY md5(u.id::text || ${q(post.title)}) LIMIT ${post.likes};
UPDATE posts SET like_count = (SELECT count(*) FROM post_likes WHERE post_id = :post_id) WHERE id = :post_id;`);
    }
}

// ── 4) 구입 경로(사용기 게시판 데모 글 — 원고 review.json 의 purchaseSource) ──
const review = JSON.parse(readFileSync(join(ROOT, 'seed', 'demo', 'content', 'review.json'), 'utf8'));
const sources = review.posts.filter((p) => p.purchaseSource);
if (sources.length !== review.posts.length) throw new Error('원고 review.json 의 글마다 purchaseSource 가 있어야 한다(사용기 게시판은 구입 경로를 꼭 고른다)');
sql.push(`CREATE TEMP TABLE demo_sources (title text, source text);
INSERT INTO demo_sources VALUES ${sources.map((p) => `(${q(p.title.slice(0, 100))}, ${q(p.purchaseSource)})`).join(', ')};
DO $$ BEGIN
    IF (SELECT count(*) FROM demo_sources s JOIN posts p ON p.title = s.title AND p.board_id = (SELECT id FROM boards WHERE slug = 'review' AND kind = 'GENERAL') AND p.deleted_at IS NULL)
       <> (SELECT count(*) FROM demo_sources) THEN
        RAISE EXCEPTION '구입 경로를 넣을 사용기 글을 원고 제목으로 다 찾지 못했다';
    END IF;
END $$;
UPDATE posts p SET purchase_source = s.source
  FROM demo_sources s
 WHERE p.board_id = :review_board AND p.title = s.title AND p.deleted_at IS NULL AND p.purchase_source IS NULL;`);

// ── 5) 구매 인증(SELF 글) — 사진은 자리표시 JPEG 의 암호문 ──
const PROOFS = [
    { nickname: '커피는아아', title: '책상 위 로봇팔 키트 한 달 갖고 논 후기', image: 'review-11-1.jpg', created: 9.6, approved: 9.1 },
    { nickname: '드론날리기', title: '3만 원대 무선 충전 거치대 2주 사용기', image: 'review-03-1.jpg', created: 8.2, approved: 7.6 },
    { nickname: '댕댕이아빠', title: '『처음 만나는 강화학습』 3주 완독 후기', image: 'review-05-1.jpg', created: 7.1, approved: 6.6 },
    { nickname: '처음왔어요', title: '한 달 써 본 폴더블폰, 바 형태로 다시 못 돌아갈 듯', image: 'review-08-1.jpg', created: 0.7, approved: null },
];
for (const pr of PROOFS) {
    const bytes = encBytes(P.PROOF, readFileSync(join(ROOT, 'seed', 'demo', 'images', pr.image)));
    sql.push(`INSERT INTO purchase_proofs (id, post_id, board_id, user_id, content, content_type, status, reviewed_by, reviewed_at, created_at)
SELECT ${nextId()}, p.id, p.board_id, p.author_id, '\\x${bytes.toString('hex')}'::bytea, 'image/jpeg', ${pr.approved == null ? "'PENDING'" : "'APPROVED'"},
       ${pr.approved == null ? 'NULL' : ':op'}, ${t(pr.approved == null ? null : agoExact(pr.approved))}, ${t(agoExact(pr.created))}
  FROM posts p WHERE p.id = ${reviewPost(pr.title)} AND p.author_id = ${user(pr.nickname)} AND p.purchase_source = 'SELF';`);
}

// ── 6) 리뷰어 칭호(신청·심사) ──
const REVIEWER_APPS = [
    { nickname: '커피는아아', created: 6.0, reviewed: 5.1 },
    { nickname: '드론날리기', created: 5.0, reviewed: 4.2 },
    { nickname: '댕댕이아빠', created: 1.0, reviewed: null },
];
for (const r of REVIEWER_APPS) {
    const appId = nextId();
    sql.push(`INSERT INTO title_applications (id, user_id, title_id, status, reviewed_by, reviewed_at, created_at)
SELECT ${appId}, ${user(r.nickname)}, id, ${r.reviewed == null ? "'PENDING'" : "'APPROVED'"}, ${r.reviewed == null ? 'NULL' : ':op'},
       ${t(r.reviewed == null ? null : agoExact(r.reviewed))}, ${t(agoExact(r.created))}
  FROM titles WHERE title_key = 'reviewer';`);
    if (r.reviewed == null) continue;
    sql.push(`INSERT INTO member_titles (id, user_id, title_id, tier, period, source, application_id, granted_at, granted_by, seen_at)
SELECT ${nextId()}, ${user(r.nickname)}, id, NULL, '', 'APPLICATION', ${appId}, ${t(agoExact(r.reviewed))}, :op, ${t(agoExact(r.reviewed - 0.4))}
  FROM titles WHERE title_key = 'reviewer';
INSERT INTO member_title_prefs (user_id, representative_title_id, hidden, updated_at)
SELECT ${user(r.nickname)}, id, false, ${t(agoExact(r.reviewed - 0.4))} FROM titles WHERE title_key = 'reviewer'
ON CONFLICT (user_id) DO UPDATE SET representative_title_id = EXCLUDED.representative_title_id, hidden = false, updated_at = EXCLUDED.updated_at;`);
}

// 조건 확인 — 리뷰어 칭호를 준(신청한) 회원이 계약 조건(정식 이상 · 사용기 3편 · 구매 인증 승인 1편)을 채우는가, 모집 중 모집의 리뷰어 몫 신청자가 리뷰어인가
sql.push(`DO $$
DECLARE bad text;
BEGIN
    SELECT string_agg(pr.nickname, ', ') INTO bad
      FROM title_applications ta
      JOIN titles t ON t.id = ta.title_id AND t.title_key = 'reviewer'
      JOIN profiles pr ON pr.user_id = ta.user_id
      JOIN grades g ON g.grade_key = pr.grade_key
     WHERE g.level < (SELECT level FROM grades WHERE grade_key = 'REGULAR')
        OR (SELECT count(*) FROM posts p WHERE p.author_id = ta.user_id AND p.board_id = (SELECT id FROM boards WHERE slug = 'review' AND kind = 'GENERAL') AND p.deleted_at IS NULL) < 3
        OR NOT EXISTS (SELECT 1 FROM purchase_proofs pp WHERE pp.user_id = ta.user_id AND pp.status = 'APPROVED' AND pp.reviewed_at < ta.created_at);
    IF bad IS NOT NULL THEN RAISE EXCEPTION '리뷰어 조건을 채우지 못한 신청: %', bad; END IF;
    IF (SELECT count(*) FROM purchase_proofs) <> ${PROOFS.length} THEN RAISE EXCEPTION '구매 인증 %개 중 일부를 넣지 못했다(글·글쓴이·SELF 확인)', ${PROOFS.length}; END IF;
    IF EXISTS (SELECT 1 FROM trial_applications a WHERE a.quota = 'REVIEWER'
                AND NOT EXISTS (SELECT 1 FROM member_titles mt JOIN titles t ON t.id = mt.title_id AND t.title_key = 'reviewer'
                                 WHERE mt.user_id = a.user_id AND mt.revoked_at IS NULL AND mt.granted_at < a.created_at)) THEN
        RAISE EXCEPTION '리뷰어 몫 신청자가 그때 리뷰어가 아니다';
    END IF;
END $$;
SELECT format('0002 — 광고주 %s · 모집 %s(%s) · 신청 %s(주소 사본 %s) · 뽑힌 줄 %s · 체험단 사용기 %s · 구입 경로 %s · 구매 인증 %s · 리뷰어 신청 %s',
    (SELECT count(*) FROM ad_advertisers WHERE business_profile_id IS NOT NULL), (SELECT count(*) FROM trials), (SELECT string_agg(status, '·' ORDER BY created_at) FROM trials),
    (SELECT count(*) FROM trial_applications), (SELECT count(*) FROM trial_applications WHERE address_purged_at IS NULL), (SELECT count(*) FROM trial_participants),
    (SELECT count(*) FROM posts WHERE trial_participant_id IS NOT NULL),
    (SELECT string_agg(purchase_source || ' ' || n, ' · ' ORDER BY purchase_source) FROM (SELECT purchase_source, count(*) n FROM posts WHERE purchase_source IS NOT NULL GROUP BY 1) x),
    (SELECT string_agg(status || ' ' || n, ' · ') FROM (SELECT status, count(*) n FROM purchase_proofs GROUP BY 1) y),
    (SELECT string_agg(status || ' ' || n, ' · ') FROM (SELECT status, count(*) n FROM title_applications GROUP BY 1) z));`);
if (DRY_RUN) sql.push('ROLLBACK;');

const script = sql.join('\n\n');
try {
    const out = execFileSync('docker', ['exec', '-i', DB_CONTAINER, 'psql', '-X', '-q', '-At', ...(DRY_RUN ? [] : ['-1']), '-v', 'ON_ERROR_STOP=1',
        '-v', `demo_now=${DEMO_NOW}`, '-v', `demo_id_base=${DEMO_ID_BASE}`, '-U', DB_USER, '-d', DB_NAME, '-f', '-'],
        { input: DRY_RUN ? `BEGIN;\n${script}` : script, encoding: 'utf8', stdio: ['pipe', 'pipe', 'pipe'], maxBuffer: 64 * 1024 * 1024 });
    console.log(out.trim());
} catch (e) {
    throw new Error(`패치 0002 실패(되돌려졌다): ${String(e.stderr ?? e.message).trim().slice(0, 3000)}`);
}
