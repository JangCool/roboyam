package com.community.installation.dto.request;

import com.community.mail.dto.MailInput;
import com.community.site.dto.SiteInput;

/**
 * 설치 실행 요청. {@code mail}·{@code social} 이 null 이면 그 단계를 건너뛴 것이다. 비밀번호·시크릿은 각 입력 레코드의 {@code toString} 이 감춘다.
 *
 * @param boardPreset 기본 게시판 구성의 아이디({@code NEUTRAL} · {@code ROBOYAM} …). 생략하면 기본(중립) 구성이다 — 목록은 {@code GET …/board-presets}.
 *                    옛 아이디 {@code BOTBI} 도 {@code ROBOYAM} 으로 받는다
 */
public record CompleteInstallationRequest(InstallRootInput root, SiteInput site, MailInput mail, InstallSocialInput social, String boardPreset) {
}
