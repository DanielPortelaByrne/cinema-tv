package com.danielportelabyrne.cinema.data.pairing

/** A server the TV found on the network, offered on the phone as a tap-to-fill choice. */
data class ServerChoice(val name: String, val address: String)

/** A torrent client that needs its sign-in before its downloads can be controlled. */
data class ClientNeed(val name: String, val host: String)

/**
 * What the sign-in page shows: the values typed so far (so a retry does not need them typed again), the servers the TV
 * found, and the one problem to fix, with the field it is about so that field can be outlined.
 */
data class SetupForm(
    val jellyfinUrl: String = "",
    val username: String = "",
    val seerrUrl: String = "",
    val seerrUsername: String = "",
    val musicUrl: String = "",
    val musicUsername: String = "",
    val sonarrUrl: String = "",
    val radarrUrl: String = "",
    val found: List<ServerChoice> = emptyList(),
    val seerrSuggestion: String? = null,
    /** Navidrome's usual address on the Jellyfin machine, offered as a tap-to-fill choice for Music. */
    val musicSuggestion: String? = null,
    val error: String? = null,
    val errorField: String? = null,
)

/**
 * The phone's pages, served by the TV while pairing is open. Styled to match Cinema on the TV (its dark ground, cream
 * text, terracotta and saffron accents, and the same roundel), with one small script for tap-to-fill, Quick Connect and
 * the restore file. Plain HTML, so nothing is downloaded to the phone. Every value from the TV goes through [escape].
 */
object PairingPages {

