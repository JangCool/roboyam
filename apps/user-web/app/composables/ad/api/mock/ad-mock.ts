// MOCK: 공개 광고 노출(`GET /api/ads` · `GET /api/ads/placements` · `POST /api/ads/impressions` · `POST /api/ads/{id}/clicks`)의 메모리 목업 — `usePublicAdApi.ts` 가 부른다.
// 광고 가젯을 공개 화면에 덧붙이는 개발 스위치(`mockAdGadgets`)는 `useAdGadgets.ts` 가 부른다. 서버가 붙으면 `ad/api/mock/` 와 함께 지운다.
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름`(쉼표로 여러 개) ─
//   ad-gadgets     홈 · 가젯 페이지 · 게시판 레이아웃 응답에 광고 가젯 5개를 화면 쪽에서 덧붙인다(맨 위 넓은 배너 · 맨 아래 한 줄 광고 · 네모 · 띠 · 작은 배너)
//   ad-keep        덧붙인 광고 가젯의 「빈 자리 유지」를 켠다(`ad-empty` 와 함께 — 「광고 문의」 안내가 보인다)
//   ad-empty       광고 후보가 하나도 없다(가젯은 접히고, 글 보기·핫딜·날개 자리도 사라진다)
//   ad-one         유형마다 비중이 가장 큰 한 건만(날개는 한쪽만 나온다 — 같은 광고를 양쪽에 두지 않는다)
//   ad-inline-off  글 보기 광고 끔(기본은 한 줄 광고 · 글 카드와 댓글 카드 사이 틈)
//   ad-inline-strip  글 보기 광고를 띠 배너로(같은 틈)
//   ad-inline-square 글 보기 광고를 네모 배너로 · 본문 바로 아래(옛 모습)
//   ad-wing-off    날개 배너 끔        ad-wing-left  왼쪽 날개만
//   ad-feed-off    게시판 목록 한 줄 광고 끔(기본 켬 — 일반 글 5번째 뒤, 첫 쪽만)    ad-feed-after-2  일반 글 2번째 뒤로(1~20)
//   ad-hotdeal     핫딜 게시판이 아니어도 게시판 목록 위에 핫딜 노출 광고(홈 상단 · 말머리를 고르면 말머리 상단)
//   ad-fail        후보 읽기 실패(500 — 광고 자리는 조용히 접힌다)
//
// ── 목업 규칙(가정) ─
//   · 이미지는 외부 서비스가 아니라 SVG data URI 로 그린 자리 이미지(규격 비율 그대로 — 1200×300 · 1200×100 · 300×250 · 300×100 · 날개 160×600).
//   · 자리 설정 기본값: 글 보기 = 한 줄 광고 · 두 카드 사이 틈, 날개 = 양쪽 켬 · 홈·가젯 페이지·게시판 목록·글 보기 네 화면.
//   · 링크는 `https://example.com/…`(예약된 예시 주소). 핫딜 노출은 `/posts/{id}`(없는 글일 수 있다).
//   · 노출·클릭은 메모리에 쌓고 개발 콘솔에 남긴다(`console.debug`). 새로고침하면 비워진다.

