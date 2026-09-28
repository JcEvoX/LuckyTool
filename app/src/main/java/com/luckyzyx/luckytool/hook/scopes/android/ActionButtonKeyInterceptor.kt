package com.luckyzyx.luckytool.hook.scopes.android

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import org.lsposed.lsparanoid.Obfuscate

/**
 * 快捷键（Action Button Key）拦截
 *
 * 接管条件：系统快捷键功能设置为"无操作"（oplus_action_button_switch_state == "nothing"）时，
 * 模块拦截 780 物理按键事件，自行判定并记录 单击 / 双击 / 长按 / 超长按 四种操作
 *
 * 原生判定：按下 <495ms = 单击（注入 781），>=495ms = 长按（注入 782），无双击与超长按逻辑
 */
@Obfuscate
object ActionButtonKeyInterceptor : YukiBaseHooker() {

    private const val TAG = "LuckyTool_ActionButtonKey"

    private const val KEYCODE_ACTION_BUTTON = 780
    private const val ACTION_SWITCH_NOTHING = "nothing"
    private const val LONG_PRESS_TIMEOUT = 495L
    private const val DOUBLE_TAP_TIMEOUT = 400L
    private const val SUPER_LONG_PRESS_TIMEOUT = 3000L

    private val mHandler = Handler(Looper.getMainLooper())

    //以下状态仅在 mHandler 线程访问
    private var mLastDownTime = 0L
    private var mLastTapUpTime = 0L
    private var mSingleTapPending = false
    private var mPendingDoubleTap = false

    private val mSingleTapRunnable = Runnable {
        mSingleTapPending = false
        Log.d(TAG, "快捷键单击 singleTap")
    }

    override fun onHook() {
        //Source StrategyActionButtonKeyLaunchApp
        "com.android.server.policy.StrategyActionButtonKeyLaunchApp".toClass().resolve().apply {
            firstMethod { name = "actionInterceptKeyBeforeQueueing" }.hook {
                before {
                    val context = firstField { type = Context::class; superclass() }
                        .of(instance).get<Context>() ?: return@before
                    val event = arg(0).get<KeyEvent>() ?: return@before
                    val keyCode = arg(2).get<Int>() ?: return@before
                    val down = arg(3).get<Boolean>() ?: return@before
                    if (keyCode != KEYCODE_ACTION_BUTTON || event.repeatCount != 0) return@before

                    //模块接管条件：系统快捷键功能已设置为"无操作"
                    if (!isSwitchNothing(context)) return@before

                    //判定与日志统一在 handler 线程执行，跳过原方法（原生 nothing 分支同样吞掉 780）
                    mHandler.post { handleActionButtonEvent(down, SystemClock.uptimeMillis()) }
                    result = 0
                }
            }
        }
    }

    private fun isSwitchNothing(context: Context): Boolean {
        return try {
            ACTION_SWITCH_NOTHING == Settings.System.getString(
                context.contentResolver,
                "oplus_action_button_switch_state"
            )
        } catch (_: Throwable) {
            false
        }
    }

    private fun handleActionButtonEvent(down: Boolean, now: Long) {
        if (down) {
            //处于双击等待窗口内的第二次按下
            if (mSingleTapPending) {
                mHandler.removeCallbacks(mSingleTapRunnable)
                mSingleTapPending = false
                mPendingDoubleTap = true
            }
            mLastDownTime = now
            return
        }
        val interval = now - mLastDownTime
        when {
            interval < LONG_PRESS_TIMEOUT -> {
                if (mPendingDoubleTap && now - mLastTapUpTime < DOUBLE_TAP_TIMEOUT) {
                    mPendingDoubleTap = false
                    mLastTapUpTime = 0L
                    Log.d(TAG, "快捷键双击 doubleTap")
                } else {
                    mPendingDoubleTap = false
                    mLastTapUpTime = now
                    mSingleTapPending = true
                    mHandler.postDelayed(mSingleTapRunnable, DOUBLE_TAP_TIMEOUT)
                }
            }

            interval < SUPER_LONG_PRESS_TIMEOUT -> {
                resetTapState()
                Log.d(TAG, "快捷键长按 longPress duration=${interval}ms")
            }

            else -> {
                resetTapState()
                Log.d(TAG, "快捷键超长按 superLongPress duration=${interval}ms")
            }
        }
    }

    private fun resetTapState() {
        mHandler.removeCallbacks(mSingleTapRunnable)
        mSingleTapPending = false
        mPendingDoubleTap = false
        mLastTapUpTime = 0L
    }
}
