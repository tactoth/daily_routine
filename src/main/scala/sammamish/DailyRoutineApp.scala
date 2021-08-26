package sammamish

import javazoom.jl.player.Player

import java.awt._
import java.awt.event.{ActionEvent, ActionListener, WindowAdapter, WindowEvent}
import java.io.FileInputStream
import java.util.Calendar
import java.util.concurrent.{Executors, TimeUnit}
import javax.swing._
import scala.ref.WeakReference

class DailyRoutineApp(debug: Boolean) extends ActionListener {
  private val planningColor = ColorPair(new Color(0xBF360C), new Color(0xFF5722))
  private val workColor = ColorPair(new Color(0x1A237E), new Color(0x5C6BC0))
  private val breakColor = ColorPair(new Color(0x006064), new Color(0x00BCD4))
  private val relaxColor = ColorPair(new Color(0x1B5E20), new Color(0x43A047))

  private val executor = Executors.newSingleThreadExecutor()
  private val frame: JFrame = new JFrame()
  private val timer: Timer = new Timer(if (debug) 1000 else 15 * 1000, this)

  private val debugStarted = System.currentTimeMillis()
  private val debugTimeScale = if (debug) 500 else 1

  // progressing
  private var currentDayOfYear = -1
  private var currentItems: Seq[Item] = Seq.empty
  private var currentItem: Option[Item] = None
  @volatile private var currentPlayer: Option[WeakReference[Player]] = None


  private def itemPairs = currentItems.zip(currentItems.tail)

  def show() {
    frame.setLayout(new GridBagLayout())

    // add ui
    timer.setRepeats(false)
    maybeAdvanceDayOrItem()

    // show window
    frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE)
    frame.addWindowListener(new WindowAdapter() {
      override def windowClosed(e: WindowEvent) {
        super.windowClosed(e)
        timer.stop()
        closeSound()
        executor.shutdown()
      }
    })


    frame.setAlwaysOnTop(true)
    frame.setVisible(true)

    currentItem.map(_.itemType).foreach(playMusicFor) // play music on launch
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
      val isPast = currentItem.exists(_.time.compareTo(item.time) > 0)

      val colorPair = item.itemType match {
        case ItemType.Planning => planningColor
        case ItemType.Work => workColor
        case ItemType.Relax => relaxColor
        case ItemType.Break => breakColor
      }
      panel.setBackground(if (isActive) colorPair.primary else colorPair.secondary)
      val textColor = if (!isPast) Color.WHITE else Color.LIGHT_GRAY

      // build internals
      def addText(text: String, textSize: Int): Unit = {
        val view = new JLabel(text)
        view.setForeground(textColor)
        view.setFont(new Font("Serif", Font.PLAIN, textSize))
        panel.add(view)
      }

      if (item.itemType != ItemType.Break || isActive) {
        addText(f"${item.time.hour}%02d:${item.time.minute}%02d", if (isActive) 32 else 14)
      }
      addText(item.name.toUpperCase, 14)

