package com.community.notificationtemplate.service;

import com.community.notificationtemplate.catalog.MessageChannel;
import com.community.notificationtemplate.dto.response.KakaoTemplateListItemView;
import com.community.notificationtemplate.dto.response.KakaoTemplateView;
import com.community.notificationtemplate.engine.MessageValues;
import com.community.notificationtemplate.entity.NotificationKakaoTemplateEntity;
import com.community.notificationtemplate.exception.TemplateNotFoundException;
import com.community.notificationtemplate.repository.master.NotificationKakaoTemplateRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 카카오톡(알림톡) 템플릿 — <b>준비 중</b>(알림 도구에서 카카오가 업체 계약 전)이라 읽기만 한다. 행을 만드는 길이 아직 없어 목록은 비어 있다(등록·검수 흐름은 계약 「미확정」 12).
 */
@Service
@Transactional(readOnly = true)
public class KakaoTemplateAdminService {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final NotificationKakaoTemplateRepository repo;
    private final TemplateSupport support;

    public KakaoTemplateAdminService(NotificationKakaoTemplateRepository repo, TemplateSupport support) {
        this.repo = repo;
        this.support = support;
    }

    public List<KakaoTemplateListItemView> list() {
        List<NotificationKakaoTemplateEntity> rows = repo.findAllByOrderByIdAsc();
        Map<Long, String> names = support.operatorNames(rows.stream().map(NotificationKakaoTemplateEntity::getUpdatedBy).toList());
        return rows.stream().map(row -> {
            List<MessageValues> using = support.usingMessages(MessageChannel.KAKAO, String.valueOf(row.getId()));
            return new KakaoTemplateListItemView(String.valueOf(row.getId()), row.getName(), row.getTemplateCode(), row.getStatus(), using.size(),
                    TemplateSupport.countsByAudience(using), row.getUpdatedAt(), row.getUpdatedBy() == null ? null : names.get(row.getUpdatedBy()));
        }).toList();
    }

    public KakaoTemplateView get(String id) {
        Long key = TemplateSupport.parseId(id);
        NotificationKakaoTemplateEntity row = (key == null ? java.util.Optional.<NotificationKakaoTemplateEntity>empty() : repo.findById(key))
                .orElseThrow(TemplateNotFoundException::new);
        List<MessageValues> using = support.usingMessages(MessageChannel.KAKAO, String.valueOf(row.getId()));
        String by = row.getUpdatedBy() == null ? null : support.operatorNames(List.of(row.getUpdatedBy())).get(row.getUpdatedBy());
        return new KakaoTemplateView(String.valueOf(row.getId()), row.getName(), row.getTemplateCode(), row.getStatus(), using.size(),
                TemplateSupport.countsByAudience(using), row.getUpdatedAt(), by, row.getApprovedBody(), row.getEmphasis(), list(row.getMappings()), list(row.getButtons()),
                row.getChannelName());
    }

    private static List<Map<String, Object>> list(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (RuntimeException e) {
            return List.of();
        }
    }
}