    /** The first-run sign-in form. Passwords are typed here, never kept or shown back. */
    fun setup(code: String, form: SetupForm = SetupForm()): String {
        val action = path("setup", code)
        val found = if (form.found.isEmpty()) "" else chips(form)
        val seerrSuggestion = seerrChip(form)
        val music = form.musicSuggestion?.let {
            "<div class=\"chips\"><button type=\"button\" class=\"chip\" data-fill=\"musicUrl\" data-value=\"${escape(it)}\">" +
                "Navidrome at ${escape(it.removePrefix("http://"))}</button></div>"
        } ?: ""
        return page(
            title = "Set up Cinema",
            eyebrow = "First run",
            lead = "Sign in to your Jellyfin. The TV signs in itself and keeps only the sign-in token. Your password isn't saved.",
            error = form.error,
            body = """
                <form method="post" action="${escape(action)}" id="setup-form">
                  <section class="card">
                    <h2>Jellyfin</h2>
                    $found
                    <label>Address
                      <input id="jellyfinUrl" name="jellyfinUrl" type="url" required value="${escape(form.jellyfinUrl)}"
                        placeholder="http://server-address:8096" autocapitalize="off" autocorrect="off" class="${fieldClass(form, "jellyfinUrl")}">
                    </label>
                    <label>Username
                      <input name="username" required value="${escape(form.username)}" autocapitalize="off" autocorrect="off"
                        autocomplete="username" class="${fieldClass(form, "username")}">
                    </label>
                    <label>Password
                      <input name="password" type="password" autocomplete="current-password" class="${fieldClass(form, "password")}">
                    </label>
                    <button type="submit" class="primary">Sign in on the TV</button>
                  </section>

                  <section class="card">
                    <h2>Quick Connect</h2>
                    <p class="hint">No password here. Get a code, then open Quick Connect in Jellyfin on another device and enter it.</p>
                    <button type="button" class="secondary" id="qc-start">Get a Quick Connect code</button>
                    <div id="qc-code" class="code" hidden></div>
                    <p id="qc-status" class="hint" role="status"></p>
                  </section>

                  <details class="card optional"${openIf(form, "seerrUrl", "seerrUsername", "seerrPassword")}>
                    <summary><span class="sec">Seerr</span> <span class="muted">optional</span></summary>
                    <p class="hint">Lets you request films and series. Leave the address blank to skip it.</p>
                    $seerrSuggestion
                    <label>Address
                      <input id="seerrUrl" name="seerrUrl" type="url" value="${escape(form.seerrUrl)}"
                        placeholder="http://server-address:5055" autocapitalize="off" autocorrect="off" class="${fieldClass(form, "seerrUrl")}">
                    </label>
                    <label>Jellyfin username
                      <input name="seerrUsername" value="${escape(form.seerrUsername)}" autocapitalize="off" autocorrect="off"
                        autocomplete="username" class="${fieldClass(form, "seerrUsername")}">
                    </label>
                    <label>Jellyfin password
                      <input name="seerrPassword" type="password" autocomplete="current-password" class="${fieldClass(form, "seerrPassword")}">
                    </label>
                  </details>

                  <details class="card optional"${openIf(form, "musicUrl", "musicUsername", "musicPassword", "musicApiKey")}>
                    <summary><span class="sec">Music</span> <span class="muted">optional</span></summary>
                    <p class="hint">A Subsonic server, such as Navidrome, adds Music to the menu. Leave the address blank to skip it.</p>
                    $music
                    <label>Address
                      <input id="musicUrl" name="musicUrl" type="url" value="${escape(form.musicUrl)}" placeholder="http://server-address:4533"
                        autocapitalize="off" autocorrect="off" class="${fieldClass(form, "musicUrl")}">
                    </label>
                    <label>Username
                      <input id="musicUsername" name="musicUsername" value="${escape(form.musicUsername)}" autocapitalize="off" autocorrect="off"
                        autocomplete="username" class="${fieldClass(form, "musicUsername")}">
                    </label>
                    <label>Password
                      <input name="musicPassword" type="password" autocomplete="current-password" class="${fieldClass(form, "musicPassword")}">
                    </label>
                    <label>Or an API key
                      <input name="musicApiKey" type="password" autocomplete="off" class="${fieldClass(form, "musicApiKey")}">
                    </label>
                  </details>

                  <details class="card optional"${openIf(form, "sonarrUrl", "sonarrApiKey", "radarrUrl", "radarrApiKey")}>
                    <summary><span class="sec">Sonarr and Radarr</span> <span class="muted">optional</span></summary>
                    <p class="hint">Only needed if Seerr doesn't share them. Each needs its address and API key from its settings.</p>
                    <label>Sonarr address
                      <input name="sonarrUrl" type="url" value="${escape(form.sonarrUrl)}" placeholder="http://server-address:8989"
                        autocapitalize="off" autocorrect="off" class="${fieldClass(form, "sonarrUrl")}">
                    </label>
                    <label>Sonarr API key
                      <input name="sonarrApiKey" type="password" autocomplete="off" class="${fieldClass(form, "sonarrApiKey")}">
                    </label>
                    <label>Radarr address
                      <input name="radarrUrl" type="url" value="${escape(form.radarrUrl)}" placeholder="http://server-address:7878"
                        autocapitalize="off" autocorrect="off" class="${fieldClass(form, "radarrUrl")}">
                    </label>
                    <label>Radarr API key
                      <input name="radarrApiKey" type="password" autocomplete="off" class="${fieldClass(form, "radarrApiKey")}">
                    </label>
                  </details>

                  <details class="card optional">
                    <summary><span class="sec">Stremio</span> <span class="muted">optional</span></summary>
                    <p class="hint">Shows your Stremio library as a Watchlist. Tap the button, sign in to Stremio on the page that opens, and this page finishes the link.</p>
                    <button type="button" class="secondary" id="stremio-start">Link Stremio</button>
                    <p id="stremio-status" class="hint" role="status"></p>
                  </details>
                  <button type="submit" class="primary" id="qc-finish" formnovalidate hidden>Finish setup</button>
                </form>
                <script>$SETUP_SCRIPT</script>
                <script>$STREMIO_SCRIPT</script>
                <script>$AUTOFILL_SCRIPT</script>
            """.trimIndent(),
        )
    }

