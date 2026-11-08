package com.community.installation.dto.response;

import java.util.List;

/**
 * 설치 화면에 보이는 기본 게시판 구성(프리셋) 한 칸.
 *
 * @param demoAvailable 이 구성으로 설치할 때 「데모 데이터 포함」을 고를 수 있는가 — 구성이 데모를 지원하고(구조 스냅숏이 있는 구성) 이 서버에 데모 팩이 들어 있을 때
 */
public record BoardPresetItem(String id, String name, String description, List<BoardPresetBoardItem> boards, boolean demoAvailable) {
}
