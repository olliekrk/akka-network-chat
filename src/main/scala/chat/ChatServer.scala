package chat

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.io.Tcp._
import akka.io.{IO, Tcp}
import chat.handlers.HubHandler

object ChatServer {

  //provide IP address and port number to listen on
  def props(address: InetSocketAddress): Props =
    Props(new ChatServer(address))

}

class ChatServer(address: InetSocketAddress) extends Actor with ActorLogging {

  import context.system

  val hub: ActorRef = context.actorOf(Props[HubHandler])

  IO(Tcp) ! Bind(self, address)

  override def receive: Receive = {
    case b@Bound(actualAddress) =>
      log.info("Chat server has started at address: " + actualAddress.toString)
      context.parent ! b

    case CommandFailed(_: Bind) =>
      log.warning("Received fail message. Shutting down the chat server.")
      context stop self

    case Connected(remote, local) =>
      log.info(s"Receiving connection from remote: $remote to the local address: $local")
      hub ! HubHandler.Register(remote, sender()) // Handler will send Tcp.Register after successful registration
      log.info("New client will be assigned to default room")

    case _ =>
      log.info("Server has received unknown message!")

  }
}
