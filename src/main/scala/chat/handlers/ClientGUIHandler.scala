package chat.handlers

import java.text.SimpleDateFormat
import java.util.Calendar

import GUI.ChatClientWindow
import akka.actor.{Actor, ActorLogging, Props}
import chat.handlers.ClientGUIHandler._
import scalafx.application.Platform
import scalafx.scene.control.TextArea

import scala.collection.mutable

object ClientGUIHandler {

  sealed trait ClientRequest

  case class ChatMessage(senderName: String, message: String, room: String) extends ClientRequest

  case class ChatNotification(message: String) extends ClientRequest

  def props(chatOutputAreas: mutable.Map[String, TextArea]) = Props(new ClientGUIHandler(chatOutputAreas))

}

class ClientGUIHandler(chatOutputAreas: mutable.Map[String, TextArea]) extends Actor with ActorLogging {
  override def receive: Receive = {
    case ChatMessage(name, message, room) =>
      val dateTimeFormatter = new SimpleDateFormat("hh:mm  a")
      val dateTime = dateTimeFormatter.format(Calendar.getInstance.getTime)
      chatOutputAreas(room).appendText(s"[$dateTime] Message from $name:\n\t$message")
    case ChatNotification(message) =>
      print(message)
      Platform.runLater(() => ChatClientWindow.warningDialog(message))
    case _ =>
    //      chatOutputAreas(room).appendText("Unknown message received")
  }
}
