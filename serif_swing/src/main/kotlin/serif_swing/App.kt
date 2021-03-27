/*
 * This Kotlin source file was generated by the Gradle 'init' task.
 */
package xyz.room409.serif.serif_swing
import com.formdev.flatlaf.*
import xyz.room409.serif.serif_shared.*
import xyz.room409.serif.serif_shared.db.DriverFactory
import kotlin.math.min
import kotlin.concurrent.thread
import java.awt.*
import java.awt.image.*
import java.awt.event.*
import javax.swing.*
import javax.swing.filechooser.*;
import javax.swing.text.*
import javax.swing.text.html.HTML
import javax.swing.text.html.HTMLEditorKit
import javax.swing.text.html.InlineView
import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.sound.sampled.Clip
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem


object AudioPlayer {
    var url = ""
    val clip = AudioSystem.getClip()
    fun loadAudio(audio_url: String) {
        if(url != audio_url) {
            clip.stop()
            url = audio_url
            val inputStream = AudioSystem.getAudioInputStream(File(url).getAbsoluteFile())
            clip.open(inputStream)
        }
    }
    fun play() {
        if(clip.isRunning()) {
            clip.stop()
        }
        clip.setFramePosition(0)
        clip.start()
    }
}

sealed class SwingState() {
    abstract fun refresh()
}
class SwingLogin(val transition: (MatrixState, Boolean) -> Unit, val onSync: () -> Unit, val panel: JPanel, val m: MatrixLogin) : SwingState() {
    var c_left = GridBagConstraints()
    var c_right = GridBagConstraints()
    var login_message_label = JLabel(m.login_message)
    var username_field = JTextField(20)
    var username_label = JLabel("Username: ")
    var password_field = JPasswordField(20)
    var password_label = JLabel("Password: ")
    var button = JButton("Login")
    var logIn: (ActionEvent) -> Unit = { transition(m.login(username_field.text, password_field.text, onSync), true) }

    init {
        panel.layout = GridBagLayout()
        c_left.anchor = GridBagConstraints.EAST
        c_left.gridwidth = GridBagConstraints.RELATIVE
        c_left.fill = GridBagConstraints.NONE
        c_left.weightx = 0.0

        c_right.anchor = GridBagConstraints.EAST
        c_right.gridwidth = GridBagConstraints.REMAINDER
        c_right.fill = GridBagConstraints.HORIZONTAL
        c_right.weightx = 1.0

        panel.add(login_message_label, c_right)
        panel.add(JLabel("Login with previous session?"), c_right)

        for (session in m.getSessions()) {
            var button = JButton(session)
            panel.add(button, c_right)
            button.addActionListener({ transition(m.loginFromSession(session, onSync), true) })
        }

        username_label.labelFor = username_field
        panel.add(username_label, c_left)
        panel.add(username_field, c_right)

        password_label.labelFor = password_field
        panel.add(password_label, c_left)
        panel.add(password_field, c_right)

        panel.add(button, c_right)

        password_field.addActionListener(logIn)
        button.addActionListener(logIn)
    }
    override fun refresh() {
        // This should change when we have multiple sessions,
        // since it will clear all text input fields on
        // refresh
        transition(m.refresh(), true)
    }
}
class SwingRooms(val transition: (MatrixState, Boolean) -> Unit, val panel: JPanel, var m: MatrixRooms) : SwingState() {
    var message_label = JLabel(m.message)
    var inner_scroll_pane = JPanel()
    init {
        panel.layout = BorderLayout()
        panel.add(message_label, BorderLayout.PAGE_START)

        inner_scroll_pane.layout = GridLayout(0, 1)
        for ((id, name, unreadCount, highlightCount, lastMessage) in m.rooms) {
            var button = JButton()
            button.layout = BoxLayout(button, BoxLayout.PAGE_AXIS)

            val room_name = JLabel("$name ($unreadCount unread / $highlightCount mentions)")
            val last_message = JLabel(lastMessage?.message?.take(80) ?: "")

            button.add(room_name)
            button.add(last_message)

            button.addActionListener({ transition(m.getRoom(id), true) })
            inner_scroll_pane.add(button)
        }
        panel.add(JScrollPane(inner_scroll_pane), BorderLayout.CENTER)

        var back_button = JButton("(Fake) Logout")
        panel.add(back_button, BorderLayout.PAGE_END)
        back_button.addActionListener({ transition(m.fake_logout(), true) })
    }
    override fun refresh() {
        transition(m.refresh(), true)
    }
    fun update(new_m: MatrixRooms) {
        if (m.rooms != new_m.rooms) {
            println("Having to transition, rooms !=")
            transition(new_m, false)
        } else {
            message_label.text = new_m.message
            m = new_m
        }
    }
}
class ImageFileFilter : FileFilter() {
    override fun accept(f: File): Boolean {
        if(f.isDirectory()) { return true }
        val fname = f.getName()
        val extension = fname.split('.').last().toLowerCase()
        val supported = arrayOf("gif", "png", "jpeg", "jpg")
        return supported.contains(extension)
    }
    override fun getDescription(): String {
        return "Supported Image files"
    }
}