import { MOCK_ERRORS, mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type { AdClickRequest, AdImpressionsRequest, PublicAd, PublicAdPlacements, PublicAdsQuery, PublicAdsResponse, PublicAdType } from '~/composables/ad/api/usePublicAdApi.types';
import type { AdPlacement } from '~/composables/ad/usePublicAds.types';
import type { GadgetStyle, HomeGadget, HomeGadgetsResponse } from '~/composables/home/api/useHomeGadgetApi.types';
import UtilHomeLayout from '#layers/home-layout/utils/util-home-layout';

/** 규격(px) — 사업자 신청 안내(`business.adTypeSize`)와 같다 */
const SIZE: Record<string, [number, number]> = {
    WIDE_BANNER: [1200, 300],
    STRIP_BANNER: [1200, 100],
    SQUARE_BANNER: [300, 250],
    SMALL_BANNER: [300, 100],
    WING_BANNER: [160, 600],
};

/** 날개 배너 자리 이미지 — 세로라 글을 위아래로 쌓는다 */
function wing(from: string, to: string, lines: string[], sub: string): string {
    const text = lines.map((line, i) => `<text x="80" y="${150 + i * 40}" text-anchor="middle" font-family="sans-serif" font-size="26" font-weight="700" fill="#ffffff">${line}</text>`).join('');
    const svg =
        `<svg xmlns="http://www.w3.org/2000/svg" width="160" height="600" viewBox="0 0 160 600">` +
        `<defs><linearGradient id="g" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="${from}"/><stop offset="1" stop-color="${to}"/></linearGradient></defs>` +
        `<rect width="160" height="600" fill="url(#g)"/>` +
        `<circle cx="80" cy="470" r="56" fill="#ffffff" fill-opacity="0.14"/>` +
        text +
        `<text x="80" y="${180 + lines.length * 40}" text-anchor="middle" font-family="sans-serif" font-size="15" fill="#ffffff" fill-opacity="0.85">${sub}</text>` +
        `</svg>`;
    return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
}

function wingAd(id: string, weight: number, colors: [string, string], lines: string[], sub: string, alt: string, path: string): PublicAd {
    return { id, type: 'WING_BANNER', version: 1, imageUrl: wing(colors[0], colors[1], lines, sub), text: null, linkUrl: `https://example.com/${path}`, altText: alt, weight, deal: null };
}

/** 자리 이미지 — 그라데이션 바탕 + 큰 글 + 작은 글(이미지 안의 색은 소재의 일부라 토큰이 아니다) */
function banner(type: string, from: string, to: string, title: string, sub: string): string {
    const [w, h] = SIZE[type] ?? [1200, 300];
    const big = Math.round(h * (h > 200 ? 0.2 : 0.3));
    const small = Math.round(big * 0.5);
    const svg =
        `<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}">` +
        `<defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="${from}"/><stop offset="1" stop-color="${to}"/></linearGradient></defs>` +
        `<rect width="${w}" height="${h}" fill="url(#g)"/>` +
        `<circle cx="${w * 0.86}" cy="${h * 0.5}" r="${h * 0.42}" fill="#ffffff" fill-opacity="0.12"/>` +
        `<text x="${Math.round(w * 0.06)}" y="${Math.round(h * 0.5)}" font-family="sans-serif" font-size="${big}" font-weight="700" fill="#ffffff">${title}</text>` +
        `<text x="${Math.round(w * 0.06)}" y="${Math.round(h * 0.5 + big * 1.1)}" font-family="sans-serif" font-size="${small}" fill="#ffffff" fill-opacity="0.85">${sub}</text>` +
        `</svg>`;
    return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
}

function bannerAd(id: string, type: PublicAdType, weight: number, colors: [string, string], title: string, sub: string, alt: string, path: string): PublicAd {
    return { id, type, version: 1, imageUrl: banner(type, colors[0], colors[1], title, sub), text: null, linkUrl: `https://example.com/${path}`, altText: alt, weight, deal: null };
}

function textAd(id: string, weight: number, text: string, path: string, advertiserName: string | null = null): PublicAd {
    return { id, type: 'TEXT_LINE', version: 1, imageUrl: null, text, linkUrl: `https://example.com/${path}`, altText: text, weight, deal: null, advertiserName };
}

function dealAd(id: string, type: PublicAdType, weight: number, postId: string, title: string, shopName: string, dealPrice: number, listPrice: number | null, prefixName: string | null): PublicAd {
    return { id, type, version: 1, imageUrl: null, text: null, linkUrl: `/posts/${postId}`, altText: title, weight, deal: { postId, title, shopName, dealPrice, listPrice, thumbnailUrl: null, prefixName } };
}

const ADS: readonly PublicAd[] = [
    bannerAd('ad-w1', 'WIDE_BANNER', 6, ['#2563eb', '#7c3aed'], '로봇 키트 가을 할인전', '서보 · 센서 · 컨트롤러 최대 40%', '로봇 키트 가을 할인전 — 서보·센서·컨트롤러 최대 40% 할인', 'robot-kit-sale'),
    bannerAd('ad-w2', 'WIDE_BANNER', 3, ['#0f766e', '#0ea5e9'], 'AI 입문 강의 수강생 모집', '주말 4주 과정 · 실습 키트 포함', 'AI 입문 강의 수강생 모집 — 주말 4주 과정, 실습 키트 포함', 'ai-class'),
    bannerAd('ad-w3', 'WIDE_BANNER', 1, ['#b45309', '#dc2626'], '드론 레이싱 대회', '11월 23일 · 참가 신청 받는 중', '드론 레이싱 대회 11월 23일 — 참가 신청 받는 중', 'drone-race'),
    bannerAd('ad-s1', 'STRIP_BANNER', 5, ['#4338ca', '#2563eb'], '3D 프린터 필라멘트 1+1', '오늘 자정까지', '3D 프린터 필라멘트 1+1 — 오늘 자정까지', 'filament'),
    bannerAd('ad-s2', 'STRIP_BANNER', 2, ['#15803d', '#65a30d'], '메이커 공간 회원 모집', '첫 달 무료', '메이커 공간 회원 모집 — 첫 달 무료', 'maker-space'),
    bannerAd('ad-q1', 'SQUARE_BANNER', 4, ['#9333ea', '#db2777'], '로봇 청소기', '신제품 체험단', '로봇 청소기 신제품 체험단 모집', 'robot-cleaner'),
    bannerAd('ad-q2', 'SQUARE_BANNER', 4, ['#0369a1', '#0891b2'], '코딩 로봇', '초등 방과후 교재', '초등 방과후 코딩 로봇 교재', 'coding-robot'),
    bannerAd('ad-m1', 'SMALL_BANNER', 5, ['#334155', '#0f172a'], '라즈베리파이 5', '재고 있음', '라즈베리파이 5 — 재고 있음', 'raspberry'),
    bannerAd('ad-m2', 'SMALL_BANNER', 1, ['#c2410c', '#ea580c'], '납땜 인두 세트', '무료 배송', '납땜 인두 세트 — 무료 배송', 'soldering'),
    wingAd('ad-g1', 5, ['#1d4ed8', '#7c3aed'], ['로봇', '키트', '할인전'], '최대 40%', '로봇 키트 할인전 — 최대 40% 할인', 'wing-robot-kit'),
    wingAd('ad-g2', 3, ['#047857', '#0e7490'], ['AI', '입문', '강의'], '주말 4주', 'AI 입문 강의 — 주말 4주 과정', 'wing-ai-class'),
    wingAd('ad-g3', 2, ['#b91c1c', '#c2410c'], ['드론', '레이싱', '대회'], '참가 신청', '드론 레이싱 대회 참가 신청', 'wing-drone'),
    textAd('ad-t1', 8, '[무료 배송] 아두이노 호환 보드 묶음 할인 중', 'arduino', '메이커샵'),
    textAd('ad-t2', 5, '로봇 동아리 단체 구매 견적 바로 받기', 'club-quote', '로보몰'),
    textAd('ad-t3', 5, 'ROS2 입문 온라인 강의 30% 할인 쿠폰', 'ros2', '에듀로봇'),
    textAd('ad-t4', 3, '중고 산업용 로봇팔 매입·판매 상담', 'robot-arm'),
    // 40자를 넘는 문구(서버는 40자까지 받는다) — 화면이 40자로 자르고, 좁은 폭에서는 말줄임으로 본다
    textAd('ad-t5', 2, '스마트팜 자동화 키트 체험단 모집 중 — 지금 신청하면 센서 세트를 함께 보내 드려요', 'smart-farm'),
    textAd('ad-t6', 1, '배터리 셀 전문 쇼핑몰 오픈 기념 적립금', 'battery'),
    dealAd('ad-h1', 'HOTDEAL_TOP', 5, '900001', '로봇 청소기 X9 역대가', '로보몰', 289_000, 459_000, '가전'),
    dealAd('ad-h2', 'HOTDEAL_TOP', 3, '900002', '서보 모터 20개 묶음', '메이커샵', 39_900, 59_000, '부품'),
    dealAd('ad-c1', 'HOTDEAL_CATEGORY', 5, '900003', '말머리 상단 — 코딩 로봇 교구 세트', '에듀로봇', 74_000, 98_000, null),
    dealAd('ad-c2', 'HOTDEAL_CATEGORY', 2, '900004', '말머리 상단 — 드론 배터리 2개', '드론나라', 32_000, null, null),
];

/** 노출·클릭 기록(개발 확인용) */
const impressions: AdImpressionsRequest['items'] = [];
const clicks: { adId: string; version: number; placement: string }[] = [];

/** `GET /api/ads` — 요청한 유형의 후보. 말머리 상단은 고른 말머리를 이름으로 채운다(목업은 말머리를 가리지 않는다) */
export async function mockPublicAds(query: PublicAdsQuery): Promise<PublicAdsResponse> {
    await mockDelay();
    const flags = mockFlags();
    if (flags.has('ad-fail')) throw MOCK_ERRORS.internal();
    if (flags.has('ad-empty')) return { items: [] };
    let items = ADS.filter((ad) => query.types.includes(ad.type));
    if (flags.has('ad-one')) {
        items = query.types.flatMap((type) => {
            const same = items.filter((ad) => ad.type === type).sort((a, b) => b.weight - a.weight);
            return same.slice(0, 1);
        });
    }
    return { items: mockClone(items) };
}

/** `GET /api/ads/placements` — 글 보기 자리(기본 한 줄 광고 · 두 카드 사이 틈) · 날개(기본 양쪽 · 네 화면) */
export async function mockAdPlacements(): Promise<PublicAdPlacements> {
    await mockDelay();
    const flags = mockFlags();
    const square = flags.has('ad-inline-square');
    const wingOff = flags.has('ad-wing-off');
    return {
        postInline: {
            enabled: !flags.has('ad-inline-off'),
            adType: square ? 'SQUARE_BANNER' : flags.has('ad-inline-strip') ? 'STRIP_BANNER' : 'TEXT_LINE',
            position: square ? 'AFTER_BODY' : 'BETWEEN_CARDS',
        },
        wings: { left: !wingOff, right: !wingOff && !flags.has('ad-wing-left'), screens: ['HOME', 'PAGE', 'BOARD', 'POST'] },
        boardList: { enabled: !flags.has('ad-feed-off'), after: feedAfter(flags) },
    };
}

/** `ad-feed-after-N` — 게시판 목록 한 줄 광고 위치(일반 글 N번째 뒤, 기본 5) */
function feedAfter(flags: Set<string>): number {
    for (const flag of flags) {
        const m = /^ad-feed-after-(\d{1,2})$/.exec(flag);
        if (m) return Number(m[1]);
    }
    return 5;
}

/** `POST /api/ads/impressions` */
export async function mockAdImpressions(request: AdImpressionsRequest): Promise<void> {
    impressions.push(...request.items);
    console.debug('[ad-mock] 노출', request.items, `누적 ${impressions.length}`);
}

/** `POST /api/ads/{id}/clicks` */
export function mockAdClick(adId: string, request: AdClickRequest): void {
    clicks.push({ adId, ...request });
    console.debug('[ad-mock] 클릭', { adId, ...request }, `누적 ${clicks.length}`);
}

/** 광고 가젯 꾸미기 — 머리 숨김 · 배너는 카드 없이(평평), 한 줄 광고는 테마 카드 */
function adStyle(type: 'AD' | 'AD_TEXT_LINE', keep: boolean): GadgetStyle {
    return {
        icon: null,
        headerStyle: 'HIDDEN',
        titleSize: 'M',
        titleColor: null,
        subtitle: '',
        cardLook: type === 'AD' ? 'FLAT' : 'DEFAULT',
        cardColor: null,
        mobileFold: 'NONE',
        empty: { keep, message: true, text: '' },
    };
}

function adGadget(id: string, type: 'AD' | 'AD_TEXT_LINE', title: string, config: Record<string, unknown>, keep: boolean): HomeGadget {
    return { id, type, title, desktop: true, mobile: true, empty: false, locked: false, style: adStyle(type, keep), config, more: null, data: null };
}

/** 받은 배치의 오른쪽 열 시작 칸 — 오른쪽 끝에 붙은 가젯들이 가장 많이 쓰는 시작 칸(로보얌 24칸 홈이면 17), 없으면 ⅔ 지점 */
function rightColumnStart(items: readonly { x: number; w: number }[], columns: number): number {
    const counts = new Map<number, number>();
    for (const it of items) if (it.x > 0 && it.x + it.w === columns) counts.set(it.x, (counts.get(it.x) ?? 0) + 1);
    let best = columns - Math.round(columns / 3);
    let most = 0;
    for (const [x, n] of counts) if (n > most || (n === most && x > best)) [best, most] = [x, n];
    return best;
}

/**
 * `?mock=ad-gadgets` — 받은 홈(·가젯 페이지·게시판 레이아웃) 응답에 광고 가젯을 덧붙인다: 맨 위 전체 폭 넓은 배너(나머지는 아래로 민다) ·
 * 맨 아래 **받은 배치의 열 그대로**(왼쪽 본문 열 0~s · 오른쪽 열 s~끝 — `rightColumnStart`, 다른 가젯과 같은 시작선·끝선) — 왼쪽 열에 한 줄 광고 3줄 · 그 아래 띠 배너, 오른쪽 열에 네모 배너 · 그 아래 작은 배너.
 * 높이(h)는 최소 높이라 내용에 가깝게 작게 둔다(내용이 길면 격자가 늘린다). 가젯 ID 는 `mock-ad-*`.
 * @param {HomeGadgetsResponse} res - 읽은 응답(`UtilHomeGadget.sanitize` 뒤).
 * @param {AdPlacement} place - 어디인가(제목에만 쓴다).
 * @returns {HomeGadgetsResponse} 덧붙인 응답(받은 값은 고치지 않는다).
 */
export function mockAdGadgets(res: HomeGadgetsResponse, place: AdPlacement): HomeGadgetsResponse {
    if (res.gadgets.some((g) => g.id.startsWith('mock-ad-'))) return res;
    const keep = mockFlags().has('ad-keep');
    const c = res.layout.columns;
    const s = rightColumnStart(res.layout.items, c);
    const top = 4;
    const shifted = res.layout.items.map((it) => ({ ...it, y: it.y + top }));
    const y = UtilHomeLayout.bottom(shifted);
    const gadgets: HomeGadget[] = [
        adGadget('mock-ad-wide', 'AD', `광고 · 넓은 배너(${place})`, { adType: 'WIDE_BANNER' }, keep),
        ...res.gadgets,
        adGadget('mock-ad-text', 'AD_TEXT_LINE', '광고 · 한 줄 광고', { count: 3 }, keep),
        adGadget('mock-ad-square', 'AD', '광고 · 네모 배너', { adType: 'SQUARE_BANNER' }, keep),
        adGadget('mock-ad-strip', 'AD', '광고 · 띠 배너', { adType: 'STRIP_BANNER' }, keep),
        adGadget('mock-ad-small', 'AD', '광고 · 작은 배너', { adType: 'SMALL_BANNER' }, keep),
    ];
    const items = [
        { gadgetId: 'mock-ad-wide', x: 0, y: 0, w: c, h: top },
        ...shifted,
        { gadgetId: 'mock-ad-text', x: 0, y, w: s, h: 6 },
        { gadgetId: 'mock-ad-square', x: s, y, w: c - s, h: 9 },
        { gadgetId: 'mock-ad-strip', x: 0, y: y + 6, w: s, h: 3 },
        { gadgetId: 'mock-ad-small', x: s, y: y + 9, w: c - s, h: 4 },
    ];
    return { ...res, layout: { columns: c, items }, gadgets };
}
