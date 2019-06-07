package GUI

import java.net.InetSocketAddress

import akka.actor.{ActorRef, ActorSystem}
import chat.ChatClient
import chat.handlers.ClientHandler
import javafx.event.EventHandler
import scalafx.Includes._
import scalafx.application.JFXApp
import scalafx.application.JFXApp.PrimaryStage
import scalafx.event.ActionEvent
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.Scene
import scalafx.scene.control.{Button, Tab, TabPane, TextArea, TextField, TextInputDialog}
import scalafx.scene.input.{KeyCode, KeyEvent}
import scalafx.scene.layout.{BorderPane, HBox, VBox}
import scalafx.scene.paint.Color._
import scalafx.scene.text.Text

import scala.collection.mutable

object ChatClientWindow extends JFXApp {


  val actorSystemName = "chat_system"

  val actorSystem = ActorSystem(actorSystemName)

  implicit def system: ActorSystem = actorSystem

  val loginDialog = LoginDialog(stage)
  loginDialog.initializeDialog()

  val activeRoomsOutput: mutable.Map[String, TextArea] = mutable.Map.empty[String, TextArea]
  val activeRoomsInput: mutable.Map[String, TextField] = mutable.Map.empty[String, TextField]
  val serverAddress = new InetSocketAddress(loginDialog.hostname, loginDialog.port)
  val clientHandler: ActorRef = actorSystem.actorOf(ClientHandler.props(activeRoomsOutput))
  val client: ActorRef = actorSystem.actorOf(ChatClient.props(serverAddress, clientHandler))


  val chatOutputArea: TextArea = new TextArea {
    editable = false
    focusTraversable = false
    style = "-fx-font: bold 10pt sans-serif; -fx-background-color: #B6AFAF;"
  }

  val chatInputField: TextField = new TextField {
    text.set("")
    onKeyPressed = (a: KeyEvent) => a.code match {
      case KeyCode.Enter =>
        val message = text() + "\n"
        text.set("")
        sendMessage(message, "default_room")
      case _ =>
    }
  }

  val tabPane = new TabPane

  val defaultTab = new Tab
  defaultTab.text = "default_room"
  defaultTab.onClosed = handle(stopApp())
  tabPane.tabs = List(defaultTab)
  // to distinguish rooms of current active client


  activeRoomsOutput += ("default_room" -> chatOutputArea)
  activeRoomsInput += ("default_room" -> chatInputField)
  val mainChat: VBox = new VBox {
    padding = Insets(5)
    alignment = Pos.TopCenter
    children = Seq(chatOutputArea, chatInputField)
  }
  defaultTab.content = mainChat

  val createRoomButton: Button = new Button {
    text = "Create Room"
    style = "-fx-background-color: #AE9797;"

  }
  createRoomButton.onAction = (e: ActionEvent) => {
    val rnd = new scala.util.Random
    val randomValue = rnd.nextInt(15)
    val dialog = new TextInputDialog(defaultValue = "new_room" + randomValue) {
      initOwner(stage)
      title = "Create room"
      headerText = "You can create your own room."
      contentText = "Please enter its name:"
    }

    val result = dialog.showAndWait()

    result match {
      case Some(name) =>
        /* todo->  wait for result whether room can be added*/
        client ! ChatClient.CreateNewRoom(name)
        val newTab = new Tab
        newTab.text = name

        val newTextArea = new TextArea {
          editable = false
          focusTraversable = false
          style = "-fx-font: bold 10pt sans-serif; -fx-background-color: #B6AFAF;"
        }
        val newTextField = new TextField {
          text.set("")
          onKeyPressed = (a: KeyEvent) => a.code match {
            case KeyCode.Enter =>
              val message = text() + "\n"
              text.set("")
              sendMessage(message, name)
            case _ =>
          }
        }
        val tabChat: VBox = new VBox {
          padding = Insets(5)
          alignment = Pos.TopCenter
          children = Seq(newTextArea, newTextField)
        }
        newTab.content = tabChat
        activeRoomsOutput += (name -> newTextArea)
        activeRoomsInput += (name -> newTextField)
        tabPane.tabs += newTab
        println(name)
      case None => println("Dialog was canceled.")
    }
  }

  val joinRoomButton: Button = new Button {
    text = "Join Room"
    style = "-fx-background-color: #AE9797;"
  }

  joinRoomButton.onAction = (e: ActionEvent) => {

    val dialog = new TextInputDialog() {
      initOwner(stage)
      title = "Join room"
      headerText = "You can join existing room."
      contentText = "Please enter its name:"
    }

    val result = dialog.showAndWait()

    result match {
      case Some(name) =>
        // todo -> wait for response,
        client ! ChatClient.JoinNewRoom(name)
        val newTab = new Tab
        newTab.text = name
        newTab.onClosed = handle(
          client ! ChatClient.LeaveRoom(name)
        )
        val newTextArea = new TextArea {
          editable = false
          focusTraversable = false
          style = "-fx-font: bold 10pt sans-serif; -fx-background-color: #B6AFAF;"
        }
        val newTextField = new TextField {
          text.set("")
          onKeyPressed = (a: KeyEvent) => a.code match {
            case KeyCode.Enter =>
              val message = text() + "\n"
              text.set("")
              sendMessage(message, name)
            case _ =>
          }
        }
        val tabChat: VBox = new VBox {
          padding = Insets(5)
          alignment = Pos.TopCenter
          children = Seq(newTextArea, newTextField)
        }
        newTab.content = tabChat
        activeRoomsOutput += (name -> newTextArea)
        activeRoomsInput += (name -> newTextField)
        tabPane.tabs += newTab
        println(name)
      case None => println("Dialog was canceled.")
    }
  }


  //  val leaveRoomButton: Button = new Button {
  //    text = "Leave Room"
  //  }


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
    scene = new Scene(900, 600) {
      root = new VBox {
        spacing = 33
        padding = Insets(30)
        alignment = Pos.Center
        // style = "-fx-background-color: #2F99C2;"
        style = "-fx-background-color: #223162;"

        children = Seq(
          new Text {
            text = "Hello Chat!"
            style = "-fx-font: bold 32pt sans-serif"
            fill = White

          }, borderPane,
          new Text{
            text = "logged as: " + loginDialog.username
            style = "-fx-font: italic 16pt sans-serif"
            fill = White
          }

        )
      }
    }

  }

  client ! ChatClient.SetUsername(loginDialog.username)

  def sendMessage(message: String, room: String): Unit = {
    println("Sending: " + message + " to room: " + room)
    client ! ChatClient.UserMessage(message, room)
  }

}