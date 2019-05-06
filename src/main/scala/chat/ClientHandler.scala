package chat

import java.net.InetSocketAddress

import akka.actor.{ActorRef, Props}

//wrzuciłem tylko żeby móc odwołać się w hub'ie

object ClientHandler {
  def props(remote: InetSocketAddress, connection: ActorRef): Props = ???

  sealed trait ClientRequest

  case class ChatMessage(senderName: String, message: String) extends ClientRequest

}
