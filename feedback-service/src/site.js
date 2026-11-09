/**
 * The service's site (GET /): what users sent, open to everyone, and a form to upload packages saved to files. The page
 * holds no data: it reads the public route (GET /v1/public/packages), where each sender is an alias, and posts uploads
 * to POST /v1/packages, as the app does. Its files are modules (wrangler.toml's rules): the page, script and style as
 * text, the font as data. What came in a package reaches the page only as text, and the CSP below runs no
 * script but the page's own file.
 */

import css from "./site/style.css";
import js from "./site/app.js";
import html from "./site/index.html";
import font from "./site/display.woff2";

const HEADERS = {
  "x-content-type-options": "nosniff",
  "referrer-policy": "no-referrer",
  "x-frame-options": "DENY",
  "cross-origin-opener-policy": "same-origin",
  "content-security-policy":
    "default-src 'none'; script-src 'self'; style-src 'self'; font-src 'self'; connect-src 'self'; " +
    "img-src 'self' blob: data:; base-uri 'none'; form-action 'none'; frame-ancestors 'none'",
};

const FRESH = "no-store";
/** The font never changes under its name: a new one gets a new name. */
const LASTING = "public, max-age=2592000, immutable";

const FILES = {
  "/": [html, "text/html; charset=utf-8", FRESH],
  "/site/app.js": [js, "text/javascript; charset=utf-8", FRESH],
  "/site/style.css": [css, "text/css; charset=utf-8", FRESH],
  "/site/display.woff2": [font, "font/woff2", LASTING],
};

/** The site's file at [pathname], a redirect from the old maintainer's page (/admin) to it, or null. */
export function siteFile(pathname) {
  if (pathname === "/admin" || pathname === "/admin/") {
    return new Response(null, { status: 301, headers: { location: "/", "cache-control": FRESH } });
  }
  const file = FILES[pathname];
  if (!file) return null;
  const [body, type, cache] = file;
  return new Response(body, { headers: { "content-type": type, "cache-control": cache, ...HEADERS } });
}