    /** Asks for the PIN, then the browser downloads `cinema-restore.json`. */
    fun save(code: String, error: String? = null): String = page(
        title = "Save your sign-in",
        eyebrow = "Backup",
        lead = "Choose a 6-digit PIN. You'll need it to restore. The file is useless without it, but keep both private.",
        error = error,
        body = """
            <form method="post" action="${escape(path("save", code))}" class="card">
              <div class="field-label">PIN</div>
              ${pinField(new = true)}
              <button type="submit" class="primary">Download cinema-restore.json</button>
            </form>
            <script>$PIN_SCRIPT</script>
        """.trimIndent(),
    )

    /**
     * Picks the saved file and asks for its PIN. The file is read in the browser and sent as base64 in a form field, so
     * the server only ever parses urlencoded forms.
     */
    fun restore(code: String, error: String? = null): String = page(
        title = "Restore Cinema",
        eyebrow = "Restore",
        lead = "Pick the cinema-restore.json file you saved, then enter its PIN.",
        error = error,
        body = """
            <form method="post" action="${escape(path("restore", code))}" id="restore" class="card">
              <label>File
                <input id="file" type="file" accept=".json,application/json" required class="file">
              </label>
              <div class="field-label">PIN</div>
              ${pinField(new = false)}
              <input type="hidden" name="bundle" id="bundle">
              <button type="submit" class="primary">Restore on the TV</button>
            </form>
            <script>$PIN_SCRIPT</script>
            <script>$RESTORE_SCRIPT</script>
        """.trimIndent(),
    )

    /**
     * The last step of setup: the torrent client that Sonarr and Radarr use needs its own sign-in before Cinema can
     * control its downloads. "Skip for now" finishes setup without it; progress still works.
     */
    fun clients(code: String, needs: List<ClientNeed>, error: String? = null, errorField: String? = null): String {
        val rows = needs.withIndex().joinToString("") { (i, need) ->
            """
              <section class="card">
                <h2>${escape(need.name)} <span class="muted">${escape(need.host)}</span></h2>
                <label>Username
                  <input name="clientUser$i" autocapitalize="off" autocorrect="off" autocomplete="username"
                    class="${if (errorField == "clientUser$i") "bad" else ""}">
                </label>
                <label>Password
                  <input name="clientPass$i" type="password" autocomplete="current-password"
                    class="${if (errorField == "clientPass$i") "bad" else ""}">
                </label>
              </section>
            """.trimIndent()
        }
        return page(
            title = "Your torrent client",
            eyebrow = "Last step",
            lead = "Cinema found the torrent client that Sonarr and Radarr use. Its sign-in is needed only to control downloads: moving one up the queue or starting it now. Download progress works without it.",
            error = error,
            body = """
                <form method="post" action="${escape(path("setup", code))}">
                  <input type="hidden" name="phase" value="clients">
                  $rows
                  <button type="submit" class="primary">Sign in to the client</button>
                  <button type="submit" name="skipClients" value="1" class="secondary">Skip for now</button>
                </form>
            """.trimIndent(),
        )
    }

    /** A short page after a submission, telling the phone what happened. */
    fun done(title: String, message: String): String = page(
        title = title,
        eyebrow = "Done",
        lead = message,
        error = null,
        body = "",
    )

