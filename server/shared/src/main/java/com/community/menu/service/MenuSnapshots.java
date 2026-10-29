package com.community.menu.service;

import com.community.board.entity.BoardEntity;
import com.community.board.repository.master.BoardRepository;
import com.community.board.service.BoardFields;
import com.community.grade.entity.GradeEntity;
import com.community.grade.repository.master.GradeRepositoryQDSL;
import com.community.home.entity.GadgetPageEntity;
import com.community.home.repository.master.GadgetPageRepository;
import com.community.menu.dto.MenuBoardRef;
import com.community.menu.dto.MenuPageRef;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 메뉴 검사·해석의 재료를 한 번에 읽는다 — 게시판(꺼진 것 포함, 게시판 순서)·등급(서열 순서)·가젯 페이지(만든 차례). 부르는 쪽의 트랜잭션 안에서 쓴다 */
@Component
public class MenuSnapshots {

    private final BoardRepository boards;
    private final GradeRepositoryQDSL grades;
    private final GadgetPageRepository pages;

    public MenuSnapshots(BoardRepository boards, GradeRepositoryQDSL grades, GadgetPageRepository pages) {
        this.boards = boards;
        this.grades = grades;
        this.pages = pages;
    }

    /** 가젯 페이지 ID → 요약(만든 차례) — 연결 유형 {@code GADGET_PAGE} */
    public Map<Long, MenuPageRef> pages() {
        Map<Long, MenuPageRef> out = new LinkedHashMap<>();
        for (GadgetPageEntity page : pages.findAllByOrderByIdAsc()) {
            out.put(page.getId(), new MenuPageRef(page.getId(), page.getPageKey(), page.getName(), page.getNameEn()));
        }
        return out;
    }

    /**
     * 게시판 ID → 요약(게시판 순서). <b>소모임 게시판(종류 {@code GROUP})은 뺀다</b> — 메뉴에 두지 않는 그릇이라(docs/features/group-contract.md)
     * 관리자 메뉴 편집의 고르기 목록에 없고, 항목으로 보내면 없는 대상과 같은 400({@code menu.target.missing})이며, 공개 메뉴에도 나오지 않는다.
     */
    public Map<Long, MenuBoardRef> boards() {
        Map<Long, MenuBoardRef> out = new LinkedHashMap<>();
        for (BoardEntity board : boards.findAllByOrderBySortOrderAscIdAsc()) {
            if (BoardFields.KIND_GROUP.equals(board.getKind())) {
                continue;
            }
            out.put(board.getId(), new MenuBoardRef(board.getId(), board.getSlug(), board.getName(), board.getNameEn(), board.getColor(),
                    board.isInvestmentNotice(), board.isActive()));
        }
        return out;
    }

    /** 등급(서열 순서) */
    public List<GradeEntity> grades() {
        return grades.findAllOrdered();
    }

    /** 등급 키 → 서열 */
    public static Map<String, Integer> levels(List<GradeEntity> grades) {
        Map<String, Integer> out = new LinkedHashMap<>();
        grades.forEach(grade -> out.put(grade.getGradeKey(), grade.getLevel()));
        return out;
    }
}
