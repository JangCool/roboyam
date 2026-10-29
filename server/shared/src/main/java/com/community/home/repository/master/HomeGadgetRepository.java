package com.community.home.repository.master;

import com.community.home.entity.HomeGadgetEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import java.util.List;

/**
 * 홈·가젯 페이지·게시판 레이아웃 가젯({@code home_gadgets}) — 홈 가젯은 테마에, 가젯 페이지 가젯({@code page_id})은 페이지 · 테마에,
 * 게시판 레이아웃 가젯({@code board_layout}, V110)은 테마에 딸린다.
 */
public interface HomeGadgetRepository extends RoboyamRepository<HomeGadgetEntity, Long> {

    /** 그 테마의 홈 가젯(만든 차례) — 가젯 페이지·게시판 레이아웃의 가젯은 빠진다 */
    List<HomeGadgetEntity> findByThemeAndPageIdIsNullAndBoardLayoutFalseOrderByIdAsc(String theme);

    /** 그 페이지 · 테마의 가젯(만든 차례) */
    List<HomeGadgetEntity> findByPageIdAndThemeOrderByIdAsc(Long pageId, String theme);

    /** 그 테마의 게시판 레이아웃 가젯(만든 차례) */
    List<HomeGadgetEntity> findByThemeAndBoardLayoutTrueOrderByIdAsc(String theme);
}
