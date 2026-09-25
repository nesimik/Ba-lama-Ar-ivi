package com.baglamaarsivim.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

enum class KaynakTipi { PAYLASIM, LINK, DOSYA }
enum class DokumanTipi { PDF, IMAGE, WORD }

@Entity(tableName = "turku")
data class Turku(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ad: String,
    val eklenmeTarihi: Long = System.currentTimeMillis(),
    val favori: Boolean = false,
    val ozelSiraNo: Int = 0,
    val notMetni: String? = null
)

@Entity(
    tableName = "video",
    foreignKeys = [ForeignKey(
        entity = Turku::class, parentColumns = ["id"], childColumns = ["turkuId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("turkuId")]
)
data class Video(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val turkuId: Long,
    val baslik: String,
    /** Uygulamanın dahili "videolar" klasöründeki dosya adı */
    val dosyaAdi: String,
    val kaynakTipi: KaynakTipi,
    val eklenmeTarihi: Long = System.currentTimeMillis(),
    val boyutByte: Long = 0,
    val sonKonumMs: Long = 0,
    val yedekTarihi: Long? = null,
    /** En son ne zaman izlendi — ana ekrandaki "Kaldığın yerden devam" kartı için */
    val sonIzlenme: Long? = null
)

@Entity(
    tableName = "dokuman",
    foreignKeys = [ForeignKey(
        entity = Turku::class, parentColumns = ["id"], childColumns = ["turkuId"],
        onDelete = ForeignKey.SET_NULL
    )],
    indices = [Index("turkuId")]
)
data class Dokuman(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** null ise doküman hiçbir türküye bağlı değildir (sadece Notalar Arşivi'nde görünür) */
    val turkuId: Long?,
    /** Kullanıcıya görünen ad */
    val dosyaAdi: String,
    /** Dahili "dokumanlar" klasöründeki dosya adı */
    val dosya: String,
    val tip: DokumanTipi,
    val eklenmeTarihi: Long = System.currentTimeMillis(),
    val boyutByte: Long = 0,
    val yedekTarihi: Long? = null
)

/** Bir doküman sayfasının üzerine çizilen not/çizim katmanı. Orijinal dosya hiç değişmez. */
@Entity(
    tableName = "isaret",
    foreignKeys = [ForeignKey(
        entity = Dokuman::class, parentColumns = ["id"], childColumns = ["dokumanId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["dokumanId", "sayfaNo"], unique = true)]
)
data class Isaret(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dokumanId: Long,
    val sayfaNo: Int,
    /** JSON: normalize (0..1) koordinatlı çizgiler ve metin notları */
    val vektorVerisi: String,
    val guncellemeTarihi: Long = System.currentTimeMillis()
)

data class TurkuOzet(
    @Embedded val turku: Turku,
    val videoSayisi: Int,
    val dokumanSayisi: Int,
    val ilkVideo: String?
)

data class SonIzlenen(
    @Embedded val video: Video,
    val turkuAdi: String
)

data class DokumanOzet(
    @Embedded val dokuman: Dokuman,
    val turkuAdi: String?
)

@Dao
interface ArsivDao {
    // ---- Türkü ----
    @Query(
        """SELECT t.*,
        (SELECT COUNT(*) FROM video v WHERE v.turkuId = t.id) AS videoSayisi,
        (SELECT COUNT(*) FROM dokuman d WHERE d.turkuId = t.id) AS dokumanSayisi,
        (SELECT v2.dosyaAdi FROM video v2 WHERE v2.turkuId = t.id ORDER BY v2.eklenmeTarihi LIMIT 1) AS ilkVideo
        FROM turku t"""
    )
    fun turkuOzetleri(): Flow<List<TurkuOzet>>

    @Query("SELECT * FROM turku ORDER BY ad COLLATE NOCASE")
    fun turkulerFlow(): Flow<List<Turku>>

    @Query("SELECT * FROM turku WHERE id = :id")
    fun turkuFlow(id: Long): Flow<Turku?>

    @Query("SELECT * FROM turku WHERE id = :id")
    suspend fun turku(id: Long): Turku?

    @Query("SELECT * FROM turku")
    suspend fun tumTurkuler(): List<Turku>

    @Query("SELECT * FROM turku WHERE ad = :ad LIMIT 1")
    suspend fun turkuAdla(ad: String): Turku?

    @Query("SELECT COALESCE(MAX(ozelSiraNo), 0) FROM turku")
    suspend fun maxSira(): Int

    @Insert
    suspend fun turkuEkle(t: Turku): Long

    @Update
    suspend fun turkuGuncelle(t: Turku)

    @Update
    suspend fun turkulerGuncelle(t: List<Turku>)

    @Delete
    suspend fun turkuSil(t: Turku)

    // ---- Video ----
    @Query("SELECT * FROM video WHERE turkuId = :turkuId ORDER BY eklenmeTarihi")
    fun videolarFlow(turkuId: Long): Flow<List<Video>>

    @Query("SELECT * FROM video WHERE turkuId = :turkuId")
    suspend fun videolar(turkuId: Long): List<Video>

    @Query("SELECT * FROM video")
    suspend fun tumVideolar(): List<Video>

    @Query("SELECT * FROM video WHERE id = :id")
    suspend fun video(id: Long): Video?

    @Insert
    suspend fun videoEkle(v: Video): Long

    @Update
    suspend fun videoGuncelle(v: Video)

    @Delete
    suspend fun videoSil(v: Video)

    @Query("DELETE FROM video WHERE turkuId = :turkuId")
    suspend fun turkuVideolariniSil(turkuId: Long)

    @Query("UPDATE video SET sonKonumMs = :ms, sonIzlenme = :zaman WHERE id = :id")
    suspend fun konumKaydet(id: Long, ms: Long, zaman: Long)

    @Query("SELECT v.*, t.ad AS turkuAdi FROM video v JOIN turku t ON v.turkuId = t.id WHERE v.sonIzlenme IS NOT NULL ORDER BY v.sonIzlenme DESC LIMIT 1")
    fun sonIzlenen(): Flow<SonIzlenen?>

    // ---- Doküman ----
    @Query("SELECT * FROM dokuman WHERE turkuId = :turkuId ORDER BY eklenmeTarihi")
    fun dokumanlarFlow(turkuId: Long): Flow<List<Dokuman>>

    @Query("SELECT * FROM dokuman WHERE turkuId = :turkuId")
    suspend fun turkuDokumanlari(turkuId: Long): List<Dokuman>

    @Query("SELECT d.*, t.ad AS turkuAdi FROM dokuman d LEFT JOIN turku t ON d.turkuId = t.id ORDER BY d.eklenmeTarihi DESC")
    fun dokumanOzetleri(): Flow<List<DokumanOzet>>

    @Query("SELECT * FROM dokuman")
    suspend fun tumDokumanlar(): List<Dokuman>

    @Query("SELECT * FROM dokuman WHERE id = :id")
    suspend fun dokuman(id: Long): Dokuman?

    @Insert
    suspend fun dokumanEkle(d: Dokuman): Long

    @Update
    suspend fun dokumanGuncelle(d: Dokuman)

    @Delete
    suspend fun dokumanSil(d: Dokuman)

    @Query("UPDATE dokuman SET turkuId = NULL WHERE turkuId = :turkuId")
    suspend fun dokumanlariAyir(turkuId: Long)

    // ---- İşaret (çizim katmanı) ----
    @Query("SELECT * FROM isaret WHERE dokumanId = :dokumanId AND sayfaNo = :sayfa LIMIT 1")
    suspend fun isaret(dokumanId: Long, sayfa: Int): Isaret?

    @Query("SELECT * FROM isaret WHERE dokumanId = :dokumanId")
    suspend fun isaretler(dokumanId: Long): List<Isaret>

    @Query("SELECT * FROM isaret")
    suspend fun tumIsaretler(): List<Isaret>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun isaretKaydet(i: Isaret)

    @Query("DELETE FROM isaret WHERE dokumanId = :dokumanId AND sayfaNo = :sayfa")
    suspend fun isaretSil(dokumanId: Long, sayfa: Int)

    @Query("DELETE FROM isaret WHERE dokumanId = :dokumanId")
    suspend fun isaretleriSil(dokumanId: Long)

    // ---- Yedek durumu ----
    @Query("UPDATE video SET yedekTarihi = :t")
    suspend fun tumVideolarYedeklendi(t: Long)

    @Query("UPDATE dokuman SET yedekTarihi = :t")
    suspend fun tumDokumanlarYedeklendi(t: Long)

    @Query("UPDATE video SET yedekTarihi = :t WHERE turkuId = :turkuId")
    suspend fun turkuVideolariYedeklendi(turkuId: Long, t: Long)

    @Query("UPDATE dokuman SET yedekTarihi = :t WHERE turkuId = :turkuId")
    suspend fun turkuDokumanlariYedeklendi(turkuId: Long, t: Long)

    @Query("SELECT COUNT(*) FROM video WHERE yedekTarihi IS NULL")
    fun yedeksizVideo(): Flow<Int>

    @Query("SELECT COUNT(*) FROM dokuman WHERE yedekTarihi IS NULL")
    fun yedeksizDokuman(): Flow<Int>

    // ---- Tam temizlik (geri yüklemede "değiştir" modu) ----
    @Query("DELETE FROM isaret")
    suspend fun isaretleriTemizle()

    @Query("DELETE FROM dokuman")
    suspend fun dokumanlariTemizle()

    @Query("DELETE FROM video")
    suspend fun videolariTemizle()

    @Query("DELETE FROM turku")
    suspend fun turkuleriTemizle()
}

@Database(
    entities = [Turku::class, Video::class, Dokuman::class, Isaret::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): ArsivDao

    companion object {
        @Volatile
        private var ornek: AppDatabase? = null

        fun get(ctx: Context): AppDatabase = ornek ?: synchronized(this) {
            ornek ?: Room.databaseBuilder(ctx.applicationContext, AppDatabase::class.java, "baglama_arsivim.db")
                .build().also { ornek = it }
        }
    }
}