    private const val SETUP_SCRIPT = """
        (function () {
          var form = document.getElementById('setup-form');
          var code = form.getAttribute('action').split('/').pop();
          var start = document.getElementById('qc-start');
          var box = document.getElementById('qc-code');
          var status = document.getElementById('qc-status');
          document.querySelectorAll('[data-fill]').forEach(function (chip) {
            chip.addEventListener('click', function () {
              var field = document.getElementById(chip.getAttribute('data-fill'));
              field.value = chip.getAttribute('data-value');
              field.classList.remove('bad');
              var next = form.querySelector('input[name=username]');
              if (next) next.focus();
            });
          });
          function poll() {
            fetch('/qc-check/' + code, { cache: 'no-store' }).then(function (r) { return r.json(); }).then(function (d) {
              if (d.status === 'approved') {
                var flag = document.createElement('input');
                flag.type = 'hidden'; flag.name = 'quickConnect'; flag.value = '1';
                form.appendChild(flag);
                status.textContent = 'Approved. Fill in any optional sections below, then tap Finish setup.';
                document.getElementById('qc-finish').hidden = false;
                box.hidden = true; start.disabled = true;
                return;
              }
              if (d.status === 'waiting') { setTimeout(poll, 2000); return; }
              box.hidden = true; start.disabled = false;
              status.textContent = d.message || 'Quick Connect stopped. Get a new code.';
            }).catch(function () { setTimeout(poll, 3000); });
          }
          start.addEventListener('click', function () {
            var url = document.getElementById('jellyfinUrl').value.trim();
            if (!url) { status.textContent = 'Enter the Jellyfin address first, or tap a server above.'; return; }
            start.disabled = true;
            status.textContent = 'Asking Jellyfin for a code…';
            fetch('/qc-start/' + code + '?address=' + encodeURIComponent(url), { cache: 'no-store' })
              .then(function (r) { return r.json(); })
              .then(function (d) {
                if (d.error) { start.disabled = false; status.textContent = d.error; return; }
                box.textContent = d.code; box.hidden = false;
                status.textContent = 'In Jellyfin, open Quick Connect and enter this code.';
                poll();
              })
              .catch(function () { start.disabled = false; status.textContent = 'Could not reach the TV. Check the Wi-Fi and try again.'; });
          });
        })();
    """

    private const val RESTORE_SCRIPT = """
        (function () {
          var form = document.getElementById('restore');
          form.addEventListener('submit', function (event) {
            var input = document.getElementById('file');
            var bundle = document.getElementById('bundle');
            if (bundle.value || !input.files.length) return;
            event.preventDefault();
            var reader = new FileReader();
            reader.onload = function () {
              bundle.value = String(reader.result).split(',')[1] || '';
              form.submit();
            };
            reader.readAsDataURL(input.files[0]);
          });
        })();
    """

    /**
     * The 6-digit PIN box, shown as six spaced digits. The script keeps only digits and spaces them as they are typed;
     * the server reads the digits back out, so the spaces never matter.
     */
    /**
     * Six PIN slots, drawn like the phone's own keypad screen. The real input sits on top of them, transparent, so a
     * tap anywhere opens the keypad and the form still submits its one field. The script copies each digit into a slot.
     */
    private fun pinField(new: Boolean): String = """
        <div class="pin-wrap">
          <div class="slots" aria-hidden="true"><span></span><span></span><span></span><span></span><span></span><span></span></div>
          <input name="pin" class="pin-input" inputmode="numeric" autocomplete="${if (new) "new-password" else "off"}" required
            maxlength="6" aria-label="6-digit PIN">
        </div>
    """.trimIndent()

    private const val PIN_SCRIPT = """
        (function () {
          document.querySelectorAll('.pin-wrap').forEach(function (wrap) {
            var input = wrap.querySelector('input.pin-input');
            var slots = wrap.querySelectorAll('.slots span');
            function show() {
              var digits = input.value.replace(/\D/g, '').slice(0, 6);
              input.value = digits;
              for (var i = 0; i < slots.length; i++) {
                slots[i].textContent = digits.charAt(i);
                slots[i].className = i === digits.length ? 'active' : (i < digits.length ? 'on' : '');
              }
            }
            input.addEventListener('input', show);
            input.addEventListener('focus', show);
            input.addEventListener('blur', function () {
              for (var i = 0; i < slots.length; i++) slots[i].className = i < input.value.length ? 'on' : '';
            });
            show();
          });
        })();
    """

