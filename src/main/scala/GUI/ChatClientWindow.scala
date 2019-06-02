package GUI

import java.net.InetSocketAddress

import akka.actor.{ActorRef, ActorSystem}
import chat.ChatClient
import chat.handlers.ClientHandler
import scalafx.Includes._
import scalafx.application.JFXApp
import scalafx.application.JFXApp.PrimaryStage
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.Scene
import scalafx.scene.control.{Button, TextArea, TextField}
import scalafx.scene.input.{KeyCode, KeyEvent}
import scalafx.scene.layout.{BorderPane, HBox, VBox}
import scalafx.scene.paint.Color._
import scalafx.scene.text.Text

object ChatClientWindow extends JFXApp {

  val actorSystemName = "chat_system"

  val actorSystem = ActorSystem(actorSystemName)

  implicit def system: ActorSystem = actorSystem

  val chatOutputArea: TextArea = new TextArea {
    editable = false
    focusTraversable = false
  }

  val chatInputField: TextField = new TextField {
    text.set("")
    onKeyPressed = (a: KeyEvent) => a.code match {
      case KeyCode.Enter =>
        val message = text() + "\n"
        text.set("")
        sendMessage(message)
      case _ =>
    }
  }

  val buttonsBar: HBox = new HBox {
    spacing = 20
    padding = Insets(10)
    alignment = Pos.Center
    children = Seq(
      new Button {
        text = "Private Chat"
      },
      new Button {
        text = "Create Room"
      },
      new Button {
        text = "Join Room"
      },
      new Button {
        text = "Leave Room"
      })
  }

  stage = new PrimaryStage {
    title = "Akka Network Chat"
    scene = new Scene(900, 600) {
      root = new VBox {
        spacing = 33
        padding = Insets(30)
        alignment = Pos.Center
        style = "-fx-background-color: #2F99C2;"

        children = Seq(
          new Text {
            text = "Hello Chat!"
            style = "-fx-font: bold 32pt sans-serif"
            fill = White
          },

          new BorderPane {
            top = buttonsBar
            center = chatOutputArea
            bottom = chatInputField
          })
      }
    }

  }

  val loginDialog = LoginDialog(stage)
  loginDialog.initializeDialog()

  // CLIENT LOGIC, PERHAPS THIS SHOULD BE EXTRACTED ELSEWHERE

  val serverAddress = new InetSocketAddress(loginDialog.hostname, loginDialog.port)
  val clientHandler: ActorRef = actorSystem.actorOf(ClientHandler.props(chatOutputArea))
  val client: ActorRef = actorSystem.actorOf(ChatClient.props(serverAddress, clientHandler))

  client ! ChatClient.SetUsername(loginDialog.username)

  def sendMessage(message: String): Unit = {
    client ! ChatClient.UserMessage(message)
  }
}