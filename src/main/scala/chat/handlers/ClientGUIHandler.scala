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

  case class RoomNotification(message: String, room: String) extends ClientRequest

  case class AcceptNewRoom(message: String) extends ClientRequest

  def props(chatOutputAreas: mutable.Map[String, TextArea]) = Props(new ClientGUIHandler(chatOutputAreas))

}

class ClientGUIHandler(chatOutputAreas: mutable.Map[String, TextArea]) extends Actor with ActorLogging {

  val lastMessageAuthors = mutable.HashMap.empty[String, String]

  override def receive: Receive = {
    case ChatMessage(name, message, room) =>
      lastMessageAuthors get room match {
        case Some(author) if author == name =>
          chatOutputAreas(room).appendText(s"\t$message")
        case _ =>
          chatOutputAreas(room).appendText(s"[$getDateTimeStamp] Message from $name:\n\t$message")
          lastMessageAuthors(room) = name
      }

    case ChatNotification(message) =>
      Platform.runLater(() => ChatClientWindow.warningDialog(message))

    case AcceptNewRoom(room) =>
      Platform.runLater(() => ChatClientWindow.addTab(room))

    case RoomNotification(message, room) =>
      chatOutputAreas(room).appendText(s"(!) [$getDateTimeStamp]:\t$message\n")

    case _ =>
  }

  def getDateTimeStamp: String = {
    val dateTimeFormatter = new SimpleDateFormat("hh:mm:ss a")
    val dateTime = dateTimeFormatter.format(Calendar.getInstance.getTime)
    dateTime
  }
}
