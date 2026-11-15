import dev.nucleusframework.pdfium.jvm.PdfiumBridge;
import org.jetbrains.skia.Bitmap;
import org.jetbrains.skia.Codec;
import org.jetbrains.skia.ColorAlphaType;
import org.jetbrains.skia.ColorType;
import org.jetbrains.skia.Data;
import org.jetbrains.skia.EncodedImageFormat;
import org.jetbrains.skia.Image;
import org.jetbrains.skia.ImageInfo;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A page cut beforehand, against PDFium: a scan's pages rendered by PDFium (as the binding does), encoded by Skia in
 * the formats a cache of amudim could keep, and decoded again by Skia (as Compose would), with each one's size.
 * Also writes each page's gray pixels (.pgm) for 1-bit and 4-bit PNGs made outside, which it decodes when given.
 *
 *   tools/java.sh SkiaBench <pdf> <width> <out dir> [png...]
 */
public class SkiaBench {
    static final int ROUNDS = 7;

    public static void main(String[] args) throws Exception {
        Path pdf = Path.of(args[0]);
        int width = Integer.parseInt(args[1]);
        Path out = Path.of(args[2]);
        Files.createDirectories(out);
        byte[] bytes = Files.readAllBytes(pdf);
        long buffer = PdfiumBridge.nAllocBuffer(bytes);
        long doc = PdfiumBridge.nOpenDocumentFromMemory(buffer, bytes.length, null);
        long first = PdfiumBridge.nLoadPage(doc, 0);
        float wPt = PdfiumBridge.nGetPageWidth(first), hPt = PdfiumBridge.nGetPageHeight(first);
        PdfiumBridge.nClosePage(first);
        int height = Math.round(width * hPt / wPt);
        System.out.printf("== %s at %d x %d px%n", pdf.getFileName(), width, height);

        // Pages 4..8 as PDFium draws them: BGRA, and their gray as a cache would keep it
        List<Image> color = new ArrayList<>(), gray = new ArrayList<>();
        for (int p = 4; p < 9; p++) {
            ByteBuffer px = ByteBuffer.allocateDirect(width * height * 4);
            long page = PdfiumBridge.nLoadPage(doc, p);
            PdfiumBridge.nRenderPageToAddress(page, 0, ((sun.nio.ch.DirectBuffer) px).address(), width, height, 0);
            PdfiumBridge.nClosePage(page);
            byte[] bgra = new byte[width * height * 4];
            px.get(bgra);
            color.add(Image.Companion.makeRaster(ImageInfo.Companion.makeN32(width, height, ColorAlphaType.PREMUL), bgra, width * 4));
            byte[] g = new byte[width * height];
            for (int i = 0; i < g.length; i++) g[i] = bgra[i * 4 + 1];
            gray.add(Image.Companion.makeRaster(new ImageInfo(width, height, ColorType.GRAY_8, ColorAlphaType.OPAQUE, null), g, width));
            Files.write(out.resolve("page" + p + ".pgm"), pgm(width, height, g));
        }
        PdfiumBridge.nCloseDocument(doc);
        PdfiumBridge.nFreeBuffer(buffer);

        System.out.println("format                 size KB   encode ms   decode ms (median)");
        row("PNG gray8", gray, EncodedImageFormat.PNG, 100, ColorType.GRAY_8, out);
        row("PNG color", color, EncodedImageFormat.PNG, 100, ColorType.Companion.getN32(), out);
        row("WebP lossless", gray, EncodedImageFormat.WEBP, 100, ColorType.Companion.getN32(), out);
        row("WebP q90", gray, EncodedImageFormat.WEBP, 90, ColorType.Companion.getN32(), out);
        row("WebP q75", gray, EncodedImageFormat.WEBP, 75, ColorType.Companion.getN32(), out);
        row("JPEG q90 gray", gray, EncodedImageFormat.JPEG, 90, ColorType.GRAY_8, out);
        row("JPEG q75 gray", gray, EncodedImageFormat.JPEG, 75, ColorType.GRAY_8, out);
        for (int i = 3; i < args.length; i++) decodeOnly(Path.of(args[i]));
    }

    /** Encodes the pages as [format] at [quality] and decodes them again into [into], timing both. */
    static void row(String name, List<Image> pages, EncodedImageFormat format, int quality, ColorType into, Path out)
            throws Exception {
        double[] enc = new double[pages.size()];
        List<byte[]> files = new ArrayList<>();
        long total = 0;
        for (int i = 0; i < pages.size(); i++) {
            long t0 = System.nanoTime();
            Data data = pages.get(i).encodeToData(format, quality, 6);
            enc[i] = (System.nanoTime() - t0) / 1e6;
            byte[] b = data.getBytes();
            files.add(b);
            total += b.length;
        }
        Files.write(out.resolve(name.replace(' ', '_') + "." + format.name().toLowerCase()), files.get(0));
        double[] dec = new double[ROUNDS * files.size()];
        int k = 0;
        for (int r = 0; r < ROUNDS; r++) for (byte[] b : files) dec[k++] = decode(b, into);
        System.out.printf("%-20s %8.0f %10.1f %10.1f%n", name, total / 1024.0 / files.size(), med(enc), med(dec));
    }

    /** Decodes [file] into a bitmap of [into], as an image shown would be, and returns the ms it took. */
    static double decode(byte[] file, ColorType into) {
        long t0 = System.nanoTime();
        Codec codec = Codec.Companion.makeFromData(Data.Companion.makeFromBytes(file, 0, file.length));
        ImageInfo info = codec.getImageInfo();
        Bitmap bitmap = new Bitmap();
        bitmap.allocPixels(new ImageInfo(info.getWidth(), info.getHeight(), into,
                into == ColorType.GRAY_8 ? ColorAlphaType.OPAQUE : ColorAlphaType.PREMUL, null));
        codec.readPixels(bitmap, 0, -1);
        double ms = (System.nanoTime() - t0) / 1e6;
        bitmap.close();
        codec.close();
        return ms;
    }

    static void decodeOnly(Path file) throws Exception {
        byte[] b = Files.readAllBytes(file);
        double[] dec = new double[ROUNDS];
        ColorType into = ColorType.GRAY_8;
        try {
            decode(b, into);
        } catch (RuntimeException e) {
            into = ColorType.Companion.getN32();
        }
        for (int r = 0; r < ROUNDS; r++) dec[r] = decode(b, into);
        System.out.printf("%-20s %8.0f %10s %10.1f  (into %s)%n", file.getFileName(), b.length / 1024.0, "-", med(dec), into);
    }

    static byte[] pgm(int w, int h, byte[] gray) {
        byte[] head = ("P5\n" + w + " " + h + "\n255\n").getBytes();
        byte[] all = Arrays.copyOf(head, head.length + gray.length);
        System.arraycopy(gray, 0, all, head.length, gray.length);
        return all;
    }

    static double med(double[] v) {
        double[] s = v.clone();
        Arrays.sort(s);
        return s[s.length / 2];
    }
}
