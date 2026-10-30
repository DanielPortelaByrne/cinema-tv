-- Cinema's Android PlayerChrome reproduced as an mpv ASS overlay.
-- Coordinates deliberately use the Android 1920 x 1080 design grid.
local mp = require('mp')
local assdraw = require('mp.assdraw')
local utils = require('mp.utils')

local overlay = mp.create_osd_overlay('ass-events')
local hide_timer
local visible, paused, scrubbing = true, false, false
local duration, position, scrub_position = 0, 0, 0
local title, chapter, year, certificate, overview = '', '', '', '', ''
local rating, runtime_min, size_bytes, audio_codec = nil, nil, nil, ''
local genres, audio, subtitles, chapters, current_chapter = {}, '', '', {}, 0

-- ASS uses BBGGRR rather than RGB. These are the exact Cinema palette values.
local C = {
    bg = '07080B', cream = 'DBECF6', muted = '96A8B6', dim = '66737F', reading = 'B9CBD7',
    ink = '0C121B', saffron = '56BEED', placeholder = '151A22', rail = '0A0D12',
    maroon = '10175D', red = '1D28C2', terra = '2B56E2', orange = '258AF0', stripe = '33BFF6',
    green = '8AD05F',
}

local function esc(value) return mp.command_native({ 'escape-ass', tostring(value or '') }) end
local function clamp(value, low, high) return math.max(low, math.min(high, value)) end
local function alpha(opacity) return string.format('%02X', clamp(math.floor((1 - opacity) * 255 + .5), 0, 255)) end
local function clock(seconds)
    seconds = math.max(0, math.floor(tonumber(seconds) or 0))
    local hours, minutes, secs = math.floor(seconds / 3600), math.floor(seconds / 60) % 60, seconds % 60
    return hours > 0 and string.format('%d:%02d:%02d', hours, minutes, secs) or string.format('%d:%02d', minutes, secs)
end
local function runtime(minutes)
    if not minutes or minutes < 1 then return nil end
    return minutes >= 60 and string.format('%dh %dm', math.floor(minutes / 60), minutes % 60) or string.format('%d min', minutes)
end
-- ASS has no TextView ellipsize; constrain dynamic mpv strings so they cannot overpaint adjacent chrome.
local function ellipsize(value, characters)
    value = tostring(value or '')
    return #value > characters and (value:sub(1, math.max(1, characters - 1)) .. '…') or value
end

local function path(ass, x, y, commands, color, opacity)
    ass:new_event()
    ass:append(string.format('{\\an7\\pos(%.1f,%.1f)\\bord0\\shad0\\1c&H%s&\\1a&H%s&\\p1}%s{\\p0}', x, y, color, alpha(opacity or 1), commands))
end
local function rect(ass, x, y, width, height, color, opacity)
    path(ass, x, y, string.format('m 0 0 l %.1f 0 %.1f %.1f 0 %.1f', width, width, height, height), color, opacity)