// adapted from https://stackoverflow.com/questions/30590031/jtextpane-line-wrap-behavior?noredirect=1&lq=1%27
object WrapEditorKit : StyledEditorKit() {
    val defaultFactory = object : ViewFactory {
        override public fun create(element: Element): View = when (val kind = element.name) {
            AbstractDocument.ContentElementName -> WrapLabelView(element)
            AbstractDocument.ParagraphElementName -> ParagraphView(element)
            AbstractDocument.SectionElementName -> BoxView(element, View.Y_AXIS)
            StyleConstants.ComponentElementName -> ComponentView(element)
            StyleConstants.IconElementName -> IconView(element)
            else -> LabelView(element)
        }
    }
    override public fun getViewFactory(): ViewFactory = defaultFactory
}
class WrapLabelView(element: Element) : LabelView(element) {
    override public fun getMinimumSpan(axis: Int): Float  {
        when (axis) {
            View.X_AXIS -> return 0.0f;
            View.Y_AXIS -> return super.getMinimumSpan(axis);
            else -> throw IllegalArgumentException("Invalid axis: " + axis);
        }
    }
}

class URLMouseListener(var message: JTextPane) : MouseAdapter() {
    override fun mouseClicked(e: MouseEvent) {
        val pos = message.viewToModel(Point(e.x, e.y))
        println("you clicked on pos $pos, message length is ${message.text.length} because it is ${message.text}")
        if (pos >= 0 && pos < message.text.length) {
            println("That is, character ${message.text[pos]}")
            val doc = (message.document as? DefaultStyledDocument)
            if (doc != null) {
                val el = doc.getCharacterElement(pos)
                val href = el.attributes.getAttribute(HTML.Attribute.HREF) as String?
                if (href != null) {
                    // In the background, so that GUI doesn't freeze
                    thread(start = true) {
                        // We have to try using xdg-open first,
                        // since PinePhone somehow implements the
                        // Desktop API but has the same problem with the
                        // GTK_BACKEND var
                        try {
                            println("Trying to open $href with exec 'xdg-open $href'")
                            val pb = ProcessBuilder("xdg-open", href)
                            // Somehow this environment variable gets set for pb
                            // when it's NOT in System.getenv(). And of course, this
                            // is the one that makes xdg-open try to launch an X version
                            // of Firefox, giving the dreaded Firefox is already running
                            // message if you've got a Wayland version running already.
                            pb.environment().clear()
                            pb.environment().putAll(System.getenv())
                            pb.redirectErrorStream(true)
                            val process = pb.start()
                            val reader = BufferedReader(InputStreamReader(process.inputStream))
                            while (reader.readLine() != null) {}
                            process.waitFor()
                            println("done trying to open url")
                        } catch (e1: Exception) {
                            try {
                                println("Trying to open $href with Desktop")
                                java.awt.Desktop.getDesktop().browse(java.net.URI(href))
                            } catch (e2: Exception) {
                                println("Couldn't get ProcessBuilder('xdg-open $href') or Desktop, problem was $e1 then $e2")
                            }
                        }
                    }
                }
            }
        }
    }
}

