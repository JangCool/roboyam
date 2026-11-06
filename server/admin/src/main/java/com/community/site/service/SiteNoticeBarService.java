package com.community.site.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.notification.service.AdminNotices;
import com.community.permission.dto.AdminActor;
import com.community.site.dto.SiteNoticeLine;
import com.community.site.dto.request.SiteNoticeBarRequest;
import com.community.site.dto.response.SiteNoticeBarSettingsResponse;
import com.community.site.dto.response.SiteNoticeBoardView;
import com.community.site.entity.SiteSettingEntity;
import com.community.site.repository.master.SiteSettingRepository;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공지 띠 설정(관리자 설정 > 일반 「공지 띠」) 조회·저장 — 사이트 설정 행의 {@code notice_bar_*} 칸(V156). 규칙은 {@link SiteNoticeBars}, 미리보기는 공개 띠와 같은 판정({@link SiteNoticeBarReader}).
 *
 * <p>검사는 칸마다 모아 한꺼번에 {@code 400 VALIDATION}: {@code fields.enabled}(없음) · {@code fields.source} · {@code fields.boardCount} · {@code fields.customText} · {@code fields.customLink}.
 * 바뀐 때만 감사({@code SITE_NOTICE_BAR_UPDATE}, 바뀐 칸의 {@code before}/{@code after})와 관리자 알림(사이트 설정 변경, 변형 {@code siteNoticeBar})을 남긴다.
 * 공개 사이트(api)는 자기 공지 띠 사본({@code PublicSiteNoticeService}, 20초)을 다시 읽을 때 따라온다.
 */
@Service
public class SiteNoticeBarService {

    private static final Logger log = LoggerFactory.getLogger(SiteNoticeBarService.class);

    private final SiteSettingRepository settings;
    private final SiteNoticeBarReader reader;
    private final AuditLogService audit;
    private final AdminNotices notices;

    public SiteNoticeBarService(SiteSettingRepository settings, SiteNoticeBarReader reader, AuditLogService audit, AdminNotices notices) {
        this.settings = settings;
        this.reader = reader;
        this.audit = audit;
        this.notices = notices;
    }

    @Transactional(readOnly = true)
    public SiteNoticeBarSettingsResponse load() {
        SiteSettingEntity row = settings.findById(SiteSettingEntity.ROW_ID).orElseThrow(() -> new CommonException(ErrorCode.NOT_FOUND));
        return view(row);
    }

    @Transactional
    public SiteNoticeBarSettingsResponse save(AdminActor actor, SiteNoticeBarRequest request) {
        Boolean enabled = request == null ? null : request.enabled();
        String source = request == null || request.source() == null ? null : request.source().strip();
        Integer boardCount = request == null ? null : request.boardCount();
        String text = SiteNoticeBars.normalize(request == null ? null : request.customText());
        String link = SiteNoticeBars.normalize(request == null ? null : request.customLink());

        Map<String, MessageRef> errors = new LinkedHashMap<>();
        if (enabled == null) {
            errors.put("enabled", new MessageRef("siteNoticeBar.enabled"));
        }
        if (!SiteNoticeBars.isSource(source)) {
            errors.put("source", new MessageRef("siteNoticeBar.source"));
        }
        if (!SiteNoticeBars.isBoardCount(boardCount)) {
            errors.put("boardCount", new MessageRef("siteNoticeBar.boardCount",
                    String.valueOf(SiteNoticeBars.BOARD_COUNTS.getFirst()), String.valueOf(SiteNoticeBars.BOARD_COUNTS.getLast())));
        }
        SiteNoticeBars.textViolation(text, source).ifPresent(violation -> errors.put("customText", violation));
        SiteNoticeBars.linkViolation(link).ifPresent(violation -> errors.put("customLink", violation));
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        SiteSettingEntity row = settings.findWithLockById(SiteSettingEntity.ROW_ID)
                .orElseThrow(() -> ValidationException.of("site", new MessageRef("siteSetting.missing")));
        Map<String, Object> before = flat(row.isNoticeBarEnabled(), row.getNoticeBarSource(), row.getNoticeBarBoardCount(), row.getNoticeBarCustomText(), row.getNoticeBarCustomLink());
        Map<String, Object> after = flat(enabled, source, boardCount, text, link);
        Map<String, Object> from = new LinkedHashMap<>();
        Map<String, Object> to = new LinkedHashMap<>();
        before.forEach((key, value) -> {
            if (!Objects.equals(value, after.get(key))) {
                from.put(key, value);
                to.put(key, after.get(key));
            }
        });
        if (!to.isEmpty()) {
            row.changeNoticeBar(enabled, source, boardCount, text, link, actor.operatorId(), Instant.now());
            audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.SITE_NOTICE_BAR_UPDATE, AuditResult.SUCCESS).target("SITE", "1")
                    .with("before", from).with("after", to));
            notices.siteSettingsChanged(actor, "siteNoticeBar");
            log.info("공지 띠 설정을 바꿨다 operatorId={} fields={}", actor.operatorId(), to.keySet());
        }
        return view(row);
    }

    private SiteNoticeBarSettingsResponse view(SiteSettingEntity row) {
        SiteNoticeBoardView board = reader.noticeBoard().map(b -> new SiteNoticeBoardView(b.slug(), b.name(), b.nameEn())).orElse(null);
        List<String> preview = reader.lines(row).stream().map(SiteNoticeLine::title).toList();
        return new SiteNoticeBarSettingsResponse(row.isNoticeBarEnabled(), row.getNoticeBarSource(), row.getNoticeBarBoardCount(), row.getNoticeBarCustomText(),
                row.getNoticeBarCustomLink(), board, preview);
    }

    /** 감사 「바뀐 내용」의 칸 — 없는 문구·링크는 빈 글자로(맵에 null 을 넣지 않는다) */
    private static Map<String, Object> flat(boolean enabled, String source, int boardCount, String text, String link) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", enabled);
        out.put("source", source);
        out.put("boardCount", boardCount);
        out.put("customText", text == null ? "" : text);
        out.put("customLink", link == null ? "" : link);
        return out;
    }
}
