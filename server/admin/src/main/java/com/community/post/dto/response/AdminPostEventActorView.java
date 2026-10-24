package com.community.post.dto.response;

/**
 * 조치한 사람.
 *
 * @param kind OPERATOR · SYSTEM(신고 자동 숨김)
 * @param id   운영자 ID(SYSTEM 은 null)
 * @param name 운영자의 지금 이름(SYSTEM·지워진 운영자는 null — 화면이 대신 그린다)
 */
public record AdminPostEventActorView(String kind, String id, String name) {
}
