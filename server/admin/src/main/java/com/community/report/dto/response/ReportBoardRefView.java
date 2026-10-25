package com.community.report.dto.response;

/** 대상의 게시판 — 지워진 게시판이면 이름 null */
public record ReportBoardRefView(String id, String name) {
}
