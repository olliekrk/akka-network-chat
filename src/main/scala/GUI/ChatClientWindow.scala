package GUI

import java.net.InetSocketAddress
import java.util.Calendar

import akka.actor.{ActorRef, ActorSystem}
import chat.ChatClient
import chat.handlers.{ClientGUIHandler, HubHandler}
import scalafx.Includes._
import scalafx.application.JFXApp
import scalafx.application.JFXApp.PrimaryStage
import scalafx.event.ActionEvent
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.Scene
import scalafx.scene.control.Alert.AlertType
import scalafx.scene.control._
import scalafx.scene.input.{KeyCode, KeyEvent}
import scalafx.scene.layout.{BorderPane, HBox, VBox}
import scalafx.scene.paint.Color._
import scalafx.scene.text.Text

import scala.collection.mutable

object GUIStyles {
  val richButtonStyle: String =
    "-fx-background-color:" +
      "#000000," +
      "linear-gradient(#7ebcea, #2f4b8f)," +
      "linear-gradient(#426ab7, #263e75)," +
      "linear-gradient(#395cab, #223768);" +
      "-fx-background-insets: 0,1,2,3;" +
      "-fx-background-radius: 3,2,2,2;" +
      "-fx-padding: 12 30 12 30;" +
      "-fx-text-fill: white;" +
      "-fx-font: bold 12pt sans-serif;"

  val commonTextStyle: String =
    "font-family: Verdana;" +
      "font-size: 11px;" +
      "color: #555555;" +
      "line-height: 1.5;" +
      "letter-spacing: .25px;"

  val tabPaneStyle: String =
    "-fx-background-color:" +
      "-fx-outer-border," +
      "-fx-inner-border," +
      "derive(-fx-color, -20%);" +
      "-fx-effect: " +
      "innershadow(two-pass-box , rgba(0,0,0,0.6) , 4, 0.0 , 0 , 0);"
}

object ChatClientWindow extends JFXApp {

  val actorSystemName = "chat_system"
  val actorSystem = ActorSystem(actorSystemName)

  implicit def system: ActorSystem = actorSystem

  val loginDialog = LoginDialog(stage)

  loginDialog.initializeDialog()

  val chatOutputArea: TextArea = new TextArea {
    editable = false
    focusTraversable = false
    style = GUIStyles.commonTextStyle
  }

  val chatInputField: TextField = new TextField {
    text.set("")
    onKeyPressed = (a: KeyEvent) => a.code match {
      case KeyCode.Enter =>
        val message = text() + "\n"
        text.set("")
        sendMessage(message, HubHandler.defaultRoomName)
      case _ =>
    }
  }

  val mainChat: VBox = new VBox {
    padding = Insets(5)
    alignment = Pos.TopCenter
    children = Seq(chatOutputArea, chatInputField)
  }

  val defaultRoomTab: Tab = new Tab {
    text = HubHandler.defaultRoomName
    content = mainChat
    onClosed = handle(stopApp())
    closable = false
  }

  val tabPane: TabPane = new TabPane {
    tabs = List(defaultRoomTab) // to distinguish rooms of current active client
    style = GUIStyles.tabPaneStyle
  }

  val activeRoomsOutput: mutable.Map[String, TextArea] = mutable.Map.empty[String, TextArea]
  val activeRoomsInput: mutable.Map[String, TextField] = mutable.Map.empty[String, TextField]
  val serverAddress = new InetSocketAddress(loginDialog.hostname, loginDialog.port)
  val clientHandler: ActorRef = actorSystem.actorOf(ClientGUIHandler.props(activeRoomsOutput))
  val client: ActorRef = actorSystem.actorOf(ChatClient.props(serverAddress, clientHandler))

  activeRoomsOutput += (HubHandler.defaultRoomName -> chatOutputArea)

  activeRoomsInput += (HubHandler.defaultRoomName -> chatInputField)

  val createRoomButton: Button = new Button {
    text = "Create Room"
    style = GUIStyles.richButtonStyle
    onAction = (_: ActionEvent) => createRoomAction()
  }

  val joinRoomButton: Button = new Button {
    text = "Join Room"
    style = GUIStyles.richButtonStyle
    onAction = (_: ActionEvent) => joinRoomAction()
  }

  val buttonsBar: HBox = new HBox {
    spacing = 25
    padding = Insets(15)
    alignment = Pos.Center
    children = Seq(createRoomButton, joinRoomButton)
  }

  val borderPane: BorderPane = new BorderPane {
    top = buttonsBar
    center = tabPane
  }

  stage = new PrimaryStage {
    title = "Akka Network Chat"
    minWidth = 900
    minHeight = 600
    scene = new Scene(900, 600) {
      root = new VBox {
        spacing = 33
        padding = Insets(30)
        alignment = Pos.Center
        style = "-fx-background-color: #223162;"

        children = Seq(
          new Text {
            text = "Hello Chat!"
            style = "-fx-font: bold 32pt sans-serif"
            fill = White

          }, borderPane,
          new Text {
            text = s"Logged as: ${loginDialog.username}\nLogin time: ${Calendar.getInstance().getTime.toString}"
            style = "-fx-font: italic 11pt sans-serif"
            fill = White
          })
      }
    }
  }

  client ! ChatClient.SetUsername(loginDialog.username)

  def sendMessage(message: String, room: String): Unit = {
    client ! ChatClient.UserMessage(message, room)
  }

  def createRoomAction(): Unit = {
    val rnd = new scala.util.Random
    val randomValue = rnd.nextInt(15)
    val dialog = new TextInputDialog(defaultValue = "new_room" + randomValue) {
      initOwner(stage)
      title = "Create room"
      headerText = "You can create your own room."
      contentText = "Please enter its name:"
    }

    dialog.showAndWait() match {
      case Some(name) => client ! ChatClient.CreateNewRoom(name)
      case None => println("Room creation dialog was canceled")
    }
  }

  def joinRoomAction(): Unit = {
    val roomJoinDialog = new TextInputDialog() {
      initOwner(stage)
      title = "Join room"
      headerText = "You can join existing room."
      contentText = "Please enter its name:"
    }

    roomJoinDialog.showAndWait() match {
      case Some(name) => client ! ChatClient.JoinNewRoom(name)
      case None => println("Room join dialog was canceled")
    }
  }

  def warningDialog(message: String): Unit = {
    new Alert(AlertType.Warning) {
      initOwner(stage)
      title = "Warning"
      headerText = "Your request is invalid"
      contentText = message
    }.showAndWait()
  }

  def addTab(room: String): Unit = {

    val newTextArea = new TextArea {
      editable = false
      focusTraversable = false
      style = GUIStyles.commonTextStyle
    }

    val newTextField = new TextField {
      text.set("")
      onKeyPressed = (a: KeyEvent) => a.code match {
        case KeyCode.Enter =>
          val message = text() + "\n"
          text.set("")
          sendMessage(message, room)
        case _ =>
      }
    }

    val tabChat: VBox = new VBox {
      padding = Insets(5)
      alignment = Pos.TopCenter
      children = Seq(newTextArea, newTextField)
    }

    val newTab = new Tab {
      text = room
      content = tabChat
      onClosed = handle(client ! ChatClient.LeaveRoom(room))
    }

    activeRoomsOutput += (room -> newTextArea)
    activeRoomsInput += (room -> newTextField)
    tabPane.tabs += newTab
  }
}