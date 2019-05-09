package chat.handlers

import java.net.InetSocketAddress

import akka.actor.{ActorRef, Props}

object ClientHandler {
  def props(remote: InetSocketAddress, connection: ActorRef): Props = ???

  sealed trait ClientRequest

  case class ChatMessage(senderName: String, message: String) extends ClientRequest

  case class ChatNotification(message: String) extends ClientRequest

}