class RecyclingList<T>(private var our_width: Int, val choose: (T) -> String, val make: Map<String, (T,()->Unit) -> Triple<List<Component>,()->Unit,(T,()->Unit) -> Unit>>) : JComponent(), Scrollable {
    data class RecyclableItem<T>(var start: Int, var end: Int, val sub_components: List<Component>, val deactivate: ()-> Unit, val recycle: (T,()->Unit) -> Unit)
    val recycle_map: MutableMap<String, ArrayDeque<RecyclableItem<T>>> = mutableMapOf()
    val subs: MutableList<Pair<String, RecyclableItem<T>>> = mutableListOf()
    var our_height = 0
    init {
        addMouseListener(object : MouseListener, MouseMotionListener {
            fun dispatchEvent(e: MouseEvent) {
                for ((_typ, recycleable) in subs) {
                    val (sy, ey, sub_components, _deactivate, _recycle) = recycleable
                    if ( (sy <= e.point.y)
                       &&(ey >= e.point.y) ) {
                        var sub_offset = sy
                        for (c in sub_components) {
                            if (e.point.y < sub_offset + c.height) {
                                val new_e = MouseEvent(c, e.id, e.getWhen(), e.modifiers, e.x, e.y-sub_offset, e.xOnScreen, e.yOnScreen, e.clickCount, e.isPopupTrigger(), e.button)
                                // a hack so that stuff like buttons have a proper parent when the reply/edit menu pops up and uses it
                                add(c)
                                c.dispatchEvent(new_e)
                                remove(c)
                                return;
                            } else {
                                sub_offset += c.height
                            }
                        }
                    }
                }
            }
            override fun mouseClicked(e: MouseEvent) = dispatchEvent(e)
            override fun mouseEntered(e: MouseEvent) = dispatchEvent(e)
            override fun mouseExited(e: MouseEvent) = dispatchEvent(e)
            override fun mousePressed(e: MouseEvent) = dispatchEvent(e)
            override fun mouseReleased(e: MouseEvent) = dispatchEvent(e)
            override fun mouseDragged(e: MouseEvent) = dispatchEvent(e)
            override fun mouseMoved(e: MouseEvent) = dispatchEvent(e)
        })
    }
    fun cleanup() = reset(1000, listOf())
    fun reset(new_width: Int, items: List<T>) {
        our_height = 0
        our_width = new_width
        for ((typ, recycleable) in subs) {
            recycleable.deactivate()
            recycle_map.getOrPut(typ, { ArrayDeque() }).add(recycleable)
        }
        subs.clear()
        for (i in items) {
            var height_delta = 0
            val typ = choose(i)
            val our_height_copy = our_height
            val repaint_lambda = { println("repaint(0, $our_height_copy, $our_width, $height_delta)"); repaint(0, our_height_copy, our_width, height_delta) }
            val possible_recycleable = recycle_map[typ]?.removeLastOrNull()
            val recycleable = if (possible_recycleable != null) {
                println("Recycling a $typ")
                possible_recycleable.recycle(i, repaint_lambda)
                possible_recycleable.start = our_height
                possible_recycleable
            } else {
                println("Creating a new $typ")
                val (sub_components, deactivate, recycle) = make[typ]!!(i, repaint_lambda)
                RecyclableItem<T>(our_height, -1, sub_components, deactivate, recycle)
            }
            for (c in recycleable.sub_components) {
                c.setSize(our_width, 1000)
                val d = c.getPreferredSize()
                val force_width = true
                if (force_width) {
                    c.setSize(our_width, d.height)
                    c.setBounds(0, our_height, our_width, d.height)
                } else {
                    c.setSize(d.width, d.height)
                    c.setBounds(0, our_height, d.width, d.height)
                }
                height_delta += c.height
                // Have to alert our hierarchy that we've changed size
            }
            recycleable.end = our_height + height_delta
            subs.add(Pair(typ, recycleable))
            our_height += height_delta
        }
        invalidate()
    }
    override fun getPreferredSize() = Dimension(our_width,our_height)
    override fun getPreferredScrollableViewportSize() = Dimension(our_width,our_height)
    override fun getScrollableUnitIncrement(p0: Rectangle, p1: Int, p2: Int) = 10
    override fun getScrollableBlockIncrement(p0: Rectangle, p1: Int, p2: Int) = 100
    override fun getScrollableTracksViewportWidth() = true
    override fun getScrollableTracksViewportHeight() = false
    override fun paintComponent(g: Graphics) {
        val clip_bounds = g.getClipBounds()
        val g = g.create()
        var count = 0
        var total = 0
        var sets = 0
        for ((_typ, recycleable) in subs) {
            val (sy, ey, sub_components, _refresh) = recycleable
            if ( (sy <= (clip_bounds.y+clip_bounds.height))
               &&(ey >= clip_bounds.y) ) {
                println("Drawing component from $sy to $ey")
                for (c in sub_components) {
                    c.paint(g)
                    count += 1
                    g.translate(0, c.height)
                }
                sets += 1
            } else {
                g.translate(0, ey-sy)
            }
            total += sub_components.size
        }
        println("painted $sets / ${subs.size}, $count / $total")
    }
}

