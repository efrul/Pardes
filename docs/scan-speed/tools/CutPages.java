import dev.nucleusframework.pdfium.jvm.PdfiumBridge;
import org.jetbrains.skia.ColorAlphaType;
import org.jetbrains.skia.ColorType;
import org.jetbrains.skia.EncodedImageFormat;
import org.jetbrains.skia.Image;
import org.jetbrains.skia.ImageInfo;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Cuts a scan into an image an amud: each page rendered by PDFium at a width, kept as its gray, written as a PNG by
 * Skia, with what each step cost. What a cache of amudim would do once per page, in the background.
 *
 *   tools/java.sh CutPages <pdf> <width> <out dir> [first page] [pages]
 */
public class CutPages {
    public static void main(String[] args) throws Exception {
        Path pdf = Path.of(args[0]);
        int width = Integer.parseInt(args[1]);
        Path out = Path.of(args[2]);
        Files.createDirectories(out);
        byte[] bytes = Files.readAllBytes(pdf);
        long buffer = PdfiumBridge.nAllocBuffer(bytes);
        long doc = PdfiumBridge.nOpenDocumentFromMemory(buffer, bytes.length, null);
        int count = PdfiumBridge.nGetPageCount(doc);
        int first = args.length > 3 ? Integer.parseInt(args[3]) : 0;
        int pages = args.length > 4 ? Integer.parseInt(args[4]) : count - first;
        double render = 0, gray = 0, encode = 0;
        long size = 0;
        for (int p = first; p < first + pages; p++) {
            long page = PdfiumBridge.nLoadPage(doc, p);
            int height = Math.round(width * PdfiumBridge.nGetPageHeight(page) / PdfiumBridge.nGetPageWidth(page));
            ByteBuffer px = ByteBuffer.allocateDirect(width * height * 4);
            long t0 = System.nanoTime();
            PdfiumBridge.nRenderPageToAddress(page, 0, ((sun.nio.ch.DirectBuffer) px).address(), width, height, 0);
            PdfiumBridge.nClosePage(page);
            long t1 = System.nanoTime();
            byte[] g = new byte[width * height];
            for (int i = 0; i < g.length; i++) g[i] = px.get(i * 4 + 1);
            long t2 = System.nanoTime();
            Image image = Image.Companion.makeRaster(new ImageInfo(width, height, ColorType.GRAY_8, ColorAlphaType.OPAQUE, null), g, width);
            byte[] png = image.encodeToData(EncodedImageFormat.PNG, 100, 6).getBytes();
            long t3 = System.nanoTime();
            Files.write(out.resolve(p + ".png"), png);
            render += (t1 - t0) / 1e6;
            gray += (t2 - t1) / 1e6;
            encode += (t3 - t2) / 1e6;
            size += png.length;
        }
        System.out.printf("cut %d pages at %d px: render %.0f, gray %.0f, PNG %.0f ms a page; %.0f KB a page, %.1f MB in all%n",
                pages, width, render / pages, gray / pages, encode / pages, size / 1024.0 / pages, size / 1e6);
        PdfiumBridge.nCloseDocument(doc);
        PdfiumBridge.nFreeBuffer(buffer);
    }
}
