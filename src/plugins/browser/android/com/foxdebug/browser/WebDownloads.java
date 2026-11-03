package com.foxdebug.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;
import android.webkit.WebView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.json.JSONObject;

/**
 * Irham.s.a WebCode IDE (v0.4.0)
 *
 * Menangani semua unduhan dari WebView preview:
 *  - http/https/localhost : diunduh langsung oleh aplikasi (bukan DownloadManager,
 *    yang tidak bisa membaca blob: dan sering gagal untuk server lokal).
 *  - data:                : didecode langsung.
 *  - blob:                : dibaca lewat JavaScript (HOOK_SCRIPT) lalu dikirim ke
 *                           sini per potongan lewat jembatan JavascriptInterface.
 *
 * File disimpan ke folder Download. Setiap error ditampilkan di dialog yang
 * teksnya bisa dipilih dan disalin (tombol "Salin").
 */
public class WebDownloads {

  public static final String BRIDGE_NAME = "__WebCodeDownload";
  private static final String TAG = "WebCodeDownloads";
  private static final int MAX_FILE_NAME = 120;

  /** Disuntikkan ke setiap halaman. Aman dijalankan berulang (idempoten). */
  public static final String HOOK_SCRIPT =
    "(function(){" +
    "if(window.__webcodeDlHooked)return;" +
    "var B=window." + BRIDGE_NAME + ";if(!B)return;" +
    "window.__webcodeDlHooked=true;" +
    "var blobs=new Map();var seq=0;" +
    "var oc=URL.createObjectURL.bind(URL);" +
    "URL.createObjectURL=function(o){var u=oc(o);try{if(o instanceof Blob)blobs.set(u,o);}catch(e){}return u;};" +
    "var orv=URL.revokeObjectURL.bind(URL);" +
    "URL.revokeObjectURL=function(u){setTimeout(function(){blobs.delete(u);},60000);return orv(u);};" +
    "function readChunks(blob,id){var CH=393216,off=0;" +
    "function next(){if(off>=blob.size){B.end(id);return Promise.resolve();}" +
    "return new Promise(function(res,rej){var fr=new FileReader();" +
    "fr.onload=function(){var s=String(fr.result);B.chunk(id,s.substring(s.indexOf(',')+1));off+=CH;res();};" +
    "fr.onerror=function(){rej(fr.error||new Error('FileReader error'));};" +
    "fr.readAsDataURL(blob.slice(off,off+CH));}).then(next);}" +
    "return next();}" +
    "function send(blob,name,mime){var id='d'+Date.now()+'_'+(seq++);" +
    "B.begin(id,String(name||'download'),String(mime||blob.type||'application/octet-stream'),blob.size);" +
    "return readChunks(blob,id).catch(function(e){B.fail(id,String((e&&e.stack)||(e&&e.message)||e));});}" +
    "function fromUrl(url,name,mime){var b=blobs.get(url);if(b)return send(b,name,mime);" +
    "return fetch(url).then(function(r){return r.blob();}).then(function(b){return send(b,name,mime);})" +
    ".catch(function(e){B.fail('','Gagal membaca '+String(url).slice(0,80)+'\\n'+String((e&&e.stack)||(e&&e.message)||e));});}" +
    "window.__webcodeSend=fromUrl;" +
    "function special(a){if(!a||!a.hasAttribute||!a.hasAttribute('download'))return false;" +
    "var h=a.href||'';return h.indexOf('blob:')===0||h.indexOf('data:')===0;}" +
    "function nm(a){return a.getAttribute('download')||'download';}" +
    "var oclick=HTMLAnchorElement.prototype.click;" +
    "HTMLAnchorElement.prototype.click=function(){if(special(this)){fromUrl(this.href,nm(this),'');return;}return oclick.apply(this,arguments);};" +
    "var odis=EventTarget.prototype.dispatchEvent;" +
    "EventTarget.prototype.dispatchEvent=function(ev){if(this instanceof HTMLAnchorElement&&ev&&ev.type==='click'&&special(this)){fromUrl(this.href,nm(this),'');return true;}return odis.apply(this,arguments);};" +
    "document.addEventListener('click',function(ev){var t=ev.target;var a=t&&t.closest?t.closest('a'):null;" +
    "if(special(a)){ev.preventDefault();fromUrl(a.href,nm(a),'');}},true);" +
    "})();";