class SwingChatRoom(val transition: (MatrixState, Boolean) -> Unit, val panel: JPanel, var m: MatrixChatRoom, var last_window_width: Int) : SwingState() {
    // From @stephenhay via https://mathiasbynens.be/demo/url-regex
    // slightly modified
    val URL_REGEX = Regex("""(https?|ftp)://[^\s/$.?#].[^\s]*""")
    val mk_sender = { msg: SharedUiMessage ->
        val sender = JTextArea()
        sender.setEditable(false)
        sender.lineWrap = true
        sender.wrapStyleWord = true
        val set_sender = { msg: SharedUiMessage -> sender.text = "${msg.sender}:  " }
        set_sender(msg)
        Pair(sender, set_sender)
    }
    val mk_menu = { msg_in: SharedUiMessage  ->
        var msg = msg_in
        val reply_option = JMenuItem("Reply")
        reply_option.addActionListener({
            println("Now writing a reply")
            replied_event_id = msg.id
        })
        val edit_option = JMenuItem("Edit")
        edit_option.addActionListener({
            println("Now editing a message")
            edited_event_id = msg.id
            message_field.text = msg.message
        })
        val show_src_option = JMenuItem("Show Source")
        show_src_option.addActionListener({
            val json_str = m.getEventSrc(msg.id)

            val window = SwingUtilities.getWindowAncestor(panel)
            val dim = window.getSize()
            val h = dim.height
            val w = dim.width
            val dialog = JDialog(window, "Event Source")

            val dpanel = JPanel(BorderLayout())
            val src_txt = JTextPane()
            src_txt.setContentType("text/plain")
            src_txt.setText(json_str)
            src_txt.setEditable(false)

            val close_btn = JButton("Close")
            close_btn.addActionListener({
                dialog.setVisible(false)
                dialog.dispose()
            })

            dpanel.add(JScrollPane(src_txt), BorderLayout.CENTER)
            dpanel.add(close_btn,BorderLayout.PAGE_END)
            dialog.add(dpanel)

            dialog.setSize(w,h/2)
            dialog.setVisible(true)
            dialog.setResizable(false)
            dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE)
        })

        val msg_action_popup = JPopupMenu()
        msg_action_popup.add(reply_option)
        if(msg.sender.contains(m.username)) {
            msg_action_popup.add(edit_option)
        }
        msg_action_popup.add(show_src_option)

