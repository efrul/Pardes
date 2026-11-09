/**
 * The maintainer's page (GET /admin): a page that holds no data. It asks for the admin token and reads the packages
 * with it from the maintainer's routes, as fetch.py does. Its three files are text modules (wrangler.toml's rules).
 * What came in a package reaches the page only as text, and the CSP below runs no script but its own file.
 */

import css from "./admin/style.css";
import js from "./admin/app.js";
import html from "./admin/index.html";

const HEADERS = {
  "cache-control": "no-store",
  "x-content-type-options": "nosniff",
  "referrer-policy": "no-referrer",
  "x-frame-options": "DENY",
  "cross-origin-opener-policy": "same-origin",
  "content-security-policy":
    "default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; img-src 'self' blob: data:; " +
    "base-uri 'none'; form-action 'none'; frame-ancestors 'none'",
};

const FILES = {
  "/admin": [html, "text/html; charset=utf-8"],
  "/admin/": [html, "text/html; charset=utf-8"],
  "/admin/app.js": [js, "text/javascript; charset=utf-8"],
  "/admin/style.css": [css, "text/css; charset=utf-8"],
};

/** The page's file at [pathname], or null. */
export function adminFile(pathname) {
  const file = FILES[pathname];
  if (!file) return null;
  const [body, type] = file;
  return new Response(body, { headers: { "content-type": type, ...HEADERS } });
}
