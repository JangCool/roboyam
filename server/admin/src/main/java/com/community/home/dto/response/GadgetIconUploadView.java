package com.community.home.dto.response;

/**
 * 가젯 제목 아이콘 올리기 결과({@code POST /api/admin/home-layouts/icons} 201) — 가젯 꾸미기 {@code style.iconUpload} 에 그대로 넣는다(저장 때 서버가 파일을 다시 찾아 주소·SVG 여부를 채운다).
 * 올리기만 하고 가젯에 쓰지 않은 파일은 「올리다 만 파일 정리」가 지운다.
 *
 * @param fileId 파일 번호(문자열)
 * @param url    공개 주소(관리자 미리보기가 저장 전에 그린다)
 * @param svg    SVG 인가(그러면 색을 칠할 수 있다 — PNG·WEBP 는 원래 색)
 */
public record GadgetIconUploadView(String fileId, String url, boolean svg) {
}
