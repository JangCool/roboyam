package com.community.post.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 소모임 글의 소모임 칸(community {@code GroupRef}·{@code GroupPostInfo} — api-groups.md 8절). 일반 글이면 응답의 {@code group} 이 null 이다.
 * 글 줄(소모임 글 탭)은 {@code id}·{@code name}·{@code visibility}·{@code dissolved} 만, 글 상세는 두 권한 칸까지 싣는다(줄에서는 칸이 빠진다).
 *
 * @param visibility   PRIVATE(멤버만) · PUBLIC(공개로 전환됨)
 * @param dissolved    해산한 소모임의 공개 글(소모임 연결은 남는다 — 화면은 링크 없이 「해산한 소모임」)
 * @param canPublish   (상세만) 내가 작성자이고 지금도 멤버이며 쓸 수 있는 상태이고 아직 비공개
 * @param canUnpublish (상세만) 공개 글이고 내가 작성자 · 모임장 · 되돌리기를 맡은 매니저({@code UNPUBLISH}), 소모임이 열려 있다
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PostGroupView(String id, String name, String visibility, boolean dissolved, Boolean canPublish, Boolean canUnpublish) {

    public static final String PUBLIC = "PUBLIC";
    public static final String PRIVATE = "PRIVATE";

    /** 글 줄용(권한 칸 없음) */
    public static PostGroupView ref(String id, String name, boolean publicPost, boolean dissolved) {
        return new PostGroupView(id, name, publicPost ? PUBLIC : PRIVATE, dissolved, null, null);
    }
}