    private fun fieldClass(form: SetupForm, name: String) = if (form.errorField == name) "bad" else ""

    /** Opens an optional section when one of its fields has the problem, so the owner sees what to fix. */
    private fun openIf(form: SetupForm, vararg names: String): String = if (form.errorField in names) " open" else ""

    /**
     * Copies the Jellyfin username into the Music username while the Music field is still untouched. Music usually uses
     * the same login as Jellyfin, and once it is typed over, the copy stops.
     */
    private const val AUTOFILL_SCRIPT = """
        (function () {
          var jellyfin = document.querySelector('input[name=username]');
          var music = document.getElementById('musicUsername');
          if (!jellyfin || !music) return;
          if (!music.value) { music.value = jellyfin.value; music.dataset.copied = '1'; }
          jellyfin.addEventListener('input', function () {
            if (music.dataset.copied === '1' || !music.value) { music.value = jellyfin.value; music.dataset.copied = '1'; }
          });
          music.addEventListener('input', function () { music.dataset.copied = ''; });
        })();
    """

    /** Links Stremio from the phone: the TV makes the code, the phone opens Stremio, and the TV waits for the sign-in. */
    private const val STREMIO_SCRIPT = """
        (function () {
          var form = document.getElementById('setup-form');
          var code = form.getAttribute('action').split('/').pop();
          var start = document.getElementById('stremio-start');
          var status = document.getElementById('stremio-status');
          var deadline = 0;
          function poll() {
            if (Date.now() > deadline) { start.disabled = false; status.textContent = 'That took too long. Tap Link Stremio to try again.'; return; }
            fetch('/stremio-check/' + code, { cache: 'no-store' }).then(function (r) { return r.json(); }).then(function (d) {
              if (d.status === 'linked') { start.disabled = false; status.textContent = 'Stremio is linked. Your Watchlist is ready on the TV.'; return; }
              if (d.status === 'waiting') { setTimeout(poll, 2000); return; }
              start.disabled = false; status.textContent = d.message || 'Stremio stopped. Tap Link Stremio to try again.';
            }).catch(function () { setTimeout(poll, 3000); });
          }
          start.addEventListener('click', function () {
            start.disabled = true; status.textContent = 'Getting a code from Stremio…';
            fetch('/stremio-start/' + code, { cache: 'no-store' }).then(function (r) { return r.json(); }).then(function (d) {
              if (d.error) { start.disabled = false; status.textContent = d.error; return; }
              window.open(d.link, '_blank');
              status.textContent = 'Sign in to Stremio on the page that opened. If it asks for a code, enter ' + d.code + '.';
              deadline = Date.now() + 10 * 60 * 1000;
              poll();
            }).catch(function () { start.disabled = false; status.textContent = 'Could not reach the TV. Check the Wi-Fi and try again.'; });
          });
        })();
    """

