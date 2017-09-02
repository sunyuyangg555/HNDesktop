import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import javafx.embed.swing.JFXPanel
import javax.swing.*
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import java.io.InputStreamReader
import javax.swing.UIManager
import java.awt.*
import javax.swing.event.ListSelectionEvent
import javax.swing.plaf.metal.DefaultMetalTheme
import javax.swing.plaf.metal.MetalLookAndFeel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.JTree
import javax.swing.event.TreeModelEvent
import javax.swing.event.TreeModelListener
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel
import kotlin.collections.ArrayList


/**
 * Created by weston on 8/26/17.
 */

class App {
    companion object {
        init {
            MetalLookAndFeel.setCurrentTheme(DefaultMetalTheme())
            UIManager.setLookAndFeel(MetalLookAndFeel())
//            UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel")
        }
    }

    val progressBar = JProgressBar(0, 30)
    var contentPane = JPanel()
    var storyControlPanel: JPanel = JPanel()
    var storyPanel: JPanel = JPanel()

    val listModel = DefaultListModel<Story>()
    val storyList = JList(listModel)

    val userLabel = JLabel()
    val pointsLabel = JLabel()
    val storyTimeLabel = JLabel()
    val commentCountLabel = JLabel()
    val contentToggleButton = JButton("View Page")

    var commentTree = JTree()
    var commentTreeRoot = DefaultMutableTreeNode()

    val jfxPanel = JFXPanel()

    init {

        val frame = JFrame("Hacker News")
        val screenSize = Toolkit.getDefaultToolkit().screenSize
        val frameSize = Dimension((screenSize.width*0.90f).toInt(), (screenSize.height*0.85f).toInt())
        frame.size = frameSize

        frame.contentPane = contentPane
        contentPane.preferredSize = frameSize

        contentPane.layout = GridBagLayout()
        val c = GridBagConstraints()
        c.anchor = GridBagConstraints.CENTER

        progressBar.preferredSize = Dimension(400, 25)
        progressBar.isStringPainted = true

        //Just add so the display updates. Not sure why, but
        //it delays showing the progress bar if we don't do this.
        contentPane.add(JLabel(""), c)

        c.gridy = 1
        contentPane.add(progressBar, c)
        progressBar.value = 0

        frame.setLocation(screenSize.width / 2 - frame.size.width / 2, screenSize.height / 2 - frame.size.height / 2)
        frame.pack()

        frame.isVisible = true
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE

        val stories = getFrontPageStories()

        contentPane = JPanel(BorderLayout())
        frame.contentPane = contentPane

        val mainPanel = getMainPanel(stories)
        contentPane.add(mainPanel)

        storyList.selectedIndex = 0

        frame.revalidate()
    }

    fun getMainPanel(storyNodes: ArrayList<JsonObject>) : JComponent {
        val stories = storyNodes
                .filter { it.get("type").asString.equals("story", true) }
                .map { Story(it) }

        val def = DefaultListCellRenderer()
        val renderer = ListCellRenderer<Story> { list, value, index, isSelected, cellHasFocus ->
            def.getListCellRendererComponent(list, value.title, index, isSelected, cellHasFocus)
        }

        storyList.cellRenderer = renderer
        storyList.addListSelectionListener { e: ListSelectionEvent? ->
            if (e != null) {
                if (!e.valueIsAdjusting) {
                    changeActiveStory(stories[storyList.selectedIndex])
                }
            }
        }

        for (story in stories) {
            listModel.addElement(story)
        }

        val mainRightPane = buildMainRightPanel()

//        mainRightPane.add(jfxPanel)

        val storyScroller = JScrollPane(storyList)
        storyScroller.verticalScrollBar.unitIncrement = 16

        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, storyScroller, mainRightPane)

