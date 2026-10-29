package com.community.home.service;

import com.community.home.dto.HomeGadgetSpec;
import com.community.home.dto.HomeGridLayout;
import com.community.home.dto.HomeLayoutItem;
import java.util.List;

/**
 * 테마별 게시판 레이아웃 기본 배치(board-layout-contract 「필드·검증」) — 저장한 적 없는 테마는 관리자·공개 모두 이것을 쓴다. 본문만 전체 폭(칸 수는 그 테마의 홈 기본 배치와 같다 —
 * 다음식 12칸, 나머지 24칸), 적용 화면 둘 다 켬 → 공개 게시판 화면이 레이아웃 없이 보던 것과 같다.
 * 본문 가젯 ID 는 시드 {@code seed-1} — 저장 요청에 이 ID 가 오면 본문을 새로 만든다. 제목은 고정이다(공개 화면에는 보이지 않는 편집기 안내).
 */
public final class BoardLayoutDefaults {

    /** 본문 시드 ID */
    public static final String BODY_ID = HomeLayoutDefaults.SEED_PREFIX + "1";
    /** 본문 제목(고정 — 저장 요청의 제목은 쓰지 않는다) */
    public static final String BODY_TITLE = "게시판 본문";

    private BoardLayoutDefaults() {
    }

    /** 적용 화면 */
    public record Screens(boolean list, boolean post) {
    }

    /** 기본 배치 + 적용 화면 */
    public record Board(Screens screens, HomeLayoutDefaults.Home home) {
    }

    public static Board of(String theme) {
        int columns = HomeLayoutDefaults.of(theme).layout().columns();
        HomeGadgetSpec body = body(BODY_ID);
        HomeGridLayout layout = new HomeGridLayout(columns, List.of(new HomeLayoutItem(BODY_ID, 0, 0, columns, HomeGadgetKinds.BOARD_BODY_MIN_H)));
        return new Board(new Screens(true, true), new HomeLayoutDefaults.Home(layout, List.of(body)));
    }

    /** 본문 가젯 값 — 제목 고정 · PC·모바일 보임 · 꾸미기·설정 없음 */
    public static HomeGadgetSpec body(String id) {
        return new HomeGadgetSpec(id, HomeGadgetKinds.BOARD_BODY, BODY_TITLE, true, true, HomeLayoutJson.object(), HomeLayoutJson.object(), null);
    }
}
