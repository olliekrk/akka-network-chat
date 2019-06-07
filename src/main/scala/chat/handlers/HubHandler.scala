package chat.handlers

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.io.Tcp
import akka.io.Tcp.{Received, Write}
import akka.util.ByteString
import chat.ServerMain._
import chat.Message
import chat.handlers.ClientHandler._
import chat.handlers.HubHandler.{Broadcast, CreateRoom, JoinRoom}

import scala.collection.mutable
import scala.util.{Failure, Success}

object HubHandler {

  sealed trait HubRequest

  case class Register(remote: InetSocketAddress, connection: ActorRef) extends HubRequest

  case class Broadcast(senderAddress: InetSocketAddress, senderName: String, message: String, roomName: String) extends HubRequest

  case class Unregister(senderAddress: InetSocketAddress) extends HubRequest

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

  // initialize default room
  chatRoomsClients += ("default_room" -> mutable.LinkedHashSet[InetSocketAddress]())

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
      chatRoomsClients("default_room").add(remoteAddress)
      log.info(s"New chat client has been registered: $remoteAddress")

    case HubHandler.Unregister(senderAddress) =>
      activeConnections -= senderAddress
      clientsChatRooms -= senderAddress
      chatRoomsClients.foreach {
        case (_, roomMembers) => roomMembers remove senderAddress
      }
      log.info(s"Chat client has been unregistered: $senderAddress")

    case HubHandler.Broadcast(senderAddress, senderName, message, roomName) =>
      //log.info(s"Broadcasting message from $senderAddress ($senderName)") //TODO: Inet fix
      log.info(s"Broadcasting message from $senderName") //alternatively
    val users = chatRoomsClients(roomName).toList
      println("users: ")
      for (u <- users) {
        println("user" + u)
        val message_request = new Message.MessageRequest(Message.OtherClientMessage)
        message_request("room") = roomName
        message_request("sender") = senderName
        message_request("message") = message
        message_request.serializeByteString match {
          case Success(value) =>
            activeConnections(u) ! Write(value)
          case Failure(exception) =>
            log.info("FAILED")
            throw exception
        }
        //        activeConnections(u) ! Write(ByteString("sender:" + senderName + " message: " + message))
      }
    //      activeConnections.foreach {
    //        case (addr, _) if addr.equals(senderAddress) => //do not send back to the sender?
    //        case (_, connection) => connection ! Write(ByteString("sender:" + senderName + " message: " + message))
    //      }


    case HubHandler.CreateRoom(senderAddress, roomName) =>
      //if such room already exists
      if (chatRoomsClients.keySet contains roomName) {
        activeConnections(senderAddress) ! ChatNotification(s"Chat room with name '$roomName' already exists")
      }

      //otherwise create new room with that name and add sender as a member of it
      else {
        println("ROOM " + roomName + " added")
        clientsChatRooms(senderAddress) += roomName
        chatRoomsClients += (roomName -> mutable.LinkedHashSet[InetSocketAddress](senderAddress))
        activeConnections(senderAddress) ! ChatNotification(s"Chat room with name '$roomName' created")
        log.info(s"Chat room with name '$roomName' has been created")
      }

    case HubHandler.JoinRoom(senderAddress, roomName) =>
      //if such room does not exist

      println("JOOOOOINNNNN")
      if (!chatRoomsClients.keySet.contains(roomName)) {
        activeConnections(senderAddress) ! ChatNotification(s"Chat room with name '$roomName' does not exist")
      }

      //if the sender is already a member of such room
//      else if (clientsChatRooms(senderAddress) contains roomName) {
//        activeConnections(senderAddress) ! ChatNotification(s"You are already a member of chat room '$roomName'")
//      }

      //otherwise let sender join that room
      else {
        clientsChatRooms(senderAddress) += roomName
        println("adding : " + senderAddress + " to: " + roomName)
        chatRoomsClients(roomName).add(senderAddress)
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
            case `senderAddress` => connection ! ChatMessage("YOU", message, roomName)
            case _ => connection ! ChatMessage(senderName, message, roomName)
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
      Message.MessageRequest.deserializeByteString(data) match {
        case Success(value) =>
          value.request match {
            case Message.ClientMessage =>
              val msg = value("message").asInstanceOf[String]
              val name = value("name").asInstanceOf[String]
              val roomName = value("room").asInstanceOf[String]
              if (!(clientNames contains sender())) {
                clientNames(sender()) = name
              }
              println("BROADCASTING IN : " + roomName)
              self ! Broadcast(new InetSocketAddress("somewhere", 0), name, msg, roomName) //TODO: same Inet fix
            case Message.CreateRoom =>
              val roomName = value("room").asInstanceOf[String]

              for ((key, value) <- activeConnections) {
                if (sender() == value) {
                  self ! CreateRoom(key, roomName)
                }
              }
            case Message.JoinRoom =>
              println("JOINING")
              val roomName = value("room").asInstanceOf[String]
              for ((key, value) <- activeConnections) {
                if (sender() == value) {
                  self ! JoinRoom(key, roomName)
                }
              }

            case _ =>
              println("Deserialization has succeeded, but message content is unknown")
          }

        case Failure(exception) =>
          println("Deserialization has failed")
          throw exception
      }

    case _ =>
      log.info("Dude, some weird sh*t happened...")
  }
}
