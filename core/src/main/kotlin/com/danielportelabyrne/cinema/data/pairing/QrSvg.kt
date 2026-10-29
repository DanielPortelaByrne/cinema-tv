package com.danielportelabyrne.cinema.data.pairing

import io.nayuki.qrcodegen.QrCode

/**
 * Draws a QR code as SVG, made here on the TV. The text (the TV's own address, with its code) never goes to a web
 * service. Uses Nayuki's MIT-licensed generator, vendored under `core/src/main/java`.
 */
object QrSvg {
    /** The SVG for [text], with [border] quiet-zone modules around it. Dark modules are one path, so it scales cleanly. */
    fun toSvg(text: String, border: Int = 4): String {
        val qr = QrCode.encodeText(text, QrCode.Ecc.MEDIUM)
        val size = qr.size + border * 2
        val path = StringBuilder()
        for (y in 0 until qr.size) {
            for (x in 0 until qr.size) {
                if (qr.getModule(x, y)) path.append("M${x + border},${y + border}h1v1h-1z")
            }
        }
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 $size $size\" shape-rendering=\"crispEdges\">" +
            "<rect width=\"$size\" height=\"$size\" fill=\"#fff\"/>" +
            "<path fill=\"#000\" d=\"$path\"/></svg>"
    }

    /** The QR version (its side in modules is 17 + 4 × version). Handy for tests and for checking a link is not too long. */
    fun versionOf(text: String): Int = QrCode.encodeText(text, QrCode.Ecc.MEDIUM).version
}
