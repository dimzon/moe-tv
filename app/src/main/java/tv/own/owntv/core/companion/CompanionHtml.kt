package tv.own.owntv.core.companion
import tv.own.owntv.core.i18n.firstLocaleCompat

import android.content.Context
import androidx.annotation.StringRes
import org.json.JSONObject
import tv.own.myowntv.R

/**
 * Localized HTML renderer for the companion web UI. Markup, CSS and protocol identifiers remain
 * here; every sentence or label visible to the remote browser is resolved from Android resources first.
 * The add/edit-source forms are gone — the app has one built-in provider — so the pages served are
 * the PIN gate, the backup upload/download pair, the background-image upload, and local sync's notice.
 */
internal object CompanionHtml {

    private const val CSS = """
      :root{
        color-scheme: dark;
        --bg:#040E0B; --panel:#12191700; --card:#1B211F; --card-2:#252B29;
        --line:#3F4945; --text:#DEE4E1; --muted:#BFC9C4;
        --accent:#8CEE2B; --accent-ink:#123A06; --danger:#FFB4AB;
      }
      @font-face{
        font-family:'Lora'; font-style:normal; font-weight:400 700;
        src:url('/lora.ttf') format('truetype'); font-display:swap;
      }
      *{box-sizing:border-box}
      body{
        margin:0; color:var(--text);
        font-family:system-ui,-apple-system,'Segoe UI',Roboto,'Helvetica Neue',Arial,sans-serif;
        background:
          radial-gradient(1200px 520px at 12% -12%, rgba(140,238,43,0.10) 0%, transparent 60%),
          radial-gradient(900px 460px at 100% 0%, rgba(140,238,43,0.06) 0%, transparent 62%),
          var(--bg);
        min-height:100vh;
      }
      h1,h2,.brand{font-family:'Lora',Georgia,'Noto Sans',system-ui,sans-serif; font-weight:700; letter-spacing:.2px}
      main{max-width:760px; margin:0 auto; padding:28px 20px 56px}
      .brandrow{display:flex; align-items:center; gap:12px; margin-bottom:22px}
      .dot{width:34px; height:34px; border-radius:10px; background:#52DBC8;
        display:grid; place-items:center; color:#003730}
      .dot svg{width:18px; height:18px}
      .brand{font-size:20px; color:var(--text)}
      .card{background:var(--card); border:1px solid var(--line); border-radius:20px;
        padding:26px 22px; box-shadow:0 24px 60px rgba(0,0,0,.45)}
      h1{margin:0 0 8px; font-size:24px}
      p{line-height:1.55; color:var(--muted); margin:0 0 16px}
      form{display:grid; gap:14px}
      label{display:grid; gap:6px; font-size:14px; color:var(--text)}
      input,select{border:1px solid var(--line); border-radius:12px; padding:13px 14px;
        background:#0C1311; color:var(--text); font-size:16px; width:100%}
      input:focus,select:focus{outline:none; border-color:var(--accent);
        box-shadow:0 0 0 3px rgba(140,238,43,.20)}
      button.go{border:0; border-radius:12px; padding:15px 16px; background:var(--accent);
        color:var(--accent-ink); font-weight:700; font-size:16px; cursor:pointer; margin-top:4px}
      .pin{font-family:'Lora',Georgia,'Noto Sans',system-ui,sans-serif; font-size:34px; letter-spacing:12px; text-align:center;
        padding:16px; caret-color:var(--accent)}
      .err{color:var(--danger); font-size:14px; margin:0 0 12px}
      .hint{font-size:13px; color:var(--muted); margin-top:6px}
      a{color:var(--accent)}
    """

    private const val LOGO_SVG =
        """<svg viewBox="0 0 24 24" fill="none"><path d="M8 5v14l11-7z" fill="currentColor"/></svg>"""

    private class Copy(private val context: Context) {
        private fun s(@StringRes id: Int): String = context.getString(id)

