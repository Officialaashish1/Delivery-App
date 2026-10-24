package `in`.localrush.core.common.geo

/**
 * Google encoded polyline (precision 5) codec, as returned by OSRM via `route.geometry`.
 */
object Polyline {
    fun decode(encoded: String?, precision: Int = 5): List<LatLng> {
        if (encoded.isNullOrEmpty()) return emptyList()
        val factor = Math.pow(10.0, precision.toDouble())
        val result = ArrayList<LatLng>(encoded.length / 4)
        var index = 0
        var lat = 0
        var lng = 0
        while (index < encoded.length) {
            var shift = 0
            var acc = 0
            var b: Int
            do {
                b = encoded[index++].code - 63
                acc = acc or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20 && index < encoded.length)
            lat += if (acc and 1 != 0) (acc shr 1).inv() else acc shr 1

            if (index >= encoded.length) break
            shift = 0
            acc = 0
            do {
                b = encoded[index++].code - 63
                acc = acc or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20 && index < encoded.length)
            lng += if (acc and 1 != 0) (acc shr 1).inv() else acc shr 1

            result.add(LatLng(lat / factor, lng / factor))
        }
        return result
    }

    fun encode(points: List<LatLng>, precision: Int = 5): String {
        val factor = Math.pow(10.0, precision.toDouble())
        val sb = StringBuilder()
        var prevLat = 0L
        var prevLng = 0L
        for (p in points) {
            val lat = Math.round(p.latitude * factor)
            val lng = Math.round(p.longitude * factor)
            encodeValue(lat - prevLat, sb)
            encodeValue(lng - prevLng, sb)
            prevLat = lat
            prevLng = lng
        }
        return sb.toString()
    }

    private fun encodeValue(value: Long, sb: StringBuilder) {
        var v = if (value < 0) (value shl 1).inv() else value shl 1
        while (v >= 0x20) {
            sb.append(((0x20 or (v and 0x1f).toInt()) + 63).toChar())
            v = v shr 5
        }
        sb.append((v.toInt() + 63).toChar())
    }
}
