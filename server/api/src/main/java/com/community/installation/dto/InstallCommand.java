package com.community.installation.dto;

import com.community.mail.dto.MailValues;
import com.community.site.dto.SiteValues;
import com.community.social.dto.SocialKeyValues;
import com.community.social.entity.SocialProvider;
import java.util.List;

/**
 * 검사를 마친 설치 입력 — 설치 트랜잭션이 이것만 받는다. {@link #toString()} 은 비밀번호를 감춘다.
 *
 * @param email         소문자로 정규화한 루트 이메일
 * @param site          슬로건이 비어 있었고 프리셋이 슬로건을 정하면 그 값이 이미 들어 있다
 * @param mail          건너뛰었으면 null
 * @param boardPresetId 고른 기본 게시판 구성의 아이디 — 요청이 고르지 않았으면 기본 프리셋(항상 값이 있다)
 */
public record InstallCommand(String email, String nickname, String password, SiteValues site, MailValues mail, List<Social> social,
                             String boardPresetId) {

    /** 검사를 마친 소셜 키 한 건 */
    public record Social(SocialProvider provider, SocialKeyValues key) {
    }

    @Override
    public String toString() {
        return "InstallCommand[nickname=" + nickname + ", site=" + site + ", mail=" + mail + ", social=" + social.size() + "건, boardPreset="
                + boardPresetId + "]";
    }
}
