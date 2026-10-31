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

    /**
     * The live page: the label of the field the TV has focused, one box whose text follows the TV field as it is typed,
     * and a Done button. It talks to the TV over a WebSocket at `/ws/<code>`, reconnects by itself, and needs no
     * downloads. Passwords on the TV are never sent here, so a password field starts empty.
     */
    fun live(code: String): String {
        val digits = code.filter { it.isDigit() }
        return page(
            bodyAttrs = """ data-code="${PairingPages.escape(digits)}"""",
            body = """
                <p class="eyebrow" id="label">Connecting…</p>
                <p class="lead">What you type here appears on the TV as you type.</p>
                <form id="live" onsubmit="return false">
                  <input id="field" autocomplete="off" autocapitalize="none" spellcheck="false" placeholder="Type…" maxlength="$MAX_LENGTH" disabled>
                  <button type="button" id="done">Done</button>
                </form>
                <p class="note status" id="status" role="status">Connecting…</p>
                <noscript><p class="note bad">This page needs JavaScript. Without it, open the plain page and use Send.</p></noscript>
            """.trimIndent(),
            script = "<script>$LIVE_SCRIPT</script>",
        )
    }

    private const val LIVE_SCRIPT = """
        (function () {
          var code = document.body.getAttribute('data-code');
          var label = document.getElementById('label');
          var field = document.getElementById('field');
          var done = document.getElementById('done');
          var status = document.getElementById('status');
          var ws = null, retry = 0, everOpened = false, stopped = false, fieldId = '', pingTimer = null, sendTimer = null;
          var waits = [500, 1000, 2000, 5000];
          function say(text) { status.textContent = text; }
          function clean(text) { return text.replace(/[\t\r\n]/g, ' '); }
          function send(text) { if (ws && ws.readyState === 1) ws.send(text); }
          function none() { fieldId = ''; field.value = ''; field.disabled = true; label.textContent = 'Nothing selected on the TV'; }
          function handle(message) {
            var kind = message.charAt(0);
            if (kind === 'F') {
              var parts = message.substring(1).split('\t');
              var same = parts[0] === fieldId;
              var max = parseInt(parts[3], 10);
              fieldId = parts[0];
              label.textContent = parts[1] || 'Type on the TV';
              field.type = parts[2] === '1' ? 'password' : 'text';
              field.maxLength = max > 0 ? max : 200;
              field.disabled = false;
              if (!(same && document.activeElement === field)) field.value = parts.slice(4).join('\t');
              if (!same) field.focus();
            } else if (kind === 'N') {
              none();
            }
          }
          function open() {
            if (stopped) return;
            var scheme = location.protocol === 'https:' ? 'wss://' : 'ws://';
            ws = new WebSocket(scheme + location.host + '/ws/' + code);
            ws.onopen = function () {
              retry = 0; everOpened = true; say('Connected to the TV');
              clearInterval(pingTimer);
              pingTimer = setInterval(function () { send('P'); }, 20000);
            };
            ws.onmessage = function (event) { handle(String(event.data)); };
            ws.onclose = function () {
              clearInterval(pingTimer);
              field.disabled = true;
              if (stopped) return;
              say(!everOpened && retry >= 3 ? 'The TV may already have a phone connected. Trying again…' : 'Reconnecting…');
              var wait = waits[Math.min(retry, waits.length - 1)];
              retry++;
              setTimeout(check, wait);
            };
          }
          // A refused upgrade does not say why, so ask the page itself: gone means the code has ended.
          function check() {
            fetch(location.pathname, { cache: 'no-store' }).then(function (response) {
              if (response.status === 403 || response.status === 410) { stopped = true; say('Scan the code on the TV again.'); none(); return; }
              open();
            }).catch(function () { open(); });
          }
          field.addEventListener('input', function () {
            if (!fieldId) return;
            clearTimeout(sendTimer);
            sendTimer = setTimeout(function () { send('T' + fieldId + '\t' + clean(field.value)); }, 120);
          });
          done.addEventListener('click', function () {
            clearTimeout(sendTimer);
            if (fieldId) send('T' + fieldId + '\t' + clean(field.value));
            send('D');
          });
          document.addEventListener('visibilitychange', function () {
            if (!document.hidden && !stopped && (!ws || ws.readyState > 1)) { retry = 0; check(); }
          });
          none();
          open();
        })();
    """

    private fun page(body: String, bodyAttrs: String = "", script: String = ""): String = """
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
          input:disabled { opacity: .55; }
          .note.status { background: rgba(255,242,222,.07); color: #bfae98; margin: 18px 0 0; }
        </style></head><body$bodyAttrs>
        <div class="brand"><span class="dot"></span><span class="wordmark">Cinema</span></div>
        $body
        $script
        </body></html>
    """.trimIndent()
}
