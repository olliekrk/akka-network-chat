package chat.handlers

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.io.Tcp
import chat.handlers.ClientHandler._

import scala.collection.mutable

object HubHandler {

  sealed trait HubRequest

  case class Register(remote: InetSocketAddress, connection: ActorRef) extends HubRequest

  case class Broadcast(senderAddress: InetSocketAddress, senderName: String, message: String) extends HubRequest

  case class Unregister(senderAddress: InetSocketAddress) extends HubRequest

  //todo: other hub requests (i.e. chat rooms related)

  case class CreateRoom(senderAddress: InetSocketAddress, roomName: String) extends HubRequest

  case class JoinRoom(senderAddress: InetSocketAddress, roomName: String) extends HubRequest

  case class TalkRoom(senderAddress: InetSocketAddress, senderName: String, roomName: String, message: String) extends HubRequest

  case class LeaveRoom(senderAddress: InetSocketAddress, senderName: String, roomName: String) extends HubRequest

}

class HubHandler extends Actor with ActorLogging {

  val activeConnections: mutable.Map[InetSocketAddress, ActorRef] =
    mutable.Map.empty[InetSocketAddress, ActorRef]

  val chatRoomsClients: mutable.Map[String, mutable.Set[InetSocketAddress]] =
    mutable.HashMap.empty[String, mutable.Set[InetSocketAddress]]

  val clientsChatRooms: mutable.Map[InetSocketAddress, mutable.Set[String]] =
    mutable.HashMap.empty[InetSocketAddress, mutable.Set[String]]

  override def receive: Receive = {
    case HubHandler.Register(remoteAddress, connection) =>
      log.info(s"New chat client has been registered: $remoteAddress")
      val clientHandler = context.actorOf(ClientHandler.props(remoteAddress, connection))
      connection ! Tcp.Register(clientHandler)
      activeConnections += (remoteAddress -> clientHandler)
      clientsChatRooms += (remoteAddress -> new mutable.LinkedHashSet[String]())

    case HubHandler.Unregister(senderAddress) =>
      activeConnections -= senderAddress
      clientsChatRooms -= senderAddress
      chatRoomsClients.foreach {
        case (_, roomMembers) => roomMembers remove senderAddress
      }
      log.info(s"Chat client has been unregistered: $senderAddress")

    case HubHandler.Broadcast(_, senderName, message) =>
      activeConnections.foreach {
        case (_, connection) => connection ! ChatMessage(senderName, message)
      }

    case HubHandler.CreateRoom(senderAddress, roomName) =>
      //if such room already exists
      if (chatRoomsClients.keySet contains roomName) {
        activeConnections(senderAddress) ! ChatNotification(s"Chat room with name '$roomName' already exists")
      }

      //otherwise create new room with that name and add sender as a member of it
      else {
        clientsChatRooms(senderAddress) += roomName
        chatRoomsClients += (roomName -> mutable.LinkedHashSet[InetSocketAddress](senderAddress))

        activeConnections(senderAddress) ! ChatNotification(s"Chat room with name '$roomName' created")
        log.info(s"Chat room with name '$roomName' has been created")
      }

    case HubHandler.JoinRoom(senderAddress, roomName) =>
      //if such room does not exist
      if (!chatRoomsClients.keySet.contains(roomName)) {
        activeConnections(senderAddress) ! ChatNotification(s"Chat room with name '$roomName' does not exist")
      }

      //if the sender is already a member of such room
      else if (clientsChatRooms(senderAddress) contains roomName) {
        activeConnections(senderAddress) ! ChatNotification(s"You are already a member of chat room '$roomName'")
      }

      //otherwise let sender join that room
      else {
        clientsChatRooms(senderAddress) += roomName

        activeConnections(senderAddress) ! ChatNotification(s"Chat room with name '$roomName' created")
        log.info(s"Client $senderAddress has joined the room '$roomName'")
      }

    case HubHandler.TalkRoom(senderAddress, senderName, roomName, message) =>
      //if such room does not exist
      if (!chatRoomsClients.keySet.contains(roomName))
        activeConnections(senderAddress) ! ChatNotification(s"Chat room with name '$roomName' does not exist")

      //if sender is not a member of a chat room
      else if (!chatRoomsClients(roomName).contains(senderAddress))
        activeConnections(senderAddress) ! ChatNotification(s"You are not a member of room '$roomName'")

      //otherwise send the message to every room member
      else
        chatRoomsClients(roomName).foreach { address: InetSocketAddress =>
          val connection = activeConnections(address)
          address match {
            case `senderAddress` => connection ! ChatMessage("YOU", message)
            case _ => connection ! ChatMessage(senderName, message)
          }
        }

    case HubHandler.LeaveRoom(senderAddress, senderName, roomName) =>
      //if such room does not exist
      if (!chatRoomsClients.keySet.contains(roomName))
        activeConnections(senderAddress) ! ChatNotification(s"Chat room with name '$roomName' does not exist")

      //if sender is not a member of a chat room
      else if (!chatRoomsClients(roomName).contains(senderAddress))
        activeConnections(senderAddress) ! ChatNotification(s"You are not a member of room '$roomName'")

      //otherwise leave the room
      else {
        chatRoomsClients(roomName) -= senderAddress

        activeConnections(senderAddress) ! ChatNotification(s"You have left the room '$roomName'")
        chatRoomsClients(roomName).foreach { address =>
          activeConnections(address) ! ChatNotification(s"$senderName has left the room")
        }

        //if there are no members left delete that room
        if (chatRoomsClients(roomName).isEmpty) {
          chatRoomsClients -= roomName
        }
      }

    case _: HubHandler.HubRequest =>
      log.warning("Request handler not yet implemented")
  }
}
