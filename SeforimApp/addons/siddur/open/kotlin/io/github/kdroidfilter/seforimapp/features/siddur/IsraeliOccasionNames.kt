package io.github.kdroidfilter.seforimapp.features.siddur

import androidx.compose.runtime.Composable
import io.github.kdroidfilter.seforimapp.siddur.IsraeliOccasion
import org.jetbrains.compose.resources.stringResource
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.siddur_yom_haatzmaut
import seforimapp.seforimapp.generated.resources.siddur_yom_hazikaron
import seforimapp.seforimapp.generated.resources.siddur_yom_yerushalayim

/** The day's name, as the siddur's bar and its tefilot's titles show it. */
@Composable
internal fun IsraeliOccasion.localizedName(): String =
    stringResource(
        when (this) {
            IsraeliOccasion.YOM_HAZIKARON -> Res.string.siddur_yom_hazikaron
            IsraeliOccasion.YOM_HAATZMAUT -> Res.string.siddur_yom_haatzmaut
            IsraeliOccasion.YOM_YERUSHALAYIM -> Res.string.siddur_yom_yerushalayim
        },
    )
