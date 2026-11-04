/**
 * Irham.s.a WebCode IDE (v0.4.1)
 *
 * Perbaikan untuk Windows: `prettier-plugin-java` membuat path file wasm dari
 * `new URL(...).pathname`, hasilnya "/G:/Projects/.../x.wasm". Di Windows,
 * Node membaca path itu sebagai "G:\G:\Projects\..." sehingga hook
 * `modify-java-files.js` gagal dengan ENOENT.
 *
 * `web-tree-sitter` memuat wasm lewat `fs/promises.readFile(path)`. Modul ini
 * membungkus fungsi itu dan membuang "/" di depan huruf drive, hanya di
 * Windows. Path lain tidak disentuh.
 */
const fs = require('fs');
const { syncBuiltinESMExports } = require('module');

/** "/G:/a/b" atau "\\G:\\a\\b" -> "G:/a/b". Selain itu dikembalikan apa adanya. */
function fixWindowsFileUrlPath(p) {
  if (typeof p !== 'string') return p;
  return p.replace(/^[\\/]+([A-Za-z]:[\\/])/, '$1');
}

/**
 * Memasang pembungkus pada fs.promises.readFile (dan readFileSync/readFile).
 * @param {{platform?: string}} [options]
 * @returns {boolean} true jika pembungkus dipasang
 */
function install(options = {}) {
  const platform = options.platform || process.platform;
  if (platform !== 'win32') return false;

  const promises = fs.promises;
  const originalPromise = promises.readFile;
  if (originalPromise.__webcodePathFix) return false;

  promises.readFile = function (p, ...rest) {
    return originalPromise.call(this, fixWindowsFileUrlPath(p), ...rest);
  };
  promises.readFile.__webcodePathFix = true;

  const originalSync = fs.readFileSync;
  fs.readFileSync = function (p, ...rest) {
    return originalSync.call(this, fixWindowsFileUrlPath(p), ...rest);
  };

  const originalCb = fs.readFile;
  fs.readFile = function (p, ...rest) {
    return originalCb.call(this, fixWindowsFileUrlPath(p), ...rest);
  };

  // `await import("fs/promises")` memakai salinan ekspor; sinkronkan.
  syncBuiltinESMExports();
  return true;
}

module.exports = { fixWindowsFileUrlPath, install };
