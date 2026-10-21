package com.community.installation.dto.response;

/** 설치 상태. 이 밖의 정보는 절대 담지 않는다 */
public record InstallStatusResponse(boolean installed, boolean authorized) {
}
