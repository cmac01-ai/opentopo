package org.opentopo.app.geoid

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object HeightModels {
    const val RECEIVER = "RECEIVER"
    const val HGEONOR2020_IMBITUBA = "HGEONOR2020_IMBITUBA"
    const val MAPGEO2015 = "MAPGEO2015"
}

data class HeightModelEvaluation(
    val model: String,
    val factorM: Double,
    val uncertaintyM: Double? = null,
    val heightType: String,
)

class GeoidModelService(private val context: Context) {

    companion object {
        const val HGEO_FACTOR_FILE = "hgeoHNOR2020_IMBITUBA_fator-conversao.txt"
        const val HGEO_UNCERTAINTY_FILE = "hgeoHNOR2020_IMBITUBA_incerteza.txt"
        const val MAPGEO_FILE = "MAPGEO2015_SIRGAS2000.txt"
    }

    private val dir: File
        get() = File(context.filesDir, "geoid").apply { mkdirs() }

    @Volatile private var hgeoFactor: RegularGeoidGrid? = null
    @Volatile private var hgeoUncertainty: RegularGeoidGrid? = null
    @Volatile private var mapgeo: RegularGeoidGrid? = null

    fun hasFile(name: String): Boolean = File(dir, name).exists()

    suspend fun importFile(uri: Uri, targetName: String) = withContext(Dispatchers.IO) {
        val target = File(dir, targetName)
        val tmp = File(dir, "$targetName.tmp")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Não foi possível abrir o arquivo selecionado" }
            tmp.outputStream().use { output -> input.copyTo(output) }
        }
        // Validate before replacing the active file.
        RegularGeoidGrid.load(tmp)
        if (target.exists()) target.delete()
        check(tmp.renameTo(target)) { "Não foi possível armazenar a grade" }
        when (targetName) {
            HGEO_FACTOR_FILE -> hgeoFactor = null
            HGEO_UNCERTAINTY_FILE -> hgeoUncertainty = null
            MAPGEO_FILE -> mapgeo = null
        }
    }

    suspend fun evaluate(model: String, latitude: Double, longitude: Double): HeightModelEvaluation? =
        withContext(Dispatchers.IO) {
            when (model) {
                HeightModels.HGEONOR2020_IMBITUBA -> {
                    val factorGrid = hgeoFactor ?: loadIfExists(HGEO_FACTOR_FILE)?.also {
                        hgeoFactor = it
                    } ?: return@withContext null
                    val factor = factorGrid.interpolate(latitude, longitude)
                        ?: return@withContext null
                    val uncertaintyGrid = hgeoUncertainty ?: loadIfExists(HGEO_UNCERTAINTY_FILE)?.also {
                        hgeoUncertainty = it
                    }
                    HeightModelEvaluation(
                        model = model,
                        factorM = factor,
                        uncertaintyM = uncertaintyGrid?.interpolate(latitude, longitude),
                        heightType = "NORMAL",
                    )
                }
                HeightModels.MAPGEO2015 -> {
                    val grid = mapgeo ?: loadIfExists(MAPGEO_FILE)?.also { mapgeo = it }
                        ?: return@withContext null
                    val factor = grid.interpolate(latitude, longitude)
                        ?: return@withContext null
                    HeightModelEvaluation(
                        model = model,
                        factorM = factor,
                        uncertaintyM = null,
                        heightType = "ORTHOMETRIC",
                    )
                }
                else -> null
            }
        }

    private fun loadIfExists(name: String): RegularGeoidGrid? {
        val file = File(dir, name)
        return if (file.exists()) RegularGeoidGrid.load(file) else null
    }
}
