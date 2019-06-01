package chat

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.io.Tcp._
import akka.io.{IO, Tcp}
import chat.handlers.HubHandler

object ChatServer {

  //provide IP address and port number
  def props(address: InetSocketAddress): Props =
    Props(new ChatServer(address))

}

class ChatServer(address: InetSocketAddress) extends Actor with ActorLogging {

  import context.system

  val hub: ActorRef = context.actorOf(Props[HubHandler])

  IO(Tcp) ! Bind(self, address)

  /*
    The manager replies either with a Tcp.CommandFailed
    or the actor handling the listen socket
    replies with a Tcp.Bound message
   */
  override def receive: Receive = {
    case b@Bound(actualAddress) =>
      log.info("Chat server has started at address: " + actualAddress.toString)
      context.parent ! b

    case CommandFailed(_: Bind) =>
      log.warning("Received fail message. Shutting down the chat server.")
      context stop self

    /*
      In order to activate the new connection a Register message
      must be sent to the connection actor, informing that one
      about who shall receive data from the socket.
    */
    case Connected(remote, local) =>
      log.info(s"Receiving connection from remote: $remote to the local address: $local")
      hub ! HubHandler.Register(remote, sender())
      log.info("sender registered to default room")
    case _ =>
      log.info("Server has received unknown message!")

  }
}
