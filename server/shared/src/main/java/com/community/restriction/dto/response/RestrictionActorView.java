package com.community.restriction.dto.response;

/** 기능 제한을 부여·해제한 운영자 — ID 는 문자열(Snowflake), 운영자가 지워졌으면 이름은 null */
public record RestrictionActorView(String id, String name) {
}
