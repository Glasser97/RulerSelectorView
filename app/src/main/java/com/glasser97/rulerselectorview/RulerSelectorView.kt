package com.glasser97.rulerselectorview

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.TextPaint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.Scroller
import androidx.core.graphics.ColorUtils
import androidx.core.view.NestedScrollingChild2
import androidx.core.view.NestedScrollingChildHelper
import androidx.core.view.ViewCompat
import kotlin.math.abs
import kotlin.math.cos

class RulerSelectorView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), NestedScrollingChild2 {

    companion object {
        private const val TAG = "RulerSelectorView"

        /**
         * 滑动中，震动的时长
         */
        private const val VIBRATOR_DURATION = 25L
    }

    /**
     * 渐变的起始色List
     */
    @SuppressLint("UseCompatLoadingForColorStateLists")
    var startColorList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        context.resources.getColorStateList(R.color.gray_dark, null)
    } else {
        context.resources.getColorStateList(R.color.gray_dark)
    }

    /**
     * 渐变的中间选中色List
     */
    @SuppressLint("UseCompatLoadingForColorStateLists")
    var middleColorList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        context.resources.getColorStateList(R.color.deepgray_light, null)
    } else {
        context.resources.getColorStateList(R.color.deepgray_light)
    }

    /**
     * 渐变的起始色
     */
    var startColor = startColorList.defaultColor

    /**
     * 渐变的中间选中色
     */
    var middleColor = middleColorList.defaultColor

    /**
     * 控件绘制画笔
     */
    private var paint: Paint

    /**
     * 文字绘制画笔
     */
    private var textPaint: TextPaint

    /**
     * 线条宽度
     * 宽度单位dp
     */
    var lineWidth = dp2px(2f)

    /**
     * 线条高度
     * 高度单位dp
     */
    var lineHeight = dp2px(46f)

    /**
     * 线条和下面文字之间的空隙
     */
    var lineInter = dp2px(6f)

    /**
     * 文字和下边界之间的空隙
     */
    var textBottomInter = dp2px(1f)


    /**
     * 刻度尺展示的数据集
     */
    private val valueArray = ArrayList<Float>()

    /**
     * 刻度之间的间距
     */
    var lineSpace = dp2px(56f)

    /**
     * 辅助手指滑动，用于惯性计算
     */
    private var scroller: Scroller

    /**
     * 嵌套滑动帮助类
     */
    private var nestChildHelper = NestedScrollingChildHelper(this).also {
        it.isNestedScrollingEnabled = true
    }

    /**
     * 跟踪用户手指滑动速度
     */
    var velocityTracker: VelocityTracker? = null

    /**
     * 定义惯性作用的最小速度
     */
    var minVelocityX = 0

    /**
     * 刻度的文字大小
     */
    var textSize = dp2px(14f)

    /**
     * 滑动的最小距离
     */
    private var touchSlop = ViewConfiguration.get(getContext()).scaledTouchSlop

    /**
     * 默认的控件宽度
     */
    private val defaultWidth =
        context.resources.displayMetrics.widthPixels - paddingLeft - paddingRight

    /**
     * 刻度的字体大小
     */
    private var textHeight = 0f

    /**
     * 第一刻度位置距离当前位置的偏移量，一定小于0
     */
    private var offsetStart = 0f

    /**
     * 磁吸标志位，每次滑动磁吸只需要一次，这里用来标志一下
     * 在发起磁吸标志后就会置为true
     * 开始滑动的时候，就会置为false
     */
    private var isMagnetEffecting = false

    /**
     * 用户手指按下控件滑动时的初始位置坐标
     */
    private var downX = 0f

    private var downY = 0f

    /**
     * 当前手指移动的距离
     */
    private var movedX = 0f

    /**
     * 手指是不是还在屏幕上
     */
    private var isDragging = false

    /**
     * 是否需要将滑动事件Dispatch到Parent的标记位
     */
    private var isNeedDispatchTouch = false

    /**
     * 绘制的范围
     */
    private var rectF = RectF()

    private var vibrator: Vibrator? = null

    private var isFirstSet = true

    var lastSelected: Float = -1f
        set(value) {
            if (value != field) {
                if (!isFirstSet) {
                    vibrate()
                }
                isFirstSet = false
                listener?.onNumSelect(getSelectedNum())
            }
            field = value
        }

    /**
     * 选中数字的监听器
     */
    private var listener: OnNumberSelectListener? = null

    fun setOnNumberSelectListener(listener: OnNumberSelectListener) {
        this.listener = listener
    }

    init {
        paint = Paint()
        paint.style = Paint.Style.FILL
        paint.isAntiAlias = true

        textPaint = TextPaint()
        textPaint.style = Paint.Style.FILL
        textPaint.textSize = textSize
        textPaint.isAntiAlias = true
        val fontMetrics = textPaint.fontMetrics
        textHeight = fontMetrics.descent - fontMetrics.ascent

        valueArray.add(0.5f)
        valueArray.add(0.6f)
        valueArray.add(0.7f)
        valueArray.add(0.8f)
        valueArray.add(0.9f)
        valueArray.add(1.0f)
        valueArray.add(1.1f)
        valueArray.add(1.2f)
        valueArray.add(1.3f)
        valueArray.add(1.4f)
        valueArray.add(1.5f)
        valueArray.add(1.6f)
        valueArray.add(1.7f)
        valueArray.add(1.8f)
        valueArray.add(1.9f)
        valueArray.add(2.0f)

        scroller = Scroller(context)
        scroller.setFriction(0.03f)
        velocityTracker = VelocityTracker.obtain()
        minVelocityX = ViewConfiguration.get(getContext()).scaledMinimumFlingVelocity

        vibrator = context.getSystemService(Service.VIBRATOR_SERVICE) as Vibrator
    }

    fun initPosition(value: Float) {
        val position = valueArray.indexOf(value)

        offsetStart = -position * lineSpace

    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val width: Int
        val height: Int

        width = if (widthMode == MeasureSpec.EXACTLY) {
            widthSize
        } else {
            defaultWidth
        }

        height = if (heightMode == MeasureSpec.EXACTLY) {
            heightSize
        } else {
            paddingTop + lineHeight.toInt() + textHeight.toInt() + paddingBottom + lineInter.toInt() + textBottomInter.toInt()
        }

        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        updatePaintColor()

        // 绘制第i个刻度，i从0开始
        for (i in valueArray.indices) {
            val lineLeft = offsetStart + movedX + (width / 2) + (lineWidth / 2) + (i * lineSpace)
            val lineRight = lineLeft + lineWidth
            rectF.set(lineLeft, paddingTop.toFloat(), lineRight, paddingTop + lineHeight)
            val middlePosition = (lineLeft + lineRight) / 2
            val middle = width / 2
            val tempColor = ColorUtils.blendARGB(
                middleColor,
                startColor,
                cos((middle - (abs(middlePosition - middle))) / middle * (3.14f / 2)).toFloat()
            )
            paint.color = tempColor
            canvas?.drawRoundRect(rectF, lineWidth / 2, lineWidth / 2, paint)

            // 绘制刻度文字
            textPaint.color = tempColor

            canvas?.drawText(
                valueArray[i].toString(),
                lineLeft + lineWidth / 2 - textPaint.measureText(valueArray[i].toString()) / 2,
                lineHeight + lineInter + paddingTop + textHeight, textPaint
            )

        }
    }

