package chat

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, Props}
import akka.io.Tcp._
import akka.io.{IO, Tcp}

object ChatServer {
  //provide IP address and port number
  def props(address: InetSocketAddress): Props =
    Props(new ChatServer(address))

  //by default chose random port on localhost
  def props(): Props =
    Props(new ChatServer(new InetSocketAddress("localhost", 0)))
}

class ChatServer(address: InetSocketAddress) extends Actor with ActorLogging {

  import context.system

  private val hub = context.actorOf(Props[HubHandler])

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
    case c@Connected(remote, local) =>
      log.info(s"Successfully connected remote host: $remote to the local address: $local")
      hub ! HubHandler.Register(remote, sender())
  }
}
