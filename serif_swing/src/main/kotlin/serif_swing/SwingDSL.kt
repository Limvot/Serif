package xyz.room409.serif.serif_swing
import com.formdev.flatlaf.*
import xyz.room409.serif.serif_shared.*
import kotlin.math.min
import kotlin.concurrent.thread
import java.awt.*
import java.awt.event.*
import javax.swing.*

class JButtonBuilder {
    var text: String = ""
    var onClickAction: (ActionEvent) -> Unit = {}
    
    inline fun withText(withText: () -> String) {
        this.text = withText()
    }
    
    fun onClick(onClickAction: (ActionEvent) -> Unit) {
        this.onClickAction = onClickAction
    }

    fun build(): JButton {
        val btn = JButton(this.text)
        btn.addActionListener(this.onClickAction)
        return btn
    }
}

fun button(lambda: JButtonBuilder.() -> Unit): JButton = JButtonBuilder().apply(lambda).build()
fun button(textLabel: String, lambda: JButtonBuilder.() -> Unit): JButton {
    val builder = JButtonBuilder()
    builder.text = textLabel
    return builder.apply(lambda).build()
}

class BorderLayoutBuilder {
    var container: Container? = null
    var north: Component? = null
    var south: Component? = null
    var east: Component? = null
    var west: Component? = null
    var center: Component? = null

    inline fun with(container: () -> Container) {
        this.container = container()
    }
    
    inline fun north(north: () -> Component) {
        this.north = north()
    }

    inline fun south(south: () -> Component) {
        this.south = south()
    }

    inline fun east(east: () -> Component) {
        this.east = east()
    }

    inline fun west(west: () -> Component) {
        this.west = west()
    }

    inline fun center(center: () -> Component) {
        this.center = center()
    }
    
    fun build(): BorderLayout {
        val layout = BorderLayout()
        this.container!!.layout = layout
        if(this.north != null) { this.container!!.add(this.north, BorderLayout.NORTH) }
        if(this.south != null) { this.container!!.add(this.south, BorderLayout.SOUTH) }
        if(this.east != null) { this.container!!.add(this.east, BorderLayout.EAST) }
        if(this.west != null) { this.container!!.add(this.west, BorderLayout.WEST) }
        if(this.center != null) { this.container!!.add(this.center, BorderLayout.CENTER) }
        return layout
    }
}

fun borderLayout(lambda: BorderLayoutBuilder.() -> Unit): BorderLayout = BorderLayoutBuilder().apply(lambda).build()


class JMenuItemBuilder {
    var text: String = ""
    var onClickAction: (ActionEvent) -> Unit = {}
    
    inline fun withText(withText: () -> String) {
        this.text = withText()
    }
    
    fun onClick(onClickAction: (ActionEvent) -> Unit) {
        this.onClickAction = onClickAction
    }

    fun build(): JMenuItem {
        val item = JMenuItem(this.text)
        item.addActionListener(this.onClickAction)
        return item
    }
}

fun menuItem(lambda: JMenuItemBuilder.() -> Unit): JMenuItem = JMenuItemBuilder().apply(lambda).build()

class MenuItems: ArrayList<JMenuItem?>() {
    fun menuItem(builder: JMenuItemBuilder.() -> Unit) {
        add(JMenuItemBuilder().apply(builder).build())
    }
    fun menuItem(text: String, lambda: JMenuItemBuilder.() -> Unit) {
        val b = JMenuItemBuilder()
        b.text = text
        add(b.apply(lambda).build())
    }
}

class JPopupMenuBuilder {
    private val items = mutableListOf<JMenuItem?>()

    fun item(item: () -> JMenuItem?) {
        items.add(item())
    }
    fun items(lambda: MenuItems.() -> Unit) {
        items.addAll(MenuItems().apply(lambda))
    }

    fun build(): JPopupMenu {
        val popup_menu = JPopupMenu()
        this.items.filterNotNull().forEach { popup_menu.add(it) }
        return popup_menu
    }
}
fun popupMenu(lambda: JPopupMenuBuilder.() -> Unit): JPopupMenu = JPopupMenuBuilder().apply(lambda).build()


class BoxLayoutBuilder {
    var container: Container? = null
    var type = BoxLayout.LINE_AXIS
    val components = mutableListOf<Component>()

    inline fun with(container: () -> Container) {
        this.container = container()
    }
    
    inline fun type(type: () -> Int) {
        this.type = type()
    }
    
    inline fun add(lambda: () -> Component) {
        this.components.add(lambda())
    }
    
    fun build(): BoxLayout {
        val layout = BoxLayout(this.container!!, this.type)
        this.container!!.layout = layout
        this.components.forEach({
            this.container!!.add(it)
        })
        return layout
    }
}

fun boxLayout(lambda: BoxLayoutBuilder.() -> Unit): BoxLayout = BoxLayoutBuilder().apply(lambda).build()


