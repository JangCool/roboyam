package com.community.installation.dto.response;

import java.util.List;

/** 설치 화면에 보이는 기본 게시판 구성(프리셋) 한 칸 */
public record BoardPresetItem(String id, String name, String description, List<BoardPresetBoardItem> boards) {
}
