package com.community.jjal.service;

import com.community.jjal.entity.JjalFileEntity;
import com.community.jjal.exception.JjalNotFoundException;
import com.community.jjal.repository.master.JjalFileRepository;
import com.community.media.service.ImageStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영자가 보는 짤 원본 — 관리자 글·신고 서랍의 본문 짤({@code AdminBodyJjalView.adminUrl}). 내린 짤(비공개 위치)·살아 있는 짤(공개 위치) 어느 쪽이든 저장소에서 그대로 읽는다.
 * 짤 신고 사건의 그림({@code GET /api/admin/reports/{id}/jjal-image})과 같은 읽기 — 열람은 감사 기록에 남기지 않는다(상세 읽기와 같다).
 */
@Service
@Transactional(readOnly = true)
public class AdminJjalImageService {

    /** 운영자 전용 원본 주소 — admin-web 이 같은 출처로 부르는 상대 경로 */
    public static final String IMAGE_PATH = "/api/admin/jjals/%s/image";

    /** 짤 원본 바이트와 형식 */
    public record JjalImage(byte[] data, String contentType) {
    }

    private final JjalFileRepository jjalFiles;
    private final ImageStorage storage;

    public AdminJjalImageService(JjalFileRepository jjalFiles, ImageStorage storage) {
        this.jjalFiles = jjalFiles;
        this.storage = storage;
    }

    /** 원본 파일이 남아 있는 짤의 운영자 주소 — 불법촬영물로 확정해 지운 짤(해시만 남음)이면 null */
    public static String adminUrl(JjalFileEntity f) {
        return f.getStorageKey() == null ? null : String.format(IMAGE_PATH, f.getPublicId());
    }

    /** 짤 원본 — 없는 공개 ID · 파일이 없는 짤(불법촬영물로 지움) · 저장소에 파일이 없으면 404 JJAL_NOT_FOUND */
    public JjalImage image(String publicId) {
        JjalFileEntity f = publicId == null || publicId.isBlank() ? null : jjalFiles.findByPublicId(publicId).orElse(null);
        if (f == null || f.getStorageKey() == null) {
            throw new JjalNotFoundException();
        }
        byte[] data = storage.readBytes(f.getStorageKey()).orElseThrow(JjalNotFoundException::new);
        return new JjalImage(data, f.getContentType());
    }
}
