package tv.own.owntv.core.categorylock

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.model.MediaType

/**
 * The user's browse customizations for [type], led by null until the store has read them. A list
 * built from the empty default would show a locked category's items, so a section's item lists wait
 * for the first real value.
 */
fun CustomizationStore.visibleOnceRead(type: MediaType): Flow<SectionCustomizations?> = flow {
    emit(null)
    emitAll(observeVisible(type))
}
