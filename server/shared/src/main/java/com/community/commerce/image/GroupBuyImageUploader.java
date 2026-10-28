package com.community.commerce.image;

import com.community.media.service.ExifOrientation;
import com.community.media.service.ImageCodec;
import com.community.media.service.ImageFormat;
import com.community.media.service.ImageStorage;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.time.ServiceTime;
import java.awt.image.BufferedImage;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 공구 이미지 올리기(계약 결정 2 — 상품 대표 이미지·요청 사진·운영자 근거 사진 같은 길). JPEG·PNG·WebP <b>정지 이미지</b>만, 5MB 까지. 다시 인코딩해(JPEG — EXIF·위치 정보가 빠진다)
 * 방향을 바로잡고 긴 변 1600px·썸네일 400px 로 저장소({@code roboyam.storage})에 둔다. 키는 {@code groupbuy/<용도>/<년월>/<번호>.jpg}, 썸네일은 {@code …_thumb.jpg}
 * ({@link #thumbKey}) — 화면이 다루는 미디어 ID 는 이 키다(DB 행 없이 — 상품·요청이 저장할 때 키를 남긴다).
 */
@Component
public class GroupBuyImageUploader {

    public static final long MAX_BYTES = 5L * 1024 * 1024;
    public static final int LONG_SIDE = 1600;
    public static final int THUMB_SIDE = 400;
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    /** 올린 그림 — 키·주소·크기 */
    public record Uploaded(String key, String thumbKey, String url, String thumbUrl, int width, int height) {
    }

    private final ImageStorage storage;
    private final ImageCodec codec;
    private final SnowflakeIdGenerator ids;
    private final ObjectProvider<Clock> clocks;

    public GroupBuyImageUploader(ImageStorage storage, ImageCodec codec, SnowflakeIdGenerator ids, ObjectProvider<Clock> clocks) {
        this.storage = storage;
        this.codec = codec;
        this.ids = ids;
        this.clocks = clocks;
    }

    /**
     * @param purpose {@code product}·{@code request}·{@code evidence}
     * @throws ValidationException {@code fields.file} — 없음·5MB 넘음·모르는 형식·움직이는 그림·읽을 수 없음
     */
    public Uploaded upload(String purpose, byte[] data) {
        if (data == null || data.length == 0) {
            throw ValidationException.of("file", new MessageRef("groupbuy.image.required"));
        }
        if (data.length > MAX_BYTES) {
            throw ValidationException.of("file", new MessageRef("groupbuy.image.tooLarge", 5));
        }
        ImageFormat format = ImageFormat.sniff(data);
        if (format == null || format == ImageFormat.GIF) {
            throw ValidationException.of("file", new MessageRef("groupbuy.image.format"));
        }
        BufferedImage image;
        try {
            image = codec.limited(() -> ExifOrientation.apply(codec.decode(data, format), format == ImageFormat.JPEG ? ExifOrientation.read(data) : 1));
        } catch (RuntimeException e) {
            throw ValidationException.of("file", new MessageRef("groupbuy.image.unreadable"));
        }
        BufferedImage main = fit(image, LONG_SIDE);
        BufferedImage thumb = fit(image, THUMB_SIDE);
        String key = "groupbuy/" + purpose + "/" + MONTH.format(clocks.getIfUnique(Clock::systemUTC).instant().atZone(ServiceTime.ZONE)) + "/" + ids.nextId() + ".jpg";
        storage.put(key, ImageCodec.jpeg(main, 0.88f));
        storage.put(thumbKey(key), ImageCodec.jpeg(thumb, 0.85f));
        return new Uploaded(key, thumbKey(key), storage.publicUrl(key), storage.publicUrl(thumbKey(key)), main.getWidth(), main.getHeight());
    }

    /** 키 → 썸네일 키 */
    public static String thumbKey(String key) {
        return key.endsWith(".jpg") ? key.substring(0, key.length() - 4) + "_thumb.jpg" : key + "_thumb";
    }

    /** 공구가 올린 키인가(남의 경로를 저장하지 못하게) */
    public static boolean ownKey(String key) {
        return key != null && key.matches("groupbuy/[a-z]+/[0-9]{6}/[0-9]+\\.jpg");
    }

    public String url(String key) {
        return key == null ? null : storage.publicUrl(key);
    }

    private static BufferedImage fit(BufferedImage source, int side) {
        int w = source.getWidth();
        int h = source.getHeight();
        int longest = Math.max(w, h);
        if (longest <= side) {
            return source;
        }
        double scale = (double) side / longest;
        return ImageCodec.resize(source, Math.max(1, (int) Math.round(w * scale)), Math.max(1, (int) Math.round(h * scale)));
    }
}
