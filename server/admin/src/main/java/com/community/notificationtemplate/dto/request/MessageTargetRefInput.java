package com.community.notificationtemplate.dto.request;

/** 요청 안의 대상 — 종류({@code EVENT|REQUIRED_MAIL}) · 코드 */
public record MessageTargetRefInput(String type, String code) {
}
