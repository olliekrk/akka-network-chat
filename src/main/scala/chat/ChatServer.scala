package chat

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.io.Tcp._
import akka.io.{IO, Tcp}
import chat.ChatServer.ClientHub
import chat.handlers.HubHandler

object ChatServer {


  case class ClientHub(hub: ActorRef)

  //provide IP address and port number
  def props(address: InetSocketAddress): Props =
    Props(new ChatServer(address))

  //by default chose random port on localhost
  def props(): Props =
    Props(new ChatServer(new InetSocketAddress("localhost", 0)))
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
      hub ! HubHandler.CreateRoom(remote, "default_room")
      log.info("Created new default room")
      hub ! HubHandler.Register(remote, sender())
      log.info("sender registered to default room")

      context.become({
        case Connected(remote, local) =>
          log.info(s"Receiving connection from remote: $remote to the local address: $local")
          hub ! HubHandler.Register(remote, sender())
          log.info("sender registered to default room")
        case _ =>
          log.info("sth here ? :<")
      })

    case _ =>
      log.info("sth here ? :<")
  }
}
