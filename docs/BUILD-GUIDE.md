# Panduan Build — Irham.s.a WebCode IDE

Panduan ini ditujukan untuk kamu dan agent AI di VS Code. Semua perintah dijalankan dari **folder root proyek** (yang berisi `package.json` dan `config.xml`).

- Nama aplikasi: **Irham.s.a WebCode IDE**
- Package name: **com.irham.s.a.web.code.ide**
- Versi saat ini: **0.4.0** (versionCode 4) — lihat `docs/versions/`

> **Catatan jujur:** bundle JavaScript dan test sudah diuji saat paket ini dibuat (lihat bagian 9). Tahap Gradle/Android (APK) **belum bisa dijalankan** di lingkungan pembuatan paket ini karena tidak ada Android SDK. Jadi kalau Gradle menampilkan error, itu bahan untuk diagnosis di bagian 8, bukan berarti panduan ini salah total.

---

## 1. Kebutuhan (prasyarat)

Nilai di bawah diambil dari `CONTRIBUTING.md` dan `config.xml` milik Acode:

| Alat | Versi |
|---|---|
| Node.js | 20 LTS atau lebih baru |
| Java JDK | 17 atau lebih baru (21 disarankan) |
| Android SDK | platform **API 36** (targetSdk 36), minSdk 26, plus Build-Tools dan Platform-Tools |
| Gradle | 8.x (diunduh otomatis oleh Cordova/gradle wrapper) |
| Git | untuk submodule |
| Shell | Linux/macOS/WSL/**Git Bash** untuk `npm run build`; di Windows PowerShell pakai perintah manual (bagian 5) |

Variabel lingkungan yang wajib ada:

```
JAVA_HOME      -> folder JDK
ANDROID_HOME   -> folder Android SDK  (juga ANDROID_SDK_ROOT)
PATH           += %ANDROID_HOME%\platform-tools, %ANDROID_HOME%\cmdline-tools\latest\bin
```

Terima lisensi SDK:

```
sdkmanager --licenses
sdkmanager "platforms;android-36" "build-tools;36.0.0" "platform-tools"
```

(Kalau nama build-tools di atas tidak ada, jalankan `sdkmanager --list` dan pilih versi 36.x terbaru.)

Pasang Cordova CLI:

```
npm install -g cordova
```

Cek semuanya:

```
node -v
java -version
cordova -v
adb version
```

---

## 2. Ambil source

Proyek ini memakai **submodule** `codemirror-lsp-client`. Di zip yang kamu terima isinya sudah ikut. Kalau kamu menaruh proyek ini ke Git sendiri:

```
git init
git add -A
git commit -m "WebCode IDE v0.4.0"
```

Folder `codemirror-lsp-client/` harus berisi file (ada `package.json` dan folder `dist/`). Kalau kosong, build akan gagal dengan error `Can't resolve '@codemirror/lsp-client'`. Perbaikannya: salin isi folder itu dari zip lagi, atau clone ulang dari https://github.com/Acode-Foundation/codemirror-lsp-client ke folder tersebut.

---

## 3. Setup pertama kali (sekali saja)

```
npm install --legacy-peer-deps
npm run setup
```

Jangan pakai `npm ci`: `package.json` sudah diubah dan `package-lock.json` bawaan Acode tidak sinkron lagi.

`npm run setup` menjalankan `utils/setup.js`, yang menambahkan platform Android Cordova dan memasang plugin dari `src/plugins`.

Kalau `npm run setup` bermasalah, jalur manual:

```
npx cordova platform add android
npx cordova prepare
```

(Buat folder `www/css/build` dan `www/js/build` kalau belum ada.)

---

## 4. Build APK (cara cepat, Linux/macOS/WSL/Git Bash)

Build **debug** (paling mudah, otomatis ditandatangani kunci debug, cukup untuk dipasang di HP sendiri):

```
npm run build -- paid d fdroid apk
```

Arti argumen:
- `paid` = varian tanpa iklan (di versi ini semua fitur Pro sudah selalu aktif)
- `d` = mode development/debug (`p` = production/release)
- `fdroid` = **jangan** sertakan plugin `proot` dan `iap` (Alpine/terminal online dan pembelian Google Play)
- `apk` = hasilnya APK (bukan bundle)

Hasil APK ada di:

```
platforms/android/app/build/outputs/apk/debug/app-debug.apk
```

Pasang ke HP lewat USB (USB debugging aktif):

```
adb install -r platforms/android/app/build/outputs/apk/debug/app-debug.apk
```

Atau kirim file APK ke HP dan buka dari file manager.

---

## 5. Build APK di Windows PowerShell (tanpa `sh`)

Ini adalah isi `utils/scripts/build.sh` yang diterjemahkan ke perintah satu per satu:

```
node ./utils/config.js d paid
npx rspack --mode development
npx cordova build android -- --packageType=apk
```

Kalau plugin `proot` atau `iap` pernah terpasang (mis. dari build sebelumnya), lepas dulu:

```
npx cordova plugin remove com.foxdebug.acode.rk.exec.proot
npx cordova plugin remove cordova-plugin-iap
```

(Perintah itu boleh gagal dengan pesan "not installed" kalau memang belum terpasang.)

---

## 6. Build release (untuk dibagikan atau dipasang permanen)

1. Buat keystore sekali saja, simpan baik-baik (kalau hilang, update tidak bisa dipasang di atas versi lama):

```
keytool -genkey -v -keystore keystore.jks -alias webcode -keyalg RSA -keysize 2048 -validity 10000
```

2. Buat file `build.json` di root (sudah ada di `.gitignore`):

```json
{
  "android": {
    "release": {
      "keystore": "keystore.jks",
      "storePassword": "ISI_PASSWORD",
      "alias": "webcode",
      "password": "ISI_PASSWORD",
      "keystoreType": ""
    }
  }
}
```

3. Build:

```
npm run build -- paid p fdroid apk
```

Hasil: `platforms/android/app/build/outputs/apk/release/app-release.apk`.

---

## 7. Mengembangkan dan menguji tanpa APK

Cek bundle JavaScript saja (cepat, tanpa Android SDK):

```
npx rspack --mode production
```

Jalankan test:

```
npx vitest run
```

Test tema baru ada di `tests/irhamThemes.test.js`.

---

## 8. Diagnosis error (untuk agent AI di VS Code)

Kalau build gagal, kumpulkan log lengkap dulu, jangan menebak:

```
npm run build -- paid d fdroid apk 2>&1 | Tee-Object build.log     # PowerShell
npm run build -- paid d fdroid apk 2>&1 | tee build.log            # bash
```

Lalu cocokkan pesan error dengan tabel ini:

| Pesan error | Penyebab | Perbaikan |
|---|---|---|
| `Can't resolve '@codemirror/lsp-client'` | folder `codemirror-lsp-client/` kosong | isi folder (bagian 2), lalu `npm install --legacy-peer-deps` |
| `package com.foxdebug.acode does not exist` atau `cannot find symbol R` | masih ada import `com.foxdebug.acode.R` di file Java | ganti jadi `com.irham.s.a.web.code.ide.R`. Sudah diperbaiki di `AlpineDocumentProvider.java` dan `Menu.java`; cari lainnya dengan `grep -rn "foxdebug.acode.R" src/plugins platforms` |
| `SDK location not found` | `ANDROID_HOME` belum diset | set variabel (bagian 1), atau buat `platforms/android/local.properties` berisi `sdk.dir=...` |
| `Failed to find Platform SDK with path: platforms;android-36` | API 36 belum terpasang | `sdkmanager "platforms;android-36"` |
| `Unsupported class file major version` / error JDK | versi Java tidak cocok | pakai JDK 17 atau 21 |
| `Could not resolve all files for configuration` (Gradle, offline) | Gradle tidak bisa mengunduh dependensi | build pertama **wajib online**; setelah cache terisi bisa `--offline` |
| `Plugin ... already installed` / konflik plugin | sisa build lama | `npx cordova platform rm android && npx cordova platform add android` lalu build ulang |
| Layar putih setelah dibuka | JS error saat start | `adb logcat -s chromium:V CordovaWebView:V` dan kirim hasilnya ke saya |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | ada app lama dengan tanda tangan berbeda | uninstall dulu (data aplikasi lama ikut hilang) |

Untuk melihat error runtime di HP, kumpulkan log:

```
adb logcat -d > logcat.txt
```

dan kirim `build.log` atau `logcat.txt` kepada saya supaya diagnosisnya berdasarkan jejak nyata.

---

## 9. Yang sudah diverifikasi saat paket dibuat

- `npx rspack --mode production` → selesai tanpa error (2 warning bawaan Acode).
- `npx vitest run` → 94 file test, 919 test lulus (termasuk test tema baru). Plugin browser (Java) dikompilasi dengan `javac` terhadap `android.jar` API 33.

Yang **belum** diverifikasi: Gradle build APK, instalasi dan tampilan di perangkat (ikon, tema di layar, perilaku saat tanpa internet).
