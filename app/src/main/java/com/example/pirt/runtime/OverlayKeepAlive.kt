package com.example.pirt.runtime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.example.pirt.runtime.pi.OverlayChatSnapshot
import kotlin.math.abs

interface OverlayChatHost {
    fun snapshot(): OverlayChatSnapshot?
    fun send(message: String): Result<Unit>
}

private enum class OverlayTab { CHAT, PROCESSES }

/** TYPE_APPLICATION_OVERLAY keep-alive bubble with quick chat and process list. */
class OverlayKeepAlive(context: Context) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val prefs = appContext.getSharedPreferences("pirt_overlay", Context.MODE_PRIVATE)
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density = appContext.resources.displayMetrics.density
    private val iconSize = (48 * density).toInt()
    private val panelWidth = (240 * density).toInt()
    private val panelHeight = (320 * density).toInt()
    private val refresh = object : Runnable {
        override fun run() {
            if (expanded) {
                renderPanel()
                main.postDelayed(this, 400L)
            }
        }
    }

    private var host: OverlayChatHost? = null
    private var root: FrameLayout? = null
    private var icon: TextView? = null
    private var panel: LinearLayout? = null
    private var tabChatView: TextView? = null
    private var tabProcessView: TextView? = null
    private var chatTabContent: LinearLayout? = null
    private var processTabContent: LinearLayout? = null
    private var titleView: TextView? = null
    private var statusView: TextView? = null
    private var replyView: TextView? = null
    private var inputView: EditText? = null
    private var sendView: TextView? = null
    private var processList: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var selectedTab = OverlayTab.CHAT
    private var expanded = false
    private var attached = false

    fun bind(host: OverlayChatHost) {
        this.host = host
        onMain { if (expanded) renderPanel() }
    }

    fun sync() {
        onMain {
            if (OverlayPermission.isActive(appContext)) attach() else detachNow()
        }
    }

    fun renderIfExpanded() {
        onMain { if (expanded) renderPanel() }
    }

    fun detach() {
        onMain { detachNow() }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    private fun detachNow() {
        main.removeCallbacks(refresh)
        val view = root ?: return
        runCatching { windowManager.removeView(view) }
        root = null
        icon = null
        panel = null
        tabChatView = null
        tabProcessView = null
        chatTabContent = null
        processTabContent = null
        titleView = null
        statusView = null
        replyView = null
        inputView = null
        sendView = null
        processList = null
        params = null
        expanded = false
        attached = false
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attach() {
        if (attached) return
        val bubble = TextView(appContext).apply {
            text = "Pi"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
            background = circle(0xE61B1B1F.toInt())
        }
        val title = TextView(appContext).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            maxLines = 1
        }
        val status = TextView(appContext).apply {
            setTextColor(0xFFB0B0B0.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            maxLines = 1
        }
        val reply = TextView(appContext).apply {
            setTextColor(0xFFE8E8E8.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setLineSpacing(0f, 1.15f)
        }
        val input = EditText(appContext).apply {
            hint = "发消息…"
            setHintTextColor(0xFF808080.toInt())
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setBackgroundColor(0xFF2A2A2E.toInt())
            setPadding(dp(10), dp(8), dp(10), dp(8))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            maxLines = 3
            imeOptions = EditorInfo.IME_ACTION_SEND
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_SEND) {
                    submitMessage()
                    true
                } else false
            }
            setOnTouchListener { view, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN && expanded) {
                    focusInputForKeyboard(view)
                }
                false
            }
            setOnFocusChangeListener { view, hasFocus ->
                if (expanded) {
                    setInputFocused(hasFocus)
                    if (hasFocus) showKeyboard(view)
                }
            }
        }
        val send = TextView(appContext).apply {
            text = "发送"
            setTextColor(0xFF9ECAFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(dp(10), dp(8), 0, dp(8))
            setOnClickListener { submitMessage() }
        }
        val list = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
        }
        val tabChat = tabButton("会话")
        val tabProcess = tabButton("进程")
        tabChat.setOnClickListener { selectTab(OverlayTab.CHAT) }
        tabProcess.setOnClickListener { selectTab(OverlayTab.PROCESSES) }
        val tabRow = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(tabChat, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(tabProcess, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        val inputRow = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(input, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(send)
        }
        val chatContent = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            addView(title)
            addView(status, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            })
            addView(ScrollView(appContext).apply {
                addView(reply)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                topMargin = dp(8)
            })
            addView(inputRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
            })
        }
        val processContent = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            addView(ScrollView(appContext).apply {
                addView(list)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        val body = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(12))
            addView(LinearLayout(appContext).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(View(appContext), LinearLayout.LayoutParams(0, 1, 1f))
                addView(TextView(appContext).apply {
                    text = "收起"
                    setTextColor(0xFF9ECAFF.toInt())
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setPadding(dp(8), dp(4), 0, dp(4))
                    setOnClickListener { collapse() }
                })
            })
            addView(tabRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            })
            addView(chatContent, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                topMargin = dp(8)
            })
            addView(processContent, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                topMargin = dp(8)
            })
        }
        val card = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            background = roundRect(0xF21B1B1F.toInt())
            elevation = 8 * density
            addView(body, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT))
        }
        val container = FrameLayout(appContext).apply {
            addView(bubble, FrameLayout.LayoutParams(iconSize, iconSize, Gravity.TOP or Gravity.END))
            addView(card, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
        val layout = WindowManager.LayoutParams(
            iconSize,
            iconSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            collapsedFlags(),
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("x", appContext.resources.displayMetrics.widthPixels - iconSize - dp(16))
            y = prefs.getInt("y", dp(120))
            alpha = 0.94f
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var dragged = false
        val slop = ViewConfiguration.get(appContext).scaledTouchSlop
        container.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                if (expanded) collapse()
                true
            } else false
        }
        bubble.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = layout.x
                    startY = layout.y
                    dragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downRawX).toInt()
                    val dy = (event.rawY - downRawY).toInt()
                    if (abs(dx) > slop || abs(dy) > slop) dragged = true
                    if (dragged) {
                        layout.x = (startX + dx).coerceAtLeast(0)
                        layout.y = (startY + dy).coerceAtLeast(0)
                        windowManager.updateViewLayout(container, layout)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragged) {
                        prefs.edit().putInt("x", layout.x).putInt("y", layout.y).apply()
                    } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                        expand()
                    }
                    true
                }
                else -> false
            }
        }
        runCatching {
            windowManager.addView(container, layout)
            root = container
            icon = bubble
            panel = card
            tabChatView = tabChat
            tabProcessView = tabProcess
            chatTabContent = chatContent
            processTabContent = processContent
            titleView = title
            statusView = status
            replyView = reply
            inputView = input
            sendView = send
            processList = list
            params = layout
            attached = true
            selectTab(selectedTab)
        }.onFailure { error ->
            RuntimeDiagnostics.error(appContext, "overlay", "无法显示悬浮窗", error)
        }
    }

    private fun tabButton(label: String): TextView = TextView(appContext).apply {
        text = label
        gravity = Gravity.CENTER
        setPadding(0, dp(10), 0, dp(10))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
    }

    private fun selectTab(tab: OverlayTab) {
        selectedTab = tab
        chatTabContent?.visibility = if (tab == OverlayTab.CHAT) View.VISIBLE else View.GONE
        processTabContent?.visibility = if (tab == OverlayTab.PROCESSES) View.VISIBLE else View.GONE
        styleTab(tabChatView, tab == OverlayTab.CHAT)
        styleTab(tabProcessView, tab == OverlayTab.PROCESSES)
        if (tab == OverlayTab.PROCESSES) {
            hideKeyboard()
            inputView?.clearFocus()
            setInputFocused(false)
        }
        renderPanel()
    }

    private fun styleTab(view: TextView?, selected: Boolean) {
        view ?: return
        view.setTextColor(if (selected) 0xFF9ECAFF.toInt() else 0xFFB0B0B0.toInt())
        view.setBackgroundColor(if (selected) 0x332A5CAA else Color.TRANSPARENT)
        view.typeface = if (selected) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
    }

    private fun submitMessage() {
        val input = inputView ?: return
        val text = input.text?.toString()?.trim().orEmpty()
        if (text.isBlank()) return
        val result = host?.send(text) ?: Result.failure(IllegalStateException("Pi 尚未连接"))
        result.onFailure { error ->
            statusView?.text = error.message ?: "发送失败"
        }.onSuccess {
            input.text?.clear()
        }
        renderPanel()
    }

    private fun expand() {
        val view = root ?: return
        val layout = params ?: return
        expanded = true
        icon?.visibility = View.GONE
        panel?.visibility = View.VISIBLE
        layout.width = panelWidth
        layout.height = panelHeight
        layout.flags = expandedFlags(inputFocused = false)
        windowManager.updateViewLayout(view, layout)
        selectTab(selectedTab)
        main.removeCallbacks(refresh)
        main.post(refresh)
    }

    private fun collapse() {
        val view = root ?: return
        val layout = params ?: return
        expanded = false
        main.removeCallbacks(refresh)
        hideKeyboard()
        inputView?.clearFocus()
        setInputFocused(false)
        panel?.visibility = View.GONE
        icon?.visibility = View.VISIBLE
        layout.width = iconSize
        layout.height = iconSize
        layout.flags = collapsedFlags()
        windowManager.updateViewLayout(view, layout)
    }

    private fun renderPanel() {
        when (selectedTab) {
            OverlayTab.CHAT -> renderChat()
            OverlayTab.PROCESSES -> renderProcesses()
        }
    }

    private fun renderChat() {
        val chat = host?.snapshot()
        titleView?.text = chat?.title ?: "当前对话"
        if (chat == null) {
            statusView?.text = "请先在应用内打开一个会话"
            replyView?.text = "暂无 AI 回复"
            sendView?.isEnabled = false
            sendView?.alpha = 0.45f
            inputView?.isEnabled = false
            return
        }
        statusView?.text = chat.status
        replyView?.text = chat.reply?.take(1200) ?: "暂无 AI 回复"
        val enabled = chat.canSend
        sendView?.isEnabled = enabled
        sendView?.alpha = if (enabled) 1f else 0.45f
        inputView?.isEnabled = enabled
    }

    private fun renderProcesses() {
        val list = processList ?: return
        val entries = HostProcesses.tree()
        list.removeAllViews()
        if (entries.isEmpty()) {
            list.addView(processRow("没有读到本应用进程。"))
            return
        }
        entries.forEach { entry ->
            val process = entry.process
            val title = buildString {
                append(hostProcessTreePrefix(entry.depth))
                append(process.label)
                append(" · pid ")
                append(process.pid)
                if (process.independent) append(" · 独立")
            }
            list.addView(processRow(title, process.command.take(72), entry.depth))
        }
    }

    private fun processRow(title: String, detail: String? = null, depth: Int = 0): LinearLayout {
        return LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10 * depth), dp(6), 0, dp(6))
            addView(TextView(appContext).apply {
                text = title
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            })
            if (detail != null) {
                addView(TextView(appContext).apply {
                    text = detail
                    setTextColor(0xFFB0B0B0.toInt())
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                })
            }
        }
    }

    private fun focusInputForKeyboard(view: View) {
        setInputFocused(true)
        view.post {
            view.requestFocus()
            showKeyboard(view)
        }
    }

    private fun showKeyboard(view: View) {
        val imm = appContext.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        val input = inputView ?: return
        val imm = appContext.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(input.windowToken, 0)
    }

    private fun setInputFocused(focused: Boolean) {
        val view = root ?: return
        val layout = params ?: return
        if (!expanded) return
        val focusable = layout.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE == 0
        if (focused == focusable) return
        layout.flags = expandedFlags(inputFocused = focused)
        layout.softInputMode = if (focused) {
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        } else {
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        windowManager.updateViewLayout(view, layout)
    }

    private fun collapsedFlags(): Int =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

    private fun expandedFlags(inputFocused: Boolean = false): Int {
        var flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        if (!inputFocused) {
            flags = flags or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = flags or 0x20000000 // LayoutParams.FLAG_SLIPPERY
        }
        return flags
    }

    private fun circle(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    private fun roundRect(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = 16 * density
    }

    private fun dp(value: Int) = (value * density).toInt()
}
