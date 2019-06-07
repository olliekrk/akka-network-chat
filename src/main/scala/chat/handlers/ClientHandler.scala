package chat.handlers

import akka.actor.{Actor, ActorLogging, Props}
import chat.handlers.ClientHandler._
import scalafx.scene.control.TextArea

import scala.collection.mutable

object ClientHandler {

  sealed trait ClientRequest

  case class ChatMessage(senderName: String, message: String, room: String) extends ClientRequest

  case class ChatNotification(message: String) extends ClientRequest

  def props(chatOutputAreas: mutable.Map[String, TextArea]) = Props(new ClientHandler(chatOutputAreas))

}

class ClientHandler(chatOutputAreas: mutable.Map[String, TextArea]) extends Actor with ActorLogging {
  override def receive: Receive = {
    case ChatMessage(name, message, room) =>
      chatOutputAreas(room).appendText(s"Message from $name:\n\t$message")
   // case ChatNotification(message) => chatOutput.appendText(s"Notification:\n\t$message")
    case _ => //chatOutput.appendText("Unknown message to received")
  }
}
