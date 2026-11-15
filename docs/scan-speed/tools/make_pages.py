"""Synthetic scans of Talmud pages, laid out as a Vilna daf, for benchmarking a scan viewer.

Each page is a bitonal image at a scan's resolution: the Gemara in the middle, Rashi and Tosafot in the side
columns, small marginal notes at the edges, a header, a scan's specks and ragged edges. The text is real (Berakhot
2a), repeated; its words are shuffled per page so that no two pages compress alike.

    python3 make_pages.py <out dir> <pages> <dpi> [first]
"""

import os
import random
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

FONTS = "/usr/share/fonts/truetype/culmus"
GEMARA = (
    "מאימתי קורין את שמע בערבית משעה שהכהנים נכנסים לאכול בתרומתן עד סוף האשמורה הראשונה דברי רבי אליעזר "
    "וחכמים אומרים עד חצות רבן גמליאל אומר עד שיעלה עמוד השחר מעשה ובאו בניו מבית המשתה אמרו לו לא קרינו את "
    "שמע אמר להם אם לא עלה עמוד השחר חייבין אתם לקרות ולא זו בלבד אמרו אלא כל מה שאמרו חכמים עד חצות מצותן עד "
    "שיעלה עמוד השחר הקטר חלבים ואברים מצותן עד שיעלה עמוד השחר וכל הנאכלים ליום אחד מצותן עד שיעלה עמוד השחר "
    "אם כן למה אמרו חכמים עד חצות כדי להרחיק אדם מן העבירה גמרא תנא היכא קאי דקתני מאימתי ותו מאי שנא דתני "
    "בערבית ברישא לתני דשחרית ברישא תנא אקרא קאי דכתיב בשכבך ובקומך והכי קתני זמן קריאת שמע דשכיבה אימת "
    "משעה שהכהנים נכנסין לאכול בתרומתן ואי בעית אימא יליף מברייתו של עולם דכתיב ויהי ערב ויהי בקר יום אחד"
)
RASHI = (
    "מאימתי קורין את שמע בערבית משעה שהכהנים נכנסים לאכול בתרומתן כהנים שנטמאו וטבלו והעריב שמשן והגיע עתם "
    "לאכול בתרומה עד סוף האשמורה הראשונה שליש הלילה כדמפרש בגמרא ומשם ואילך עבר זמן דלא מיקרי תו זמן שכיבה "
    "ולא קרינן ביה בשכבך ומקמי הכי נמי לאו זמן שכיבה לפיכך הקורא קודם לכן לא יצא ידי חובתו אם כן למה קורין "
    "אותה בבית הכנסת כדי לעמוד בתפלה מתוך דברי תורה והכי תניא בברייתא בברכות ירושלמי ולפיכך חובה עלינו לקרותה"
)
TOSAFOT = (
    "מאימתי קורין את שמע בערבית פירש רש\"י ואנן היכי קרינן מבעוד יום ואין אנו ממתינין לצאת הכוכבים כדמפרש "
    "בגמרא ועל כן פירש שקריאת שמע שעל המטה עיקר והיא לאחר צאת הכוכבים והכי איתא בירושלמי אם קרא קודם לכן לא "
    "יצא ואם כן למה מתפללין בבית הכנסת כדי לעמוד בתפלה מתוך דברי תורה תימה לפירושו והלא אין העולם רגילין "
    "לסמוך קריאת שמע שעל המטה אלא פרשה ראשונה ועוד קשה דצריך לברך בקריאת שמע שתים לפניה ושתים לאחריה בערבית"
)


def words(text, rng):
    w = text.split()
    rng.shuffle(w)
    return w


def column(draw, text, font, box, line_h, rng):
    """Fills [box] (x0, y0, x1, y1) with [text]'s words, right to left, justified as the daf's lines."""
    x0, y0, x1, y1 = box
    pool = []
    y = y0
    while y + line_h <= y1:
        line = []
        width = 0
        space = draw.textlength(" ", font=font)
        while True:
            if not pool:
                pool = words(text, rng)
            w = pool[-1]
            ww = draw.textlength(w, font=font)
            if line and width + space + ww > x1 - x0:
                break
            line.append(w)
            width += ww + (space if len(line) > 1 else 0)
            pool.pop()
        # Justified: the words spread over the whole width, from the right
        total = sum(draw.textlength(w, font=font) for w in line)
        gap = (x1 - x0 - total) / max(1, len(line) - 1)
        x = x1
        for w in line:
            ww = draw.textlength(w, font=font)
            draw.text((x - ww, y), w, font=font, fill=0, direction="rtl", language="he")
            x -= ww + gap
        y += line_h


def page(n, dpi, rng):
    s = dpi / 300
    w, h = int(2950 * s), int(4490 * s)
    img = Image.new("L", (w, h), 255)
    d = ImageDraw.Draw(img)
    main = ImageFont.truetype(f"{FONTS}/FrankRuehlCLM-Medium.ttf", int(54 * s))
    side = ImageFont.truetype(f"{FONTS}/KeterYG-Medium.ttf", int(38 * s))
    note = ImageFont.truetype(f"{FONTS}/MiriamCLM-Book.ttf", int(26 * s))
    head = ImageFont.truetype(f"{FONTS}/FrankRuehlCLM-Bold.ttf", int(72 * s))
    d.text((w // 2, int(170 * s)), f"ברכות פרק ראשון דף {n}", font=head, fill=0, anchor="mm", direction="rtl")
    d.line([(int(300 * s), int(260 * s)), (w - int(300 * s), int(260 * s))], fill=0, width=int(4 * s))
    top, bottom = int(330 * s), int(4250 * s)
    column(d, GEMARA, main, (int(1060 * s), top, int(1890 * s), bottom), int(74 * s), rng)
    column(d, RASHI, side, (int(1950 * s), top, int(2660 * s), bottom), int(52 * s), rng)
    column(d, TOSAFOT, side, (int(290 * s), top, int(1000 * s), bottom), int(52 * s), rng)
    column(d, RASHI, note, (int(2700 * s), top, int(2900 * s), int(2600 * s)), int(36 * s), rng)
    column(d, TOSAFOT, note, (int(60 * s), top, int(250 * s), int(2600 * s)), int(36 * s), rng)
    # A scan: soft edges thresholded with noise, and specks
    img = img.filter(ImageFilter.GaussianBlur(0.9 * s))
    px = img.load()
    for _ in range(int(2500 * s * s)):
        x, y, r = rng.randrange(w), rng.randrange(h), rng.choice((1, 1, 2, 3))
        for dx in range(r):
            for dy in range(r):
                if x + dx < w and y + dy < h:
                    px[x + dx, y + dy] = 0
    noise = Image.effect_noise((w, h), 40).filter(ImageFilter.GaussianBlur(0.5))
    img = Image.blend(img, noise, 0.18)
    return img.point(lambda v: 0 if v < 140 else 255, mode="1")


def main():
    out, pages, dpi = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
    first = int(sys.argv[4]) if len(sys.argv) > 4 else 0
    os.makedirs(out, exist_ok=True)
    for i in range(first, pages):
        rng = random.Random(i)
        path = os.path.join(out, f"p{i:04d}.png")
        if not os.path.exists(path):
            page(i + 2, dpi, rng).save(path, optimize=True)
        print(path, flush=True)


if __name__ == "__main__":
    main()
