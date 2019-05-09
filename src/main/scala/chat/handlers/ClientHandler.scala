package chat.handlers

import akka.actor.{Actor, ActorLogging}
import ClientHandler._

object ClientHandler {

  sealed trait ClientRequest

  case class ChatMessage(senderName: String, message: String) extends ClientRequest

  case class ChatNotification(message: String) extends ClientRequest

}

class ClientHandler extends Actor with ActorLogging {
  override def receive: Receive = {
    case ChatMessage => println("Message")
    case ChatNotification(message) => println("Notification: " + message)
    case _ => println("Unknown")
  }
}
