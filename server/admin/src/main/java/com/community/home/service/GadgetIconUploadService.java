package com.community.home.service;

import com.community.home.dto.response.GadgetIconUploadView;
import com.community.media.entity.FileEntity;
import com.community.media.entity.FilePurpose;
import com.community.media.repository.master.FileRepository;
import com.community.media.service.ImageFormat;
import com.community.media.service.ImageStorage;
import com.community.permission.dto.AdminActor;
import com.community.site.service.LogoImageProcessor;
import com.community.site.service.SiteImageFiles;
import com.community.site.service.SvgInspector;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * 가젯 제목 아이콘 올리기({@code POST /api/admin/home-layouts/icons}) — 파일 용도 {@link FilePurpose#GADGET_ICON}(V178). 올린 파일은 {@code PENDING} 이고, 가젯 저장이
 * {@code style.iconUpload.fileId} 로 쓰면 {@code IN_USE} 가 된다({@code GadgetLayoutWriter}).
 *
 * <p>규칙(어기면 {@code 400 VALIDATION fields.file}): 빈 파일 → 용량 {@value #MAX_KB}KB → 형식. 형식은 이름·Content-Type 을 믿지 않고 내용으로 본다:
 * 첫 글자가 {@code <} 면 SVG — 로고 SVG 와 같은 검사({@link SvgInspector}, 정사각 요구 없음 · 위험 요소가 있으면 지우지 않고 거절 · 10만 자 한도)를 통과한 글을 저장한다.
 * 아니면 서명으로 PNG·WEBP 만 받고 로고 그림과 같이 다시 만든다({@link LogoImageProcessor} — 메타데이터를 뗀다). JPEG·GIF 는 받지 않는다.
 * 저장소 키 {@code gadget-icon/{버전}/icon.{svg|png|webp}}.
 */
@Service
public class GadgetIconUploadService {

    private static final Logger log = LoggerFactory.getLogger(GadgetIconUploadService.class);
    /** 용량 한도 */
    static final int MAX_KB = 200;
    static final String KEY_PREFIX = "gadget-icon/";
    public static final String SVG_TYPE = "image/svg+xml";

    private final LogoImageProcessor processor;
    private final SiteImageFiles imageFiles;
    private final ImageStorage storage;
    private final FileRepository files;
    private final SnowflakeIdGenerator ids;
    private final TransactionTemplate tx;

    public GadgetIconUploadService(LogoImageProcessor processor, SiteImageFiles imageFiles, ImageStorage storage, FileRepository files, SnowflakeIdGenerator ids,
                                   PlatformTransactionManager txManager) {
        this.processor = processor;
        this.imageFiles = imageFiles;
        this.storage = storage;
        this.files = files;
        this.ids = ids;
        this.tx = new TransactionTemplate(txManager);
    }

    public GadgetIconUploadView upload(AdminActor actor, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ValidationException.of("file", new MessageRef(file == null ? "file.required" : "site.image.empty"));
        }
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw ValidationException.of("file", new MessageRef("site.image.corrupt"));
        }
        if (data.length > MAX_KB * 1024) {
            throw ValidationException.of("file", new MessageRef("gadgetIcon.size", String.valueOf(MAX_KB)));
        }
        boolean svg = looksLikeSvg(data);
        byte[] out;
        String extension;
        String contentType;
        int width = 0;
        int height = 0;
        if (svg) {
            String source;
            try {
                source = SvgInspector.inspect(new String(data, StandardCharsets.UTF_8), false).source();
            } catch (ValidationException e) {
                // 로고 검사는 입력칸 svgSource 로 알린다 — 여기서는 올린 파일 칸으로 옮긴다
                MessageRef first = e.getFields().values().stream().findFirst().orElse(new MessageRef("site.svg.unreadable"));
                throw ValidationException.of("file", first);
            }
            out = source.getBytes(StandardCharsets.UTF_8);
            extension = "svg";
            contentType = SVG_TYPE;
        } else {
            ImageFormat format = ImageFormat.sniff(data);
            if (format != ImageFormat.PNG && format != ImageFormat.WEBP) {
                throw ValidationException.of("file", new MessageRef("gadgetIcon.type"));
            }
            LogoImageProcessor.Processed processed = processor.process(data, "gadgetIcon.type", "gadgetIcon.size");
            out = processed.data();
            extension = processed.format().extension();
            contentType = processed.format().contentType();
            width = processed.width();
            height = processed.height();
        }

        String key = KEY_PREFIX + imageFiles.newVersion() + "/icon." + extension;
        imageFiles.writeAll(Map.of(key, out));
        long fileId = ids.nextId();
        FileEntity entity = FileEntity.pending(fileId, FilePurpose.GADGET_ICON, key, contentType, out.length, width, height, actor.operatorId(), Instant.now());
        try {
            tx.executeWithoutResult(status -> files.persist(entity));
        } catch (RuntimeException e) {
            imageFiles.deleteQuietly(List.of(key)); // 장부에 없는 파일이 남지 않게
            throw e;
        }
        log.info("가젯 아이콘을 올렸다 fileId={} svg={} bytes={} operatorId={}", fileId, svg, out.length, actor.operatorId());
        return new GadgetIconUploadView(Long.toString(fileId), storage.publicUrl(key), svg);
    }

    /** SVG 파일인가 — BOM·앞 공백을 건너뛴 첫 글자가 {@code <} */
    static boolean looksLikeSvg(byte[] data) {
        int i = 0;
        if (data.length >= 3 && (data[0] & 0xff) == 0xef && (data[1] & 0xff) == 0xbb && (data[2] & 0xff) == 0xbf) {
            i = 3;
        }
        while (i < data.length && (data[i] == ' ' || data[i] == '\n' || data[i] == '\r' || data[i] == '\t')) {
            i++;
        }
        return i < data.length && data[i] == '<';
    }
}
