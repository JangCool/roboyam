package com.community.nicknameimage.dto.response;

import java.util.List;

/** 신청 조건 — {@code mode} 는 관리자 설정 방식(GRADE · CUSTOM), {@code items} 는 켜진 조건만(차례는 {@link NicknameImageRequirementItemView}) */
public record NicknameImageRequirementsView(String mode, List<NicknameImageRequirementItemView> items) {
}
