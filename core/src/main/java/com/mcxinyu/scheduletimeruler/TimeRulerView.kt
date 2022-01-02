package com.mcxinyu.scheduletimeruler

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.Scroller
import androidx.annotation.ColorInt
import androidx.annotation.StringDef
import androidx.core.view.GestureDetectorCompat
import java.util.*
import kotlin.math.ceil
import kotlin.properties.Delegates

/**
 * @author [yuefeng](mailto:mcxinyu@foxmail.com) in 2021/12/24.
 */
open class TimeRulerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs), GestureDetector.OnGestureListener,
    ScaleGestureDetector.OnScaleGestureListener {

    @ColorInt
    protected var tickTextColor: Int
    protected var tickTextSize: Float
    protected var showTickText: Boolean

    protected var showTick: Boolean
    protected var minTickSpace: Float

    @ColorInt
    protected var normalTickColor: Int
    protected var normalTickWidth: Float
    protected var normalTickHeight: Float

    @ColorInt
    protected var keyTickColor: Int
    protected var keyTickWidth: Float
    protected var keyTickHeight: Float

    @ColorInt
    protected var cursorLineColor: Int
    protected var cursorLineWidth: Float
    protected var cursorLinePositionPercentage: Float
    protected var showCursorLine: Boolean

    @ColorInt
    protected var baselineOutDayColor: Int

    @ColorInt
    protected var baselineColor: Int
    protected var baselineWidth: Float
    protected var baselinePosition: Float
    protected var showBaseline: Boolean

    protected lateinit var paint: Paint
    protected lateinit var timeModel: TimeModel

    /**
     * 游标所在位置时间
     */
    protected var cursorTimeValue = 0L
    protected var cursorLinePosition by Delegates.notNull<Float>()

    /**
     * 每格最小占有像素，由[minTickSpace]处以一小时毫秒数得到
     */
    protected var minUnitPixel: Float

    /**
     * 每格最大占有像素
     */
    protected var maxUnitPixel: Float

    /**
     * 每毫秒 单位时间占用像素 区间 [[minUnitPixel], [maxUnitPixel]]
     */
    protected var unitPixel by Delegates.notNull<Float>()

    protected var tickSpacePixel by Delegates.notNull<Float>()

    protected var scaleRatio = 1.0f

    private var scrollHappened: Boolean = false
    private var gestureDetectorCompat = GestureDetectorCompat(context, this)
    private var scaleGestureDetector = ScaleGestureDetector(context, this)
    private var scroller = Scroller(context)

    init {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.TimeRulerView)
        showBaseline =
            typedArray.getBoolean(R.styleable.TimeRulerView_trv_showBaseline, true)
        baselinePosition =
            typedArray.getFloat(R.styleable.TimeRulerView_trv_baselinePosition, 0.3f)
        baselineColor =
            typedArray.getColor(R.styleable.TimeRulerView_trv_baselineColor, Color.LTGRAY)
        baselineOutDayColor =
            typedArray.getColor(
                R.styleable.TimeRulerView_trv_baselineOutDayColor,
                Color.parseColor("#FFFAFAFA")
            )
        baselineWidth = typedArray.getDimension(
            R.styleable.TimeRulerView_trv_baselineWidth,
            1.toPx(context)
        )

        showCursorLine =
            typedArray.getBoolean(R.styleable.TimeRulerView_trv_showCursorLine, true)
        cursorLinePositionPercentage =
            typedArray.getFloat(R.styleable.TimeRulerView_trv_cursorLinePosition, 0.3f)
        cursorLineColor =
            typedArray.getColor(
                R.styleable.TimeRulerView_trv_cursorLineColor,
                Color.BLUE
            )
        cursorLineWidth = typedArray.getDimension(
            R.styleable.TimeRulerView_trv_cursorLineWidth,
            1.toPx(context)
        )

        keyTickHeight =
            typedArray.getDimension(
                R.styleable.TimeRulerView_trv_keyTickHeight,
                16.toPx(context)
            )
        keyTickWidth =
            typedArray.getDimension(
                R.styleable.TimeRulerView_trv_keyTickWidth,
                1.toPx(context)
            )
        keyTickColor =
            typedArray.getColor(
                R.styleable.TimeRulerView_trv_keyTickColor,
                Color.GRAY
            )

        normalTickHeight =
            typedArray.getDimension(
                R.styleable.TimeRulerView_trv_normalTickHeight,
                8.toPx(context)
            )
        normalTickWidth =
            typedArray.getDimension(
                R.styleable.TimeRulerView_trv_normalTickWidth,
                1.toPx(context)
            )
        normalTickColor =
            typedArray.getColor(
                R.styleable.TimeRulerView_trv_normalTickColor,
                Color.LTGRAY
            )

        showTick =
            typedArray.getBoolean(R.styleable.TimeRulerView_trv_showTick, true)
        minTickSpace =
            typedArray.getDimension(
                R.styleable.TimeRulerView_trv_minTickSpace,
                80.toPx(context)
            )
        minUnitPixel = minTickSpace / (60 * 60 * 1000)
        maxUnitPixel = minUnitPixel * 60

        showTickText =
            typedArray.getBoolean(R.styleable.TimeRulerView_trv_showTickText, true)
        tickTextSize =
            typedArray.getDimension(
                R.styleable.TimeRulerView_trv_tickTextSize,
                14.toPxForSp(context)
            )
        tickTextColor =
            typedArray.getColor(
                R.styleable.TimeRulerView_trv_tickTextColor,
                Color.DKGRAY
            )

        typedArray.recycle()

        initThing()
    }

    private fun initThing() {
        paint = Paint()
        paint.isAntiAlias = true
        paint.isDither = true
        paint.style = Paint.Style.FILL_AND_STROKE

        timeModel = TimeModel()
        cursorTimeValue = timeModel.startTimeValue
    }

    override fun onSizeChanged(width: Int, height: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(width, height, oldw, oldh)

        cursorLinePosition = height * cursorLinePositionPercentage

        unitPixel = maxUnitPixel * scaleRatio
        tickSpacePixel = timeModel.unitTimeValue * unitPixel
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
//        setMeasuredDimension(
//            getDefaultSize(suggestedMinimumWidth, widthMeasureSpec), getHeightSize(
//                suggestedMinimumHeight, heightMeasureSpec
//            )
//        )
    }

    private fun getHeightSize(size: Int, heightMeasureSpec: Int): Int {
        var result = size
        val contentHeight: Int = calculateContentWidth(baselinePosition)
        val specMode = MeasureSpec.getMode(heightMeasureSpec)
        val specSize = MeasureSpec.getSize(heightMeasureSpec)
        when (specMode) {
            MeasureSpec.UNSPECIFIED -> result = if (size > contentHeight) size else contentHeight
            MeasureSpec.AT_MOST -> result = contentHeight
            MeasureSpec.EXACTLY -> result =
                if (specSize > contentHeight) specSize else contentHeight
        }
        return result
    }

    protected fun calculateContentWidth(baselinePositionProportion: Float): Int {
        var tickValueHeight = 0
        if (showTickText) {
            //让字体不要溢出，这里应该动态换算基线左边还有位置显示文本么？
            paint.setTextSize(20.toPx(context))
            val fontMetrics: Paint.FontMetrics = paint.getFontMetrics()
            val ceil = Math.ceil((fontMetrics.bottom - fontMetrics.top).toDouble())
            tickValueHeight = ceil.toInt()
        }
        return ((keyTickHeight + tickValueHeight) / baselinePositionProportion + 0.5f).toInt()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        onDrawBaseline(canvas)

        val fitstTickLinePosition =
            cursorLinePosition - (cursorTimeValue - timeModel.startTimeValue) * unitPixel
        if (fitstTickLinePosition > 0) {
            onDrawFrontDay(canvas, 0f, 0f, width.toFloat(), fitstTickLinePosition)
        }

        onDrawTick(canvas)

        val lastTickLinePosition =
            height - cursorLinePosition - (timeModel.endTimeValue - cursorTimeValue) * unitPixel
        if (lastTickLinePosition > 0) {
            onDrawBackDay(canvas, 0f, lastTickLinePosition, width.toFloat(), height.toFloat())
        }

        onDrawCursor(canvas)
    }

    private fun onDrawTick(canvas: Canvas) {
        //在游标线以上包含的今日时间
        val frontTodayTimeRange = cursorTimeValue - timeModel.startTimeValue
        //可见的第一个刻度的时间在屏幕外的偏移量
        val frontFirstTimeOffset = frontTodayTimeRange % timeModel.unitTimeValue
        //向前走，距离游标线最近的刻度代表的时间
        val frontLastTimeOffsetValue = cursorTimeValue - frontFirstTimeOffset
        //向前走，距离游标线最近的刻度所在位置
        val frontLastTimePosition = cursorLinePosition - frontFirstTimeOffset * unitPixel
        val frontCount = cursorLinePosition / tickSpacePixel

        //从游标线往前画
        for (i in 0 until ceil(frontCount.toDouble()).toInt()) {
            val timeValue = frontLastTimeOffsetValue - timeModel.unitTimeValue * i
            if (timeValue < timeModel.startTimeValue) {
                break
            }

            val x = width * baselinePosition - normalTickHeight
            val y = frontLastTimePosition - tickSpacePixel * i

            onDrawTickLine(canvas, x, y)
            onDrawTickText(canvas, x, y, timeValue)
        }

        val backFirstTimeValue = frontLastTimeOffsetValue + timeModel.unitTimeValue
        val backFirstTimePosition = frontLastTimePosition + tickSpacePixel
        val backCount = (height - cursorLinePosition) / (tickSpacePixel + normalTickWidth)

        //从游标线往后画
        for (i in 0 until ceil(backCount.toDouble()).toInt()) {
            val timeValue = backFirstTimeValue + timeModel.unitTimeValue * i

            val x = width * baselinePosition - normalTickHeight
            val y = backFirstTimePosition + tickSpacePixel * i

            onDrawTickLine(canvas, x, y)
            onDrawTickText(canvas, x, y, timeValue)

            if (timeValue > timeModel.endTimeValue) {
                break
            }
        }
    }

    protected fun onDrawBackDay(canvas: Canvas, x0: Float, y0: Float, x1: Float, y1: Float) {
        val rectText = Rect()
        paint.getTextBounds("23:59", 0, "23:59".length, rectText)

        //region plan1
//        paint.color = -0x1000000
//        val rect = Rect(x0.toInt(), y0.toInt(), x1.toInt(), y1.toInt() + rectText.height() / 2)
//        canvas.drawRect(rect, paint)
        //endregion

        //region plan2
        paint.color = baselineOutDayColor
        val rect2 = Rect(
            (width * baselinePosition).toInt() - 1,
            (y0 + keyTickWidth).toInt(),
            (width * baselinePosition + baselineWidth).toInt() + 1,
            y1.toInt()
        )
        canvas.drawRect(rect2, paint)
        //endregion
    }

    protected fun onDrawFrontDay(canvas: Canvas, x0: Float, y0: Float, x1: Float, y1: Float) {
        val rectText = Rect()
        paint.getTextBounds("00:00", 0, "00:00".length, rectText)

        //region plan1
//        paint.color = -0x1000000
//        val rect = Rect(x0.toInt(), y0.toInt(), x1.toInt(), y1.toInt() - rectText.height() / 2)
//        canvas.drawRect(rect, paint)
        //endregion

        //region plan2
        paint.color = baselineOutDayColor
        val rect2 = Rect(
            (width * baselinePosition).toInt() - 1,
            0,
            (width * baselinePosition + baselineWidth).toInt() + 1,
            y1.toInt()
        )
        canvas.drawRect(rect2, paint)
        //endregion
    }

    protected fun onDrawTickText(canvas: Canvas, x: Float, y: Float, timeValue: Long) {
        if (showTickText) {
            paint.color = tickTextColor
            paint.textAlign = Paint.Align.LEFT
            paint.textSize = tickTextSize

            val text = simpleDateFormat.format(timeValue)

            val rect = Rect()
            paint.getTextBounds(text, 0, text.length, rect)
            val w = rect.width()
            val h = rect.height()

            canvas.drawText(text, x - w - h, y + h / 2, paint)
        }
    }

    protected fun onDrawTickLine(canvas: Canvas, x: Float, y: Float) {
        if (showTick) {
            paint.color = normalTickColor
            paint.strokeWidth = normalTickWidth
            canvas.drawLine(x, y, width * baselinePosition, y, paint)
            paint.strokeWidth = 1f
        }
    }

    protected fun onDrawCursor(canvas: Canvas) {
        if (showCursorLine) {
            paint.color = cursorLineColor
            paint.strokeWidth = cursorLineWidth
            canvas.drawLine(0f, cursorLinePosition, width.toFloat(), cursorLinePosition, paint)
            paint.strokeWidth = 1f

            val text = simpleDateFormat2.format(cursorTimeValue)

            val rect = Rect()
            paint.getTextBounds(text, 0, text.length, rect)

//            val x = width * baselinePosition - normalTickHeight

            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(text, width / 2f, cursorLinePosition - rect.height(), paint)
        }
    }

    protected fun onDrawBaseline(canvas: Canvas) {
        if (showBaseline) {
            paint.color = baselineColor

            val rect = Rect(
                (width * baselinePosition).toInt(),
                0,
                (width * baselinePosition + baselineWidth).toInt(),
                height
            )
            canvas.drawRect(rect, paint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)
        gestureDetectorCompat.onTouchEvent(event)
        return true
    }

    private var status: Int = STATUS_NONE

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        status = STATUS_ZOOM
        var scaleFactor = detector.scaleFactor
        unitPixel *= scaleFactor
        if (unitPixel > maxUnitPixel) {
            unitPixel = maxUnitPixel
            scaleFactor = 1f
        } else if (unitPixel < minUnitPixel) {
            unitPixel = minUnitPixel
            scaleFactor = 1f
        }

        onScale(timeModel, unitPixel)

        scaleRatio *= scaleFactor

        tickSpacePixel = timeModel.unitTimeValue * unitPixel

        Log.d(
            TAG,
            "maxUnitPixel $maxUnitPixel minUnitPixel $minUnitPixel unitPixel $unitPixel scaleRatio $scaleRatio tickSpacePixel $tickSpacePixel"
        )

        invalidate()

        return unitPixel < maxUnitPixel || unitPixel > minUnitPixel
    }

    protected fun onScale(timeModel: TimeModel, unitPixel: Float) {

    }

    override fun onScaleBegin(detector: ScaleGestureDetector) = true

    override fun onScaleEnd(detector: ScaleGestureDetector) {
    }

    override fun onDown(e: MotionEvent): Boolean {
        if (status == STATUS_SCROLL_FLING) {
            scroller.forceFinished(true)
        } else {
            scrollHappened = false
        }
        status = STATUS_DOWN
        return true
    }

    override fun onShowPress(e: MotionEvent) {
    }

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        return performClick()
    }

    override fun onScroll(e1: MotionEvent, e2: MotionEvent, distanceX: Float, distanceY: Float):
            Boolean {
        if (e2.pointerCount > 1) {
            return false
        }
        if (scaleGestureDetector.isInProgress) {
            return false
        }
        if (!scrollHappened) {
            scrollHappened = true
            return true
        }

        status = STATUS_SCROLL

        val increment = distanceY / unitPixel
        cursorTimeValue += increment.toLong()

        var result = true
        if (cursorTimeValue > timeModel.endTimeValue) {
            cursorTimeValue = timeModel.endTimeValue
            result = false
        } else if (cursorTimeValue < timeModel.startTimeValue) {
            cursorTimeValue = timeModel.startTimeValue
            result = false
        }

        invalidate()

        return result
    }

    override fun onLongPress(e: MotionEvent) {
    }

    override fun onFling(e1: MotionEvent, e2: MotionEvent, velocityX: Float, velocityY: Float):
            Boolean {
        status = STATUS_SCROLL_FLING

        val startY = ((cursorTimeValue - timeModel.startTimeValue) * unitPixel).toInt()
        val maxY = ((timeModel.endTimeValue - timeModel.startTimeValue) * unitPixel).toInt()

        scroller.fling(0, startY, 0, (-velocityY).toInt(), 0, 0, 0, maxY)

        invalidate()

        return true
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            val currY = scroller.currY
            cursorTimeValue = timeModel.startTimeValue + (currY / unitPixel).toLong()
            if (cursorTimeValue > timeModel.endTimeValue) {
                cursorTimeValue = timeModel.endTimeValue
            } else if (cursorTimeValue < timeModel.startTimeValue) {
                cursorTimeValue = timeModel.startTimeValue
            }
            invalidate()
        } else {
            if (status == STATUS_SCROLL_FLING) {
                status = STATUS_DOWN
            }
        }
    }

    companion object {
        val TAG = TimeRulerView::class.java.simpleName
        const val STATUS_NONE = 0
        const val STATUS_DOWN = STATUS_NONE + 1
        const val STATUS_SCROLL = STATUS_DOWN + 1
        const val STATUS_SCROLL_FLING = STATUS_SCROLL + 1
        const val STATUS_ZOOM = STATUS_SCROLL_FLING + 1

        const val MODE_UINT_D5_MIN = "unit d5 minute"
        const val MODE_UINT_1_MIN = "unit 1 minute"
        const val MODE_UINT_5_MIN = "unit 5 minute"
        const val MODE_UINT_10_MIN = "unit 10 minute"
        const val MODE_UINT_30_MIN = "unit 30 minute"
        const val MODE_UINT_1_HOUR = "unit 1 hour"
    }

    @StringDef(
        MODE_UINT_D5_MIN,
        MODE_UINT_1_MIN,
        MODE_UINT_5_MIN,
        MODE_UINT_10_MIN,
        MODE_UINT_30_MIN,
        MODE_UINT_1_HOUR
    )
    annotation class Mode
}

