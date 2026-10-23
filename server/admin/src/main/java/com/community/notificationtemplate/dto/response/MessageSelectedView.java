package com.community.notificationtemplate.dto.response;

/** 언어별로 쓰는 문구(이름과 함께) */
public record MessageSelectedView(String id, String name, boolean builtin) {
}
