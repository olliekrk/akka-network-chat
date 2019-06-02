package chat.handlers

import akka.actor.{Actor, ActorLogging, Props}
import chat.handlers.ClientHandler._
import scalafx.scene.control.TextArea

object ClientHandler {

  sealed trait ClientRequest

  case class ChatMessage(senderName: String, message: String) extends ClientRequest

  case class ChatNotification(message: String) extends ClientRequest

  def props(chatOutputArea: TextArea) = Props(new ClientHandler(chatOutputArea))

}

class ClientHandler(chatOutput: TextArea) extends Actor with ActorLogging {
  override def receive: Receive = {
    case ChatMessage(name, message) => chatOutput.appendText(s"Message from $name:\n\t$message")
    case ChatNotification(message) => chatOutput.appendText(s"Notification:\n\t$message")
    case _ => chatOutput.appendText("Unknown message to received")
  }
}
