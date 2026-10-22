package com.community.board.dto;

import java.util.List;
import java.util.Set;

/**
 * 새로 만들 게시판 하나 — 관리자 「만들기」와 설치 프리셋 적용이 같은 길({@code BoardCreator})로 만든다. 검사를 마친 값만 담는다.
 *
 * @param writeMin        글쓰기 최소 자격(null = 기본값 따름)
 * @param commonActive    켜 둘 공통 말머리(QUESTION·REVIEW·CHAT·NOTICE 중). 나머지 공통 말머리는 꺼진 채 만든다
 * @param prefixes        고유 말머리(표시 순서대로)
 */
public record NewBoard(String slug, String name, String nameEn, String description, String kind, String layout, boolean viewToggle, String color,
                       boolean investmentNotice, String writeMin, Set<String> commonActive, List<Prefix> prefixes) {

    /** 고유 말머리 하나 */
    public record Prefix(String name, boolean staffOnly, String color) {
    }
}
