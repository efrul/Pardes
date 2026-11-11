// npm install --prefix <temp-dir> @hebcal/leyning@10.1.0 @hebcal/core@6.14.4 @hebcal/hdate@0.23.1
// node tools/generate_readings.mjs <temp-dir>
import {mkdirSync, writeFileSync} from 'node:fs';
import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';
import {gzipSync} from 'node:zlib';

const requireTool = createRequire(resolve(process.argv[2], 'package.json'));
const load = name => import(pathToFileURL(requireTool.resolve(name)).href);
const {HDate, months} = await load('@hebcal/hdate');
const {getSedra} = await load('@hebcal/core/dist/esm/sedra');
const {getLeyningForHolidayKey, getLeyningOnDate, getWeekdayReading} = await load('@hebcal/leyning');

const bookTitles = {
  Genesis: 'בראשית', Exodus: 'שמות', Leviticus: 'ויקרא', Numbers: 'במדבר', Deuteronomy: 'דברים',
  Joshua: 'יהושע', Judges: 'שופטים', 'I Samuel': 'שמואל א', 'II Samuel': 'שמואל ב',
  'I Kings': 'מלכים א', 'II Kings': 'מלכים ב', Isaiah: 'ישעיהו', Jeremiah: 'ירמיהו', Ezekiel: 'יחזקאל',
  Hosea: 'הושע', Joel: 'יואל', Amos: 'עמוס', Obadiah: 'עובדיה', Jonah: 'יונה', Micah: 'מיכה',
  Nachum: 'נחום', Habakkuk: 'חבקוק', Zephaniah: 'צפניה', Haggai: 'חגי', Zechariah: 'זכריה', Malachi: 'מלאכי',
  'Song of Songs': 'שיר השירים', Ruth: 'רות', Lamentations: 'איכה', Ecclesiastes: 'קהלת', Esther: 'אסתר',
};

const roshHashana = year => new HDate(1, months.TISHREI, year);
function signature(year) {
  const start = roshHashana(year);
  return `${start.getDay()}-${roshHashana(year + 1).abs() - start.abs()}`;
}

// The 14 possible year types repeat. The inputs to the traditional reading schedule
// are weekday of Rosh Hashana, length of the year and Israel/diaspora.
const samples = new Map();
for (let year = 5700; year <= 5900; year++) {
  if (!samples.has(signature(year))) samples.set(signature(year), year);
}
if (samples.size !== 14) throw Error(`Expected 14 year types, got ${samples.size}`);

function nextWeekdayAliyot(shabbat, il) {
  let next = shabbat.add(7, 'days');
  const first = getSedra(next.getFullYear(), il).lookup(next);
  if (first.chag && next.getMonth() === months.TISHREI && next.getDate() > 2 && next.getDate() <= (il ? 22 : 23)) {
    return getWeekdayReading(['Vezot Haberakhah']);
  }
  while (true) {
    const result = getSedra(next.getFullYear(), il).lookup(next);
    if (!result.chag) return getWeekdayReading(result.parsha);
    next = next.add(7, 'days');
  }
}

const pack = (passages, numericLabels = false) =>
  Object.entries(passages?.k ? [passages] : passages ?? {}).map(([label, passage]) => {
    const title = bookTitles[passage.k];
    if (!title) throw Error(`No database title for ${passage.k}`);
    return [numericLabels ? label : '', title, passage.b, passage.e].join(';');
  }).join('|');

const rows = [];
function add(key, il, offset, service, reading, aliyot = reading?.fullkriyah ?? reading?.weekday, megillah = reading?.megillah) {
  if (!aliyot && !megillah && !reading?.haft) return;
  rows.push([
    key, Number(il), offset, service, reading?.name.he ?? '',
    pack(aliyot, true), pack(reading?.haft), pack(reading?.seph), pack(megillah),
  ].join('\t'));
}

for (const [key, year] of [...samples].sort()) {
  const start = roshHashana(year);
  const length = roshHashana(year + 1).abs() - start.abs();
  for (const il of [false, true]) {
    for (let offset = 0; offset < length; offset++) {
      const day = new HDate(start.abs() + offset);
      const shabbat = day.getDay() === 6;
      const readings = getLeyningOnDate(day, il, true);
      const modern = /^(Yom HaAtzma|Yom Yerushalayim)/;
      const morning = readings.find(r =>
        !modern.test(r.name.en) && !/Mincha|Afternoon|^Erev|^Shushan Purim/.test(r.name.en) &&
        (r.fullkriyah?.['1'] || r.weekday?.['1']));
      add(key, il, offset, 'morning', morning);
      const mincha = readings.find(r => /Mincha|Afternoon/.test(r.name.en) && r.fullkriyah?.['1']);
      if (mincha) {
        add(key, il, offset, 'mincha', mincha);
      } else if (shabbat) {
        add(key, il, offset, 'mincha', null, nextWeekdayAliyot(day, il));
      }
      const night = readings.find(r => r.name.en === 'Erev Simchat Torah');
      if (night) add(key, il, offset + 1, 'night', night);
      if (morning?.megillah && ['Esther', 'Lamentations'].includes(Object.values(morning.megillah)[0].k)) {
        add(key, il, offset, 'night', morning, null, morning.megillah);
      }
      if (day.getMonth() === (HDate.isLeapYear(year) ? months.ADAR_II : months.ADAR_I)) {
        const shushan = getLeyningForHolidayKey('Shushan Purim');
        if (day.getDate() === 15 && !shabbat) {
          add(key, il, offset, 'walled_morning', shushan);
          add(key, il, offset, 'walled_night', shushan, null, shushan.megillah);
        }
        if (day.getDate() === 14 && day.getDay() === 5) {
          add(key, il, offset, 'walled_morning', shushan);
          add(key, il, offset, 'walled_night', shushan, null, shushan.megillah);
        }
        if (day.getDate() === 15 && shabbat && morning) {
          const zachor = getLeyningForHolidayKey('Shabbat Zachor');
          const maftir = {k: 'Exodus', b: '17:8', e: '17:16'};
          add(key, il, offset, 'walled_morning', {...morning, haft: zachor.haft, seph: zachor.seph},
            {...morning.fullkriyah, M: maftir});
        }
      }
    }
  }
}
const directory = new URL('../src/jvmMain/resources/open-siddur/', import.meta.url);
mkdirSync(directory, {recursive: true});
writeFileSync(new URL('readings.tsv.gz', directory), gzipSync(rows.join('\n') + '\n', {level: 9}));
console.log(`Generated ${rows.length} schedules across ${samples.size} year types`);
