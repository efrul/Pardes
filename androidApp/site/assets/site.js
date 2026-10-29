// The site of Pardes, its four pages' one script. Each part acts only where its elements are.
//
// Data: releases.json (every release with a Pardes APK) and downloads.json (Pardes for desktop's installers, the
// semantic search's zips, the library's files), both written by android-site.yml at every deploy, which runs again
// after every release, in the GitHub API's shape. They are read from this site, not from api.github.com, which allows
// 60 calls an hour per IP and which some filtering proxies block. Only the downloads page asks the API, for the
// current download count and for the library's newest files (SeforimLibrary releases on its own), and keeps what the
// snapshots say when it can't.
//
// A value goes where an element names it: data-text="apk.version" takes the text, data-href="apk.url" the link.
// An element with data-need="apk.sha" is shown once that value is known.
(function () {
  'use strict';
  var REPO = 'efrul/Pardes';
  var API = 'https://api.github.com/repos/' + REPO + '/releases?per_page=100';
  var LIBRARY_API = 'https://api.github.com/repos/kdroidFilter/SeforimLibrary/releases/latest';
  var page = document.body.getAttribute('data-page');
  var $ = function (id) { return document.getElementById(id); };
  var each = function (selector, fn) { Array.prototype.forEach.call(document.querySelectorAll(selector), fn); };

  // The one-page site's addresses, which installed apps (AppReleases.kt opens #changelog), the READMEs and older
  // release notes still link to.
  var MOVED = {
    changelog: 'changelog.html', downloads: 'downloads.html', 'dl-library': 'downloads.html#library',
    'dl-meaning': 'downloads.html#meaning', install: 'guide.html#phone', desktop: 'guide.html#computer',
    library: 'guide.html#library', shots: '#features'
  };
  ['lib-download', 'lib-export', 'lib-subset', 'lib-computer', 'lib-files', 'lib-card', 'lib-update', 'lib-meaning']
    .forEach(function (id) { MOVED[id] = 'guide.html#' + id; });
  if (page === 'home' && MOVED[location.hash.slice(1)]) {
    location.replace(MOVED[location.hash.slice(1)]);
    return;
  }

  function esc(s) {
    return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }

  // Dates: the Hebrew date in letters, as a sefer prints it (ז׳ בחשוון תשפ״ז), then the civil date.
  var ONES = ['', 'א', 'ב', 'ג', 'ד', 'ה', 'ו', 'ז', 'ח', 'ט'];
  var TENS = ['', 'י', 'כ', 'ל', 'מ', 'נ', 'ס', 'ע', 'פ', 'צ'];
  var HUNDREDS = ['', 'ק', 'ר', 'ש', 'ת'];
  function letters(n) {
    var s = '';
    while (n >= 400) { s += 'ת'; n -= 400; }
    if (n >= 100) { s += HUNDREDS[Math.floor(n / 100)]; n %= 100; }
    if (n === 15) s += 'טו';
    else if (n === 16) s += 'טז';
    else s += TENS[Math.floor(n / 10)] + ONES[n % 10];
    return s.length > 1 ? s.slice(0, -1) + '״' + s.slice(-1) : s + '׳';
  }
  function hebrew(d) {
    try {
      var day = 0, month = '', year = 0;
      new Intl.DateTimeFormat('he-IL-u-ca-hebrew', { day: 'numeric', month: 'long', year: 'numeric' }).formatToParts(d)
        .forEach(function (p) {
          if (p.type === 'day') day = +p.value;
          else if (p.type === 'month') month = p.value;
          else if (p.type === 'year') year = +p.value;
        });
      return day && month && year ? letters(day) + ' ב' + month + ' ' + letters(year % 1000) : '';
    } catch (e) { return ''; }
  }
  function civil(d) { return d.toLocaleDateString('he-IL', { day: 'numeric', month: 'long', year: 'numeric' }); }
  function both(d) { var h = hebrew(d); return h ? h + ' · ' + civil(d) : civil(d); }
  // A changelog date (YYYY-MM-DD) is a day, not an instant: noon local time, so no time zone moves it.
  function day(ymd) { var p = ymd.split('-'); return new Date(+p[0], p[1] - 1, +p[2], 12); }
  function mb(bytes) { return Math.round(bytes / 1048576).toLocaleString('he-IL') + ' מ״ב'; }
  // A manifest is a few hundred bytes: in kilobytes, not as 0 megabytes
  function size(bytes) { return bytes < 1048576 ? Math.max(1, Math.round(bytes / 1024)) + ' ק״ב' : mb(bytes); }
  function gb(bytes) { return (bytes / 1073741824).toLocaleString('he-IL', { maximumFractionDigits: 1 }) + ' ג״ב'; }
  function newer(a, b) {
    var x = a.split('.'), y = b.split('.');
    for (var i = 0; i < Math.max(x.length, y.length); i++) {
      var d = (+x[i] || 0) - (+y[i] || 0);
      if (d) return d > 0;
    }
    return false;
  }

  function set(key, value) {
    if (value == null || value === '') return;
    each('[data-text="' + key + '"]', function (el) { el.textContent = value; });
    each('[data-href="' + key + '"]', function (el) { el.href = value; });
    each('[data-need="' + key + '"]', function (el) { el.hidden = false; });
  }

  // A filtering proxy may hold a request open instead of refusing it: give up after a while.
  function get(url, ms, asText) {
    var ctl = window.AbortController ? new AbortController() : null;
    var timer = ctl ? setTimeout(function () { ctl.abort(); }, ms) : 0;
    var opts = { cache: 'no-cache' };
    if (ctl) opts.signal = ctl.signal;
    if (!asText && /^https:/.test(url)) opts.headers = { Accept: 'application/vnd.github+json' };
    return fetch(url, opts)
      .then(function (r) { if (!r.ok) throw new Error(r.status); return asText ? r.text() : r.json(); })
      .then(function (v) { clearTimeout(timer); return v; }, function (e) { clearTimeout(timer); throw e; });
  }

  // ── Pardes for Android: android-<versionName>-<run>[-r<attempt>], as android-release.yml tags it. The newest release
  // with an APK is offered; a pre-release is a build for testers, offered only when there is no other release, and does
  // not mark its version released. Every APK's downloads are counted.
  function android(list) {
    var latest = null, pre = null, total = 0, released = {};
    list.forEach(function (r) {
      var apks = (r.assets || []).filter(function (a) { return /^pardes-.*\.apk$/i.test(a.name); });
      if (r.draft || !apks.length) return;
      apks.forEach(function (a) { total += a.download_count || 0; });
      var v = (/^android-(\d+(?:\.\d+)*)-\d+/.exec(r.tag_name || '') || [])[1] || null;
      if (r.prerelease) { if (!pre) pre = { rel: r, apk: apks[0], version: v }; return; }
      if (v) released[v] = r.published_at; // newest first: the last one seen is the version's first build
      if (!latest) latest = { rel: r, apk: apks[0], version: v };
    });
    latest = latest || pre;
    if (latest) { latest.total = total; latest.released = released; }
    return latest;
  }
  function showAndroid(a, asOf) {
    var sha = (a.apk.digest || '').replace(/^sha256:/, '');
    set('apk.url', a.apk.browser_download_url);
    set('apk.name', a.apk.name);
    set('apk.version', a.version || a.rel.tag_name);
    set('apk.size', mb(a.apk.size));
    set('apk.date', both(new Date(a.rel.published_at)));
    set('apk.sha', sha);
    set('apk.total', a.total.toLocaleString('he-IL'));
    set('apk.asof', asOf ? 'נכון ל־' + civil(new Date(asOf)) : 'נכון לעכשיו');
    if (a.rel.prerelease) set('apk.pre', 'גרסת ניסיון');
  }

  // ── Pardes for desktop: the newest desktop-<version>-<run> that is not a pre-release, its installers as
  // desktop-release.yml names them.
  var DESKTOP = {
    win: /-windows-x64-nsis\.exe$/i, winarm: /-windows-arm64-nsis\.exe$/i,
    mac: /-macos-arm64\.dmg$/i, macintel: /-macos-x64\.dmg$/i,
    deb: /_amd64\.deb$/i, rpm: /\.x86_64\.rpm$/i, pacman: /-x86_64\.pacman$/i,
    debarm: /_arm64\.deb$/i, rpmarm: /\.aarch64\.rpm$/i, pacmanarm: /-aarch64\.pacman$/i
  };
  function desktop(list) {
    var rel = list.filter(function (r) {
      return !r.draft && !r.prerelease && /^desktop-\d/.test(r.tag_name || '') &&
        (r.assets || []).some(function (a) { return /\.exe$/i.test(a.name); });
    })[0];
    if (!rel) return null;
    var released = {};
    list.forEach(function (r) {
      var v = (/^desktop-(\d+(?:\.\d+)*)-/.exec(r.tag_name || '') || [])[1];
      if (v && !r.draft && !r.prerelease) released[v] = r.published_at;
    });
    return { rel: rel, version: (/^desktop-(\d+(?:\.\d+)*)-/.exec(rel.tag_name) || [])[1] || rel.tag_name, released: released };
  }
  function showDesktop(d) {
    set('desk.version', d.version);
    set('desk.date', both(new Date(d.rel.published_at)));
    set('desk.page', d.rel.html_url);
    Object.keys(DESKTOP).forEach(function (k) {
      var a = (d.rel.assets || []).filter(function (x) { return DESKTOP[k].test(x.name); })[0];
      if (!a) return;
      set('desk.' + k + '.url', a.browser_download_url);
      set('desk.' + k + '.name', a.name);
      set('desk.' + k + '.size', mb(a.size));
    });
  }

  // ── The semantic search's files: semantic-<number>-<library>, as semantic-release.yml tags it, with its two zips.
  // The highest number, and of its builds the newest library's, as the apps pick one (SemanticPackReleases.newest: the
  // files work on every library release).
  function pack(list) {
    var best = null;
    list.forEach(function (r) {
      var m = /^semantic-(\d+)-(.+)$/.exec(r.tag_name || '');
      if (r.draft || r.prerelease || !m) return;
      var zips = ['pardes-model.zip', 'pardes.vec.lucene.zip'].map(function (name) {
        return (r.assets || []).filter(function (a) { return a.name === name; })[0];
      });
      if (!zips[0] || !zips[1]) return;
      var n = +m[1], key = m[2].replace(/^.*-/, '').replace(/^v/, '');
      if (!best || n > best.n || (n === best.n && key > best.key)) best = { n: n, key: key, zips: zips, rel: r };
    });
    return best;
  }
  function showPack(p) {
    set('pack.version', String(p.n));
    set('pack.size', gb(p.zips[0].size + p.zips[1].size));
    set('pack.model.url', p.zips[0].browser_download_url);
    set('pack.model.size', size(p.zips[0].size));
    set('pack.index.url', p.zips[1].browser_download_url);
    set('pack.index.size', size(p.zips[1].size));
    set('pack.page', p.rel.html_url);
  }

  // ── The library's files (SeforimLibrary's latest release): every seforim_bundle.tar.zst file, in order.
  function showLibrary(rel) {
    var list = $('library-files');
    var files = ((rel && rel.assets) || []).filter(function (a) { return a.name.indexOf('seforim_bundle.tar.zst') === 0; });
    if (!list || !files.length) return false;
    files.sort(function (a, b) { return a.name < b.name ? -1 : a.name > b.name ? 1 : 0; });
    list.innerHTML = files.map(function (a) {
      return '<li><a href="' + esc(a.browser_download_url) + '"><span class="ltr">' + esc(a.name) + '</span></a><span class="size">' + size(a.size) + '</span></li>';
    }).join('');
    list.hidden = false;
    set('library.tag', rel.tag_name);
    set('library.page', rel.html_url);
    set('library.size', gb(files.reduce(function (s, a) { return s + a.size; }, 0)));
    set('library.count', String(files.length));
    return true;
  }

  // ── The data, as the snapshots have it; on the downloads page the API's answer replaces it.
  var releases = get('releases.json', 10000).catch(function () { return null; });
  var downloads = get('downloads.json', 10000).catch(function () { return null; });
  var state = { android: null, desktop: null };
  releases.then(function (d) {
    var a = d && Array.isArray(d.releases) && android(d.releases);
    if (a && !state.live) { state.android = a; showAndroid(a, d.generated_at); }
    marks();
  });
  downloads.then(function (d) {
    if (!d || !Array.isArray(d.releases)) return;
    var k = desktop(d.releases), p = pack(d.releases);
    if (k) { state.desktop = k; showDesktop(k); }
    if (p) showPack(p);
    if (page === 'downloads' && !state.libraryLive) showLibrary(d.library);
    marks();
  });
  if (page === 'downloads') {
    // Up to 1000 releases, 100 a page
    (function api(n, all) {
      return get(API + '&page=' + n, 10000).then(function (list) {
        if (!Array.isArray(list)) throw new Error('not a list');
        all = all.concat(list);
        return list.length === 100 && n < 10 ? api(n + 1, all) : all;
      });
    })(1, []).then(function (list) {
      var a = android(list);
      if (a) { state.live = true; state.android = a; showAndroid(a, null); }
    }).catch(function () {});
    get(LIBRARY_API, 10000).then(function (rel) { if (showLibrary(rel)) state.libraryLive = true; }).catch(function () {});
  }

  // ── The device this page is opened on: which tab of the download box, and what the home page's button downloads.
  var ua = navigator.userAgent, os = 'android';
  if (/Android/i.test(ua)) os = 'android';
  else if (/Windows/i.test(ua)) os = 'windows';
  else if (/Macintosh|Mac OS X/i.test(ua) && !/iPhone|iPad|iPod/i.test(ua) && !(navigator.maxTouchPoints > 1)) os = 'mac';
  else if (/Linux|X11|CrOS/i.test(ua)) os = 'linux';

  // Tabs: a [role=tablist] of buttons, each naming its panel. In RTL the next tab is to the left.
  function tabs(list, onSelect) {
    var all = Array.prototype.slice.call(list.querySelectorAll('[role="tab"]'));
    function select(tab, focus) {
      all.forEach(function (t) {
        var on = t === tab;
        t.setAttribute('aria-selected', on ? 'true' : 'false');
        t.tabIndex = on ? 0 : -1;
        $(t.getAttribute('aria-controls')).hidden = !on;
      });
      if (focus) tab.focus();
      if (onSelect) onSelect(tab);
    }
    all.forEach(function (t, i) {
      t.addEventListener('click', function () { select(t, false); });
      t.addEventListener('keydown', function (e) {
        var n = all.length, j = null;
        if (e.key === 'ArrowLeft') j = (i + 1) % n;
        else if (e.key === 'ArrowRight') j = (i - 1 + n) % n;
        else if (e.key === 'Home') j = 0;
        else if (e.key === 'End') j = n - 1;
        if (j === null) return;
        e.preventDefault();
        select(all[j], true);
      });
    });
    return function (key) {
      var t = all.filter(function (x) { return x.getAttribute('data-key') === key; })[0] || all[0];
      select(t, false);
    };
  }

  var getTabs = $('get-tabs');
  if (getTabs) {
    var choose = tabs(getTabs);
    choose(os);
    var rec = document.querySelector('#p-' + os + ' .rec');
    if (rec) rec.hidden = false;
    each('[data-go]', function (a) { a.addEventListener('click', function () { choose(a.getAttribute('data-go')); }); });
    var hero = $('hero-get');
    if (hero) {
      var main = { android: 'apk.url', windows: 'desk.win.url', mac: 'desk.mac.url' }[os];
      if (main) hero.setAttribute('data-href', main);
      $('hero-get-label').textContent = { android: 'הורדה לאנדרואיד', windows: 'הורדה ל־Windows', mac: 'הורדה למק', linux: 'הורדה ללינוקס' }[os];
    }
  }

  // ── The changelogs: androidApp/CHANGELOG.md (changelog.md) and desktop/CHANGELOG.md (changelog-desktop.md). Their
  // own small Markdown: "## <version> · <date>" starts a version, "###" a heading in it, "- " a list item, `code`,
  // **bold** and [text](https://…) inline; the title and introduction before the first version, and HTML lines, are left
  // out.
  function inline(s) {
    return s.split('`').map(function (part, i) {
      if (i % 2) return '<code>' + esc(part) + '</code>';
      return esc(part)
        .replace(/\*\*(.+?)\*\*/g, '<b>$1</b>')
        .replace(/\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g, '<a href="$2">$1</a>');
    }).join('');
  }
  function renderLog(md, el) {
    var out = [], open = false, inList = false, first = true;
    function endList() { if (inList) { out.push('</ul>'); inList = false; } }
    function endVersion() { endList(); if (open) { out.push('</div></details>'); open = false; } }
    md.replace(/\r/g, '').split('\n').forEach(function (line) {
      var m = /^## +(\d+(?:\.\d+)*)(?:\s+·\s+(\d{4}-\d{2}-\d{2}))?\s*$/.exec(line);
      if (m) {
        endVersion();
        out.push('<details class="version" data-version="' + esc(m[1]) + '"' + (first ? ' open' : '') + '><summary>' +
          '<h3>גרסה <span class="ltr">' + esc(m[1]) + '</span></h3><span class="tag" hidden></span>' +
          '<span class="date">' + (m[2] ? esc(both(day(m[2]))) : '') + '</span></summary><div class="body">');
        open = true;
        first = false;
        return;
      }
      if (!open || /^\s*</.test(line)) return;
      if ((m = /^### +(.*)$/.exec(line))) { endList(); out.push('<h4>' + inline(m[1]) + '</h4>'); return; }
      if ((m = /^- +(.*)$/.exec(line))) {
        if (!inList) { out.push('<ul>'); inList = true; }
        out.push('<li>' + inline(m[1]) + '</li>');
        return;
      }
      if (!line.trim()) { endList(); return; }
      endList();
      out.push('<p>' + inline(line) + '</p>');
    });
    endVersion();
    if (first) throw new Error('no versions');
    el.innerHTML = out.join('');
    el.setAttribute('data-ready', '');
  }

  // Once a changelog and its releases are in: the newest release's version is marked, and a version the changelog
  // already describes but no release has yet (the site deploys on a push, the release comes after) says so, with the
  // newest released version opened beside it. A released version is dated by its first release.
  function mark(el, info) {
    if (!el || !el.hasAttribute('data-ready') || !info || !info.version) return;
    Array.prototype.forEach.call(el.querySelectorAll('.version'), function (d) {
      var v = d.getAttribute('data-version'), tag = d.querySelector('.tag');
      var soon = !info.released[v] && newer(v, info.version);
      tag.textContent = v === info.version ? 'האחרונה' : soon ? 'בקרוב' : '';
      tag.className = soon ? 'tag soon' : 'tag';
      tag.hidden = !tag.textContent;
      if (info.released[v]) d.querySelector('.date').textContent = both(new Date(info.released[v]));
      var prev = d.previousElementSibling;
      if (v === info.version && !d.open && prev && !info.released[prev.getAttribute('data-version')]) d.open = true;
    });
  }
  function marks() {
    mark($('log-android'), state.android);
    mark($('log-desktop'), state.desktop);
  }
  [['log-android', 'changelog.md', 'androidApp/CHANGELOG.md'], ['log-desktop', 'changelog-desktop.md', 'desktop/CHANGELOG.md']]
    .forEach(function (l) {
      var el = $(l[0]);
      if (!el) return;
      get(l[1], 10000, true)
        .then(function (md) { renderLog(md, el); marks(); })
        .catch(function () {
          el.innerHTML = '<p class="status">יומן השינויים לא נטען. הוא נמצא גם ב־<a href="https://github.com/' + REPO + '/blob/main/' + l[2] + '">GitHub</a>.</p>';
        });
    });
  var logTabs = $('log-tabs');
  if (logTabs) {
    var chooseLog = tabs(logTabs, function (t) {
      if (history.replaceState) history.replaceState(null, '', '#' + t.getAttribute('data-key'));
    });
    var wanted = location.hash.slice(1);
    chooseLog(wanted === 'desktop' || wanted === 'android' ? wanted : os === 'android' ? 'android' : 'desktop');
  }

  // ── Copy buttons: data-copy names the element to copy; a command's button copies the command beside it.
  function selectText(el) {
    var r = document.createRange();
    r.selectNodeContents(el);
    var s = window.getSelection();
    s.removeAllRanges();
    s.addRange(r);
  }
  function copyFrom(button, el) {
    var label = button.textContent;
    var done = function () {
      button.textContent = 'הועתק';
      setTimeout(function () { button.textContent = label; }, 2500);
    };
    if (navigator.clipboard) navigator.clipboard.writeText(el.textContent).then(done, function () { selectText(el); });
    else selectText(el);
  }
  each('[data-copy]', function (b) { b.addEventListener('click', function () { copyFrom(b, $(b.getAttribute('data-copy'))); }); });
  each('.cmd .copy', function (b) { b.addEventListener('click', function () { copyFrom(b, b.parentNode.querySelector('pre')); }); });

  // ── A tool's screenshot opens at full size; without <dialog> the link opens the picture itself.
  var viewer = $('viewer');
  if (viewer && typeof viewer.showModal === 'function') {
    each('a.shot', function (a) {
      a.addEventListener('click', function (e) {
        e.preventDefault();
        var fig = a.parentNode;
        $('viewer-img').src = a.getAttribute('href');
        $('viewer-img').alt = a.querySelector('img').alt;
        $('viewer-cap').textContent = fig.querySelector('figcaption').textContent;
        viewer.showModal();
      });
    });
    $('viewer-close').addEventListener('click', function () { viewer.close(); });
    viewer.addEventListener('click', function (e) { if (e.target === viewer) viewer.close(); });
  }
})();
