package com.community.installation.dto.response;

import java.util.List;

/** 설치 화면에 보이는 게시판 한 칸 — 이름과 말머리 이름들(순서대로)뿐이다 */
public record BoardPresetBoardItem(String name, List<String> prefixes) {
}
