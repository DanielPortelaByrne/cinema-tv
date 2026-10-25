-- Cinema's compact, gamepad-friendly mpv controls. Colors and spacing match the Cinema desktop theme.
local mp = require 'mp'
local assdraw = require 'mp.assdraw'
local overlay = mp.create_osd_overlay('ass-events')
local timer
local visible = true
local duration, position, paused, title = 0, 0, false, ''

local function time(s)
    s = math.max(0, math.floor(s or 0))
    return string.format('%d:%02d', math.floor(s / 60), s % 60)
end
local function draw()
    local w, h = mp.get_osd_size()
    if not w or w == 0 or not h or h == 0 then return end
    local scale = math.min(w / 1920, h / 1080)
    local size = math.floor(25 * scale)
    local barW, barH = math.floor(1600 * scale), math.floor(8 * scale)
    local x, y = math.floor((w - barW) / 2), h - math.floor(115 * scale)
    local f = duration > 0 and math.max(0, math.min(1, position / duration)) or 0
    local ass = assdraw.ass_new()
    ass:append('{\\an7\\bord0\\shad0\\1c&HDBECF6&\\3c&H0B0807&\\fs' .. size .. '}')
    if visible then
        ass:append('{\\pos(' .. math.floor(48 * scale) .. ',' .. math.floor(48 * scale) .. ')}' .. mp.command_native({'escape-ass', title}))
        ass:append('{\\pos(' .. x .. ',' .. y .. ')}{\\rDefault\\1c&H342923&}▰')
        -- A hairline progress indicator with a saffron played section and a bright playhead.
        ass:append('{\\pos(' .. x .. ',' .. y .. ')}{\\1c&H362F29&}━{\\1c&H56BEED&}')
        local rail = assdraw.ass_new()
        rail:new_event(); rail:pos(x, y); rail:draw_start(); rail:rect_cw(0, 0, barW, barH); rail:draw_stop()
        rail:new_event(); rail:pos(x, y); rail:draw_start(); rail:rect_cw(0, 0, math.floor(barW * f), barH); rail:draw_stop()
        ass:append('{\\pos(' .. x .. ',' .. (y - math.floor(12 * scale)) .. ')}{\\bord0\\1c&H362F29&}━')
        ass:append('{\\pos(' .. math.floor(w / 2) .. ',' .. (h - math.floor(68 * scale)) .. ')}{\\an2\\fs' .. math.floor(19 * scale) .. '}')
        ass:append('◀  −10s     {\\1c&H56BEED&}' .. (paused and '▶  PLAY' or '❚❚  PAUSE') .. '{\\1c&HDBECF6&}     +10s  ▶')
        ass:append('{\\pos(' .. x .. ',' .. (y + math.floor(23 * scale)) .. ')}{\\fs' .. math.floor(17 * scale) .. '}' .. time(position) .. '    ' .. time(duration))
        ass:append('{\\pos(' .. (w - math.floor(48 * scale)) .. ',' .. math.floor(48 * scale) .. ')}{\\an3\\fs' .. math.floor(17 * scale) .. '}A Play   B Stop   ←→ Seek   LB/RB Chapters   X Subs   Y Audio')
    end
    overlay.data = ass.text
    overlay:update()
end
local function activity()
    visible = true
    if timer then timer:kill() end
    timer = mp.add_timeout(4, function() visible = false; draw() end)
    draw()
end
mp.observe_property('time-pos', 'number', function(_, v) position = v or 0; draw() end)
mp.observe_property('duration', 'number', function(_, v) duration = v or 0; draw() end)
mp.observe_property('pause', 'bool', function(_, v) paused = v or false; draw() end)
mp.observe_property('media-title', 'string', function(_, v) title = v or ''; draw() end)
mp.register_event('file-loaded', activity)
mp.register_event('seek', activity)
mp.register_event('playback-restart', draw)
mp.add_forced_key_binding('MBTN_LEFT', 'cinema-osc-click', activity, {complex=true})
mp.add_forced_key_binding('WHEEL_UP', 'cinema-osc-wheel-up', activity)
mp.add_forced_key_binding('WHEEL_DOWN', 'cinema-osc-wheel-down', activity)
mp.add_forced_key_binding('SPACE', 'cinema-osc-space', activity)
mp.add_forced_key_binding('LEFT', 'cinema-osc-left', activity)
mp.add_forced_key_binding('RIGHT', 'cinema-osc-right', activity)
mp.register_script_message('cinema-activity', activity)
mp.register_event('shutdown', function() overlay:remove() end)
activity()
