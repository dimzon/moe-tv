package tv.own.owntv.core.i18n
import tv.own.owntv.*
import tv.own.owntv.core.*
import tv.own.owntv.core.backup.*
import tv.own.owntv.core.brand.*
import tv.own.owntv.core.catalog.*
import tv.own.owntv.core.categorylock.*
import tv.own.owntv.core.companion.*
import tv.own.owntv.core.content.*
import tv.own.owntv.core.customize.*
import tv.own.owntv.core.database.*
import tv.own.owntv.core.database.dao.*
import tv.own.owntv.core.database.entity.*
import tv.own.owntv.core.di.*
import tv.own.owntv.core.download.*
import tv.own.owntv.core.drm.*
import tv.own.owntv.core.epg.*
import tv.own.owntv.core.home.*
import tv.own.owntv.core.i18n.*
import tv.own.owntv.core.launcher.*
import tv.own.owntv.core.live.*
import tv.own.owntv.core.menu.*
import tv.own.owntv.core.metadata.*
import tv.own.owntv.core.model.*
import tv.own.owntv.core.nav.*
import tv.own.owntv.core.network.*
import tv.own.owntv.core.parser.*
import tv.own.owntv.core.player.*
import tv.own.owntv.core.profile.*
import tv.own.owntv.core.recording.*
import tv.own.owntv.core.reminder.*
import tv.own.owntv.core.repository.*
import tv.own.owntv.core.settings.*
import tv.own.owntv.core.setup.*
import tv.own.owntv.core.sports.*
import tv.own.owntv.core.sports.model.*
import tv.own.owntv.core.stalker.*
import tv.own.owntv.core.storage.*
import tv.own.owntv.core.subtitles.*
import tv.own.owntv.core.sync.*
import tv.own.owntv.core.sync.local.*
import tv.own.owntv.core.sync.work.*
import tv.own.owntv.core.theme.*
import tv.own.owntv.core.timeshift.*
import tv.own.owntv.core.trending.*
import tv.own.owntv.core.tv.*
import tv.own.owntv.core.ui.*
import tv.own.owntv.core.ui.stage.*
import tv.own.owntv.core.update.*
import tv.own.owntv.core.util.*
import tv.own.owntv.core.weather.*
import tv.own.owntv.player.*

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.core.theme.AccentColor
import tv.own.myowntv.ui.theme.OwnTVTheme
import tv.own.myowntv.ui.theme.PopupFontTheme
import tv.own.owntv.core.theme.ThemeMode

/** Debug-only, static screen for checking system glyph fallback on representative TV devices. */
class FontFallbackQaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OwnTVTheme(
                themeMode = ThemeMode.DARK,
                accent = AccentColor.TEAL,
                systemInDarkTheme = true,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .verticalScroll(rememberScrollState())
                        .padding(32.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    QaSection("Normal OwnTV typography")
                    PopupFontTheme { QaSection("PopupFontTheme at 1f") }
                    PopupFontTheme(fontScale = 0.75f) {
                        QaSection("Compact PopupFontTheme at 0.75f")
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun QaSection(heading: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(heading, style = MaterialTheme.typography.titleLarge)
        Text("Arabic — العربية: أهلاً بكم في تلفازكم")
        Text("Simplified Chinese — 简体中文：正在播放电视节目")
        Text("Traditional Chinese — 繁體中文：正在播放電視節目")
        Text("Japanese — 日本語：テレビ番組を再生中")
        Text("Korean — 한국어: 텔레비전 프로그램 재생 중")
        Text("Malayalam — മലയാളം: OwnTV-ലേക്ക് സ്വാഗതം")
        Text("Hindi — हिन्दी: OwnTV में आपका स्वागत है")
        Text("Bangla — বাংলা: OwnTV-তে স্বাগতম")
        Text("Cyrillic — Русский: Сейчас в эфире")
        Text("Greek — Ελληνικά: Τώρα παίζει")
        Text("Mixed Latin/Arabic — OwnTV • الحلقة 12 • HD")
        Text("Mixed Latin/CJK — OwnTV • 第12話 • 4K")
        Text("Provider title — الأخبار الدولية / 世界ニュース")
        Text("Provider subtitle — الحلقة الجديدة / 新しいエピソード")
        Text("Channel — القناة الأولى / 総合テレビ")
        Text("Programme — رحلة عبر الزمن / 時間を旅する")
        Text("Playlist — قائمة العائلة / 家族のプレイリスト")
        Text("Subtitle file — العربية_日本語_한국어.srt")
    }
}
