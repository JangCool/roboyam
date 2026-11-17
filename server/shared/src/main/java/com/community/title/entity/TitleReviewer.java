package com.community.title.entity;

/** 신청 칭호를 심사하는 사람({@code titles.reviewer}) — 운영자만 · 운영자 + 연결 게시판 매니저 */
public enum TitleReviewer {
    OPERATOR,
    OPERATOR_AND_BOARD_MANAGER
}
