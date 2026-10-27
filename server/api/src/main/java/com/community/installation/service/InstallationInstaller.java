package com.community.installation.service;

import com.community.installation.dto.InstallCommand;
import com.community.installation.dto.InstallationCompleted;
import com.community.installation.entity.InstallationEntity;
import com.community.installation.exception.InstallationAlreadyInstalledException;
import com.community.installation.repository.master.InstallationRepositoryQDSL;
import com.community.mail.service.MailSettingsService;
import com.community.operator.entity.OperatorEntity;
import com.community.operator.repository.master.OperatorRepository;
import com.community.site.service.SiteSettingsService;
import com.community.social.service.SocialSettingsService;
import com.community.user.exception.UserEmailTakenException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 설치를 <b>한 트랜잭션</b>으로 실행한다 ({@code complete}): 루트 운영자(최고 관리자, {@code operators}) + 사이트 설정 + 메일 설정 + 소셜 키 + 완료 표시.
 * 루트 계정은 회원({@code users})이 아니라 운영자다 — 공개 사이트 회원·프로필을 만들지 않는다(docs/domain/operator-permissions.md 확정 규칙 1).
 *
 * <h2>실패하면 아무것도 남지 않는다</h2>
 * 어느 단계에서 던지든 트랜잭션 전체가 롤백된다 — 루트 운영자·사이트·메일·소셜 설정이 하나도 생기지 않고 {@code installation} 은 {@code PENDING} 그대로다.
 * 메모리 상태(설치 상태 캐시)는 <b>커밋된 뒤에만</b> 바뀐다 — 완료 알림({@link InstallationCompleted})은 {@code AFTER_COMMIT} 리스너만 듣기 때문에
 * 롤백되면 아무도 받지 못한다.
 *
 * <h2>동시에 두 번 호출돼도 한 번만</h2>
 * 시작하자마자 {@code installation} 행을 쓰기 잠금으로 잡는다 — 두 번째 호출은 첫 번째가 끝날 때까지 기다렸다가 완료된 상태를 보고 {@code 409 ALREADY_INSTALLED}.
 *
 * <p>입력 검사는 이 클래스 밖({@link InstallationService})에서 끝낸 값({@link InstallCommand})만 받는다. 이 클래스는 <b>이미 있는 운영자 이메일</b>(DB 가 아는 것)만 다시 본다.
 * 입력의 닉네임은 루트 운영자의 이름({@code operator_name})이 되고 유일하지 않아도 된다.
 * 기본 게시판 구성은 고른 아이디를 {@code installation.board_preset} 에 적고 같은 트랜잭션에서 그 구성으로 일반 게시판 · 소모임 게시판과 분류 · 공개 메뉴 {@code main} 트리를 만든다
 * ({@link BoardPresetApplier#applyAtInstall}).
 * 로그에는 운영자 ID 만 남기고 이메일·비밀번호·시크릿을 남기지 않는다.
 */
@Service
public class InstallationInstaller {

    private static final Logger log = LoggerFactory.getLogger(InstallationInstaller.class);

    private final InstallationRepositoryQDSL installation;
    private final OperatorRepository operators;
    private final PasswordEncoder encoder;
    private final SnowflakeIdGenerator ids;
    private final SiteSettingsService siteSettings;
    private final MailSettingsService mailSettings;
    private final SocialSettingsService socialSettings;
    private final ApplicationEventPublisher events;
    private final BoardPresetApplier boardPresets;

    public InstallationInstaller(InstallationRepositoryQDSL installation, OperatorRepository operators, PasswordEncoder encoder,
                                 SnowflakeIdGenerator ids, SiteSettingsService siteSettings, MailSettingsService mailSettings,
                                 SocialSettingsService socialSettings, ApplicationEventPublisher events, BoardPresetApplier boardPresets) {
        this.installation = installation;
        this.operators = operators;
        this.encoder = encoder;
        this.ids = ids;
        this.siteSettings = siteSettings;
        this.mailSettings = mailSettings;
        this.socialSettings = socialSettings;
        this.events = events;
        this.boardPresets = boardPresets;
    }

    /**
     * 설치를 실행한다. 던지면 아무것도 남지 않는다.
     *
     * @throws InstallationAlreadyInstalledException 이미 설치가 끝났을 때(동시에 호출한 쪽이 먼저 끝낸 경우 포함)
     * @throws UserEmailTakenException               이미 그 이메일의 운영자가 있을 때(설치 전 DB 에는 있을 수 없는 비정상 상황)
     */
    @Transactional
    public void install(InstallCommand command) {
        InstallationEntity row = installation.lockRow()
                .orElseThrow(() -> new IllegalStateException("installation 행이 없다 — V4__installation.sql 이 적용됐는지 확인하세요"));
        if (row.isCompleted()) {
            throw new InstallationAlreadyInstalledException(); // 잠금을 잡고 보니 이미 설치가 끝나 있었다
        }

        if (operators.existsByEmail(command.email())) {
            throw new UserEmailTakenException();
        }

        Instant now = Instant.now();
        long rootId = ids.nextId();
        try {
            // 바로 내보낸다(flush) — 동시에 같은 값이 들어와 유니크 인덱스에 걸리면 여기서 알아내고, 아래 설정 행들이 참조할 운영자가 DB 에 먼저 있게 한다
            operators.persistAndFlush(OperatorEntity.createRoot(rootId, command.email(), command.nickname(), encoder.encode(command.password()), now));
        } catch (DataIntegrityViolationException e) {
            throw translateDuplicate(e);
        }

        siteSettings.create(command.site(), rootId, now);
        if (command.mail() != null) {
            mailSettings.create(command.mail(), rootId, now);
        }
        for (InstallCommand.Social entry : command.social()) {
            socialSettings.create(entry.provider(), entry.key(), rootId, now);
        }

        if (installation.markCompleted(rootId, now, command.boardPresetId()) == 0) {
            throw new InstallationAlreadyInstalledException(); // 완료를 표시하지 못했다(이미 완료)
        }
        // 고른 게시판 구성으로 기본 게시판(+말머리) · 소모임 게시판과 분류 · 메뉴 트리를 만든다 — 같은 트랜잭션이라 설치가 롤백되면 모두 없다
        boardPresets.applyAtInstall(command.boardPresetId(), rootId, now);
        installation.markBoardPresetApplied(now);
        events.publishEvent(new InstallationCompleted(rootId));
        log.info("설치를 실행했다 rootOperatorId={} 메일={} 소셜={}건 게시판구성={}", rootId, command.mail() != null ? "설정" : "건너뜀",
                command.social().size(), command.boardPresetId());
    }

    /** 동시에 같은 이메일로 들어가 유니크 인덱스에 걸린 경우 — 이메일이 겹쳤음을 알려 준다. 그 밖의 무결성 오류는 그대로 던진다 */
    private static RuntimeException translateDuplicate(DataIntegrityViolationException e) {
        String message = String.valueOf(e.getMostSpecificCause().getMessage());
        if (message.contains("ux_operators_email")) {
            return new UserEmailTakenException();
        }
        return e;
    }
}