        val app = s(R.string.app_name)
        val pinTitle = s(R.string.companion_pin_page_title)
        val pinHeading = s(R.string.companion_pin_heading)
        val pinDescription = s(R.string.companion_pin_description)
        val pinPlaceholder = s(R.string.companion_pin_placeholder)
        val pinAria = s(R.string.companion_pin_aria)
        val continueLabel = s(R.string.companion_continue)
        val pinMismatch = s(R.string.companion_pin_mismatch)
        val sendToTv = s(R.string.companion_send_to_tv)

        val savedTitle = s(R.string.companion_saved_page_title)
        val savedHeading = s(R.string.companion_saved_heading)
        val savedBackupBody = s(R.string.companion_saved_backup_body)
        val savedBackupLink = s(R.string.companion_saved_backup_link)
        val savedImageBody = s(R.string.companion_saved_image_body)
        val savedImageLink = s(R.string.companion_saved_image_link)

        val backupTitle = s(R.string.companion_backup_page_title)
        val backupHeading = s(R.string.companion_backup_heading)
        val backupDescription = s(R.string.companion_backup_description)
        val backupFile = s(R.string.companion_backup_file)
        val chooseBackup = s(R.string.companion_choose_backup)
        val sending = s(R.string.companion_sending)
        val couldNotReach = s(R.string.companion_could_not_reach)
        val couldNotRead = s(R.string.companion_could_not_read)
        // Full sentence template; the browser substitutes only the numeric HTTP status after the
        // Android resource has supplied the localized sentence and punctuation.
        val uploadFailed = s(R.string.companion_upload_failed).replace("%1\$d", "__STATUS__")

        val imageTitle = s(R.string.companion_image_page_title)
        val imageHeading = s(R.string.companion_image_heading)
        val imageDescription = s(R.string.companion_image_description)
        val imageFile = s(R.string.companion_image_file)
        val chooseImage = s(R.string.companion_choose_image)
        val imageTooLarge = s(R.string.companion_image_too_large)

        val downloadTitle = s(R.string.companion_download_page_title)
        val downloadHeading = s(R.string.companion_download_heading)
        val downloadDescription = s(R.string.companion_download_description)
        val downloadBackup = s(R.string.companion_download_backup)
    }

    private fun page(context: Context, title: String, inner: String): String {
        val c = Copy(context)
        // AppLocale.wrap() resolves the effective device locale when the stored tag is empty.
        // Advertise that effective locale to browser screen readers and choose the matching writing
        // direction; never use the persisted empty tag itself as the HTML language.
        val locale = context.resources.configuration.firstLocaleCompat()
            ?: java.util.Locale.getDefault()
        val lang = locale.toLanguageTag().ifBlank { "en" }
        val dir = if (context.resources.configuration.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL) "rtl" else "ltr"
        return """
            <!doctype html><html lang="${lang.h()}" dir="$dir"><head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>${title.h()}</title><style>$CSS</style>
            </head><body><main>
              <div class="brandrow"><span class="dot">$LOGO_SVG</span><span class="brand">${c.app.h()}</span></div>
              $inner
            </main></body></html>
        """.trimIndent()
    }

    fun pinPage(context: Context, error: String?): String {
        val c = Copy(context)
        return page(context, c.pinTitle, """
            <div class="card">
              <h1>${c.pinHeading.h()}</h1>
              <p>${c.pinDescription.h()}</p>
              ${if (error != null) "<p class=\"err\">${error.h()}</p>" else ""}
              <form method="post" action="/">
                <input class="pin" name="pin" inputmode="numeric" pattern="[0-9]*" maxlength="6"
                       autofocus placeholder="${c.pinPlaceholder.h()}" aria-label="${c.pinAria.h()}" required>
                <button class="go" type="submit">${c.continueLabel.h()}</button>
              </form>
            </div>
        """.trimIndent())
    }

    fun backupUploadPage(context: Context, pin: String): String {
        val c = Copy(context)
        return page(context, c.backupTitle, uploadPage(c, pin, backup = true))
    }

    fun imageUploadPage(context: Context, pin: String): String {
        val c = Copy(context)
        return page(context, c.imageTitle, uploadPage(c, pin, backup = false))
    }