        return splitPane
    }

    fun changeActiveStory(story: Story) {
        userLabel.text = "Posted by: ${story.user}"
        pointsLabel.text = "Points: ${story.points}"
        storyTimeLabel.text = "Time: ${story.time}"
        commentCountLabel.text = "Comments: ${story.totalComments}"

//        Platform.runLater({
//            val webView = WebView()
//            jfxPanel.scene = Scene(webView)
//            webView.engine.load(story.url)
//        })

        storyPanel.removeAll()
        storyPanel.add(getCommentsPanel())
        loadComments(story)
    }

    fun loadComments(story: Story) {
        Thread(Runnable {
            if (story.kids != null) {
            var index = 0
            val iter = story.kids.iterator()
            while(iter.hasNext()) {
                val commentId = iter.next().asInt
                val address = "$index"
                loadCommentAux(Comment(getItem(commentId), address), address)
                index++
            }
        } }).start()
    }

    fun loadCommentAux(comment: Comment, treeAddress: String) {
        commentArrived(comment, treeAddress)

        if (comment.kids != null) {
            var index = 0
            val iter = comment.kids.iterator()
            while(iter.hasNext()) {
                val commentId = iter.next().asInt
                val address = treeAddress+";$index"
                loadCommentAux(Comment(getItem(commentId), address), address)
                index++
            }
        }
    }

    fun commentArrived(comment: Comment, treeAddress: String) {
        addNodeAtAddress(DefaultMutableTreeNode(comment.text), comment.treeAddress)
    }

    fun addNodeAtAddress(node: DefaultMutableTreeNode, address: String) {
        val addressArray = address.split(";")
        var parentNode = commentTreeRoot
        for ((index, addressComponent) in addressArray.withIndex()) {

            // Don't use the last component in the addressArray since that's the index
            // which the child should be added at
            if (index < addressArray.size - 1) {
                parentNode = parentNode.getChildAt(Integer.parseInt("$addressComponent")) as DefaultMutableTreeNode
            }

        }

        (commentTree.model as DefaultTreeModel).insertNodeInto(node, parentNode, Integer.parseInt(addressArray[addressArray.size-1]))
    }

    fun getCommentsPanel() : JPanel {
        val panel = JPanel(BorderLayout())
        commentTreeRoot = DefaultMutableTreeNode("User comments")
        commentTree = JTree(commentTreeRoot)
        commentTree.cellRenderer = CommentCellRenderer()
        commentTree.getModel().addTreeModelListener(ExpandTreeListener(commentTree))
        val treeScroller = JScrollPane(commentTree)
        treeScroller.verticalScrollBar.unitIncrement = 16
        treeScroller.horizontalScrollBar.unitIncrement = 16
        panel.add(treeScroller, BorderLayout.CENTER)

        //set up custom cell renderer

        return panel
    }

    fun buildMainRightPanel() : JPanel {
        val root = JPanel()
        root.layout = BorderLayout()

        storyControlPanel = JPanel(GridLayout(1, 2))
        storyControlPanel.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)

        val leftPanel = JPanel()
        val rightPanel = JPanel(BorderLayout())

        storyControlPanel.add(leftPanel)
        storyControlPanel.add(rightPanel)

        leftPanel.layout = BoxLayout(leftPanel, BoxLayout.Y_AXIS)
        leftPanel.add(pointsLabel)
        leftPanel.add(commentCountLabel)
        leftPanel.add(userLabel)
        leftPanel.add(storyTimeLabel)

        rightPanel.add(contentToggleButton, BorderLayout.EAST)

        storyPanel = JPanel(BorderLayout())

        root.add(storyControlPanel, BorderLayout.NORTH)

        root.add(storyPanel, BorderLayout.CENTER)

        return root
    }

    //For testing—doesn't download anything
    fun getFrontPageStories2() : ArrayList<JsonObject> {
        val stories = ArrayList<JsonObject>()

        for (i in 0..30) {
            stories.add(getStory2(i).asJsonObject)
            progressBar.value = i
        }

        return stories
    }

    fun getFrontPageStories() : ArrayList<JsonObject> {
        val storyIDs = treeFromURL("https://hacker-news.firebaseio.com/v0/topstories.json")
        val iter = storyIDs.asJsonArray.iterator()
        val stories = ArrayList<JsonObject>()

        var count : Int = 0
        while (iter.hasNext()) {
            val id = (iter.next() as JsonPrimitive).asInt

            val story = getItem(id)

            stories.add(story.asJsonObject)
            count++
            progressBar.value = count

            if (count > 29)
                break
        }

        return stories
    }

    fun getItem(id: Int) : JsonObject {
        val item = treeFromURL("https://hacker-news.firebaseio.com/v0/item/$id.json")

        return item.asJsonObject
    }

    //For testing—doesn't download anything
    fun getStory2(id: Int) : JsonElement {
        val story = treeFromURL2("https://hacker-news.firebaseio.com/v0/item/$id.json")

        return story
    }

    fun treeFromURL(url: String) : JsonElement {
        val url = URL(url)
        val connection = (url.openConnection()) as HttpsURLConnection

        val reader = InputStreamReader(connection?.inputStream)

        val parser = JsonParser()
        val element = parser.parse(reader)

        reader.close()

        return element
    }

    //For testing—doesn't download anything
    fun treeFromURL2(url: String) : JsonElement {


        val parser = JsonParser()
        val element = parser.parse("{\"by\":\"madmork\",\"descendants\":39,\"id\":15111862,\"kids\":[15112163,15112064,15112298,15112289,15112028,15112075,15112092,15112065,15112050,15111894,15111981,15112003,15112149,15112150,15112282],\"score\":62,\"time\":1503857171,\"title\":\"Jumping Ship: Signs It's Time to Quit\",\"type\":\"story\",\"url\":\"https://www.madmork.com/single-post/2017/08/25/Jumping-Ship-7-Signs-its-time-to-quit\"}")


        return element
    }

    class CommentCellRenderer : DefaultTreeCellRenderer() {
        override fun getTreeCellRendererComponent(tree: JTree?, value: Any?, sel: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean): Component? {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)

            val panel = JPanel(BorderLayout())
            val mainTextLabel = JLabel(value.toString())
            mainTextLabel.maximumSize = Dimension(500, 10000)
            mainTextLabel.preferredSize = Dimension(500, this.preferredSize.height)
            panel.add(mainTextLabel, BorderLayout.CENTER)

            panel.preferredSize = Dimension(500, this.preferredSize.height)
            panel.maximumSize = Dimension(500, 10000)
            panel.background = Color(220, 220, 220)

            return panel
        }
//        \
//        {
//
//
//        }
    }

    class ExpandTreeListener(theTree: JTree) : TreeModelListener {

        val tree = theTree

        override fun treeStructureChanged(e: TreeModelEvent?) {
        }

        override fun treeNodesChanged(e: TreeModelEvent?) {
        }

        override fun treeNodesRemoved(e: TreeModelEvent?) {
        }

        override fun treeNodesInserted(e: TreeModelEvent?) {
            if (e != null) {
                this.tree.expandPath(e.treePath)
            }
        }
    }
}

fun main(args: Array<String>) {
    App()
}