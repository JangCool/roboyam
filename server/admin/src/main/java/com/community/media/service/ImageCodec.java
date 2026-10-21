package com.community.media.service;

import com.community.media.exception.ImageUnreadableException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.function.Supplier;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.springframework.stereotype.Component;

/**
 * 이미지 읽기·크기 맞추기·다시 인코딩 — JDK 의 {@code ImageIO}(PNG·JPEG·GIF)만 쓴다(새 라이브러리 없음). 저장소·DB·규칙(검사 순서·문구)은 모른다.
 *
 * <ul>
 *   <li>{@link #probe}: <b>디코딩하지 않고</b> 머리(헤더)만 읽어 가로·세로를 잰다 — 그림이 너무 크면 디코딩하기 전에 거절하려는 용도(디코딩 폭탄 방지).</li>
 *   <li>{@link #decode}: 첫 장을 디코딩한다(GIF 는 첫 프레임). 무거우므로 {@link #limited} 안에서 부른다.</li>
 *   <li>{@link #png}·{@link #jpeg}: 다시 인코딩한다 — 메타데이터(EXIF·텍스트 청크·ICC)와 그림 뒤에 붙은 데이터는 새 파일에 들어가지 않는다.</li>
 *   <li>{@link #resize}: 줄일 때 절반씩 줄이며 부드럽게 맞춘다(한 번에 확 줄이면 계단이 진다). 투명은 그대로 지킨다.</li>
 * </ul>
 */
@Component
public class ImageCodec {

    static {
        // 읽고 쓰는 동안 디스크에 임시 파일을 만들지 않는다(메모리 스트림만 쓴다)
        ImageIO.setUseCache(false);
    }

    private final ImageGate gate;

    public ImageCodec(ImageGate gate) {
        this.gate = gate;
    }

    /** 읽은 가로·세로(px) */
    public record Info(int width, int height) {
    }

    /** 동시 처리 수 제한 아래에서 무거운 일을 한다 */
    public <T> T limited(Supplier<T> work) {
        return gate.run(work);
    }

    /** 머리만 읽어 가로·세로를 잰다. 읽을 수 없으면 {@link ImageUnreadableException} */
    public Info probe(byte[] data, ImageFormat format) {
        if (format == ImageFormat.WEBP) {
            WebpContainer.Info info = WebpContainer.inspect(data);
            return new Info(info.width(), info.height());
        }
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            ImageReader reader = readerFor(in, format);
            try {
                reader.setInput(in, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0) {
                    throw new ImageUnreadableException("크기가 0 이다");
                }
                return new Info(width, height);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof ImageUnreadableException unreadable) {
                throw unreadable;
            }
            throw new ImageUnreadableException("머리를 읽지 못했다", e);
        }
    }

    /** 첫 장을 디코딩한다. 읽을 수 없으면 {@link ImageUnreadableException}. WebP 는 디코딩하지 않는다(컨테이너만 정리한다) */
    public BufferedImage decode(byte[] data, ImageFormat format) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            ImageReader reader = readerFor(in, format);
            try {
                reader.setInput(in, true, true);
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw new ImageUnreadableException("디코딩 결과가 없다");
                }
                return image;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof ImageUnreadableException unreadable) {
                throw unreadable;
            }
            throw new ImageUnreadableException("디코딩하지 못했다", e);
        }
    }

    /** WebP 의 메타데이터·뒤에 붙은 데이터를 뗀 새 파일(그림은 그대로) */
    public byte[] cleanWebp(byte[] data) {
        return WebpContainer.clean(data);
    }

    private static ImageReader readerFor(ImageInputStream in, ImageFormat format) throws IOException {
        if (in == null) {
            throw new ImageUnreadableException("스트림을 열지 못했다");
        }
        String name = switch (format) {
            case PNG -> "png";
            case JPEG -> "jpeg";
            case GIF -> "gif";
            case WEBP -> throw new ImageUnreadableException("WebP 는 디코딩하지 않는다");
        };
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(name);
        while (readers.hasNext()) {
            ImageReader reader = readers.next();
            // 서명으로 알아낸 형식의 읽기 구현이 이 스트림을 읽을 수 있는지 확인한다
            in.mark();
            boolean ok;
            try {
                ok = reader.getOriginatingProvider().canDecodeInput(in);
            } finally {
                in.reset();
            }
            if (ok) {
                return reader;
            }
            reader.dispose();
        }
        throw new ImageUnreadableException("읽을 수 있는 구현이 없다");
    }

    // ── 쓰기 ──

    /** PNG 로 쓴다. {@code flatten} 이면 흰 배경에 얹어 투명을 없앤다(불투명 RGB). 메타데이터는 쓰지 않는다 */
    public static byte[] png(BufferedImage image, boolean flatten) {
        BufferedImage out = flatten ? onWhite(image) : normalized(image);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(out, "png", bytes)) {
                throw new IllegalStateException("PNG 쓰기 구현이 없다");
            }
        } catch (IOException e) {
            throw new IllegalStateException("PNG 를 쓰지 못했다", e);
        }
        return bytes.toByteArray();
    }

    /** JPEG 로 쓴다(투명은 흰 배경에 얹는다). 메타데이터는 쓰지 않는다 */
    public static byte[] jpeg(BufferedImage image, float quality) {
        BufferedImage rgb = onWhite(image);
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IllegalStateException("JPEG 쓰기 구현이 없다");
        }
        ImageWriter writer = writers.next();
        try {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ImageOutputStream stream = ImageIO.createImageOutputStream(bytes)) {
                writer.setOutput(stream);
                writer.write(null, new IIOImage(rgb, null, null), param);
            }
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("JPEG 를 쓰지 못했다", e);
        } finally {
            writer.dispose();
        }
    }

    /** 흰 배경에 얹은 불투명 RGB 그림 */
    private static BufferedImage onWhite(BufferedImage image) {
        BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, out.getWidth(), out.getHeight());
            g.drawImage(image, 0, 0, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    /** 쓰기 구현이 다루기 쉬운 ARGB·RGB 로 맞춘다(색 모델이 별난 그림은 옮겨 그린다) */
    private static BufferedImage normalized(BufferedImage image) {
        int type = image.getType();
        if (type == BufferedImage.TYPE_INT_ARGB || type == BufferedImage.TYPE_INT_RGB || type == BufferedImage.TYPE_3BYTE_BGR || type == BufferedImage.TYPE_4BYTE_ABGR) {
            return image;
        }
        BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(), image.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.drawImage(image, 0, 0, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    // ── 크기 맞추기 ──

    /**
     * {@code width}×{@code height} 로 맞춘다(가로·세로 비율은 호출하는 쪽이 정한다 — 늘이지 않고 줄이는 경우만 쓴다). 줄일 때는 목표의 두 배보다 클 동안 절반씩 줄이고
     * 마지막에 한 번 맞춘다. 같은 크기면 그대로 돌려준다. 투명 가장자리가 검게 번지지 않도록 곱한 알파({@code ARGB_PRE})로 계산한다.
     */
    public static BufferedImage resize(BufferedImage source, int width, int height) {
        if (source.getWidth() == width && source.getHeight() == height) {
            return source;
        }
        BufferedImage current = source;
        while (current.getWidth() >= width * 2 && current.getHeight() >= height * 2) {
            current = step(current, current.getWidth() / 2, current.getHeight() / 2, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        }
        return step(current, width, height, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    }

    private static BufferedImage step(BufferedImage source, int width, int height, Object interpolation) {
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return out;
    }
}
