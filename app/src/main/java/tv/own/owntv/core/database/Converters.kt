package tv.own.owntv.core.database

import androidx.room.TypeConverter
import tv.own.owntv.core.tv.TvProviderSurface

/** Stores the one persisted enum as its stable name (survives reordering). */
class Converters {
    @TypeConverter fun tvProviderSurfaceToString(v: TvProviderSurface): String = v.name
    @TypeConverter fun stringToTvProviderSurface(v: String): TvProviderSurface = TvProviderSurface.valueOf(v)
}
