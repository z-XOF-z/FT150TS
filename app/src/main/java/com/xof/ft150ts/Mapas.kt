package com.xof.ft150ts

import android.content.Context
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import kotlinx.coroutines.flow.MutableStateFlow
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.MapTileIndex

/**
 * Estilos del mapa. [oscuro] = mientras carga se pinta negro (en AMOLED no se ve gris).
 *
 * Desde septiembre de 2026 CARTO pide llave (salía "API key required"). Ahora Noche y Claro
 * se hacen con el mapa de OpenStreetMap y un filtro de color, sin llave. Si pones tu llave
 * gratis de CARTO en Ajustes > Mapa, Noche y Claro vuelven a usar los de CARTO.
 */
enum class EstiloMapa(val nombre: String, val credito: String, val oscuro: Boolean) {
    NOCHE("Noche", "© OpenStreetMap", true),
    OSCURO("Oscuro clásico", "© OpenStreetMap", true),
    ESTANDAR("Estándar", "© OpenStreetMap", false),
    CLARO("Claro", "© OpenStreetMap", false),
    TERRENO("Terreno", "© OpenStreetMap © OpenTopoMap", false),
    SATELITE("Satélite", "Imágenes © Esri", false),
}

object Mapas {
    /** Llave de CARTO (opcional, gratis para uso personal en carto.com/basemaps). */
    val llaveCarto = MutableStateFlow("")
    private val LLAVE_VALIDA = Regex("^[A-Za-z0-9_.-]{8,200}$")

    fun iniciar(ctx: Context) {
        llaveCarto.value = ctx.applicationContext.getSharedPreferences("mapas", Context.MODE_PRIVATE)
            .getString("llaveCarto", "") ?: ""
    }

    fun ponerLlaveCarto(ctx: Context, llave: String) {
        val l = llave.trim().takeIf { it.isEmpty() || LLAVE_VALIDA.matches(it) } ?: return
        llaveCarto.value = l
        ctx.applicationContext.getSharedPreferences("mapas", Context.MODE_PRIVATE).edit().putString("llaveCarto", l).apply()
    }

    /** ¿Este estilo está usando CARTO (con tu llave)? */
    fun usaCarto(e: EstiloMapa) = llaveCarto.value.isNotBlank() && (e == EstiloMapa.NOCHE || e == EstiloMapa.CLARO)

    fun credito(e: EstiloMapa) = if (usaCarto(e)) "© OpenStreetMap © CARTO" else e.credito

    /** CARTO con llave: la llave va al final de la URL (?key=...). Nombre nuevo para no usar el caché viejo con la marca de agua. */
    private fun carto(nombre: String, ruta: String, llave: String): ITileSource =
        object : OnlineTileSourceBase(
            nombre, 0, 20, 256, ".png",
            arrayOf("https://basemaps.cartocdn.com/$ruta/"),
            "© OpenStreetMap © CARTO",
        ) {
            override fun getTileURLString(pMapTileIndex: Long): String =
                baseUrl + MapTileIndex.getZoom(pMapTileIndex) + "/" + MapTileIndex.getX(pMapTileIndex) + "/" +
                    MapTileIndex.getY(pMapTileIndex) + imageFilenameEnding() + "?key=" + llave
        }

    private val terreno: ITileSource by lazy {
        XYTileSource(
            "OpenTopo", 0, 17, 256, ".png",
            arrayOf("https://a.tile.opentopomap.org/", "https://b.tile.opentopomap.org/", "https://c.tile.opentopomap.org/"),
            "© OpenStreetMap © OpenTopoMap",
        )
    }

    /** Satélite de Esri: su URL va en orden z/y/x (al revés que los demás). */
    private val satelite: ITileSource by lazy {
        object : OnlineTileSourceBase(
            "EsriSatelite", 0, 19, 256, "",
            arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"),
            "Imágenes © Esri",
        ) {
            override fun getTileURLString(pMapTileIndex: Long): String =
                baseUrl + MapTileIndex.getZoom(pMapTileIndex) + "/" +
                    MapTileIndex.getY(pMapTileIndex) + "/" + MapTileIndex.getX(pMapTileIndex)
        }
    }

    fun fuente(e: EstiloMapa): ITileSource {
        val llave = llaveCarto.value
        return when {
            usaCarto(e) && e == EstiloMapa.NOCHE -> carto("CartoNocheLlave", "dark_all", llave)
            usaCarto(e) -> carto("CartoClaroLlave", "rastertiles/voyager", llave)
            e == EstiloMapa.TERRENO -> terreno
            e == EstiloMapa.SATELITE -> satelite
            else -> TileSourceFactory.MAPNIK      // Noche, Oscuro, Estándar y Claro: OpenStreetMap
        }
    }

    /** Filtro de color que se pone sobre los cuadros del mapa (null = colores normales). */
    fun filtro(e: EstiloMapa): ColorFilter? = when {
        usaCarto(e) -> null
        e == EstiloMapa.NOCHE -> noche
        e == EstiloMapa.OSCURO -> invertido
        e == EstiloMapa.CLARO -> claro
        else -> null
    }

    /**
     * Noche: invierte y gira el tono 180° (el blanco queda casi negro, pero el agua sigue azul
     * y los parques verdes), un poco más oscuro para AMOLED.
     */
    private val noche: ColorFilter by lazy {
        val m = ColorMatrix(INVERTIR)
        m.postConcat(ColorMatrix(GIRO_180))
        m.postConcat(ColorMatrix().apply { setScale(0.82f, 0.84f, 0.9f, 1f) })
        ColorMatrixColorFilter(m)
    }

    /** Oscuro clásico: solo invierte (como antes). */
    private val invertido: ColorFilter by lazy { ColorMatrixColorFilter(ColorMatrix(INVERTIR)) }

    /** Claro: colores suaves (menos saturados y un poco más claros), parecido al de CARTO. */
    private val claro: ColorFilter by lazy {
        val m = ColorMatrix().apply { setSaturation(0.35f) }
        m.postConcat(
            ColorMatrix(
                floatArrayOf(
                    0.92f, 0f, 0f, 0f, 22f,
                    0f, 0.92f, 0f, 0f, 22f,
                    0f, 0f, 0.92f, 0f, 24f,
                    0f, 0f, 0f, 1f, 0f,
                )
            )
        )
        ColorMatrixColorFilter(m)
    }

    private val INVERTIR = floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f,
    )

    /** Giro de tono de 180° (matriz de W3C feColorMatrix hueRotate con cos = -1, sen = 0). */
    private val GIRO_180 = floatArrayOf(
        -0.574f, 1.430f, 0.144f, 0f, 0f,
        0.426f, 0.430f, 0.144f, 0f, 0f,
        0.426f, 1.430f, -0.856f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )
}
