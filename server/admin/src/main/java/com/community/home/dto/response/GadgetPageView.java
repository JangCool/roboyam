package com.community.home.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * 가젯 페이지 한 개 — admin-web {@code GadgetPage}.
 *
 * @param folderId     기준 폴더 ID — 없으면 null
 * @param folderName   기준 폴더 이름 — 폴더가 없거나 지워졌으면 null
 * @param folderBoards 기준 폴더의 게시판(하위 폴더까지, 메뉴 차례 — 꺼진 것 포함)
 * @param menuLinks    이 페이지를 「누르면 가는 곳」으로 쓰는 메뉴 노드 수
 * @param updatedBy    마지막으로 고친 운영자 이름 — 못 찾으면 null
 */
public record GadgetPageView(String id, String key, String name, String nameEn, String description, String folderId, String folderName,
                             List<Board> folderBoards, boolean writeButton, long menuLinks, Instant updatedAt, String updatedBy) {

    /** @param layout 게시판 목록 모양 {@code LIST}·{@code CARD}·{@code GALLERY} */
    public record Board(String slug, String name, String nameEn, String layout) {
    }
}
