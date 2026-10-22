package com.community.menu.target;

/**
 * 항목 상태 — 저장은 되지만 방문자에게 안 보이는 이유(관리자 화면이 줄에 보인다). admin-web {@code MenuItemStatus} 와 같은 값이다.
 */
public enum MenuTargetStatus {
    OK,
    /** 꺼진 게시판 */
    BOARD_INACTIVE,
    /** 공동구매 판매 스위치가 꺼졌다({@code MenuSales}) */
    SALES_OFF,
    /** 연결한 대상이 없다(지워진 게시판·레지스트리에 없는 앱 화면 키) */
    TARGET_MISSING,
    /** 서버가 모르는 연결 유형 — 공개 응답에서 빠지고 저장 때는 받은 그대로 보존한다 */
    UNKNOWN_TYPE
}
