package com.community.dashboard.dto.response.gadget;

import java.util.List;

/** {@code NICKNAME_IMAGES} — 닉네임 이미지 심사 대기 건수 + 오래된 차례 6장(움직이는 이미지는 정지 화면) */
public record DashboardNicknameImagesView(long pending, List<Recent> recent) implements DashboardGadgetData {

    public record Recent(String id, String nickname, String imageUrl) {
    }
}
