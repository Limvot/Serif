/*
 * This Kotlin source file was generated by the Gradle 'init' task.
 */
package xyz.room409.serif.serif_swing
import com.formdev.flatlaf.*
import xyz.room409.serif.serif_shared.*
import xyz.room409.serif.serif_shared.db.DriverFactory
import java.awt.*
import java.awt.event.*
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import javax.sound.sampled.AudioSystem
import javax.swing.*
import javax.swing.filechooser.*
import javax.swing.text.*
import javax.swing.text.html.HTML
import kotlin.concurrent.thread
import kotlin.math.min

import java.util.Timer
import kotlin.concurrent.fixedRateTimer
import org.jcodec.common.io.NIOUtils
import org.jcodec.api.FrameGrab
import org.jcodec.common.model.Picture
import org.jcodec.common.model.ColorSpace
import org.jcodec.common.DemuxerTrack
import org.jcodec.scale.ColorUtil
import org.jcodec.scale.RgbToBgr
import org.jcodec.scale.Transform

object AudioPlayer {
    var url = ""
    val clip = AudioSystem.getClip()
    fun loadAudio(audio_url: String) {
        if (url != audio_url) {
            clip.stop()
            url = audio_url
            val inputStream = AudioSystem.getAudioInputStream(File(url).getAbsoluteFile())
            clip.open(inputStream)
        }
    }
    fun play() {
        if (clip.isRunning()) {
            clip.stop()
        }
        clip.setFramePosition(0)
        clip.start()
    }
}

class VideoPlayer {
    var url = ""
    val image_array = ArrayList<BufferedImage>()
    var playing_task = Timer()
    var idx = 0
    var framerate = 17L
    var playing = false


    fun loadVideo(video_url: String, pic_out: JButton) {
        if (url != video_url) {
            image_array.clear()
            url = video_url
            val file = File(video_url);
            val grab = FrameGrab.createFrameGrab(NIOUtils.readableChannel(file))
            val vt: DemuxerTrack = grab.getVideoTrack()
            val frame_count = vt.getMeta().getTotalFrames()
            val duration = vt.getMeta().getTotalDuration()
            framerate = (1000.0*duration).toLong() / (frame_count).toLong()
            while(true) {
                val picture: Picture? = grab.getNativeFrame()
                if(picture == null) {
                    break
                }
                val buff_img = toBufferedImage(picture)
                image_array.add(buff_img)
            }
            idx = 0
            pic_out.setIcon(ImageIcon(image_array[idx]))
        }
    }
    private fun toBufferedImage(_src: Picture): BufferedImage {
        var src = _src
		if (src.getColor() != ColorSpace.BGR) {
			val bgr = Picture.createCropped(src.getWidth(), src.getHeight(), ColorSpace.BGR, src.getCrop())
			if (src.getColor() == ColorSpace.RGB) {
				RgbToBgr().transform(src, bgr)
			} else {
				val transform = ColorUtil.getTransform(src.getColor(), ColorSpace.RGB)
				transform.transform(src, bgr)
				RgbToBgr().transform(bgr, bgr)
			}
			src = bgr
		}

        var dst = BufferedImage(src.getCroppedWidth(), src.getCroppedHeight(),
                BufferedImage.TYPE_3BYTE_BGR)

        if (src.getCrop() == null)
            toBufferedImage(src, dst);
        else
            toBufferedImageCropped(src, dst);

        return dst
    }
    private fun toBufferedImageCropped(src: Picture, dst: BufferedImage) {
        val data = (dst.getRaster().getDataBuffer() as DataBufferByte).getData();
        val srcData = src.getPlaneData(0);
        val dstStride = dst.getWidth() * 3;
        val srcStride = src.getWidth() * 3;
        var srcOff = 0
        var dstOff = 0
        for (line in  0..dst.getHeight()-1) {
            var _is = srcOff
            var id = dstOff
            while (id < (dstOff + dstStride)) {
                // Unshifting, since JCodec stores [0..255] -> [-128, 127]
                data[id] = (srcData[_is] + 128).toByte()
                data[id + 1] = (srcData[_is + 1] + 128).toByte()
                data[id + 2] = (srcData[_is + 2] + 128).toByte()
                id += 3
                _is += 3
            }
            srcOff += srcStride;
            dstOff += dstStride;
        }
    }
    private fun toBufferedImage(src: Picture, dst: BufferedImage) {
        val _data = (dst.getRaster().getDataBuffer() as DataBufferByte).getData();
        val srcData = src.getPlaneData(0);
        for (i in 0.._data.size-1) {
            // Unshifting, since JCodec stores [0..255] -> [-128, 127]
            _data[i] = (srcData[i] + 128).toByte()
        }
    }

