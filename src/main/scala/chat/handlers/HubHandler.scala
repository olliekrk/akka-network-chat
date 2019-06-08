package chat.handlers

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.io.Tcp
import akka.io.Tcp.{PeerClosed, Received, Write}
import chat.Message
import chat.Message.MessageRequest
import chat.handlers.ClientGUIHandler._
import chat.handlers.HubHandler.{Broadcast, CreateRoom, JoinRoom, LeaveRoom}

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

  val defaultRoomName: String = "Public"

}

class HubHandler extends Actor with ActorLogging {

  import context.system

  // after registration we have active addresses of clients in hub,
  // but to prevent from receiving your own messages I am gonna hold  actor ref -> name  map
  // and send messages only to other clients
  var clientNames = mutable.Map.empty[ActorRef, String]

  val activeConnections: mutable.Map[InetSocketAddress, ActorRef] =
    mutable.Map.empty[InetSocketAddress, ActorRef]

  val chatRoomsClients: mutable.Map[String, mutable.Set[InetSocketAddress]] =
    mutable.HashMap.empty[String, mutable.Set[InetSocketAddress]]

  // initialize default room
  chatRoomsClients += (HubHandler.defaultRoomName -> mutable.LinkedHashSet[InetSocketAddress]())

  val clientsChatRooms: mutable.Map[InetSocketAddress, mutable.Set[String]] =
    mutable.HashMap.empty[InetSocketAddress, mutable.Set[String]]

  def serializeAndWrite(request: MessageRequest, connectionActor: ActorRef): Unit = {
    request.serializeByteString match {
      case Success(serializedRequest) => connectionActor ! Write(serializedRequest)
      case Failure(e) => log.info(s"Message serialization has failed with: ${e.toString}")
    }
  }

  override def receive: Receive = {
    case HubHandler.Register(remoteAddress, connection) =>
      log.info(s"Trying to register new client: $remoteAddress")

      // deciding who will be handling data incoming from new connection
      connection ! Tcp.Register(self)

      activeConnections += (remoteAddress -> connection)
      clientsChatRooms += (remoteAddress -> new mutable.LinkedHashSet[String]())
      chatRoomsClients(HubHandler.defaultRoomName).add(remoteAddress)
      log.info(s"New chat client has been registered: $remoteAddress")

    case HubHandler.Unregister(senderAddress) =>
      activeConnections -= senderAddress
      clientsChatRooms -= senderAddress
      chatRoomsClients.foreach { case (_, roomMembers) => roomMembers remove senderAddress }
      log.info(s"Chat client has been unregistered: $senderAddress")

    case HubHandler.Broadcast(_, senderName, message, roomName) =>
      val message_request = new Message.MessageRequest(Message.OtherClientMessage)
      log.info(s"Broadcasting message from $senderName") // alternatively

      message_request("room") = roomName
      message_request("sender") = senderName
      message_request("message") = message

      message_request.serializeByteString match {
        case Success(value) =>
          chatRoomsClients(roomName).foreach(addr => activeConnections(addr) ! Write(value))
        case Failure(exception) =>
          log.info("Message serialization has failed")
          throw exception
      }

    case HubHandler.CreateRoom(senderAddress, roomName) =>
      //if such room already exists
      if (chatRoomsClients.keySet contains roomName) {

        // TODO: NOTIFICATION EXAMPLE
        log.warning("Attempt to create another room with the same name")
        val requestMap = Map("message" -> s"Chat room with name '$roomName' can be create")
        val request = Message.prepareRequest(Message.Notification, requestMap)
        serializeAndWrite(request, activeConnections(senderAddress))
      }

      //otherwise create new room with that name and add sender as a member of it
      else {
        clientsChatRooms(senderAddress) += roomName
        chatRoomsClients += (roomName -> mutable.LinkedHashSet[InetSocketAddress](senderAddress))
        activeConnections(senderAddress) ! ChatNotification(s"Chat room with name '$roomName' created")
        val requestMap = Map("room" -> roomName)
        println("lol: "+ requestMap("room"))
        val request = Message.prepareRequest(Message.AcceptCreateRoom, requestMap)
        serializeAndWrite(request, activeConnections(senderAddress))
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
        chatRoomsClients(roomName).add(senderAddress)
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
            case Message.LeaveRoom =>
              val name = value("name").asInstanceOf[String]
              val roomName = value("room").asInstanceOf[String]
              for ((key, value) <- activeConnections) {
                if (sender() == value) {
                  self ! LeaveRoom(key, name, roomName)
                }
              }
            case _ =>
              println("Deserialization has succeeded, but message content is unknown")
          }

        case Failure(exception) =>
          println("Deserialization has failed")
          throw exception
      }

    case PeerClosed =>
      log.info("Client has been disconnected")

    case _ =>
      log.info("Dude, some weird sh*t happened...")
  }

}
