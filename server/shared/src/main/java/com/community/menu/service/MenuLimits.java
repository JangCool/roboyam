package com.community.menu.service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 공개 사이트 메뉴의 한도·값 목록 — <b>한 곳</b>. admin-web {@code UtilMenu.LIMITS}·{@code SET_LIMITS}·{@code PLACEMENTS}·{@code AUDIENCES} 와 같은 값이다(어긋나면 화면 검사와 서버 판정이 달라진다).
 */
public final class MenuLimits {

    public static final int FOLDER_DEPTH = 3;
    public static final int ITEM_DEPTH = 4;
    public static final int NODES = 80;
    public static final int MOBILE_TABS = 10;
    public static final int LABEL_MAX = 20;
    public static final int NAME_EN_MAX = 40;
    public static final int PATH_MAX = 500;
    public static final int HREF_MAX = 2000;
    /** 모르는 유형의 targetId·targetKey 길이(표의 칸 크기) */
    public static final int TARGET_VALUE_MAX = 64;

    public static final int SET_KEY_MIN = 2;
    public static final int SET_KEY_MAX = 30;
    public static final int SET_NAME_MAX = 20;
    public static final int SET_NAME_EN_MAX = 40;
    public static final int SET_DESCRIPTION_MAX = 100;
    public static final Pattern SET_KEY = Pattern.compile("^[a-z][a-z0-9-]*$");

    /** 연결 유형 코드 모양(모르는 유형을 보존할 때도 이 모양만 받는다) */
    public static final Pattern TARGET_TYPE = Pattern.compile("^[A-Z][A-Z0-9_]{0,29}$");

    public static final String SIDE = "SIDE";
    public static final List<String> PLACEMENTS = List.of(SIDE, "MYPAGE", "GROUP_BUY", "CUSTOM");
    public static final List<String> AUDIENCES = List.of("ALL", "GUEST", "MEMBER", "GRADE_MIN", "STAFF");

    /** 상단 가로 메뉴 첫 줄의 구분선 — 없음 · 앞 · 뒤 */
    public static final List<String> DIVIDERS = List.of("NONE", "BEFORE", "AFTER");

    /** 연결이 있는 폴더의 상단 탭 동작 — 이동하고 하위 메뉴도 열기(기본) · 이동만 */
    public static final List<String> LINK_CLICKS = List.of("NAVIGATE_OPEN", "NAVIGATE");

    /** 항목 설명(메뉴 설명 줄) 글자 수 */
    public static final int DESCRIPTION_MAX = 80;

    /** 게시판이 만들어질 때 자동 추가 폴더를 찾는 그룹 */
    public static final String MAIN_SET = "main";

    private MenuLimits() {
    }

    public static boolean isSide(String placement) {
        return SIDE.equals(placement);
    }

    /** 로그인한 사람에게만 보이는 보이는 대상인가(로그인 필요 앱 화면에 쓸 수 있는 값) */
    public static boolean isLoginAudience(String audience) {
        return "MEMBER".equals(audience) || "GRADE_MIN".equals(audience) || "STAFF".equals(audience);
    }
}
