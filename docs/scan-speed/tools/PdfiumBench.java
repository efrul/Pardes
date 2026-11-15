import dev.nucleusframework.pdfium.jvm.PdfiumBridge;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Times what Nucleus's PDFium binding does for one page of a scan, step by step, through its own JNI bridge (the same
 * calls PdfDocument.jvm.kt makes): opening the file, loading a page, its links (which PdfPage asks for by default),
 * a preview render at a quarter of the width (PdfPage's first pass) and a full render at a few widths.
 *
 *   tools/java.sh PdfiumBench <pdf>...
 */
public class PdfiumBench {
    static final int FPDF_ANNOT = 0x01;
    static final int ROUNDS = 5;

    public static void main(String[] args) throws Exception {
        for (String file : args) bench(Path.of(file));
    }

    static void bench(Path file) throws Exception {
        System.out.printf("%n== %s (%.1f MB)%n", file.getFileName(), Files.size(file) / 1e6);
        // Opening: read the file, copy it to native memory, parse it, and the form-fill environment
        double[] read = new double[ROUNDS], alloc = new double[ROUNDS], open = new double[ROUNDS], form = new double[ROUNDS];
        long doc = 0, formHandle = 0, buffer = 0;
        byte[] bytes = null;
        for (int r = 0; r < ROUNDS; r++) {
            if (doc != 0) {
                PdfiumBridge.nCloseFormEnv(formHandle);
                PdfiumBridge.nCloseDocument(doc);
                PdfiumBridge.nFreeBuffer(buffer);
            }
            long t0 = System.nanoTime();
            bytes = Files.readAllBytes(file);
            long t1 = System.nanoTime();
            buffer = PdfiumBridge.nAllocBuffer(bytes);
            long t2 = System.nanoTime();
            doc = PdfiumBridge.nOpenDocumentFromMemory(buffer, bytes.length, null);
            long t3 = System.nanoTime();
            formHandle = PdfiumBridge.nInitFormEnv(doc);
            long t4 = System.nanoTime();
            read[r] = ms(t1 - t0); alloc[r] = ms(t2 - t1); open[r] = ms(t3 - t2); form[r] = ms(t4 - t3);
        }
        System.out.printf("open: read %.1f, native copy %.1f, parse %.1f, form env %.1f ms (median of %d, warm)%n",
                med(read), med(alloc), med(open), med(form), ROUNDS);
        int pages = PdfiumBridge.nGetPageCount(doc);
        long first = PdfiumBridge.nLoadPage(doc, 0);
        float wPt = PdfiumBridge.nGetPageWidth(first), hPt = PdfiumBridge.nGetPageHeight(first);
        PdfiumBridge.nClosePage(first);
        System.out.printf("pages %d, page %.0f x %.0f pt%n", pages, wPt, hPt);

        // Each step on pages not drawn before, as a reader turning pages meets them
        int page = 10;
        double[] load = new double[ROUNDS], links = new double[ROUNDS];
        for (int r = 0; r < ROUNDS; r++) {
            long t0 = System.nanoTime();
            long p = PdfiumBridge.nLoadPage(doc, (page + r) % PdfiumBridge.nGetPageCount(doc));
            long t1 = System.nanoTime();
            PdfiumBridge.nClosePage(p);
            long t2 = System.nanoTime();
            p = PdfiumBridge.nLoadPage(doc, (page + r) % PdfiumBridge.nGetPageCount(doc));
            PdfiumBridge.nCountPageLinks(doc, p);
            PdfiumBridge.nClosePage(p);
            long t3 = System.nanoTime();
            load[r] = ms(t1 - t0); links[r] = ms(t3 - t2);
        }
        System.out.printf("page: load %.2f ms, load+links %.2f ms%n", med(load), med(links));
        page += ROUNDS;
        int[] widths = {400, 1100, 1600, 2048};
        for (int w : widths) {
            double[] t = new double[ROUNDS];
            for (int r = 0; r < ROUNDS; r++) {
                t[r] = render(doc, formHandle, page + r, w, wPt, hPt, w == 400 ? 0 : FPDF_ANNOT, w == 400 ? 0 : formHandle);
            }
            page += ROUNDS;
            System.out.printf("render %4d px (%s): %.1f ms  [%s]%n", w, w == 400 ? "PREVIEW" : "FULL", med(t), fmt(t));
        }
        // The same page again: PDFium keeps nothing of a page once it is closed
        double[] again = new double[ROUNDS];
        for (int r = 0; r < ROUNDS; r++) again[r] = render(doc, formHandle, 3, 1600, wPt, hPt, FPDF_ANNOT, formHandle);
        System.out.printf("render 1600 px, the same page %d times: [%s]%n", ROUNDS, fmt(again));
        PdfiumBridge.nCloseFormEnv(formHandle);
        PdfiumBridge.nCloseDocument(doc);
        PdfiumBridge.nFreeBuffer(buffer);
    }

    static double render(long doc, long form, int page, int w, float wPt, float hPt, int flags, long formHandle) {
        int h = Math.round(w * hPt / wPt);
        ByteBuffer pixels = ByteBuffer.allocateDirect(w * h * 4);
        long t0 = System.nanoTime();
        long p = PdfiumBridge.nLoadPage(doc, page % PdfiumBridge.nGetPageCount(doc));
        boolean ok = PdfiumBridge.nRenderPageToAddress(p, formHandle, address(pixels), w, h, flags);
        PdfiumBridge.nClosePage(p);
        long t1 = System.nanoTime();
        if (!ok) throw new IllegalStateException("render failed " + PdfiumBridge.nGetLastError());
        return ms(t1 - t0);
    }

    static long address(ByteBuffer direct) {
        return ((sun.nio.ch.DirectBuffer) direct).address();
    }

    static double ms(long nanos) { return nanos / 1e6; }

    static double med(double[] v) {
        double[] s = v.clone();
        Arrays.sort(s);
        return s[s.length / 2];
    }

    static String fmt(double[] v) {
        StringBuilder b = new StringBuilder();
        for (double x : v) b.append(String.format("%.0f ", x));
        return b.toString().trim();
    }
}