      // customize the cell
      val duration = nextItem.time.toMinutes - item.time.toMinutes
      bagConstraints.gridy += 1
      bagConstraints.ipadx = if (isActive) 10 else 5
      bagConstraints.ipady = math.max(0, duration - 30) / 2
      frame.add(panel, bagConstraints)
    }
  }

  override def actionPerformed(maybeEvent: ActionEvent) { // timer event
    maybeAdvanceDayOrItem()
  }

  private def maybeAdvanceDayOrItem() {
    val calendar = getCalendar
    val isItemsUpdated = maybeAdvanceToNewDay(calendar)
    val currentPair = findCurrentPair(calendar)
    val currentItem = currentPair.map(_._1)

    val isItemUpdated: Boolean = {
      if (currentItem == this.currentItem) false
      else {
        this.currentItem = currentItem
        true
      }
    }

    // we've entered a new item
    if (isItemsUpdated || isItemUpdated) {
      populateItems()
      frame.revalidate()
      frame.repaint()

      if (isItemsUpdated) {
        frame.pack()
        val screenSize = Toolkit.getDefaultToolkit.getScreenSize
        frame.setBounds(screenSize.width - frame.getWidth, 0, frame.getWidth, frame.getHeight)
      }
    }

    if (isItemUpdated) {
      playMusicFor(currentItem.map(_.itemType).getOrElse(ItemType.Relax))
    }

    // schedule next update
    val maxDelay = TimeUnit.MILLISECONDS.convert(1, TimeUnit.MINUTES)
    val delay = currentPair match {
      case None => maxDelay // 1min (we are inactive, if we set this to a value too long  when we are back....)
      case Some((_, next)) => // estimate
        val now = calendar.getTimeInMillis
        calendar.set(Calendar.HOUR_OF_DAY, next.time.hour)
        calendar.set(Calendar.MINUTE, next.time.minute)
        (calendar.getTimeInMillis - now)
    }

    // we are good, internally, absolute time is used, see timerQueue().addTimer, javax.swing.TimerQueue.DelayedTimer.getDelay
    timer.setInitialDelay(math.min(delay / debugTimeScale, maxDelay).toInt)
    timer.start()
  }

  private def closeSound(): Unit = {
    for (playerRef <- currentPlayer) {
      val maybePlayer = playerRef.get
      for (player <- maybePlayer) {
        player.close()
      }
    }
  }

  private def playMusicFor(itemType: ItemType.Value): Unit = {
    closeSound()

    // sounds downloaded from https://www.zapsplat.com/sound-effect-category/church-bells/
    val musicFile = itemType match {
      case ItemType.Break => "data/zapsplat_musical_retro_classic_vibraphone_mystery_tone_002_45104.mp3" // break
      case ItemType.Relax => "data/zapsplat_musical_heavely_euphoria_happy_dreamy_swell_001_1860950.mp3" // relax or end of work
      case _ /*ItemType.Planning || ItemType.Work */ => "data/audeption_church_bell_with_street_and_some_birds_010.mp3" // work
    }

    executor.execute(new Runnable {
      override def run(): Unit = {
        val player = new Player(new FileInputStream(musicFile))
        currentPlayer = Some(new WeakReference[Player](player))
        player.play()
        player.close()
      }
    })
  }

  private def findCurrentPair(calendar: Calendar) = {
    val now = SimpleTime(
      calendar.get(Calendar.HOUR_OF_DAY),
      calendar.get(Calendar.MINUTE)
    )
    itemPairs.find { case (first, second) => first.time.compareTo(now) <= 0 && second.time.compareTo(now) > 0 }
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
    val Planning, Work, Break, Relax = Value
  }

  private def maybeAdvanceToNewDay(calendar: Calendar): Boolean = {
    val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
    if (dayOfYear == currentDayOfYear) {
      return false
    }

    frame.setTitle(s"${calendar.get(Calendar.DAY_OF_MONTH)}/${calendar.get(Calendar.MONTH) + 1}")
    currentDayOfYear = dayOfYear
    currentItems = buildItems(calendar)
    true
  }

  private def buildItems(calendar: Calendar) = {
    val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
    val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
    val isBeginOfMonth = dayOfMonth == 1
    val isEndOfMonth = dayOfMonth == calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val isBeginOfWeek = dayOfWeek == 2
    val isEndOfWeek = dayOfWeek == 1 // sunday is first

    // morning
    val morningATypical = Seq(
      Item(SimpleTime(9, 30), "Plan the day", ItemType.Planning),
      Item(SimpleTime(9, 55), "Short break", ItemType.Break),
      Item(SimpleTime(10, 0), "Focused block A", ItemType.Work)
    )

    val morningB = Seq(
      Item(SimpleTime(11, 15), "Stretch break", ItemType.Break),
      Item(SimpleTime(11, 30), "Focused block B", ItemType.Work),
      Item(SimpleTime(12, 45), "Lunch break", ItemType.Relax)
    )

    // afternoon
    val afternoon = Seq(
      Item(SimpleTime(14, 0), "Focused block C", ItemType.Work),
      Item(SimpleTime(15, 15), "Break", ItemType.Break),
      Item(SimpleTime(15, 30), "Focused block D", ItemType.Work),
      Item(SimpleTime(16, 45), "Exercise Break", ItemType.Break),
      Item(SimpleTime(17, 0), "Focused block E", ItemType.Work),
      Item(SimpleTime(18, 0), "Family time", ItemType.Relax)
    )

    // evening
    val eveningTypical = Seq(
      Item(SimpleTime(19, 30), "Evening work time 1", ItemType.Work),
      Item(SimpleTime(20, 45), "Break", ItemType.Break),
      Item(SimpleTime(21, 0), "Evening closing time", ItemType.Work)
    )

    val endItem = Item(SimpleTime(21, 30), "END", ItemType.Relax)

    (if (!isBeginOfMonth && !isBeginOfWeek)
      morningATypical
    else
      Seq(morningATypical
        .head
        .copy(name = if (isBeginOfMonth) "Begin of month" else "Begin of week", itemType = ItemType.Planning))) ++
      morningB ++ afternoon ++
      (if (!isEndOfMonth && !isEndOfWeek) eveningTypical
      else
        Seq(eveningTypical.head.copy(name = if (isEndOfMonth) "End of Month" else "End of Week", itemType = ItemType.Planning))) :+
      endItem sortBy (_.time)
  }


  private def getCalendar = {
    if (debug) {
      val calendar = Calendar.getInstance()
      val fakeCal = Calendar.getInstance()
      fakeCal.set(2021, 7, 31, 9, 30)
      calendar.setTimeInMillis(fakeCal.getTimeInMillis + (System.currentTimeMillis() - debugStarted) * debugTimeScale)
      calendar
    } else {
      Calendar.getInstance()
    }
  }

}

object DailyRoutineApp {
  def main(args: Array[String]): Unit = {
    new DailyRoutineApp(args.contains("-d")).show()
  }
}