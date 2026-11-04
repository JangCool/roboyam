package com.community.dashboard.service.gadget;

import com.community.dashboard.dto.DashboardGadgetQuery;
import com.community.dashboard.dto.DashboardGadgetType;
import com.community.dashboard.dto.response.gadget.DashboardGadgetData;

/**
 * 가젯 종류 하나의 데이터를 만드는 빈 — 종류마다 하나({@code DashboardGadgetService} 가 {@link #type()} 으로 모은다). 종류 권한(그중 하나라도)은 서비스가 먼저 거르고,
 * 종류 안의 항목별 권한(예: 오늘 일정의 광고·포인트 상점·배치)은 제공자가 {@link DashboardGadgetQuery#actor()} 로 거른다.
 *
 * <p><b>제공자가 없는 종류</b>는 「서버가 아직 줄 수 없음」으로 null + {@code unavailable} 이 된다 — 지금은 제재뿐이다(방문 현황 · 인기 페이지 · 유입 경로 · 알림 상태 · 글 활동 · 가입 추이 · 게시판 순위는
 * 2026-11-04 통계 수집 집계 표로 붙였다 — 수집이 꺼진 종류는 null + {@code disabled}, site-analytics-contract). 예전 목록:
 * 제재). 회원 현황({@code MEMBER_STATS})은 휴면을 빼고 붙였다(2026-11-03). 커머스 종류(GROUPBUY_PROGRESS · GROUPBUY_QNA · SALES_SUMMARY · ORDER_TREND · RECONCILE)는 2단계에서 붙였다({@code DashboardCommerceStore}).
 *
 * <p>트랜잭션은 서비스가 연다(읽기 전용 — 캐시에 없을 때만). 결과는 서비스가 종류별 수명만큼 캐시한다({@code DashboardGadgetCache}) — 운영자 권한으로 항목을 거르는 제공자를
 * 새로 만들면 캐시 키에 권한 조합이 들어가도록 {@code DashboardGadgetCache#scoped} 에 더하고, 요청 언어의 문구를 담아도 된다(키에 언어가 있다).
 * 결과 레코드는 바꾸지 않는 값이어야 한다(여러 요청이 함께 쓴다). 서버 부담 원칙(admin-dashboard-contract)을 지킨다 — 숫자는 인덱스로 센 COUNT, 목록은 최대 10행, 추이는 일별·시간별 집계 표만.
 */
public interface DashboardGadgetProvider {

    DashboardGadgetType type();

    DashboardGadgetData load(DashboardGadgetQuery query);
}
