package chat

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.io.Tcp

import scala.collection.mutable

object HubHandler {

  sealed trait HubRequest

  case class Register(remote: InetSocketAddress, connection: ActorRef) extends HubRequest

  case class Broadcast(senderAddress: InetSocketAddress, senderName: String, message: String) extends HubRequest

  case class Unregister(senderAddress: InetSocketAddress) extends HubRequest

  //todo: other hub requests (i.e. chat rooms related)

  case class CreateRoom(senderAddress: InetSocketAddress, roomName: String) extends HubRequest

  case class JoinRoom(senderAddress: InetSocketAddress, roomName: String) extends HubRequest

  case class TalkRoom(senderAddress: InetSocketAddress, senderName: String, message: String) extends HubRequest

}

class HubHandler extends Actor with ActorLogging {

  var activeConnections: mutable.Map[InetSocketAddress, ActorRef] =
    mutable.Map.empty[InetSocketAddress, ActorRef]

  override def receive: Receive = {
    case HubHandler.Register(remote, connection) =>
      log.info(s"New chat client has been registered: $remote")
      val clientHandler = context.actorOf(ClientHandler.props(remote, connection))
      connection ! Tcp.Register(clientHandler)
      activeConnections += (remote -> clientHandler)

    case HubHandler.Unregister(senderAddress) =>
      log.info(s"Chat client has been unregistered: $senderAddress")
      activeConnections -= senderAddress

    case HubHandler.Broadcast(_, senderName, message) =>
      activeConnections.foreach {
        case (_, connection) => connection ! ClientHandler.ChatMessage(senderName, message)
      }

    case _: HubHandler.HubRequest =>
      log.warning("Not yet implemented")
  }
}
