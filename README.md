# A1 Android v1.3 COMPLETE

A1 Stock Intelligence — Android source project.

## Yang sudah dikerjakan
- Android UI dasar
- Room database: event, ownership, watchlist
- WorkManager sinkronisasi setiap 24 jam
- HTTP collector untuk halaman pengumuman publik IDX/BEI
- Jsoup parser dan klasifikasi event
- SHA-256 fingerprint untuk deduplikasi
- notifikasi Android saat event baru ditemukan
- struktur ownership untuk histori/deteksi perubahan

## Batas penting
Data kepemilikan publik >1%/>5% berasal dari KSEI dan dipublikasikan oleh BEI. Endpoint internal/berlisensi tidak ditebak atau dibypass. Jika situs IDX mengembalikan 403 atau mengubah struktur, adapter perlu diperbarui atau dipindahkan ke collector server-side yang menggunakan akses resmi.

## Build
Buka folder ini di Android Studio, Gradle Sync, lalu Build > Build APK(s).

Project ini adalah source code; APK belum diklaim sebagai hasil build karena lingkungan ini tidak menyediakan Android SDK/Gradle build toolchain.
