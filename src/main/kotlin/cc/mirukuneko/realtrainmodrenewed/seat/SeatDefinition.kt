package cc.mirukuneko.realtrainmodrenewed.seat

import net.minecraft.world.phys.Vec3
import org.joml.Vector3f

class SeatDefinition @JvmOverloads constructor(
    var id: String? = null,
    var type: SeatType? = null,
    var position: Vec3? = null,
    var polygon: List<Vec3>? = null,
) {
    enum class SeatType {
        DRIVER,
        PASSENGER,
    }

    var rotation: Vector3f? = Vector3f(0f, 0f, 0f)
    var height: Double = 1.0
    var isDriver: Boolean = false

    fun isPointInPolygon(point: Vec3): Boolean {
        val points = polygon
        if (points == null || points.size < 3) {
            return false
        }

        var inside = false
        var j = points.size - 1
        for (i in points.indices) {
            val pi = points[i]
            val pj = points[j]
            if ((pi.z > point.z) != (pj.z > point.z) &&
                point.x < (pj.x - pi.x) * (point.z - pi.z) / (pj.z - pi.z) + pi.x
            ) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    fun isPointInSeat(point: Vec3): Boolean {
        if (!isPointInPolygon(point)) {
            return false
        }
        val seatPosition = position ?: return false
        val minY = seatPosition.y
        val maxY = seatPosition.y + height
        return point.y >= minY && point.y <= maxY
    }
}