  private final Context context;
  private final WebView webView;
  private final Handler ui = new Handler(Looper.getMainLooper());
  private final Map<String, Session> sessions = new HashMap<>();

  /** Satu unduhan blob yang sedang diterima dari JavaScript. */
  private static class Session {

    String name;
    String mime;
    File temp;
    OutputStream out;
    boolean failed;
  }

  public WebDownloads(Context context, WebView webView) {
    this.context = context;
    this.webView = webView;
  }

  /** Objek yang didaftarkan ke WebView dengan nama BRIDGE_NAME. */
  public Object bridge() {
    return new Bridge();
  }

  /** Menyuntikkan skrip pengait ke halaman yang sedang dimuat. */
  public void injectHooks() {
    webView.evaluateJavascript(HOOK_SCRIPT, null);
  }

  // ------------------------------------------------------------------
  // Dipanggil dari DownloadListener WebView
  // ------------------------------------------------------------------

  public void handle(
    final String url,
    final String userAgent,
    final String contentDisposition,
    final String mimeType,
    final long contentLength
  ) {
    try {
      final String mime = isEmpty(mimeType)
        ? "application/octet-stream"
        : mimeType;
      if (url.startsWith("blob:")) {
        injectHooks();
        String name = fixName(
          URLUtil.guessFileName(url, contentDisposition, mime),
          mime
        );
        String js =
          "window.__webcodeSend&&window.__webcodeSend(" +
          JSONObject.quote(url) +
          "," +
          JSONObject.quote(name) +
          "," +
          JSONObject.quote(mime) +
          ");";
        ui.post(() -> webView.evaluateJavascript(js, null));
        return;
      }

      final String name = fixName(
        URLUtil.guessFileName(url, contentDisposition, mime),
        mime
      );
      confirm(
        name,
        contentLength,
        () ->
          new Thread(
            () -> downloadUrl(url, userAgent, name, mime),
            "webcode-download"
          )
            .start(),
        null
      );
    } catch (Throwable e) {
      showError("Unduhan gagal dimulai", e);
    }
  }

