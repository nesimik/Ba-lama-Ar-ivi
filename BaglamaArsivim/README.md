# Bağlama Arşivim 🎵

Saz (bağlama) derslerinin videolarını ve notalarını telefonda **kalıcı, düzenli ve çevrimdışı** tutan Android uygulaması.
Kotlin + Jetpack Compose + Room + Media3 (ExoPlayer) + WorkManager.

## APK'yı alma (GitHub)

1. GitHub'da **yeni, boş** bir repo aç (ör. `baglama-arsivim`).
2. **Add file → Upload files** ile zip'ten çıkan dosyaları yükle → **Commit changes**.
   - Proje bir alt klasöre düşse de sorun değil, derleme onu kendisi bulur.
   - Repoda projenin **tek kopyası** olsun. İkinci kopya varsa derleme bilerek durur.
3. **ÖNEMLİ:** Web yüklemesi `.github` gibi noktayla başlayan klasörleri atlar. Bu yüzden derleme dosyasını elle oluştur:
   - **Add file → Create new file**
   - Dosya adı kutusuna tam olarak `.github/workflows/apk.yml` yaz.
   - İçine `GITHUB_ACTIONS_apk.yml` dosyasının tamamını yapıştır → **Commit changes**.
4. **Actions** sekmesi → "APK Oluştur" çalışır (yaklaşık 5–8 dk). Çalışmazsa **Run workflow**.
5. Yeşil tik → çalışmaya tıkla → en altta **Artifacts → BaglamaArsivim-apk** indir → zip'i aç → `app-debug.apk`'yı telefona kur.

Kırmızı hata çıkarsa: kırmızı adıma tıkla, `e:` ile başlayan satırları kopyalayıp düzeltme iste.

### Güncelleme kurmak
- Uygulama sabit bir imza anahtarıyla (`app/imza/baglama.keystore`) imzalanır.
  Yeni APK eskisinin **üzerine** kurulur, arşivin silinmez. Bu dosyayı silme ya da değiştirme.
- Her güncellemede `app/build.gradle.kts` içindeki `versionCode`'u 1 artır.
- `applicationId` (`com.baglamaarsivim`) değiştirilirse telefonda ayrı bir uygulama olarak kurulur.

## Özellikler
- **WhatsApp'tan paylaşımla ekleme:** Videoyu/notayı aç → Paylaş → Bağlama Arşivim → türkü seç. Dosya anında telefona kopyalanır.
- **Link'ten indirme** (doğrudan video linkleri; YouTube vb. desteklenmez), bildirimde ilerleme, iptal.
- **Türkü arşivi:** arama, 5 sıralama (tarih, alfabe, özel sıra ↑↓), liste/ızgara görünümü, favoriler.
- **Video oynatıcı:** 0.5x–1.5x hız (ton değişmez, seçilen hız hatırlanır), A-B tekrar döngüsü, ±5 sn atlama,
  kaldığı yerden devam, tam ekran. **Video + nota aynı ekranda:** nota videonun altında açılır, video çalmaya devam eder.
- **Ana ekranda "Kaldığın yerden devam" kartı** ve 14 günden uzun süredir yedek alınmadıysa hatırlatma.
- **Notalar Arşivi:** PDF / resim / Word; kamerayla tarama; türküye bağlı ya da bağımsız.
- **Nota üzerine not alma:** kalem (6 renk, kalınlık), silgi, metin notu, geri al / yinele, yakınlaştırma.
  Çizimler ayrı katmanda saklanır, **orijinal dosya hiç değişmez**. PNG veya işaretli PDF olarak dışa aktarılır.
- **Hocanın notları:** her türküye serbest metin alanı.
- **Yedekleme:** tek .zip; kaydetme ekranında **Drive** seçilirse doğrudan Google Drive'a gider. Geri yükleme: birleştir veya tümünü değiştir.
  Her dosyada "Yedeklendi ✅ / Yedeklenmedi" durumu.

## Bilinçli sadeleştirmeler (ileride eklenebilir)
- Drive yedeği Google API yerine Android'in dosya kaydetme ekranı üzerinden (Google Cloud ayarı / giriş gerekmez).
- Özel sıralama sürükle-bırak yerine ↑↓ oklarla (Arşiv → sıralama "Özel sıra" → ⇅ düğmesi).
- Tarama sistem kamerasıyla fotoğraf olarak (ML Kit kenar algılama yok).
- Word dosyaları harici uygulamada açılır (kodda WebView önizleme için TODO var).

## Proje yapısı
```
app/src/main/java/com/baglamaarsivim/
├── BaglamaApp.kt, MainActivity.kt   (uygulama, gezinme, paylaşım karşılama)
├── data/   Veri.kt (Room), ArsivRepository.kt, Yedekleme.kt, Katman.kt (çizim katmanı, PDF/resim, dışa aktarım)
├── work/   IndirmeWorker.kt (link indirme)
├── ui/     ekranlar: AnaEkran, TurkuDetayEkrani, OynaticiEkrani, DokumanEkrani, NotalarEkrani, AyarlarEkrani, IceAktarEkrani
└── util/   Yardimcilar.kt
```
