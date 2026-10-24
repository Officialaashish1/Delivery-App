package `in`.localrush.core.maps

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import kotlin.math.roundToInt

/** Bitmap helpers for marker icons (MapLibre style images need bitmaps). */
object MapIcons {
    /** Rasterise a (vector) drawable resource; [tint] optional ARGB. */
    fun fromDrawable(context: Context, @DrawableRes res: Int, sizeDp: Float = 40f, tint: Int? = null): Bitmap {
        val drawable = AppCompatResources.getDrawable(context, res)!!.mutate()
        if (tint != null) DrawableCompat.setTint(drawable, tint)
        val px = dp(context, sizeDp)
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, px, px)
        drawable.draw(Canvas(bmp))
        return bmp
    }

    /** Render any Android View (e.g. an inflated price badge / rider avatar layout) to a bitmap. */
    fun fromView(view: View): Bitmap {
        view.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        val bmp = Bitmap.createBitmap(view.measuredWidth.coerceAtLeast(1), view.measuredHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        return bmp
    }

    /** Circular dot with white ring: generic "you are here"/destination marker. */
    fun dot(context: Context, color: Int, sizeDp: Float = 18f, ringDp: Float = 3f): Bitmap {
        val px = dp(context, sizeDp)
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = px / 2f
        paint.color = Color.WHITE; c.drawCircle(r, r, r, paint)
        paint.color = color; c.drawCircle(r, r, r - dp(context, ringDp), paint)
        return bmp
    }

    /** Pin: a drawable res drawn inside a circular badge with a small tail; good for restaurant/home. */
    fun badge(context: Context, @DrawableRes icon: Int, bg: Int, fg: Int = Color.WHITE, sizeDp: Float = 44f): Bitmap {
        val px = dp(context, sizeDp)
        val tail = px / 5
        val bmp = Bitmap.createBitmap(px, px + tail, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg }
        val r = px / 2f
        c.drawCircle(r, r, r, paint)
        val path = android.graphics.Path().apply {
            moveTo(r - tail * 0.8f, px - tail * 0.6f); lineTo(r + tail * 0.8f, px - tail * 0.6f); lineTo(r, (px + tail).toFloat()); close()
        }
        c.drawPath(path, paint)
        val drawable = AppCompatResources.getDrawable(context, icon)!!.mutate()
        DrawableCompat.setTint(drawable, fg)
        val inset = (px * 0.24f).roundToInt()
        drawable.setBounds(inset, inset, px - inset, px - inset)
        drawable.draw(c)
        return bmp
    }

    private fun dp(context: Context, v: Float) = (v * context.resources.displayMetrics.density).roundToInt()
}
