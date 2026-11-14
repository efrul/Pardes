"""Scanned-tractate PDFs from the synthetic pages, one per encoding a scan may have, for benchmarking.

    python3 make_pdfs.py <pages dir> <out dir> <pages in each PDF>

Each PDF has the given number of pages, cycling over the page images; every page has an image stream of its own, as
a scanned book's PDF has. The encodings:
  jbig2-sym   JBIG2 with a shared symbol dictionary (jbig2enc -s), as archive.org and most scanned books
  jbig2-gen   JBIG2 generic regions, lossless (jbig2enc without -s)
  g4          CCITT Group 4, the classic bitonal fax coding (by tiff2pdf)
  jpeg300     grayscale JPEG at the scan's resolution, quality 50
  jpeg150     grayscale JPEG at half the resolution, quality 60
and jbig2-gen-1page.pdf, the first page alone. Needs jbig2enc's `jbig2` and libtiff's `tiff2pdf` (apt: jbig2,
libtiff-tools) and the culmus fonts for make_pages.py.
"""

import glob
import io
import os
import subprocess
import sys
import tempfile

from PIL import Image, ImageFilter

DPI = 300


class Pdf:
    def __init__(self):
        self.objects = []

    def add(self, body: bytes) -> int:
        self.objects.append(body)
        return len(self.objects)

    def stream(self, header: str, data: bytes) -> int:
        return self.add(f"<< {header} /Length {len(data)} >>\nstream\n".encode() + data + b"\nendstream")

    def write(self, path: str, root: int):
        out = io.BytesIO()
        out.write(b"%PDF-1.5\n%\xe2\xe3\xcf\xd3\n")
        offsets = []
        for i, body in enumerate(self.objects, 1):
            offsets.append(out.tell())
            out.write(f"{i} 0 obj\n".encode() + body + b"\nendobj\n")
        xref = out.tell()
        out.write(f"xref\n0 {len(self.objects) + 1}\n0000000000 65535 f \n".encode())
        for off in offsets:
            out.write(f"{off:010d} 00000 n \n".encode())
        out.write(f"trailer\n<< /Size {len(self.objects) + 1} /Root {root} 0 R >>\nstartxref\n{xref}\n%%EOF\n".encode())
        with open(path, "wb") as f:
            f.write(out.getvalue())


def build(path, images, count, globals_data=None):
    """[images]: (width, height, image dict header, data) per page image; the PDF cycles over them."""
    pdf = Pdf()
    pdf.add(b"")  # 1: catalog, filled at the end
    pdf.add(b"")  # 2: pages
    glob_ref = pdf.stream("", globals_data) if globals_data is not None else None
    kids = []
    for i in range(count):
        w, h, header, data = images[i % len(images)]
        if glob_ref is not None:
            header = header.replace("GLOBALS", f"{glob_ref} 0 R")
        img = pdf.stream(header, data)
        pw, ph = w * 72 / DPI, h * 72 / DPI
        content = pdf.stream("", f"q {pw:.2f} 0 0 {ph:.2f} 0 0 cm /Im0 Do Q".encode())
        page = pdf.add(
            f"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 {pw:.2f} {ph:.2f}] "
            f"/Resources << /XObject << /Im0 {img} 0 R >> >> /Contents {content} 0 R >>".encode()
        )
        kids.append(page)
    pdf.objects[0] = b"<< /Type /Catalog /Pages 2 0 R >>"
    pdf.objects[1] = f"<< /Type /Pages /Count {len(kids)} /Kids [{' '.join(f'{k} 0 R' for k in kids)}] >>".encode()
    pdf.write(path, 1)


def jbig2(pages, symbols):
    if not symbols:
        # Generic regions: one page a run, its PDF stream on stdout
        out = []
        for p in pages:
            w, h = Image.open(p).size
            data = subprocess.run(["jbig2", "-p", p], check=True, capture_output=True).stdout
            header = (f"/Type /XObject /Subtype /Image /Width {w} /Height {h} /ColorSpace /DeviceGray "
                      f"/BitsPerComponent 1 /Filter /JBIG2Decode")
            out.append((w, h, header, data))
        return out, None
    with tempfile.TemporaryDirectory() as tmp:
        base = os.path.join(tmp, "out")
        args = ["jbig2", "-p", "-b", base] + (["-s", "-t", "0.85"] if symbols else []) + pages
        subprocess.run(args, check=True, capture_output=True)
        out = []
        for i, p in enumerate(pages):
            w, h = Image.open(p).size
            data = open(f"{base}.{i:04d}", "rb").read()
            parms = " /DecodeParms << /JBIG2Globals GLOBALS >>" if symbols else ""
            header = (f"/Type /XObject /Subtype /Image /Width {w} /Height {h} /ColorSpace /DeviceGray "
                      f"/BitsPerComponent 1 /Filter /JBIG2Decode{parms}")
            out.append((w, h, header, data))
        globals_data = open(f"{base}.sym", "rb").read() if symbols else None
        return out, globals_data


def g4(pages, count, path):
    """CCITT Group 4 by libtiff's tiff2pdf, which carries the TIFF's G4 data into the PDF as it is."""
    images = [Image.open(p).convert("1") for p in pages]
    cycle = [images[i % len(images)] for i in range(count)]
    with tempfile.TemporaryDirectory() as tmp:
        tif = os.path.join(tmp, "g4.tif")
        cycle[0].save(tif, save_all=True, append_images=cycle[1:], compression="group4", dpi=(DPI, DPI))
        subprocess.run(["tiff2pdf", "-o", path, tif], check=True)


def jpeg(pages, scale, quality):
    out = []
    for p in pages:
        im = Image.open(p).convert("L").filter(ImageFilter.GaussianBlur(0.8))
        if scale != 1:
            im = im.resize((int(im.width * scale), int(im.height * scale)), Image.LANCZOS)
        buf = io.BytesIO()
        im.save(buf, format="JPEG", quality=quality)
        w, h = im.size
        header = (f"/Type /XObject /Subtype /Image /Width {w} /Height {h} /ColorSpace /DeviceGray "
                  f"/BitsPerComponent 8 /Filter /DCTDecode")
        # Shown at the page's size whatever the image's resolution
        out.append((int(w / scale), int(h / scale), header, buf.getvalue()))
    return out


def main():
    src, dst, count = sys.argv[1], sys.argv[2], int(sys.argv[3])
    os.makedirs(dst, exist_ok=True)
    pages = sorted(glob.glob(os.path.join(src, "p*.png")))
    imgs, glob_data = jbig2(pages, symbols=True)
    build(os.path.join(dst, "jbig2-sym.pdf"), imgs, count, glob_data)
    imgs, _ = jbig2(pages, symbols=False)
    build(os.path.join(dst, "jbig2-gen.pdf"), imgs, count)
    g4(pages, count, os.path.join(dst, "g4.pdf"))
    build(os.path.join(dst, "jpeg300.pdf"), jpeg(pages, 1, 50), count)
    build(os.path.join(dst, "jpeg150.pdf"), jpeg(pages, 0.5, 60), count)
    # One page alone, as a tractate cut into a PDF an amud would have it
    imgs, _ = jbig2(pages[:1], symbols=False)
    build(os.path.join(dst, "jbig2-gen-1page.pdf"), imgs, 1)
    for f in sorted(glob.glob(os.path.join(dst, "*.pdf"))):
        n = 1 if f.endswith("-1page.pdf") else count
        print(f"{os.path.basename(f):20} {os.path.getsize(f) / 1e6:6.1f} MB  {os.path.getsize(f) / n / 1e3:5.0f} KB/page")


if __name__ == "__main__":
    main()