    private fun updateImage(pic_out: JButton) {
        pic_out.setIcon(ImageIcon(image_array[idx]))
        idx++
    }
    fun play(pic_out: JButton) {
        if(playing) {
            playing = false
            playing_task.cancel()
            println("Stopping video playback")
        } else {
            playing = true
            println("Starting video playback")
            playing_task = fixedRateTimer("pic cb", false, 0L, framerate) {
                if(idx >= image_array.size) {
                    idx = 0
                }
                updateImage(pic_out)
            }
        }
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
        var topPanel = JPanel()
        topPanel.layout = BoxLayout(topPanel, BoxLayout.LINE_AXIS)
        topPanel.add(message_label)
        var newRoomButton = JButton("New Room")
        topPanel.add(newRoomButton)
        newRoomButton.addActionListener({

            val window = SwingUtilities.getWindowAncestor(panel)
            val dim = window.getSize()
            val h = dim.height
            val w = dim.width
            val dialog = JDialog(window, "Create Room")

            val dpanel = JPanel()
            dpanel.layout = BoxLayout(dpanel, BoxLayout.PAGE_AXIS)
            // name, room_alias_name, topic
            var roomname_field = JTextField(20)
            var roomname_label = JLabel("Room Name: ")
            var alias_field = JTextField(20)
            var alias_label = JLabel("Alias: ")
            var topic_field = JTextField(20)
            var topic_label = JLabel("Topic: ")

            val create_btn = JButton("Create")
            create_btn.addActionListener({
                println(m.createRoom(roomname_field.text, alias_field.text, topic_field.text))
                dialog.setVisible(false)
                dialog.dispose()
            })

            val close_btn = JButton("Close")
            close_btn.addActionListener({
                dialog.setVisible(false)
                dialog.dispose()
            })
            dpanel.add(roomname_label)
            dpanel.add(roomname_field)
            dpanel.add(alias_label)
            dpanel.add(alias_field)
            dpanel.add(topic_label)
            dpanel.add(topic_field)
            dpanel.add(create_btn)
            dpanel.add(close_btn)
            dialog.add(dpanel)

            dialog.setSize(w, h / 2)
            dialog.setVisible(true)
            dialog.setResizable(false)
            dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE)
        })
        panel.add(topPanel, BorderLayout.PAGE_START)

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
        if (f.isDirectory()) { return true }
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
        public override fun create(element: Element): View = when (val kind = element.name) {
            AbstractDocument.ContentElementName -> WrapLabelView(element)
            AbstractDocument.ParagraphElementName -> ParagraphView(element)
            AbstractDocument.SectionElementName -> BoxView(element, View.Y_AXIS)
            StyleConstants.ComponentElementName -> ComponentView(element)
            StyleConstants.IconElementName -> IconView(element)
            else -> LabelView(element)
        }
    }
    public override fun getViewFactory(): ViewFactory = defaultFactory
}
class WrapLabelView(element: Element) : LabelView(element) {
    public override fun getMinimumSpan(axis: Int): Float {
        when (axis) {
            View.X_AXIS -> return 0.0f
            View.Y_AXIS -> return super.getMinimumSpan(axis)
            else -> throw IllegalArgumentException("Invalid axis: " + axis)
        }
    }
}

