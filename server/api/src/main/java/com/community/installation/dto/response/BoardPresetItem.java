package com.community.installation.dto.response;

import java.util.List;

/**
 * 설치 화면에 보이는 기본 게시판 구성(프리셋) 한 칸.
 *
 * @param siteName      이 구성을 고르면 넣을 사이트 이름(없으면 null) — 설치 화면이 사이트 이름이 처음 값일 때 미리 채운다
 * @param siteTagline   이 구성을 고르면 넣을 한 줄 소개(없으면 null) — 설치 화면이 소개가 비었을 때 미리 채운다
 * @param demoAvailable 이 구성으로 설치할 때 「데모 데이터 포함」을 고를 수 있는가 — 구성이 데모를 지원하고(구조 스냅숏이 있는 구성) 이 서버에 데모 팩이 들어 있을 때
 */
public record BoardPresetItem(String id, String name, String description, List<BoardPresetBoardItem> boards, String siteName, String siteTagline, boolean demoAvailable) {
}