    private fun uploadPage(c: Copy, pin: String, backup: Boolean): String {
        val heading = if (backup) c.backupHeading else c.imageHeading
        val description = if (backup) c.backupDescription else c.imageDescription
        val fileLabel = if (backup) c.backupFile else c.imageFile
        val choose = if (backup) c.chooseBackup else c.chooseImage
        val endpoint = if (backup) "/backup" else "/background"
        val contentType = if (backup) "application/json" else "text/plain"
        val accept = if (backup) ".own,.json,application/json,application/octet-stream" else "image/*"
        return """
            <div class="card">
              <h1>${heading.h()}</h1>
              <p>${description.h()}</p>
              <form id="f" onsubmit="return false">
                <label>${fileLabel.h()} <input id="file" type="file" accept="$accept" required></label>
                <button class="go" id="send" type="submit">${c.sendToTv.h()}</button>
              </form>
              <p id="status" class="hint"></p>
            </div>
            <script>
              var f=document.getElementById('file'), b=document.getElementById('send'), s=document.getElementById('status');
              document.getElementById('f').addEventListener('submit',function(){
                var file=f.files&&f.files[0];
                if(!file){s.textContent=${choose.js()};return false;}
                ${if (!backup) "if(file.size>25*1024*1024){s.textContent=${c.imageTooLarge.js()};return false;}" else ""}
                b.disabled=true; s.textContent=${c.sending.js()};
                var r=new FileReader();
                r.onload=function(){
                  fetch('$endpoint?pin=$pin',{method:'POST',headers:{'Content-Type':'$contentType'},body:r.result})
                    .then(function(res){
                      if(res.ok){document.open();res.text().then(function(t){document.write(t);document.close();});}
                      else{b.disabled=false;s.textContent=${c.uploadFailed.js()}.replace('__STATUS__',String(res.status));}
                    })
                    .catch(function(){b.disabled=false;s.textContent=${c.couldNotReach.js()};});
                };
                r.onerror=function(){b.disabled=false;s.textContent=${c.couldNotRead.js()};};
                r.readAsDataURL(file); return false;
              });
            </script>
        """.trimIndent()
    }

    fun imageSentPage(context: Context, pin: String): String {
        val c = Copy(context)
        return page(context, c.savedTitle, """
            <div class="card"><h1>${c.savedHeading.h()}</h1>
              <p>${c.savedImageBody.h()}</p><p><a href="/?pin=$pin">${c.savedImageLink.h()}</a></p>
            </div>
        """.trimIndent())
    }

    /**
     * The only page local sync serves. There is nothing for a browser to do here — the other OwnTV
     * app is the client — but the address is discoverable and shown on screen, so someone will type
     * it in, and a blank page or a 404 would look like the feature is broken.
     */
    fun localSyncPage(context: Context): String = page(
        context,
        context.getString(R.string.local_sync_browser_title),
        """
            <div class="card"><h1>${context.getString(R.string.local_sync_browser_title).h()}</h1>
              <p>${context.getString(R.string.local_sync_browser_body).h()}</p>
            </div>
        """.trimIndent(),
    )

    fun backupSentPage(context: Context, pin: String): String {
        val c = Copy(context)
        return page(context, c.savedTitle, """
            <div class="card"><h1>${c.savedHeading.h()}</h1>
              <p>${c.savedBackupBody.h()}</p><p><a href="/?pin=$pin">${c.savedBackupLink.h()}</a></p>
            </div>
        """.trimIndent())
    }

    fun backupDownloadPage(context: Context, pin: String): String {
        val c = Copy(context)
        return page(context, c.downloadTitle, """
            <div class="card"><h1>${c.downloadHeading.h()}</h1>
              <p>${c.downloadDescription.h()}</p>
              <a class="go" href="/backup.own?pin=$pin" download="owntv-backup.own" style="display:block;text-align:center;text-decoration:none">${c.downloadBackup.h()}</a>
            </div>
        """.trimIndent())
    }

    private fun String.js(): String = JSONObject.quote(this)

    private fun String.h(): String =
        replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
