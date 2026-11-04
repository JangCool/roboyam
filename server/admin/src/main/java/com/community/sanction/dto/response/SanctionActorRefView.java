package com.community.sanction.dto.response;

/** 부여·해제·답변한 운영자 — 이름이 없으면 null */
public record SanctionActorRefView(String id, String name) {
}
