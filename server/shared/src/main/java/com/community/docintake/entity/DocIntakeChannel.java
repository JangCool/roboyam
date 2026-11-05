package com.community.docintake.entity;

/** 종이 접수 방식 — 우편 · 직접 전달 · 팩스(종이로 받은 것). 파일로 받은 팩스는 접수 기록이 아니라 파일 줄의 {@link DocFileChannel#FAX} */
public enum DocIntakeChannel {
    MAIL, VISIT, FAX
}