  private void downloadUrl(
    String url,
    String userAgent,
    String name,
    String mime
  ) {
    try {
      if (url.startsWith("data:")) {
        byte[] data = decodeDataUrl(url);
        Saved saved = newTarget(name, mime);
        try {
          saved.out.write(data);
        } finally {
          saved.out.close();
        }
        saved.finish();
        toast("Tersimpan: " + saved.label);
        return;
      }

      HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
      conn.setConnectTimeout(15000);
      conn.setReadTimeout(30000);
      conn.setInstanceFollowRedirects(true);
      if (!isEmpty(userAgent)) conn.setRequestProperty("User-Agent", userAgent);
      String cookies = CookieManager.getInstance().getCookie(url);
      if (!isEmpty(cookies)) conn.setRequestProperty("Cookie", cookies);
      int code = conn.getResponseCode();
      if (code < 200 || code >= 300) {
        throw new IOException("Server membalas HTTP " + code + " untuk " + url);
      }

      Saved saved = newTarget(name, mime);
      try (InputStream in = conn.getInputStream()) {
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) != -1) saved.out.write(buf, 0, n);
      } finally {
        saved.out.close();
        conn.disconnect();
      }
      saved.finish();
      toast("Tersimpan: " + saved.label);
    } catch (Throwable e) {
      showError("Unduhan gagal: " + name, e);
    }
  }

  private static byte[] decodeDataUrl(String url) throws IOException {
    int comma = url.indexOf(',');
    if (comma < 0) throw new IOException("data: URL tidak valid");
    String header = url.substring(5, comma);
    String body = url.substring(comma + 1);
    if (header.endsWith(";base64")) {
      return Base64.decode(body, Base64.DEFAULT);
    }
    return Uri.decode(body).getBytes("UTF-8");
  }

  // ------------------------------------------------------------------
  // Jembatan JavaScript (blob:)
  // ------------------------------------------------------------------

  private class Bridge {

    @JavascriptInterface
    public void begin(String id, String name, String mime, long size) {
      try {
        Session s = new Session();
        s.mime = isEmpty(mime) ? "application/octet-stream" : mime;
        s.name = fixName(name, s.mime);
        File dir = new File(context.getCacheDir(), "webcode-downloads");
        if (!dir.exists() && !dir.mkdirs()) {
          throw new IOException("Tidak bisa membuat folder " + dir);
        }
        s.temp = File.createTempFile("dl", ".part", dir);
        s.out = new FileOutputStream(s.temp);
        synchronized (sessions) {
          sessions.put(id, s);
        }
      } catch (Throwable e) {
        showError("Unduhan gagal dimulai", e);
      }
    }

    @JavascriptInterface
    public void chunk(String id, String base64) {
      Session s;
      synchronized (sessions) {
        s = sessions.get(id);
      }
      if (s == null || s.failed) return;
      try {
        s.out.write(Base64.decode(base64, Base64.DEFAULT));
      } catch (Throwable e) {
        s.failed = true;
        cleanup(id);
        showError("Gagal menulis data unduhan", e);
      }
    }

    @JavascriptInterface
    public void end(final String id) {
      final Session s;
      synchronized (sessions) {
        s = sessions.get(id);
      }
      if (s == null || s.failed) return;
      try {
        s.out.close();
      } catch (IOException e) {
        cleanup(id);
        showError("Gagal menutup file sementara", e);
        return;
      }
      confirm(
        s.name,
        s.temp.length(),
        () ->
          new Thread(() -> saveSession(id, s), "webcode-download").start(),
        () -> cleanup(id)
      );
    }

    @JavascriptInterface
    public void fail(String id, String message) {
      if (!isEmpty(id)) cleanup(id);
      showError("Unduhan gagal", message == null ? "(tanpa pesan)" : message);
    }
  }

  private void saveSession(String id, Session s) {
    try {
      Saved saved = newTarget(s.name, s.mime);
      try (InputStream in = new java.io.FileInputStream(s.temp)) {
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) != -1) saved.out.write(buf, 0, n);
      } finally {
        saved.out.close();
      }
      saved.finish();
      toast("Tersimpan: " + saved.label);
    } catch (Throwable e) {
      showError("Gagal menyimpan " + s.name, e);
    } finally {
      cleanup(id);
    }
  }

  private void cleanup(String id) {
    Session s;
    synchronized (sessions) {
      s = sessions.remove(id);
    }
    if (s == null) return;
    try {
      if (s.out != null) s.out.close();
    } catch (IOException ignored) {}
    if (s.temp != null) //noinspection ResultOfMethodCallIgnored
      s.temp.delete();
  }

  // ------------------------------------------------------------------
  // Penyimpanan ke folder Download
  // ------------------------------------------------------------------

  private static class Saved {

    OutputStream out;
    String label;
    Runnable onFinish;

    void finish() {
      if (onFinish != null) onFinish.run();
    }
  }

  private Saved newTarget(String name, String mime) throws IOException {
    final Saved saved = new Saved();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      final ContentResolver resolver = context.getContentResolver();
      ContentValues values = new ContentValues();
      values.put(MediaStore.Downloads.DISPLAY_NAME, name);
      values.put(MediaStore.Downloads.MIME_TYPE, mime);
      values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
      values.put(MediaStore.Downloads.IS_PENDING, 1);
      final Uri uri = resolver.insert(
        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
        values
      );
      if (uri == null) throw new IOException("MediaStore menolak membuat file " + name);
      saved.out = resolver.openOutputStream(uri);
      if (saved.out == null) throw new IOException("Tidak bisa membuka " + uri);
      saved.label = "Download/" + name;
      saved.onFinish = () -> {
        ContentValues done = new ContentValues();
        done.put(MediaStore.Downloads.IS_PENDING, 0);
        resolver.update(uri, done, null, null);
      };
      return saved;
    }

    File dir = Environment.getExternalStoragePublicDirectory(
      Environment.DIRECTORY_DOWNLOADS
    );
    File target = uniqueFile(dir, name);
    try {
      if (!dir.exists() && !dir.mkdirs()) {
        throw new IOException("Tidak bisa membuat " + dir);
      }
      saved.out = new FileOutputStream(target);
    } catch (IOException | SecurityException e) {
      // Tanpa izin penyimpanan: pakai folder khusus aplikasi.
      Log.w(TAG, "Folder Download tidak bisa ditulis, pakai folder aplikasi", e);
      File fallback = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
      if (fallback == null) throw new IOException("Tidak ada penyimpanan yang bisa ditulis", e);
      if (!fallback.exists()) //noinspection ResultOfMethodCallIgnored
        fallback.mkdirs();
      target = uniqueFile(fallback, name);
      saved.out = new FileOutputStream(target);
    }
    saved.label = target.getAbsolutePath();
    return saved;
  }

  private static File uniqueFile(File dir, String name) {
    File f = new File(dir, name);
    if (!f.exists()) return f;
    int dot = name.lastIndexOf('.');
    String base = dot > 0 ? name.substring(0, dot) : name;
    String ext = dot > 0 ? name.substring(dot) : "";
    for (int i = 1; i < 1000; i++) {
      f = new File(dir, base + " (" + i + ")" + ext);
      if (!f.exists()) return f;
    }
    return new File(dir, base + "-" + System.currentTimeMillis() + ext);
  }

  // ------------------------------------------------------------------
  // Dialog dan util
  // ------------------------------------------------------------------

  private void confirm(
    final String name,
    final long size,
    final Runnable yes,
    final Runnable no
  ) {
    ui.post(() -> {
      try {
        String sizeText = size > 0 ? " (" + formatSize(size) + ")" : "";
        new AlertDialog.Builder(context)
          .setTitle("Simpan file")
          .setMessage("Simpan \"" + name + "\"" + sizeText + " ke folder Download?")
          .setPositiveButton("Simpan", (d, w) -> yes.run())
          .setNegativeButton("Batal", (d, w) -> {
            if (no != null) no.run();
          })
          .setOnCancelListener(d -> {
            if (no != null) no.run();
          })
          .show();
      } catch (Throwable e) {
        showError("Tidak bisa menampilkan dialog unduhan", e);
      }
    });
  }

  private void toast(final String message) {
    ui.post(() -> Toast.makeText(context, message, Toast.LENGTH_LONG).show());
  }

  public void showError(String title, Throwable e) {
    showError(title, Log.getStackTraceString(e));
  }

  public void showError(final String title, final String detail) {
    Log.e(TAG, title + "\n" + detail);
    showErrorDialog(context, title, detail);
  }

  /** Dialog error dengan teks yang bisa dipilih dan tombol Salin. */
  public static void showErrorDialog(
    final Context context,
    final String title,
    final String detail
  ) {
    new Handler(Looper.getMainLooper()).post(() -> {
      try {
        int pad = (int) (16 * context.getResources().getDisplayMetrics().density);
        TextView text = new TextView(context);
        text.setText(detail);
        text.setTextIsSelectable(true);
        text.setTextSize(12);
        text.setPadding(pad, pad, pad, pad);
        ScrollView scroll = new ScrollView(context);
        scroll.addView(
          text,
          new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
          )
        );
        new AlertDialog.Builder(context)
          .setTitle(title)
          .setView(scroll)
          .setPositiveButton("Salin", (d, w) -> {
            ClipboardManager cm = (ClipboardManager) context.getSystemService(
              Context.CLIPBOARD_SERVICE
            );
            if (cm != null) {
              cm.setPrimaryClip(
                ClipData.newPlainText("error", title + "\n\n" + detail)
              );
              Toast.makeText(context, "Error disalin", Toast.LENGTH_SHORT).show();
            }
          })
          .setNegativeButton("Tutup", null)
          .show();
      } catch (Throwable ignored) {
        Log.e(TAG, "Tidak bisa menampilkan dialog error", ignored);
      }
    });
  }

  /** Mencari Activity di balik ContextWrapper. */
  public static Activity findActivity(Context context) {
    while (context instanceof ContextWrapper) {
      if (context instanceof Activity) return (Activity) context;
      context = ((ContextWrapper) context).getBaseContext();
    }
    return null;
  }

  private static boolean isEmpty(String s) {
    return s == null || s.trim().isEmpty();
  }

  private static String formatSize(long bytes) {
    if (bytes < 1024) return bytes + " B";
    if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
    return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
  }

  /** Membersihkan nama file dan menambah ekstensi dari MIME jika belum ada. */
  static String fixName(String name, String mime) {
    String n = isEmpty(name) ? "download" : name.trim();
    n = n.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
    while (n.startsWith(".")) n = n.substring(1);
    if (n.isEmpty()) n = "download";
    if (n.length() > MAX_FILE_NAME) {
      int dot = n.lastIndexOf('.');
      String ext = dot > 0 && n.length() - dot <= 10 ? n.substring(dot) : "";
      n = n.substring(0, MAX_FILE_NAME - ext.length()) + ext;
    }
    if (n.indexOf('.') < 0 && !isEmpty(mime)) {
      String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
      if (ext != null) n = n + "." + ext;
    }
    return n;
  }
}
