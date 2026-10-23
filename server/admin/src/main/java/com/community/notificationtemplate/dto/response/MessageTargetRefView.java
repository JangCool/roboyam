package com.community.notificationtemplate.dto.response;

/** 문구가 가리키는 대상 — 종류({@code EVENT|REQUIRED_MAIL}) · 코드 · 받는 사람({@code MEMBER|OPERATOR}) */
public record MessageTargetRefView(String type, String code, String audience) {
}
