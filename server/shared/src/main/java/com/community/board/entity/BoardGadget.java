package com.community.board.entity;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 홈 가젯 — 게시판 설정의 「가젯 노출 금지」({@code boards.gadget_exclusions}, V69)에 고를 수 있는 값. 고른 가젯에는 그 게시판 글이 나오지 않는다.
 * 목록의 원천은 이 enum 이고 표시 이름은 메시지 번들 {@code board.gadget.<이름>} 이다(관리자 게시판 설정 응답의 {@code gadgets}).
 *
 * <p>홈 가젯이 새로 생기면 여기에 값을 더한다 — 함께: 번들 {@code board.gadget.<이름>} 두 언어 + 새 마이그레이션으로 {@code ck_boards_gadget_exclusions} 의 값 목록을 넓힌다
 * + 그 가젯의 질의에서 이 값을 고른 게시판을 뺀다. 통합 검색은 가젯이 아니다(게시판 정책 칸 {@code searchable} 이 따로 있다).
 */
public enum BoardGadget {
    /** 인기글 — DB 배치 popular-rank(R__batch_24_popular_rank.sql) 후보와 {@code GET /api/home/popular} 읽기 */
    POPULAR,
    /** 최신글 — {@code GET /api/home/latest} */
    LATEST;

    /** 저장된 이름들을 enum 순서의 목록으로(중복·모르는 이름은 버린다) */
    public static List<BoardGadget> parse(String[] names) {
        Set<BoardGadget> found = EnumSet.noneOf(BoardGadget.class);
        if (names != null) {
            for (String name : names) {
                find(name).ifPresent(found::add);
            }
        }
        return List.copyOf(found);
    }

    /** 이름 하나 — 모르면 비었다 */
    public static Optional<BoardGadget> find(String name) {
        if (name == null) {
            return Optional.empty();
        }
        for (BoardGadget gadget : values()) {
            if (gadget.name().equals(name)) {
                return Optional.of(gadget);
            }
        }
        return Optional.empty();
    }

    /** 저장할 모양 — enum 순서, 중복 없음 */
    public static String[] toNames(Collection<BoardGadget> gadgets) {
        Set<BoardGadget> sorted = gadgets.isEmpty() ? EnumSet.noneOf(BoardGadget.class) : EnumSet.copyOf(gadgets);
        return sorted.stream().map(Enum::name).toArray(String[]::new);
    }
}
