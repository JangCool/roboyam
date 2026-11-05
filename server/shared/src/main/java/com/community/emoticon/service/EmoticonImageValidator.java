package com.community.emoticon.service;

import com.community.emoticon.entity.EmoticonImageFormat;
import com.community.emoticon.entity.EmoticonImageKind;
import com.community.emoticon.exception.EmoticonImageInvalidException;
import com.community.media.exception.ImageUnreadableException;
import com.community.media.service.ImageCodec;
import com.community.media.service.ImageFormat;
import com.community.zconfiguration.message.MessageRef;
import java.awt.image.BufferedImage;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * 로보티콘 그림 검사기 — 작가 올리기(공개 사이트)와 운영 팩 올리기(관리자)가 <b>같이</b> 쓴다. 저장소·DB 는 모른다.
 *
 * <p><b>바이트로</b> 형식을 가른다({@link EmoticonImageInspector}). 이유 코드와 차례는 user-web {@code UtilEmoticonSpec.imageProblems}·{@code coverProblems} 의 첫 문제와 같다:
 * <ul>
 *   <li>로보티콘: {@code TYPE}(GIF·PNG 아님·깨짐) → {@code DIMENSION}(정확히 200×200) → {@code TOO_LARGE}(2MB) → {@code TOO_MANY_FRAMES}(GIF 24) → {@code NOT_LOOP}(GIF 무한 반복).
 *       권장 용량(300KB)을 넘는 것은 받는다({@link Checked#heavy} — 화면이 경고).</li>
 *   <li>대표 이미지: {@code COVER_TYPE}(PNG 아님) → {@code DIMENSION} → {@code TOO_LARGE}(1MB).</li>
 * </ul>
 * <b>무한 반복</b>: 작가 올리기는 막고(user-web 목업 — 오류), 운영자 올리기는 받아 둔다(admin-web 목업 — 「권장을 넘은 것(한 번만 재생)은 받아 두고 화면이 경고」). 그래서 부르는 쪽이
 * {@code loopRequired} 를 정한다. 크기를 머리에서 먼저 보고(논리 화면·IHDR 이 200×200, 프레임이 화면 안) 그다음에 디코딩해(동시 처리 제한 {@code ImageGate} 아래) 정말 그림인지 본다 —
 * 디코딩 실패는 {@code TYPE}(대표는 {@code COVER_TYPE}).
 *
 * <p><b>저장할 바이트</b>: GIF 는 받은 그대로(움직임을 지킨다 — 끝 표식 뒤에 붙은 바이트만 뗀다), PNG 는 다시 인코딩한다(메타데이터·뒤에 붙은 데이터가 남지 않게). 지문은 저장할 바이트의 SHA-256.
 */
@Component
public class EmoticonImageValidator {

    private final ImageCodec codec;

    public EmoticonImageValidator(ImageCodec codec) {
        this.codec = codec;
    }

    /**
     * 검사를 지난 그림.
     *
     * @param data        저장할 바이트
     * @param contentHash 저장할 바이트의 SHA-256(hex 64자)
     * @param heavy       권장 용량(300KB)을 넘었다(받지만 화면이 경고)
     */
    public record Checked(EmoticonImageKind kind, EmoticonImageFormat format, int width, int height, int frames, boolean loopForever, byte[] data, String contentHash,
                          boolean heavy) {
    }

    /**
     * @param field        오류를 붙일 칸({@code file} · {@code cover})
     * @param loopRequired GIF 무한 반복을 막는 규칙으로 볼까(작가 참 · 운영자 거짓)
     * @throws EmoticonImageInvalidException 규격에 맞지 않으면(400 {@code VALIDATION fields.<칸>} = 이유 코드)
     */
    public Checked check(byte[] data, EmoticonImageKind kind, boolean loopRequired, String field) {
        boolean cover = kind == EmoticonImageKind.COVER;
        int size = data == null ? 0 : data.length;
        EmoticonImageInspector.Info info = EmoticonImageInspector.inspect(data);
        if (info == null || (cover && info.format() != EmoticonImageFormat.PNG)) {
            throw typeProblem(field, cover);
        }
        if (info.width() != EmoticonSpec.IMAGE_SIZE || info.height() != EmoticonSpec.IMAGE_SIZE) {
            throw new EmoticonImageInvalidException(field, "DIMENSION", new MessageRef("emoticon.image.DIMENSION", EmoticonSpec.IMAGE_SIZE,
                    String.valueOf(info.width()), String.valueOf(info.height())));
        }
        int max = cover ? EmoticonSpec.COVER_MAX_BYTES : EmoticonSpec.MAX_BYTES;
        if (size > max) {
            throw new EmoticonImageInvalidException(field, "TOO_LARGE", new MessageRef("emoticon.image.TOO_LARGE", EmoticonSpec.bytes(max), EmoticonSpec.bytes(size)));
        }
        if (info.format() == EmoticonImageFormat.GIF && info.frames() > EmoticonSpec.MAX_FRAMES) {
            throw new EmoticonImageInvalidException(field, "TOO_MANY_FRAMES", new MessageRef("emoticon.image.TOO_MANY_FRAMES", EmoticonSpec.MAX_FRAMES,
                    String.valueOf(info.frames())));
        }
        if (loopRequired && info.format() == EmoticonImageFormat.GIF && !info.loopForever()) {
            throw new EmoticonImageInvalidException(field, "NOT_LOOP", new MessageRef("emoticon.image.NOT_LOOP"));
        }
        byte[] stored;
        try {
            stored = codec.limited(() -> render(data, info));
        } catch (ImageUnreadableException e) {
            throw typeProblem(field, cover);
        }
        return new Checked(kind, info.format(), info.width(), info.height(), info.frames(), info.loopForever(), stored, sha256(stored),
                size > EmoticonSpec.RECOMMENDED_BYTES);
    }

    private byte[] render(byte[] data, EmoticonImageInspector.Info info) {
        if (info.format() == EmoticonImageFormat.GIF) {
            codec.decode(data, ImageFormat.GIF); // 정말 GIF 인지(첫 장면) — 머리만 그럴듯한 파일을 여기서 거른다
            return info.end() < data.length ? Arrays.copyOf(data, info.end()) : data;
        }
        BufferedImage image = codec.decode(data, ImageFormat.PNG);
        return ImageCodec.png(image, false);
    }

    private static EmoticonImageInvalidException typeProblem(String field, boolean cover) {
        return cover ? new EmoticonImageInvalidException(field, "COVER_TYPE", new MessageRef("emoticon.image.COVER_TYPE"))
                : new EmoticonImageInvalidException(field, "TYPE", new MessageRef("emoticon.image.TYPE"));
    }

    /** SHA-256(hex 64자) */
    public static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