class SwingChatRoom(val transition: (MatrixState, Boolean) -> Unit, val panel: JPanel, var m: MatrixChatRoom, var last_window_width: Int) : SwingState() {
    // From @stephenhay via https://mathiasbynens.be/demo/url-regex
    // slightly modified
    val URL_REGEX = Regex("""(https?|ftp)://[^\s/$.?#].[^\s]*""")
    var inner_scroll_pane = JPanel()
    var c_left = GridBagConstraints()
    var c_right = GridBagConstraints()
    var message_field = JTextField(20)
    var replied_event_id = ""
    var edited_event_id = ""
    init {
        panel.layout = BorderLayout()

        val backfill_button = JButton("Backfill")
        backfill_button.addActionListener({ m.requestBackfill() })
        panel.add(backfill_button, BorderLayout.PAGE_START)

        val group_layout = GroupLayout(inner_scroll_pane)
        inner_scroll_pane.layout = group_layout
        redrawMessages(last_window_width)
        panel.add(
            JScrollPane(
                inner_scroll_pane,
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
            if (ret == JFileChooser.APPROVE_OPTION) {
                val file = fc.getSelectedFile()
                message_field.text = ""
                transition(m.sendImageMessage(file.toPath().toString()), true)
                println("Selected ${file.toPath()}")
            }
        }
        message_field.addActionListener(onSend)
        send_button.addActionListener(onSend)
        attach_button.addActionListener(onAttach)
        back_button.addActionListener({ transition(m.exitRoom(), true) })
        m.messages.lastOrNull()?.let { m.sendReceipt(it.id) }
    }
    fun redrawMessages(draw_width: Int) {
        inner_scroll_pane.removeAll()
        val layout = inner_scroll_pane.layout as GroupLayout
        val parallel_group = layout.createParallelGroup(GroupLayout.Alignment.LEADING)
        var seq_vert_groups = layout.createSequentialGroup()
        for (msg in m.messages) {
            val _sender = msg.sender
            val sender = JTextArea("$_sender:  ")
            val show_edit_btn = _sender.contains(m.username)
            sender.setEditable(false)
            sender.lineWrap = true
            sender.wrapStyleWord = true

            val msg_widget =
                when (msg) {
                    is SharedUiImgMessage -> {
                        val img_url = msg.url
                        val og_image_icon = ImageIcon(img_url)
                        val og_image = og_image_icon.image
                        val img_width: Int = og_image.getWidth(null)
                        val img_height: Int = og_image.getHeight(null)
                        if (draw_width != 0 && img_width != 0 && img_height != 0) {
                            val new_width = min(draw_width, img_width)
                            val new_height = min(img_height, (img_height * new_width) / img_width)
                            JLabel(ImageIcon(og_image.getScaledInstance(new_width, new_height, Image.SCALE_DEFAULT)))
                        } else {
                            JLabel(og_image_icon)
                        }
                    }
                    is SharedUiAudioMessage -> {
                        val audio_url = msg.url
                        val play_btn = JButton("Play/Pause $audio_url")
                        play_btn.addActionListener({
                            AudioPlayer.loadAudio(audio_url)
                            AudioPlayer.play()
                        })
                        play_btn
                    }
                    is SharedUiVideoMessage -> {
                        val message_panel = JPanel()
                        message_panel.layout = BoxLayout(message_panel, BoxLayout.PAGE_AXIS)
                        val video_url = msg.url
                        val video_btn = JButton("Video $video_url")
                        val pic_chan = ImageIcon()

                        val vp = VideoPlayer()
                        vp.loadVideo(video_url, video_btn)
                        video_btn.addActionListener({
                            vp.play(video_btn)
                        })

                        message_panel.add(video_btn)
                        message_panel
                    }
                    is SharedUiFileMessage -> {
                        val file_url = msg.url
                        val filename = msg.filename
                        val mimetype = msg.mimetype
                        val btn = JButton()
                        val message_panel = JPanel()
                        message_panel.layout = BoxLayout(message_panel, BoxLayout.PAGE_AXIS)
                        val l = JLabel("Download $filename")
                        message_panel.add(l)
                        arrayOf(mimetype, file_url).forEach {
                            message_panel.add(JLabel(it))
                        }
                        btn.add(message_panel)
                        btn
                    }
                    is SharedUiLocationMessage -> {
                        val loc_str = msg.location
                        val body = msg.message
                        val btn = JButton()
                        val message_panel = JPanel()
                        message_panel.layout = BoxLayout(message_panel, BoxLayout.PAGE_AXIS)
                        val l = JLabel("Location")
                        message_panel.add(l)
                        body.split("\n").forEach { part ->
                            message_panel.add(JLabel(part))
                        }
                        btn.add(message_panel)
                        val parts = loc_str.split(",")
                        val lat = parts[0].replace("geo:","")
                        val lon = parts[1]
                        val href = "https://maps.google.com/?q=$lat,$lon"
                        btn.addActionListener({ openUrl(href) })
                        btn
                    }
                    else -> {
                        val message = JTextPane()
                        message.setEditorKit(WrapEditorKit)
                        message.setEditable(false)
                        // This is mandatory to make it wrap, for some reason
                        // It's not in the examples I found online
                        // My best guess is that because of the layout, it
                        // won't smash it smaller than preferred size, but it will
                        // stretch it to fit the larger size?
                        message.setPreferredSize(Dimension(0, 0))

                        var current_idx = 0
                        val simpleAttrs = SimpleAttributeSet()
                        for (url_match in URL_REGEX.findAll(msg.message)) {
                            if (url_match.range.start > current_idx) {
                                message.document.insertString(current_idx, msg.message.slice(current_idx..url_match.range.start - 1), simpleAttrs)
                                current_idx = url_match.range.start
                            }
                            val urlAttrs = SimpleAttributeSet()
                            StyleConstants.setUnderline(urlAttrs, true)
                            urlAttrs.addAttribute(HTML.Attribute.HREF, url_match.value)
                            message.document.insertString(current_idx, url_match.value, urlAttrs)
                            current_idx = url_match.range.endInclusive + 1
                        }
                        if (current_idx < msg.message.length) {
                            message.document.insertString(current_idx, msg.message.slice(current_idx..msg.message.length - 1), simpleAttrs)
                        }

                        message.addMouseListener(object : MouseAdapter() {
                            override fun mouseClicked(e: MouseEvent) {
                                val pos = message.viewToModel(Point(e.x, e.y))
                                println("you clicked on pos $pos")
                                if (pos >= 0 && pos < msg.message.length) {
                                    println("That is, character ${msg.message[pos]}")
                                    val doc = (message.document as? DefaultStyledDocument)
                                    if (doc != null) {
                                        val el = doc.getCharacterElement(pos)
                                        val href = el.attributes.getAttribute(HTML.Attribute.HREF) as String?
                                        if (href != null) {
                                            openUrl(href)
                                        }
                                    }
                                }
                            }
                        })
                        message
                    }
                }

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
                dpanel.add(close_btn, BorderLayout.PAGE_END)
                dialog.add(dpanel)

                dialog.setSize(w, h / 2)
                dialog.setVisible(true)
                dialog.setResizable(false)
                dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE)
            })

