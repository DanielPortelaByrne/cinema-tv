package com.danielportelabyrne.cinema.data.pairing

/**
 * The phone page for typing into the TV: one text box and a Send button, in the Cinema look (warm dark, cream type, the
 * saffron and terracotta of the TV's buttons). Each send hands its text to the TV's focused field and the page stays open
 * for the next one. Pure HTML and the reading of what the phone sent.
 */
object PhoneTextPage {
    const val MAX_LENGTH = 200

    /** The text the phone sent, trimmed and cut to [MAX_LENGTH]; null when there was nothing to send. */
    fun textFrom(form: Map<String, String>): String? = form["text"]?.trim()?.take(MAX_LENGTH)?.takeIf { it.isNotEmpty() }

    /** The page. [sent] is what the last send delivered, and [problem] says why the last send was refused. */
    fun form(sent: String? = null, problem: String? = null): String = page(
        body = """
            <p class="eyebrow">Type on the TV</p>
            <p class="lead">Type here and press Send. It lands in the search box on the TV.</p>
            ${problem?.let { """<p class="note bad">${PairingPages.escape(it)}</p>""" }.orEmpty()}
            ${sent?.let { """<p class="note sent">Sent: ${PairingPages.escape(it)}</p>""" }.orEmpty()}
            <form method="post" action="">
              <input name="text" autocomplete="off" autocapitalize="none" spellcheck="false" placeholder="Type…" autofocus maxlength="$MAX_LENGTH">
              <button type="submit">Send to TV</button>
            </form>
        """.trimIndent(),
    )

    private fun page(body: String): String = """
        <!doctype html><html lang="en"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Cinema · Type on the TV</title>
        <style>
          :root { color-scheme: dark; }
          * { box-sizing: border-box; }
          body { margin: 0; min-height: 100vh; padding: 28px 22px 40px; background: radial-gradient(120% 70% at 85% -10%, #3a2216 0%, #120c09 55%, #0b0706 100%);
                 color: #f1e4cf; font: 18px/1.45 -apple-system, "Segoe UI", Roboto, system-ui, sans-serif; }
          .brand { display: flex; align-items: center; gap: 12px; margin: 0 0 26px; }
          .dot { width: 34px; height: 34px; border-radius: 50%; background: radial-gradient(circle at 50% 50%, #edbe56 0 22%, #e8905a 40%, #d0623a 66%, #7a2e1a 100%);
                 box-shadow: 0 0 18px rgba(232,144,90,.35); display: inline-flex; align-items: center; justify-content: center; }
          .dot::after { content: ""; width: 0; height: 0; border-left: 9px solid #1b120c; border-top: 6px solid transparent; border-bottom: 6px solid transparent; margin-left: 3px; }
          .wordmark { font: 600 24px/1 Georgia, "Times New Roman", serif; letter-spacing: .01em; }
          .eyebrow { margin: 0 0 8px; color: #edbe56; font-size: 13px; font-weight: 600; letter-spacing: .22em; text-transform: uppercase; }
          .lead { margin: 0 0 22px; color: #bfae98; }
          .note { margin: 0 0 16px; padding: 12px 14px; border-radius: 14px; font-size: 16px; }
          .note.sent { background: rgba(237,190,86,.12); color: #edbe56; }
          .note.bad { background: rgba(255,138,122,.12); color: #ff9a8a; }
          form { display: flex; flex-direction: column; gap: 14px; }
          input { font: inherit; font-size: 20px; padding: 18px 18px; border-radius: 18px; border: 1px solid rgba(241,228,207,.14);
                  background: rgba(255,242,222,.09); color: #f1e4cf; outline: none; }
          input:focus { border-color: rgba(237,190,86,.7); box-shadow: 0 0 0 3px rgba(237,190,86,.18); }
          input::placeholder { color: #8f8272; }
          button { font: 600 19px/1 -apple-system, "Segoe UI", Roboto, system-ui, sans-serif; padding: 18px 16px; border: 0; border-radius: 999px; cursor: pointer;
                   background: linear-gradient(120deg, #eb9a66 0%, #d0623a 100%); color: #1b120c; box-shadow: 0 10px 24px rgba(208,98,58,.28); }
          button:active { transform: scale(.98); }
        </style></head><body>
        <div class="brand"><span class="dot"></span><span class="wordmark">Cinema</span></div>
        $body
        </body></html>
    """.trimIndent()
}
