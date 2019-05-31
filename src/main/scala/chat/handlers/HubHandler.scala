package chat.handlers

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.io.Tcp
import akka.io.Tcp.{Received, Write}
import akka.util.ByteString
import chat.Main._
import chat.handlers.ClientHandler._
import chat.handlers.HubHandler.Broadcast
import chat.Message
import scala.util.{Failure, Success}
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

  // after registration we have active addresses of clients in hub,
  // but to prevent from receiving your own messages I am gonna hold  actor ref -> name  map
  // and send messages only to other clients
  var clientNames = mutable.Map.empty[ActorRef, String]
  val activeConnections: mutable.Map[InetSocketAddress, ActorRef] =
    mutable.Map.empty[InetSocketAddress, ActorRef]

  val chatRoomsClients: mutable.Map[String, mutable.Set[InetSocketAddress]] =
    mutable.HashMap.empty[String, mutable.Set[InetSocketAddress]]

  val clientsChatRooms: mutable.Map[InetSocketAddress, mutable.Set[String]] =
    mutable.HashMap.empty[InetSocketAddress, mutable.Set[String]]

  override def receive: Receive = {
    case HubHandler.Register(remoteAddress, connection) =>
      log.info(s"Trying to register new client: $remoteAddress")

      // deciding who will be handling data incoming from new connection
      connection ! Tcp.Register(self)
      println("register in hub: " + connection)

      activeConnections += (remoteAddress -> connection)
      clientsChatRooms += (remoteAddress -> new mutable.LinkedHashSet[String]())
      log.info(s"New chat client has been registered: $remoteAddress")

    case HubHandler.Unregister(senderAddress) =>
      activeConnections -= senderAddress
      clientsChatRooms -= senderAddress
      chatRoomsClients.foreach {
        case (_, roomMembers) => roomMembers remove senderAddress
      }
      log.info(s"Chat client has been unregistered: $senderAddress")

    case HubHandler.Broadcast(senderAddress, senderName, message) =>
      println("BROADCASTING")
      //      log.info(s"Broadcasting message from $senderAddress ($senderName)")
      activeConnections.foreach {
        case (_, connection) =>

          if ((clientNames contains connection) && clientNames(connection).equals(senderName)) {
            println("avoid " + connection + "name: " + senderName)
          } else {
            connection ! Write(ByteString("sender:" + senderName + " message: " + message))
          }

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

    case Received(data) =>
      println("sender: " + sender())
      Message.MessageRequest.deserializeByteString(data) match {
        case Success(value) =>
          value.request match {
            case Message.ClientMessage =>
              val msg = value("message").asInstanceOf[String]
              val name = value("name").asInstanceOf[String]
              if (clientNames contains sender()) {
                println("contains sender()")
              } else {
                clientNames(sender()) = name
                //                println("added: " + clientNames(sender()))
              }
              //              val address = value("connection")
              //inetsocket probably useless
              self ! Broadcast(new InetSocketAddress(hostname, server_port), name, msg)
            case _ =>
              println("sth else")
          }

        case Failure(exception) =>
          println("FAIL :<")
          throw exception

      }


    case _ =>
      log.info("Unknown")
  }
}