end
local function circle(ass, cx, cy, radius, color, opacity)
    -- libass's cubic-path implementation differs between mpv builds. A 32-sided polygon is visually round at
    -- this scale and, unlike a Bézier path, cannot produce the broken wedge seen on some TVs.
    local points = {}
    for i = 0, 32 do
        local angle = -math.pi / 2 + i * math.pi * 2 / 32
        points[#points + 1] = string.format('%s %.2f %.2f', i == 0 and 'm' or 'l', radius * math.cos(angle), radius * math.sin(angle))
    end
    path(ass, cx, cy, table.concat(points, ' '), color, opacity)
end
local function stroke_path(ass, x, y, commands, color, width)
    ass:new_event()
    ass:append(string.format('{\\an7\\pos(%.1f,%.1f)\\bord%.2f\\1a&HFF&\\3c&H%s&\\3a&H00&\\p1}%s{\\p0}', x, y, width, color, commands))
end

-- Exact geometry of ic_cin_b10.xml / ic_cin_f10.xml, scaled from their 24px viewport to Android's 64px icon box.
local function seek_ten_icon(ass, cx, cy, forward, scale)
    local unit = 64 * scale / 24
    local function point(x, y)
        if forward then x = 24 - x end -- f10 is b10 mirrored about the vector viewport's centre
        return (x - 12) * unit, (y - 12) * unit
    end
    local commands = {}
    local arc_points = {}
    for i = 0, 48 do
        -- Sample Android's 270-degree arc densely; the denser path avoids the
        -- visibly faceted ring produced by coarse sampling in libass.
        local angle = math.pi - i * (math.pi * 1.5 / 48)
        local px, py = point(12.2 + 8 * math.cos(angle), 12 + 8 * math.sin(angle))
        arc_points[#arc_points + 1] = { px, py }
        commands[#commands + 1] = string.format('%s %.2f %.2f', i == 0 and 'm' or 'l', px, py)
    end
    local x, y = point(9, 4); commands[#commands + 1] = string.format('l %.2f %.2f', x, y)
    local ax, ay = point(9, 1.6); local bx, by = point(5.8, 4.2); local dx, dy = point(9, 6.8)
    commands[#commands + 1] = string.format('m %.2f %.2f l %.2f %.2f l %.2f %.2f', ax, ay, bx, by, dx, dy)
    local stroke = 1.7 * unit
    stroke_path(ass, cx, cy, table.concat(commands, ' '), C.cream, stroke)
    -- ASS/libass uses sharp joins and caps for open vector paths; overlay small
    -- round caps at the Android path's endpoints and arrowhead corners.
    local cap = stroke / 2
    for _, p in ipairs({ arc_points[1], arc_points[#arc_points] }) do
        circle(ass, cx + p[1], cy + p[2], cap, C.cream)
    end
    for _, xy in ipairs({ {9, 4}, {9, 1.6}, {5.8, 4.2}, {9, 6.8} }) do
        local px, py = point(xy[1], xy[2])
        circle(ass, cx + px, cy + py, cap, C.cream)
    end
end
local function text(ass, x, y, value, size, color, align, font, bold, opacity)
    ass:new_event()
    ass:append(string.format('{\\an%d\\pos(%d,%d)\\bord0\\shad0\\fs%d\\1c&H%s&\\1a&H%s&%s%s}%s',
        align or 7, x, y, math.max(1, math.floor(size)), color, alpha(opacity or 1),
        font and ('\\fn' .. font) or '', bold and '\\b1' or '\\b0', esc(value)))
end
local function stripe_mark(ass, x, y, scale, vertical)
    local colors = { C.stripe, C.orange, C.terra, C.red, C.maroon }
    for i, color in ipairs(colors) do
        if vertical then rect(ass, x + (i - 1) * 6 * scale, y, 6 * scale, 30 * scale, color)
        else rect(ass, x, y + (i - 1) * 2 * scale, 40 * scale, 2 * scale, color) end
    end
end

-- A sampled ribbon follows the same wavy shape and five-band playhead as WaveScrubberView.
local function wave_scrubber(ass, x, y, width, fraction, scale)
    local amp, thickness, step = 11 * scale, 26 * scale, math.max(2, math.floor(4 * scale))
    local function wave(px) return y + amp * math.sin((px - x) * math.pi * 2 / (150 * scale)) end
    local function ribbon(from, to, color, height, opacity)
        if to <= from then return end
        local points, px = { string.format('m %.1f %.1f', from - x, wave(from) - y - height / 2) }, from
        while px < to do
            px = math.min(to, px + step)
            points[#points + 1] = string.format('l %.1f %.1f', px - x, wave(px) - y - height / 2)
        end
        while px > from do
            points[#points + 1] = string.format('l %.1f %.1f', px - x, wave(px) - y + height / 2)
            px = math.max(from, px - step)
        end
        points[#points + 1] = string.format('l 0 %.1f', wave(from) - y + height / 2)
        path(ass, x, y, table.concat(points, ' '), color, opacity)
    end
    fraction = clamp(fraction or 0, 0, 1)
    local played = width * fraction
    ribbon(x, x + width, C.rail, thickness * .7, .62)
    ribbon(x, x + played, C.maroon, thickness, 1)
    ribbon(x, x + played, C.red, thickness * .72, 1)
    ribbon(x, x + played, C.terra, thickness * .46, 1)
    ribbon(x, x + played, C.orange, thickness * .22, 1)
    for _, item in ipairs(chapters) do
        if item.time and duration > 0 and item.time > 0 then
            local cx = x + width * item.time / duration
            rect(ass, cx - 3 * scale, wave(cx) - 3 * scale, 6 * scale, 6 * scale, cx <= x + played and C.ink or C.cream)
        end
    end
    local hx, hy = x + played, wave(x + played)
    -- glow then the five Android stripe rings and cream centre
    circle(ass, hx, hy, 37 * scale, C.cream, .10)
    for i, color in ipairs({ C.maroon, C.red, C.terra, C.orange, C.stripe }) do
        circle(ass, hx, hy, (24 - i * 3) * scale, color)
    end
    circle(ass, hx, hy, 10 * scale, C.cream)
end

local function metadata(paused_view)
    local fields = {}
    -- The controls show year, two genres and runtime; the paused meta row additionally has the certificate, rating,
    -- and a third genre, matching PlayerChrome.setMeta.
    if paused_view and certificate ~= '' then fields[#fields + 1] = certificate end
    if year ~= '' then fields[#fields + 1] = year end
    local length = runtime(runtime_min)
    if length then fields[#fields + 1] = length end
    for i = 1, math.min(paused_view and 3 or 2, #genres) do fields[#fields + 1] = genres[i] end
    if paused_view and rating then fields[#fields + 1] = string.format('★ %.1f', rating) end
    return table.concat(fields, '   ·   ')
end
local function current_chapter_name(at)
    local chosen
    for index, item in ipairs(chapters) do
        if (item.time or 0) <= at then chosen = index else break end
    end
    return chosen and chapters[chosen] or nil, chosen
end

local function transport(ass, w, scale)
    local y, cx = 696 * scale, w / 2
    local function previous(x, mirrored)
        -- Match ic_cin_prev/ic_cin_next's 24dp vector viewport, shown at 44dp.
        -- Prev is a bar at x=6 and a left-pointing triangle; next mirrors it.
        local unit = 44 * scale / 24
        local function px(value) return ((mirrored and 24 - value or value) - 12) * unit end
        rect(ass, x + px(6), y - 7 * unit, 1.7 * unit, 14 * unit, C.cream)
        local left, right = px(8.5), px(19)
        path(ass, x + left, y, string.format('m 0 %.1f l %.1f %.1f 0 %.1f', -6.5 * unit, right - left, 6.5 * unit, 13 * unit), C.cream)
    end
    previous(cx - 292 * scale, true)
    seek_ten_icon(ass, cx - 165 * scale, y, false, scale)
    text(ass, cx - 165 * scale, y + 6 * scale, '10', 17 * scale, C.cream, 8, 'Inter', true)
    for i, color in ipairs({ C.maroon, C.red, C.terra, C.orange, C.stripe }) do
        circle(ass, cx, y, (101 - i * 9) * scale, color)
    end
    circle(ass, cx, y, 56 * scale, C.cream)
    -- The centre disc uses only rectangle/polygon primitives. Inter's Roman-numeral pause glyph has a broken
    -- fallback in the mpv/libass build used on the desktop.
    if paused then
        path(ass, cx - 12 * scale, y - 20 * scale, string.format('m 0 0 l %.1f %.1f l 0 %.1f l 0 0', 34 * scale, 20 * scale, 40 * scale), C.ink)
    else
        rect(ass, cx - 16 * scale, y - 22 * scale, 10 * scale, 44 * scale, C.ink)
        rect(ass, cx + 6 * scale, y - 22 * scale, 10 * scale, 44 * scale, C.ink)
    end
    seek_ten_icon(ass, cx + 165 * scale, y, true, scale)
    text(ass, cx + 165 * scale, y + 6 * scale, '10', 17 * scale, C.cream, 8, 'Inter', true)
    previous(cx + 292 * scale, false)
end

local function controls(ass, w, h, scale)
    local left, right = 96 * scale, w - 96 * scale
    local shown = scrubbing and scrub_position or position
    local fraction = duration > 0 and shown / duration or 0
    -- Android's full top/bottom cinematic scrim.
    rect(ass, 0, 0, w, 190 * scale, C.bg, .85)
    rect(ass, 0, h - 250 * scale, w, 250 * scale, C.bg, .95)
    stripe_mark(ass, left, 70 * scale, scale, false)
    text(ass, left + 54 * scale, 68 * scale, scrubbing and 'SCRUBBING' or 'NOW PLAYING', 15 * scale, C.saffron, 7, 'Inter', true)
    text(ass, left, 136 * scale, ellipsize(title ~= '' and title or 'Cinema', 34), 60 * scale, C.cream, 7, 'Fraunces')
    local meta = metadata(false)
    if meta ~= '' then text(ass, left, 190 * scale, meta, 21 * scale, C.muted, 7, 'Inter') end
    text(ass, right, 82 * scale, os.date('%H:%M'), 22 * scale, C.muted, 9, 'Inter', true)

    if scrubbing then
        local x = clamp(50 * scale + 1820 * scale * fraction - 210 * scale, 96 * scale, w - 516 * scale)
        rect(ass, x + 20 * scale, 520 * scale, 420 * scale, 236 * scale, C.orange)
        rect(ass, x + 10 * scale, 510 * scale, 420 * scale, 236 * scale, C.stripe)
        rect(ass, x, 500 * scale, 420 * scale, 236 * scale, C.placeholder)
        rect(ass, x, 646 * scale, 420 * scale, 90 * scale, C.bg, .78)
        text(ass, x + 20 * scale, 714 * scale, clock(shown), 44 * scale, C.cream, 7, 'Fraunces')
        local ch, index = current_chapter_name(shown)
        if ch then
            text(ass, x, 804 * scale, string.format('CHAPTER %d OF %d', index, #chapters), 15 * scale, C.saffron, 8, 'Inter', true)
            text(ass, x, 846 * scale, ch.title or ('Chapter ' .. index), 30 * scale, C.cream, 8, 'Fraunces')
        end
        text(ass, left, 1016 * scale, '◀ ▶  Scrub                 OK  Play from here                 Back  Cancel', 18 * scale, C.muted, 7, 'Inter')
    else
        transport(ass, w, scale)
        local chip_x = left
        for _, chip in ipairs({ subtitles ~= '' and ('CC  ' .. subtitles) or nil, audio ~= '' and ('◖  ' .. audio) or nil }) do
            if chip then
                local width = math.min(360, #chip * 12 + 52) * scale
                rect(ass, chip_x, h - 94 * scale, width, 54 * scale, C.cream, .075)
                text(ass, chip_x + 22 * scale, h - 67 * scale, chip, 20 * scale, C.cream, 7, 'Inter')
                chip_x = chip_x + width + 16 * scale
            end
        end
        rect(ass, right - 54 * scale, h - 94 * scale, 54 * scale, 54 * scale, C.cream, .075)
        text(ass, right - 27 * scale, h - 67 * scale, '☷', 26 * scale, C.cream, 8, 'Inter')
    end
    wave_scrubber(ass, 50 * scale, 854 * scale, 1820 * scale, fraction, scale)
    if not scrubbing then
        local ch, index = current_chapter_name(position)
        if ch then text(ass, w / 2, 964 * scale, string.format('CH %d  ·  %s', index, ch.title or ('Chapter ' .. index)), 17 * scale, C.saffron, 8, 'Inter', true) end
        text(ass, left, 969 * scale, clock(position), 23 * scale, C.cream, 7, 'Inter', true)
        text(ass, right, 969 * scale, duration > 0 and (clock(duration - position) .. ' left') or '', 23 * scale, C.muted, 9, 'Inter')
    end
end

local function paused_screen(ass, w, h, scale)
    local left, right = 96 * scale, w - 96 * scale
    local fraction = duration > 0 and position / duration or 0
    -- Android keeps the paused frame visible below its scrim. ASS cannot blur it, so do not bury it in opaque black.
    rect(ass, 0, 0, w, h, C.bg, .40)
    stripe_mark(ass, left, 78 * scale, scale, true)
    text(ass, left + 44 * scale, 88 * scale, 'PAUSED', 17 * scale, C.saffron, 7, 'Inter', true)
    local paused_title = title ~= '' and title or 'Now paused'
    local paused_size = #paused_title > 30 and 68 or #paused_title > 16 and 84 or 104
    -- PlayerChrome constrains this TextView to 900 mockup pixels.
    text(ass, left, 196 * scale, ellipsize(paused_title, paused_size == 68 and 23 or paused_size == 84 and 19 or 15), paused_size * scale, C.cream, 7, 'Fraunces')
    local meta = metadata(true)
    if meta ~= '' then text(ass, left, 252 * scale, meta, 23 * scale, C.muted, 7, 'Inter') end
    if overview ~= '' then
        local words, line, y = {}, '', 292 * scale
        for word in overview:gmatch('%S+') do
            local candidate = line == '' and word or (line .. ' ' .. word)
            if #candidate > 72 then words[#words + 1] = line; line = word else line = candidate end
            if #words == 3 then break end
        end
        if #words < 3 and line ~= '' then words[#words + 1] = line end
        for index, value in ipairs(words) do text(ass, left, y + (index - 1) * 31 * scale, value, 24 * scale, C.reading, 7, 'Inter') end
    end
    rect(ass, left, 416 * scale, 182 * scale, 76 * scale, C.cream)
    text(ass, left + 91 * scale, 454 * scale, '▶  Resume', 22 * scale, C.ink, 8, 'Inter', true)
    rect(ass, left + 200 * scale, 416 * scale, 202 * scale, 76 * scale, C.cream, .10)
    text(ass, left + 301 * scale, 454 * scale, '↶  Start over', 20 * scale, C.cream, 8, 'Inter', true)
    if duration > 0 then text(ass, left + 434 * scale, 454 * scale, 'Ends at ' .. os.date('%H:%M', os.time() + duration - position), 20 * scale, C.muted, 7, 'Inter') end

    local card_x, card_y, card_w = 1130 * scale, 70 * scale, 694 * scale
    rect(ass, card_x, card_y, card_w, 470 * scale, C.bg, .72)
    text(ass, card_x + 36 * scale, card_y + 32 * scale, 'STREAM', 15 * scale, C.saffron, 7, 'Inter', true)
    text(ass, card_x + card_w - 36 * scale, card_y + 32 * scale, '●  Healthy', 18 * scale, C.green, 9, 'Inter', true)
    local video = mp.get_property('video-codec') or '—'
    local vw, vh = mp.get_property_number('width', 0), mp.get_property_number('height', 0)
    local video_value = vw > 0 and string.format('%s  ·  %d × %d', video, vw, vh) or video
    local specs = { { 'VIDEO', video_value }, { 'AUDIO', audio_codec ~= '' and audio_codec or (audio ~= '' and audio or 'Default track') }, { 'FILE SIZE', size_bytes and string.format('%.1f GB', size_bytes / 1073741824) or '—' } }
    for index, spec in ipairs(specs) do
        local column, row = (index - 1) % 2, math.floor((index - 1) / 2)
        local x, y = card_x + 36 * scale + column * 315 * scale, card_y + 94 * scale + row * 112 * scale
        text(ass, x, y, spec[1], 14 * scale, C.dim, 7, 'Inter', true)
        text(ass, x, y + 34 * scale, ellipsize(spec[2], 21), 22 * scale, C.cream, 7, 'Inter')
    end
    -- Android hides the bitrate sparkline until genuine samples are supplied. mpv does not expose a matching
    -- rolling bitrate property, so deliberately leave this area empty rather than draw fabricated telemetry.

    if #chapters > 0 then
        text(ass, left, 612 * scale, 'CHAPTERS  ·  ' .. #chapters, 15 * scale, C.saffron, 7, 'Inter', true)
        local active = current_chapter + 1
        local first = clamp(active - 1, 1, math.max(1, #chapters - 3))
        for index = first, math.min(#chapters, first + 3) do
            local x, on = left + (index - first) * 292 * scale, index == active
            rect(ass, x, 668 * scale, 268 * scale, 151 * scale, on and C.placeholder or C.bg, on and .98 or .78)
            if on then rect(ass, x, 814 * scale, 268 * scale * clamp((position - (chapters[index].time or 0)) / math.max(1, ((chapters[index + 1] or {}).time or duration) - (chapters[index].time or 0)), 0, 1), 5 * scale, C.saffron) end
            text(ass, x + 16 * scale, 692 * scale, tostring(index), 15 * scale, C.cream, 7, 'Inter', true)
            text(ass, x + 16 * scale, 846 * scale, chapters[index].title or ('Chapter ' .. index), 21 * scale, on and C.cream or C.muted, 7, 'Inter', true)
            text(ass, x + 16 * scale, 874 * scale, clock(chapters[index].time or 0), 16 * scale, C.dim, 7, 'Inter')
        end
    end
    wave_scrubber(ass, 50 * scale, 902 * scale, 1820 * scale, fraction, scale)
    text(ass, left, 1000 * scale, clock(position), 21 * scale, C.cream, 7, 'Inter', true)
    text(ass, right, 1000 * scale, duration > 0 and (clock(duration - position) .. ' left') or '', 21 * scale, C.muted, 9, 'Inter')
end

local function draw()
    local w, h = mp.get_osd_size()
    if not w or w < 1 or not h or h < 1 then return end
    local ass, scale = assdraw.ass_new(), math.min(w / 1920, h / 1080)
    if paused then paused_screen(ass, w, h, scale) elseif visible then controls(ass, w, h, scale) end
    overlay.data, overlay.res_x, overlay.res_y = ass.text, w, h
    overlay:update()
end

local function activity()
    visible = true
    if hide_timer then hide_timer:kill() end
    if not paused then hide_timer = mp.add_timeout(5, function() visible = false; scrubbing = false; draw() end) end
    draw()
end
local function seek(delta)
    activity()
    if scrubbing then scrub_position = clamp(scrub_position + delta, 0, duration) else mp.commandv('seek', delta, 'relative') end
    draw()
end
local function toggle_pause()
    if scrubbing then mp.commandv('seek', scrub_position, 'absolute'); scrubbing = false end
    mp.commandv('cycle', 'pause'); activity()
end

local function update_tracks(_, tracks)
    audio, subtitles = '', ''
    for _, track in ipairs(tracks or {}) do
        local description = track.title or track.lang
        if track.type == 'audio' and track.selected then audio = tostring(description or 'Audio') end
        if track.type == 'sub' and track.selected then subtitles = tostring(description or 'Subtitles') end
    end
    draw()
end

mp.observe_property('time-pos', 'number', function(_, value) position = value or 0; if not scrubbing then scrub_position = position end; draw() end)
mp.observe_property('duration', 'number', function(_, value) duration = value or 0; draw() end)
mp.observe_property('pause', 'bool', function(_, value) paused = value or false; scrubbing = false; activity() end)
mp.observe_property('media-title', 'string', function(_, value) title = value or ''; draw() end)
mp.observe_property('chapter-metadata/by-key/title', 'string', function(_, value) chapter = value or ''; draw() end)
mp.observe_property('track-list', 'native', update_tracks)
mp.observe_property('chapter-list', 'native', function(_, value) chapters = value or {}; draw() end)
mp.observe_property('chapter', 'number', function(_, value) current_chapter = value or 0; draw() end)

mp.register_script_message('cinema-meta', function(json)
    local data = utils.parse_json(json or '') or {}
    title = data.title or title; year = data.year and tostring(data.year) or year
    certificate = data.certificate or certificate; overview = data.overview or overview
    rating = tonumber(data.rating) or rating; runtime_min = tonumber(data.runtimeMin) or runtime_min
    size_bytes = tonumber(data.sizeBytes) or size_bytes; audio_codec = data.audioCodec or audio_codec
    genres = data.genres or genres; draw()
end)
mp.register_script_message('cinema-activity', activity)

-- Keyboard/mouse controls mirror PlayerChrome while the desktop gamepad continues to use IPC commands.
mp.add_forced_key_binding('SPACE', 'cinema-toggle', toggle_pause)
mp.add_forced_key_binding('ENTER', 'cinema-enter', toggle_pause)
mp.add_forced_key_binding('LEFT', 'cinema-left', function() seek(-10) end, { repeatable = true })
mp.add_forced_key_binding('RIGHT', 'cinema-right', function() seek(10) end, { repeatable = true })
mp.add_forced_key_binding('UP', 'cinema-scrub', function() activity(); scrubbing = true; scrub_position = position; draw() end)
mp.add_forced_key_binding('ESC', 'cinema-cancel-scrub', function() if scrubbing then scrubbing = false; draw() else mp.command('quit') end end)
mp.add_forced_key_binding('MBTN_LEFT', 'cinema-mouse-activity', activity, { complex = true })
mp.add_forced_key_binding('WHEEL_UP', 'cinema-wheel-up', activity)
mp.add_forced_key_binding('WHEEL_DOWN', 'cinema-wheel-down', activity)
mp.register_event('file-loaded', activity)
mp.register_event('seek', activity)
mp.register_event('playback-restart', draw)
mp.register_event('shutdown', function() overlay:remove() end)
activity()