    /** Tap-to-fill buttons for the servers the TV found, and for the Seerr address that goes with the Jellyfin one. */
    private fun chips(form: SetupForm): String = buildString {
        append("<div class=\"chips\">")
        form.found.forEach { s ->
            append("<button type=\"button\" class=\"chip\" data-fill=\"jellyfinUrl\" data-value=\"${escape(s.address)}\">")
            append("${escape(s.name)} · ${escape(s.address.removePrefix("http://"))}</button>")
        }
        append("</div>")
    }

    /** The Seerr suggestion, drawn inside the Seerr section so it sits with the field it fills. */
    private fun seerrChip(form: SetupForm): String = form.seerrSuggestion?.let {
        "<div class=\"chips\"><button type=\"button\" class=\"chip\" data-fill=\"seerrUrl\" data-value=\"${escape(it)}\">" +
            "Seerr at ${escape(it.removePrefix("http://"))}</button></div>"
    } ?: ""

    private fun path(action: String, code: String) = "/$action/${escape(code)}"

    private fun page(title: String, eyebrow: String, lead: String, error: String?, body: String): String = """
        <!doctype html>
        <html lang="en">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <meta name="robots" content="noindex">
          <meta name="color-scheme" content="dark">
          <title>${escape(title)} · Cinema</title>
          <style>
            :root { --bg: #0B0807; --cream: #F6ECDB; --muted: #B6A896; --dim: #7F7366; --terra: #E27A49; --saffron: #EDBE56;
                    --ink: #1B120C; --field: #1F1812; --line: rgba(255,242,222,.14); --danger: #F0A48A; }
            * { box-sizing: border-box; }
            [hidden] { display: none !important; }
            body { margin: 0; background: var(--bg); color: var(--cream); font: 16px/1.5 -apple-system, system-ui, "Segoe UI", Roboto, sans-serif; }
            main { max-width: 34rem; margin: 0 auto; padding: 20px 16px 48px; }
            header { display: flex; align-items: center; gap: 12px; margin-bottom: 22px; }
            .wordmark { font: 600 24px/1 Georgia, "Times New Roman", serif; letter-spacing: -.01em; }
            .eyebrow { display: flex; align-items: center; gap: 10px; margin: 0 0 6px; color: var(--saffron); font-size: 12px;
                       font-weight: 700; letter-spacing: .2em; text-transform: uppercase; }
            .eyebrow::before { content: ""; width: 36px; height: 6px; border-radius: 3px;
                               background: linear-gradient(90deg, #F6BF33 0 20%, #F08A25 20% 40%, #E2562B 40% 60%, #C2281D 60% 80%, #6B1A10 80% 100%); }
            .bands { display: flex; height: 10px; border-radius: 5px; overflow: hidden; margin: 0 0 22px; }
            .bands span { flex: 1; }
            .bands span:nth-child(1) { background: #F6BF33; }
            .bands span:nth-child(2) { background: #F08A25; }
            .bands span:nth-child(3) { background: #E2562B; }
            .bands span:nth-child(4) { background: #C2281D; }
            .bands span:nth-child(5) { background: #6B1A10; }
            h1 { font: 500 34px/1.1 Georgia, "Times New Roman", serif; margin: 0 0 10px; letter-spacing: -.02em; }
            .lead { margin: 0 0 18px; color: var(--muted); }
            .card { background: rgba(255,242,222,.06); border: 1px solid var(--line); border-radius: 18px; padding: 16px; margin: 0 0 14px; }
            h2 { font-size: 15px; margin: 2px 0 12px; letter-spacing: .02em; }
            label { display: block; margin: 0 0 12px; font-size: 14px; font-weight: 600; color: var(--muted); }
            input { display: block; width: 100%; margin-top: 6px; padding: 14px 16px; font: inherit; font-size: 17px; color: var(--cream);
                    background: var(--field); border: 1px solid var(--line); border-radius: 14px; }
            input::placeholder { color: var(--dim); }
            input:focus { outline: 2px solid var(--terra); outline-offset: 1px; }
            input.bad { border-color: var(--danger); box-shadow: 0 0 0 2px rgba(240,164,138,.35); }
            input.file { padding: 12px; }
            button { font: inherit; border: 0; cursor: pointer; }
            button:disabled { opacity: .5; cursor: default; }
            .primary { display: block; width: 100%; margin-top: 6px; padding: 16px; font-size: 17px; font-weight: 700; color: var(--ink);
                       background: #E27A49; border-radius: 999px; }
            .secondary { display: block; width: 100%; padding: 14px; font-size: 16px; font-weight: 600; color: var(--cream);
                         background: rgba(255,242,222,.1); border: 1px solid var(--line); border-radius: 999px; }
            .chips { display: flex; flex-wrap: wrap; gap: 8px; margin: 0 0 14px; }
            .chip { padding: 10px 14px; font-size: 14px; font-weight: 600; color: var(--cream); background: rgba(255,242,222,.1);
                    border: 1px solid var(--line); border-radius: 999px; }
            .hint { margin: 0 0 12px; font-size: 14px; color: var(--muted); }
            .muted { color: var(--dim); font-weight: 400; }
            .code { margin: 14px 0; text-align: center; font: 600 44px/1 Georgia, serif; letter-spacing: .18em; color: var(--saffron); }
            .error { margin: 0 0 16px; padding: 14px 16px; border-radius: 14px; font-size: 15px; line-height: 1.45;
                     background: rgba(240,164,138,.1); border: 1px solid rgba(240,164,138,.6); color: var(--danger); }
            .footer { margin-top: 26px; font-size: 13px; color: var(--dim); text-align: center; }
            .field-label { font-size: 14px; font-weight: 600; color: var(--muted); margin: 0 0 6px; }
            details.optional > summary { display: flex; align-items: baseline; gap: 8px; cursor: pointer; list-style: none; }
            details.optional > summary::-webkit-details-marker { display: none; }
            details.optional > summary::after { content: "+"; margin-left: auto; font-size: 22px; color: var(--muted); }
            details.optional[open] > summary::after { content: "–"; }
            details.optional[open] > summary { margin-bottom: 12px; }
            .sec { font-size: 15px; font-weight: 700; letter-spacing: .02em; }
            .pin-wrap { position: relative; margin: 6px 0 18px; }
            .slots { display: flex; justify-content: space-between; gap: 10px; }
            .slots span { flex: 1; height: 76px; display: flex; align-items: center; justify-content: center;
                          font: 500 40px/1 Georgia, "Times New Roman", serif; color: var(--cream);
                          background: rgba(255,242,222,.11); border: 2px solid var(--line); border-radius: 14px; }
            .slots span.on { border-color: var(--terra); }
            .slots span.active { border-color: var(--saffron); background: rgba(246,191,51,.12); }
            .pin-input { position: absolute; inset: 0; width: 100%; height: 100%; margin: 0; padding: 0; opacity: 0; border: 0;
                         font-size: 16px; color: transparent; background: transparent; }
          </style>
        </head>
        <body>
          <main>
            <header>
              ${LOGO}
              <span class="wordmark">Cinema</span>
            </header>
            <div class="bands" aria-hidden="true"><span></span><span></span><span></span><span></span><span></span></div>
            <p class="eyebrow">${escape(eyebrow)}</p>
            <h1>${escape(title)}</h1>
            <p class="lead">${escape(lead)}</p>
            ${if (error.isNullOrBlank()) "" else "<p class=\"error\" role=\"alert\">${escape(error)}</p>"}
            $body
            <p class="footer">Stays on your home network. Nothing is sent to anyone else.</p>
          </main>
        </body>
        </html>
    """.trimIndent()

    /** The roundel from the TV's logo, in the same colours. */
    /** The Cinema mark from the TV's cinema_mark drawable: five flat concentric rings and a dark play triangle. */
    private const val LOGO = """<svg viewBox="0 0 56 56" width="48" height="48" aria-hidden="true"><circle cx="28" cy="28" r="27" fill="#6B1A10"/><circle cx="28" cy="28" r="22" fill="#C2281D"/><circle cx="28" cy="28" r="17" fill="#E2562B"/><circle cx="28" cy="28" r="12" fill="#F08A25"/><circle cx="28" cy="28" r="7.2" fill="#F6BF33"/><path d="M25.2 24.2v7.6a1 1 0 0 0 1.5.86l6.3-3.8a1 1 0 0 0 0-1.72l-6.3-3.8a1 1 0 0 0-1.5.86z" fill="#1B0D06"/></svg>"""

    /** Escapes text for an HTML body or attribute. */
    fun escape(text: String): String = buildString(text.length) {
        for (c in text) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(c)
            }
        }
    }
}
