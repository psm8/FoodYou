package com.maksimowiczm.foodyou.app.ui.stash.add

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.domain.measurement.MeasurementType
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import java.io.File
import java.io.FileOutputStream
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StashAddProductScreenshotTest {
    @Test
    fun capture_default_stash_target_screen() {
        captureScreenshot(
            fileName = "stash-add-product-default-target.png",
            state =
                StashAddProductState(
                    productId = FoodId.Product(1L),
                    productName = "Milk",
                    isLoading = false,
                    suggestions = listOf(Measurement.Milliliter(250.0), Measurement.Serving(1.0)),
                    possibleMeasurementTypes =
                        listOf(
                            MeasurementType.Milliliter,
                            MeasurementType.FluidOunce,
                            MeasurementType.Serving,
                        ),
                    selectedMeasurement = Measurement.Milliliter(250.0),
                ),
        )
    }

    @Test
    fun capture_explicit_stash_selection_screen() {
        captureScreenshot(
            fileName = "stash-add-product-explicit-stash.png",
            state =
                StashAddProductState(
                    productId = FoodId.Product(2L),
                    productName = "Greek yogurt",
                    isLoading = false,
                    suggestions = listOf(Measurement.Serving(1.0), Measurement.Gram(100.0)),
                    possibleMeasurementTypes =
                        listOf(
                            MeasurementType.Serving,
                            MeasurementType.Gram,
                            MeasurementType.Package,
                        ),
                    selectedMeasurement = Measurement.Serving(1.0),
                    stashes =
                        listOf(
                            StashAddProductStash(StashDefinitionId(1L), "Fridge"),
                            StashAddProductStash(StashDefinitionId(2L), "Pantry"),
                        ),
                    selectedStashId = StashDefinitionId(2L),
                ),
        )
    }

    private fun captureScreenshot(fileName: String, state: StashAddProductState) {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    MaterialTheme {
                        StashAddProductContent(
                            state = state,
                            onBack = {},
                            onSave = {},
                            onSelectStash = {},
                        )
                    }
                }
            }

            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.waitForIdleSync()
            Thread.sleep(500)

            scenario.onActivity { activity ->
                val root = activity.findViewById<android.view.View>(android.R.id.content)
                val bitmap =
                    Bitmap.createBitmap(
                        root.width.coerceAtLeast(1),
                        root.height.coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888,
                    )
                root.draw(Canvas(bitmap))

                val targetDirectory =
                    File(activity.getExternalFilesDir(null), "issue4-artifacts").apply { mkdirs() }
                val targetFile = File(targetDirectory, fileName)

                FileOutputStream(targetFile).use { output ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                }

                assertTrue(targetFile.exists(), "Expected screenshot ${targetFile.absolutePath}")
            }
        }
    }
}
