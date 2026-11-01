package ds.dsh.DeepSeek

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/** Original line icons used by the standalone module UI. */
internal class ModuleGlyphView(
    context: Context,
    private val type: Int,
    color: Int,
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var glyphColor: Int = color

    init {
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
    }

    fun setGlyphColor(value: Int) {
        glyphColor = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val size = minOf(w, h)
        val cx = w / 2f
        val cy = h / 2f
        paint.color = glyphColor
        paint.strokeWidth = maxOf(1.6f, size * 0.075f)
        paint.style = Paint.Style.STROKE
        path.reset()

        when (type) {
            HOME -> {
                path.moveTo(cx - size * .32f, cy - size * .02f)
                path.lineTo(cx, cy - size * .30f)
                path.lineTo(cx + size * .32f, cy - size * .02f)
                path.moveTo(cx - size * .24f, cy - size * .08f)
                path.lineTo(cx - size * .24f, cy + size * .28f)
                path.lineTo(cx + size * .24f, cy + size * .28f)
                path.lineTo(cx + size * .24f, cy - size * .08f)
                canvas.drawPath(path, paint)
            }

            SETTINGS -> {
                canvas.drawCircle(cx, cy, size * .13f, paint)
                canvas.drawCircle(cx, cy, size * .31f, paint)
                for (i in 0 until 8) {
                    val angle = Math.PI * i / 4.0
                    canvas.drawLine(
                        cx + cos(angle).toFloat() * size * .31f,
                        cy + sin(angle).toFloat() * size * .31f,
                        cx + cos(angle).toFloat() * size * .39f,
                        cy + sin(angle).toFloat() * size * .39f,
                        paint,
                    )
                }
            }

            REPOSITORY -> {
                canvas.drawCircle(cx, cy, size * .32f, paint)
                path.moveTo(cx - size * .17f, cy + size * .29f)
                path.cubicTo(
                    cx - size * .18f, cy + size * .10f,
                    cx - size * .28f, cy + size * .10f,
                    cx - size * .28f, cy - size * .03f,
                )
                path.cubicTo(
                    cx - size * .28f, cy - size * .15f,
                    cx - size * .19f, cy - size * .22f,
                    cx, cy - size * .22f,
                )
                path.cubicTo(
                    cx + size * .19f, cy - size * .22f,
                    cx + size * .28f, cy - size * .15f,
                    cx + size * .28f, cy - size * .03f,
                )
                path.cubicTo(
                    cx + size * .28f, cy + size * .10f,
                    cx + size * .18f, cy + size * .10f,
                    cx + size * .17f, cy + size * .29f,
                )
                canvas.drawPath(path, paint)
            }

            LICENSE -> {
                val rect = RectF(
                    cx - size * .25f, cy - size * .33f,
                    cx + size * .25f, cy + size * .33f,
                )
                canvas.drawRoundRect(rect, size * .04f, size * .04f, paint)
                canvas.drawLine(
                    cx - size * .14f, cy - size * .12f,
                    cx + size * .14f, cy - size * .12f, paint,
                )
                canvas.drawLine(
                    cx - size * .14f, cy,
                    cx + size * .14f, cy, paint,
                )
                canvas.drawLine(
                    cx - size * .14f, cy + size * .12f,
                    cx + size * .05f, cy + size * .12f, paint,
                )
            }

            else -> {
                val tray = RectF(
                    cx - size * .28f, cy + size * .12f,
                    cx + size * .28f, cy + size * .31f,
                )
                canvas.drawRoundRect(tray, size * .04f, size * .04f, paint)
                val direction = if (type == EXPORT) -1f else 1f
                canvas.drawLine(
                    cx, cy + direction * size * .18f,
                    cx, cy - direction * size * .25f, paint,
                )
                path.moveTo(cx - size * .13f, cy - direction * size * .10f)
                path.lineTo(cx, cy - direction * size * .25f)
                path.lineTo(cx + size * .13f, cy - direction * size * .10f)
                canvas.drawPath(path, paint)
            }
        }
    }

    companion object {
        const val HOME = 1
        const val SETTINGS = 2
        // 3 was the removed sponsor glyph.
        const val REPOSITORY = 4
        const val LICENSE = 5
        const val EXPORT = 6
        const val IMPORT = 7
    }
}