        val msg_action_button = JButton("...")
        msg_action_button.addActionListener({
            msg_action_popup.show(msg_action_button,0,0)
        })
        Pair(msg_action_button, { new_msg: SharedUiMessage -> msg = new_msg; })
    }
    val recycling_message_list = RecyclingList<SharedUiMessage>(last_window_width,
        { when (it) {
                is SharedUiImgMessage -> "img"
                is SharedUiAudioMessage -> "audio"
                else -> "text"
        } },
        mapOf(
            "img" to { msg: SharedUiMessage, repaint_cell ->
                msg as SharedUiImgMessage
                val set_icon_image = { icon: ImageIcon, msg: SharedUiImgMessage, repaint_cell: ()->Unit ->
                    val img_url = msg.url
                    val og_image_icon = ImageIcon(img_url)
                    val og_image = og_image_icon.image
                    val img_width: Int = og_image.getWidth(null)
                    val img_height: Int = og_image.getHeight(null)
                    if (last_window_width != 0 && img_width != 0 && img_height != 0) {
                        val new_width = min(last_window_width, img_width)
                        val new_height = min(img_height, (img_height * new_width)/img_width)
                        icon.setImage(og_image.getScaledInstance(new_width, new_height, Image.SCALE_DEFAULT))
                    } else {
                        icon.setImage(og_image)
                    }
                    icon.setImageObserver(object : ImageObserver {
                        override fun imageUpdate(img: Image, infoFlags: Int, x: Int, y: Int, w: Int, h: Int): Boolean {
                            repaint_cell()
                            return ((infoFlags and (ImageObserver.ALLBITS or ImageObserver.ABORT)) == 0)
                        }
                    })
                }
                val icon = ImageIcon()
                set_icon_image(icon, msg, repaint_cell)
                val (sender, set_sender) = mk_sender(msg)
                val (menu, set_menu) = mk_menu(msg)
                Triple(listOf(sender, JLabel(icon), menu), { icon.setImageObserver(null) }, { msg, repaint_cell ->
                    set_icon_image(icon, msg as SharedUiImgMessage, repaint_cell)
                    set_sender(msg)
                    set_menu(msg)
                })
            },
            "audio" to { msg, repaint_cell ->
                msg as SharedUiAudioMessage
                var audio_url = msg.url
                val play_btn = JButton("Play/Pause $audio_url")
                play_btn.addActionListener({
                    AudioPlayer.loadAudio(audio_url)
                    AudioPlayer.play()
                })
                val (sender, set_sender) = mk_sender(msg)
                val (menu, set_menu) = mk_menu(msg)
                Triple(listOf(sender, play_btn, menu), { Unit }, { msg, repaint_cell -> set_sender(msg); set_menu(msg); audio_url = (msg as SharedUiAudioMessage).url; })
            },
            "text" to { msg, repaint_cell ->
                val message = JTextPane()
                message.setEditorKit(WrapEditorKit);
                message.setEditable(false)
                message.addMouseListener(URLMouseListener(message))

                val simpleAttrs = SimpleAttributeSet()
                val set_text = { msg: SharedUiMessage ->
                    message.document.remove(0, message.document.length)
                    var current_idx = 0
                    for (url_match in URL_REGEX.findAll(msg.message)) {
                        if (url_match.range.start > current_idx) {
                            message.document.insertString(current_idx, msg.message.slice(current_idx .. url_match.range.start-1), simpleAttrs)
                            current_idx = url_match.range.start
                        }
                        val urlAttrs = SimpleAttributeSet()
                        StyleConstants.setUnderline(urlAttrs, true)
                        urlAttrs.addAttribute(HTML.Attribute.HREF, url_match.value)
                        message.document.insertString(current_idx, url_match.value, urlAttrs)
                        current_idx = url_match.range.endInclusive + 1
                    }
                    if (current_idx < msg.message.length) {
                        message.document.insertString(current_idx, msg.message.slice(current_idx .. msg.message.length-1), simpleAttrs)
                    }
                }

                set_text(msg)
                val (sender, set_sender) = mk_sender(msg)
                val (menu, set_menu) = mk_menu(msg)

                Triple(listOf(sender, message, menu), { Unit }, { msg, repaint_cell -> set_text(msg); set_sender(msg); set_menu(msg) })
            }
        )
    )
    val c_left = GridBagConstraints()
    val c_right = GridBagConstraints()
    val message_field = JTextField(20)
    var replied_event_id = ""
    var edited_event_id = ""
    init {
        panel.layout = BorderLayout()

        val backfill_button = JButton("Backfill")
        backfill_button.addActionListener({ m.requestBackfill() })
        panel.add(backfill_button, BorderLayout.PAGE_START)

        recycling_message_list.reset(last_window_width, m.messages)
        panel.add(
            JScrollPane(
                recycling_message_list,
                JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            ),
            BorderLayout.CENTER
        )

        val message_panel = JPanel()
        message_panel.layout = BorderLayout()
        var back_button = JButton("Back")
        message_panel.add(back_button, BorderLayout.LINE_START)
        message_panel.add(message_field, BorderLayout.CENTER)
        val msg_panel_actions = JPanel()
        msg_panel_actions.layout = BoxLayout(msg_panel_actions, BoxLayout.LINE_AXIS)
        var attach_button = JButton("+")
        msg_panel_actions.add(attach_button)
        var send_button = JButton("Send")
        msg_panel_actions.add(send_button)
        message_panel.add(msg_panel_actions, BorderLayout.LINE_END)
        panel.add(message_panel, BorderLayout.PAGE_END)
        val onSend: (ActionEvent) -> Unit = {
            val text = message_field.text
            message_field.text = ""
            val res =
            when {
                replied_event_id == "" && edited_event_id == "" -> m.sendMessage(text)
                replied_event_id != "" -> {
                    val eventid = replied_event_id
                    replied_event_id = ""
                    println("Replying to $eventid")
                    m.sendReply(text, eventid)
                }
                else -> {
                    val eventid = edited_event_id
                    edited_event_id = ""
                    println("Editing $eventid")
                    m.sendEdit(text, eventid)
                }
            }
            transition(res, true)
        }
        val onAttach: (ActionEvent) -> Unit = {
            val fc = JFileChooser()
            val iff = ImageFileFilter()
            fc.addChoosableFileFilter(iff)
            fc.setFileFilter(iff)
            val ret = fc.showDialog(panel, "Attach")
            if(ret == JFileChooser.APPROVE_OPTION) {
                val file = fc.getSelectedFile()
                message_field.text = ""
                transition(m.sendImageMessage(file.toPath().toString()), true)
                println("Selected ${file.toPath()}")
            }
        }
        message_field.addActionListener(onSend)
        send_button.addActionListener(onSend)
        attach_button.addActionListener(onAttach)
        back_button.addActionListener({ recycling_message_list.cleanup(); transition(m.exitRoom(), true) })
        m.sendReceipt(m.messages.last().id)
    }
    override fun refresh() {
        transition(m.refresh(), true)
    }
    fun update(new_m: MatrixChatRoom, window_width: Int) {
        if (m.messages != new_m.messages || last_window_width != window_width) {
            m = new_m
            recycling_message_list.reset(window_width, m.messages)
            last_window_width = window_width
        } else {
            m = new_m
        }
    }
}