//    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
//        super.onSizeChanged(w, h, oldw, oldh)
//        //最长刻度线的长度默认为控件总高度的2/3
//        lineHeight = h.toFloat().coerceAtMost(lineHeight)
//    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        velocityTracker?.addMovement(event)

        when (event?.action) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                isNeedDispatchTouch = false
                // 默认子View拦截滑动事件
                this.parent?.requestDisallowInterceptTouchEvent(true)
                if (!scroller.isFinished) {
                    scroller.abortAnimation()
                }

                startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH)
                // 此时要开始计算movedX的值，存一下到offset里
                downX = event.x
                downY = event.y
                offsetStart += movedX
                movedX = 0f


            }
            MotionEvent.ACTION_MOVE -> {
                movedX = event.x - downX
                val movedY = event.y - downY

                if (!isNeedDispatchTouch) {
                    isNeedDispatchTouch = isShouldDispatchTouchEvent(movedX, movedY)
                }

                if (isNeedDispatchTouch) {
                    val offset = intArrayOf(0, 0)
                    dispatchNestedScroll(
                        0,
                        0,
                        -movedX.toInt(),
                        -movedY.toInt(),
                        offset,
                        ViewCompat.TYPE_TOUCH
                    )
                    movedX = 0f
                }

                // 边界控制
                if (offsetStart + movedX > 0) {
                    movedX = 0f
                    offsetStart = 0f
                } else if (offsetStart + movedX < -1 * (valueArray.size - 1) * lineSpace) {
                    offsetStart = -1 * (valueArray.size - 1) * lineSpace
                    movedX = 0f
                }

                lastSelected = getSelectedNum()

                postInvalidate()
            }
            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_UP -> {

                // 进入脱手滑动状态，此时也要开始计算movedX的值，存一下到offset里
                offsetStart += movedX
                movedX = 0f
                isDragging = false

                if (!isNeedDispatchTouch) {
                    // 计算手指松开时候的滑动速率
                    velocityTracker?.computeCurrentVelocity(500)
                    val velocityX = velocityTracker?.xVelocity
                    if (abs(velocityX ?: 0f) > minVelocityX) {
                        scroller.fling(
                            0,
                            0,
                            (velocityX ?: 0f).toInt(),
                            0,
                            Int.MIN_VALUE,
                            Int.MAX_VALUE,
                            0,
                            0
                        )
                        // 手指抬起的时候，表示一次滑动，需要磁吸结尾，[isMagnetEffecting]标志位置为false
                        isMagnetEffecting = false
                    } else {
                        magnetEffect(shouldIntercept = false)
                    }
                }

                isNeedDispatchTouch = false
                stopNestedScroll(ViewCompat.TYPE_TOUCH)
                postInvalidate()
            }
        }
        return true
    }

    override fun computeScroll() {
        super.computeScroll()
        if (!scroller.computeScrollOffset()) {
            if (offsetStart + movedX >= 0) {
                offsetStart = 0f
                movedX = 0f
            }

            magnetEffect()
            return
        }

        //继续惯性滑动
        movedX = scroller.currX - scroller.startX.toFloat()

        //滑动结束:边界控制
        if (offsetStart + movedX > 0) {
            movedX = 0f
            offsetStart = 0f
            scroller.forceFinished(true)
        } else if (offsetStart + movedX < -1 * (valueArray.size - 1) * lineSpace) {
            offsetStart = -1 * (valueArray.size - 1) * lineSpace
            movedX = 0f
            scroller.forceFinished(true)
        }

        lastSelected = getSelectedNum()

        postInvalidate()
    }

    // region NestedScrollingChild2

    override fun hasNestedScrollingParent(type: Int): Boolean {
        return nestChildHelper.hasNestedScrollingParent(type)
    }

    override fun startNestedScroll(axes: Int, type: Int): Boolean {
        return nestChildHelper.startNestedScroll(axes, type)
    }

    override fun dispatchNestedPreScroll(
        dx: Int,
        dy: Int,
        consumed: IntArray?,
        offsetInWindow: IntArray?,
        type: Int
    ): Boolean {
        return nestChildHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, type)
    }

    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?,
        type: Int
    ): Boolean {
        return nestChildHelper.dispatchNestedScroll(
            dxConsumed,
            dyConsumed,
            dxUnconsumed,
            dyUnconsumed,
            offsetInWindow,
            type
        )
    }

    override fun stopNestedScroll(type: Int) {
        nestChildHelper.stopNestedScroll(type)
    }

    // endregion

    /**
     * 判断当前这个事件是否需要自己完全消化，不给到外部使用
     * 默认是拦截滑动的。
     *
     * @return 为true表示需要，为false表示不需要
     */
    private fun isShouldDispatchTouchEvent(movedX: Float, movedY: Float): Boolean {
        val isInTouchSlop = movedX * movedX + movedY * movedY < touchSlop * touchSlop * 2
        val isVerticalTouch = abs(movedX * 4) < abs(movedY)
        return isInTouchSlop && isVerticalTouch
    }

    /**
     * 在需要的时机进行磁吸效果
     */
    private fun magnetEffect(shouldIntercept: Boolean = true) {

        if (shouldIntercept && isMagnetEffecting) {
            return
        }

        if (isDragging) {
            return
        }

        if (offsetStart + movedX <= 0 && offsetStart + movedX >= -1 * (valueArray.size - 1) * lineSpace) {
            //手指松开时需要磁吸效果
            offsetStart += movedX
            movedX = 0f

            // 计算吸附距离
            val magnetOffset =
                ((offsetStart / lineSpace - 0.5f).toInt() - offsetStart / lineSpace) * lineSpace

            val duration = abs(magnetOffset) / lineSpace * 1000
            scroller.startScroll(0, 0, magnetOffset.toInt(), 0, duration.toInt())
            isMagnetEffecting = true
            postInvalidate()

        } else if (offsetStart + movedX > 0) {
            movedX = 0f
            offsetStart = 0f
        } else {
            offsetStart = -1 * (valueArray.size - 1) * lineSpace
            movedX = 0f
        }
    }

    @SuppressLint("MissingPermission")
    private fun vibrate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(
                VibrationEffect.createOneShot(
                    VIBRATOR_DURATION,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
        } else {
            vibrator?.vibrate(VIBRATOR_DURATION)
        }
    }

    private fun setValueArray(valueList: List<Float>) {
        valueArray.clear()
        valueArray.addAll(valueList)
    }


    private fun updatePaintColor() {
        startColor = startColorList.defaultColor
        middleColor = middleColorList.defaultColor
    }

    private fun getSelectedNum(): Float {
        val i = (abs((offsetStart + movedX) / lineSpace - 0.5).toInt())
        return if (i >= 0 && i < valueArray.size) {
            valueArray[i]
        } else if (i < 0) {
            valueArray[0]
        } else {
            valueArray[valueArray.size - 1]
        }
    }

    private fun dp2px(dp: Float): Float {
        return context.resources.displayMetrics.density * dp
    }


}

interface OnNumberSelectListener {
    fun onNumSelect(selectedNum: Float)
}