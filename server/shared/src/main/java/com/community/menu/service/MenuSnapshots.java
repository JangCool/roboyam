package com.community.menu.service;

import com.community.board.entity.BoardEntity;
import com.community.board.repository.master.BoardRepository;
import com.community.grade.entity.GradeEntity;
import com.community.grade.repository.master.GradeRepositoryQDSL;
import com.community.menu.dto.MenuBoardRef;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 메뉴 검사·해석의 재료를 한 번에 읽는다 — 게시판(꺼진 것 포함, 게시판 순서)·등급(서열 순서). 부르는 쪽의 트랜잭션 안에서 쓴다 */
@Component
public class MenuSnapshots {

    private final BoardRepository boards;
    private final GradeRepositoryQDSL grades;

    public MenuSnapshots(BoardRepository boards, GradeRepositoryQDSL grades) {
        this.boards = boards;
        this.grades = grades;
    }

    /** 게시판 ID → 요약(게시판 순서) */
    public Map<Long, MenuBoardRef> boards() {
        Map<Long, MenuBoardRef> out = new LinkedHashMap<>();
        for (BoardEntity board : boards.findAllByOrderBySortOrderAscIdAsc()) {
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