            val msg_action_popup = JPopupMenu()
            msg_action_popup.add(reply_option)
            if (show_edit_btn) {
                msg_action_popup.add(edit_option)
            }
            msg_action_popup.add(show_src_option)

            val msg_action_button = JButton("...")
            msg_action_button.addActionListener({
                msg_action_popup.show(msg_action_button, 0, 0)
            })

            parallel_group.addComponent(sender)
            parallel_group.addComponent(msg_widget)
            parallel_group.addComponent(msg_action_button)
            seq_vert_groups.addComponent(sender)
            seq_vert_groups.addGroup(
                layout.createSequentialGroup()
                    .addPreferredGap(sender, msg_widget, LayoutStyle.ComponentPlacement.INDENT)
                    .addComponent(msg_widget)
                    .addComponent(msg_action_button)
            )
        }
        layout.setHorizontalGroup(parallel_group)
        layout.setVerticalGroup(seq_vert_groups)
    }
    override fun refresh() {
        transition(m.refresh(), true)
    }
    fun update(new_m: MatrixChatRoom, window_width: Int) {
        if (m.messages != new_m.messages || last_window_width != window_width) {
            m = new_m
            redrawMessages(window_width)
            last_window_width = window_width
        } else {
            m = new_m
        }
    }
    private fun openUrl(href: String) {
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