class App {
    var frame = JFrame("Serif")
    var sstate: SwingState
    fun refresh_all() {
        sstate.refresh()
        frame.validate()
        frame.repaint()
    }

    init {
        // Each UI will create it's specific DriverFactory
        // And call this function before the backend can get
        // information out of the database
        Database.initDb(DriverFactory())

        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        sstate = constructStateView(MatrixLogin())
        frame.pack()
        frame.setVisible(true)
        frame.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                println("Refresh-alling!")
                refresh_all()
            }
        })
    }

    fun transition(new_state: MatrixState, partial: Boolean) {
        // TODO: update current view if new_state is the same type as mstate
        val s = sstate
        if (partial) {
            when {
                new_state is MatrixChatRoom && s is SwingChatRoom -> { s.update(new_state, frame.width); return; }
                new_state is MatrixRooms && s is SwingRooms -> { s.update(new_state); return; }
            }
        }
        sstate = constructStateView(new_state)
    }

    fun constructStateView(mstate: MatrixState): SwingState {
        frame.contentPane.removeAll()
        var panel = JPanel()
        val to_ret = when (mstate) {
            is MatrixLogin -> SwingLogin(
                ::transition,
                { javax.swing.SwingUtilities.invokeLater({ refresh_all() }) },
                panel, mstate
            )
            is MatrixRooms -> SwingRooms(::transition, panel, mstate)
            is MatrixChatRoom -> SwingChatRoom(::transition, panel, mstate, frame.width)
        }
        frame.add(panel)
        frame.validate()
        frame.repaint()
        return to_ret
    }
}

fun main(args: Array<String>) {
    FlatDarkLaf.install()
    UIManager.getLookAndFeelDefaults().put("defaultFont", Font("Serif", Font.PLAIN, 16))
    javax.swing.SwingUtilities.invokeLater({ App() })
}
