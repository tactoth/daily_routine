package sammamish

import javazoom.jl.player.Player

import java.awt._
import java.awt.event.{ActionEvent, ActionListener, WindowAdapter, WindowEvent}
import java.io.FileInputStream
import java.util.Calendar
import java.util.concurrent.Executors
import javax.swing._

object Main extends ActionListener {
  private val workColor = ColorPair(new Color(0x1A237E), new Color(0x5C6BC0))
  private val breakColor = ColorPair(new Color(0x006064), new Color(0x00BCD4))
  private val relaxColor = ColorPair(new Color(0x1B5E20), new Color(0x43A047))

  private val frame: JFrame = new JFrame()
  private val timer: Timer = new Timer(5 * 1000, this)

  private val soundExecutor = Executors.newSingleThreadExecutor()

  // progressing
  private var currentItem: Option[Item] = None

  private val items = Seq(
    // morning
    Item(SimpleTime(9, 30), "Plan the day", ItemType.Work),
    Item(SimpleTime(9, 55), "Short break", ItemType.Break),
    Item(SimpleTime(10, 0), "Focused block A", ItemType.Work),
    Item(SimpleTime(11, 15), "Stretch break", ItemType.Break),
    Item(SimpleTime(11, 30), "Focused block B", ItemType.Work),
    Item(SimpleTime(12, 45), "Lunch break", ItemType.Relax),

    // afternoon
    Item(SimpleTime(14, 0), "Focused block C", ItemType.Work),
    Item(SimpleTime(15, 15), "Break", ItemType.Break),
    Item(SimpleTime(15, 30), "Focused block D", ItemType.Work),
    Item(SimpleTime(16, 45), "Exercise Break", ItemType.Break),
    Item(SimpleTime(17, 0), "Focused block E", ItemType.Work),
    Item(SimpleTime(18, 0), "Family time", ItemType.Relax),

    // evening
    Item(SimpleTime(19, 30), "Evening work time 1", ItemType.Work),
    Item(SimpleTime(20, 45), "Break", ItemType.Break),
    Item(SimpleTime(21, 0), "Evening closing time", ItemType.Work),

    // end day
    Item(SimpleTime(21, 30), "END", ItemType.Relax)
  ).sortBy(_.time)
  private val itemPairs = items.zip(items.tail)


  def show() {
    val screenSize = Toolkit.getDefaultToolkit.getScreenSize
    val w = 180
    val h = 720
    frame.setBounds(screenSize.width - w, 0, w, h)
    frame.setLayout(new GridBagLayout())

    // add ui
    this.currentItem = findCurrentItem()
    populateItems()

    // set layout
    timer.start()

    // show window
    frame.setTitle("Daily Routine")
    frame.setAlwaysOnTop(true)
    frame.setVisible(true)
    frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE)
    frame.addWindowListener(new WindowAdapter() {
      override def windowClosed(e: WindowEvent) {
        super.windowClosed(e)
        timer.stop()
      }
    })
  }

  private def populateItems() {
    // reset
    frame.getContentPane.removeAll()

    val bagConstraints = new GridBagConstraints()
    bagConstraints.insets = new Insets(1, 0, 1, 0)
    bagConstraints.fill = GridBagConstraints.HORIZONTAL

    for ((item, nextItem) <- itemPairs /* if Range.inclusive(item.time.hour - 4, nextItem.time.hour + 4).contains(now.hour) */ ) {
      // filter out items that's either too early or too late

      val panel = new JPanel()
      panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS))

      val isActive = currentItem.contains(item)
      val colorPair = item.itemType match {
        case ItemType.Work => workColor
        case ItemType.Relax => relaxColor
        case ItemType.Break => breakColor
      }
      panel.setBackground(if (isActive) colorPair.primary else colorPair.secondary)

      val textColor = if (isActive) Color.WHITE else Color.LIGHT_GRAY

      // build internals
      val time = new JLabel(f"${item.time.hour}%02d:${item.time.minute}%02d")
      time.setForeground(textColor)
      panel.add(time)
      time.setFont(new Font("Serif", Font.BOLD, 16))

      val name = new JLabel(item.name.toUpperCase)
      name.setForeground(textColor)
      name.setFont(new Font("Serif", Font.PLAIN, 14))
      panel.add(name)

      // customize the cell
      val duration = nextItem.time.toMinutes - item.time.toMinutes
      bagConstraints.gridy += 1
      bagConstraints.ipadx = if (isActive) 20 else 10
      bagConstraints.ipady = math.max(0, duration - 30) / 2
      frame.add(panel, bagConstraints)
    }
  }

  override def actionPerformed(maybeEvent: ActionEvent) { // timer event
    val currentItem = findCurrentItem()
    if (currentItem == this.currentItem) return

    // we've entered a new item
    this.currentItem = currentItem
    populateItems()
    frame.revalidate()

    {
      // sounds downloaded from https://www.zapsplat.com/sound-effect-category/church-bells/
      val musicFile = currentItem match {
        case Some(item) if item.itemType == ItemType.Break => "data/zapsplat_musical_retro_classic_vibraphone_mystery_tone_002_45104.mp3" // break
        case Some(item) if item.itemType == ItemType.Work => "data/audeption_church_bell_with_street_and_some_birds_010.mp3" // work
        case _ => "data/zapsplat_musical_heavely_euphoria_happy_dreamy_swell_001_1860950.mp3" // relax or end of work
      }
      soundExecutor.submit(new Runnable {
        override def run(): Unit = {
          new Player(new FileInputStream(musicFile)).play()
        }
      })
    }
  }

  private def findCurrentItem(): Option[Item] = {
    val dateOrdering = implicitly[Ordering[SimpleTime]]
    import dateOrdering._

    val now = getNow
    itemPairs.find { case (first, second) => first.time <= now && second.time > now }.map { case (first, _) => first }
  }


  private def getNow = {
    val calendar = Calendar.getInstance()
    SimpleTime(
      calendar.get(Calendar.HOUR_OF_DAY),
      calendar.get(Calendar.MINUTE)
    )
  }

  case class ColorPair(primary: Color, secondary: Color)

  case class Item(time: SimpleTime, name: String, itemType: ItemType.Value)

  case class SimpleTime(hour: Int, minute: Int) extends Comparable[SimpleTime] {
    def toMinutes: Int = hour * 60 + minute

    override def compareTo(other: SimpleTime): Int = {
      val hourDiff = hour.compareTo(other.hour)
      if (hourDiff != 0) hourDiff
      else minute.compareTo(other.minute)
    }
  }

  object ItemType extends Enumeration {
    val Work, Break, Relax = Value
  }

  def main(args: Array[String]): Unit = {
    Main.show()
  }
}