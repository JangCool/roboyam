package com.community.sanction.dto.response;

/** 해제한 사람 — {@code type} MANAGER(그 게시판 매니저 — 닉네임) · OPERATOR(운영자 — 이름). 지워졌으면 이름 null */
public record BoardSanctionLiftActorView(String type, String id, String name) {
}
