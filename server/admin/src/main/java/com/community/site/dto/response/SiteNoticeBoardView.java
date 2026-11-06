package com.community.site.dto.response;

/**
 * 공지 띠가 읽는 공지 게시판(slug {@code notice}) — 관리자가 저장한 게시판 이름 그대로({@code nameEn} 은 없으면 null, 어느 언어를 보일지는 화면이 정한다).
 */
public record SiteNoticeBoardView(String slug, String name, String nameEn) {
}
