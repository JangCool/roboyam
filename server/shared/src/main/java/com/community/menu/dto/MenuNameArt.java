package com.community.menu.dto;

/**
 * 메뉴 항목 이름 그림(V258 {@code menu_nodes.name_art}) — 이름 모양 {@code LOGO} 일 때 이름 자리에 그리는 SVG 채움 패스. 관리자 화면이 개구체(Gaegu 700)로 이름을 굳혀 보내고
 * 서버는 모양만 본다({@code MenuRules#nameArtProblems}). 관리자 트리 요청·응답과 공개 메뉴 항목이 같은 모양이다(docs/features/achu-page-contract.md 「메뉴 이름 그림」).
 *
 * @param d  채움 패스 — (0,0)~(w,h) 안, 그리기 명령·숫자·공백·쉼표·마침표·빼기만, 60000자 이하
 * @param w  viewBox 너비(0 초과 10000 이하)
 * @param h  viewBox 높이(0 초과 10000 이하)
 * @param em 그릴 높이 — 글자 크기 배수(0.3~2)
 * @param sw 같은 색 테두리 두께(viewBox 단위 0~10). 없으면 null(테두리 없음)
 */
public record MenuNameArt(String d, Double w, Double h, Double em, Double sw) {
}
