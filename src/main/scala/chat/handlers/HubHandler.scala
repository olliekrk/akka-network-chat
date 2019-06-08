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

  var clientNames = mutable.Map.empty[ActorRef, String]

  val activeConnections: mutable.Map[InetSocketAddress, ActorRef] = mutable.Map.empty[InetSocketAddress, ActorRef]

  val chatRoomsClients: mutable.Map[String, mutable.Set[InetSocketAddress]] = mutable.HashMap.empty[String, mutable.Set[InetSocketAddress]]

  val clientsChatRooms: mutable.Map[InetSocketAddress, mutable.Set[String]] = mutable.HashMap.empty[InetSocketAddress, mutable.Set[String]]

  // initialize default room
  chatRoomsClients += (HubHandler.defaultRoomName -> mutable.LinkedHashSet[InetSocketAddress]())

  def serializeAndWrite(request: MessageRequest, connectionActor: ActorRef): Unit = {
    request.serializeByteString match {
      case Success(serializedRequest) => connectionActor ! Write(serializedRequest)
      case Failure(e) => log.info(s"Message serialization has failed with: ${e.toString}")
    }
  }

  def doBroadcast(senderName: String, message: String, roomName: String): Unit = {
    val requestMap = Map("room" -> roomName, "sender" -> senderName, "message" -> message)
    val request = Message.prepareRequest(Message.OtherClientMessage, requestMap)
    log.info(s"Broadcasting message from $senderName")
    chatRoomsClients(roomName).foreach({ addr => serializeAndWrite(request, activeConnections(addr)) })
  }

  def createRoom(senderAddress: InetSocketAddress, roomName: String): Unit = {
    if (chatRoomsClients.keySet contains roomName) {
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
      val request = Message.prepareRequest(Message.AcceptCreateRoom, requestMap)
      serializeAndWrite(request, activeConnections(senderAddress))
      log.info(s"Chat room with name '$roomName' has been created")
    }
  }

  def doJoinRoom(senderAddress: InetSocketAddress, roomName: String): Unit = {
    //if such room does not exist
    if (!chatRoomsClients.keySet.contains(roomName)) {
      activeConnections(senderAddress) ! ChatNotification(s"Chat room with name '$roomName' does not exist")
      val requestMap = Map("message" -> s"Chat room with name '$roomName' does not exist")
      val request = Message.prepareRequest(Message.Notification, requestMap)
      serializeAndWrite(request, activeConnections(senderAddress))
    }

    //if the sender is already a member of such room
    else if (clientsChatRooms(senderAddress) contains roomName) {
      activeConnections(senderAddress) ! ChatNotification(s"You are already a member of chat room '$roomName'")
      val requestMap = Map("message" -> s"You are already member of: '$roomName' ")
      val request = Message.prepareRequest(Message.Notification, requestMap)
      serializeAndWrite(request, activeConnections(senderAddress))
    }

    //otherwise let sender join that room
    else {
      clientsChatRooms(senderAddress) += roomName
      chatRoomsClients(roomName).add(senderAddress)
      log.info(s"Client $senderAddress has joined the room '$roomName'")
      val requestMap = Map("room" -> roomName)
      val request = Message.prepareRequest(Message.AcceptJoinRoom, requestMap)
      serializeAndWrite(request, activeConnections(senderAddress))
    }
  }

  def doLeaveRoom(senderAddress: InetSocketAddress, senderName: String, roomName: String): Unit ={
    //if such room does not exist
    if (!chatRoomsClients.keySet.contains(roomName))
      activeConnections(senderAddress) ! ChatNotification(s"Chat room with name '$roomName' does not exist")

    //if sender is not a member of a chat room
    else if (!chatRoomsClients(roomName).contains(senderAddress))
      activeConnections(senderAddress) ! ChatNotification(s"You are not a member of room '$roomName'")

    //otherwise leave the room
    else {
      chatRoomsClients(roomName) -= senderAddress
      clientsChatRooms(senderAddress) -= roomName
      activeConnections(senderAddress) ! ChatNotification(s"You have left the room '$roomName'")
      chatRoomsClients(roomName).foreach { address =>
        activeConnections(address) ! ChatNotification(s"$senderName has left the room")
      }

      //if there are no members left delete that room
      if (chatRoomsClients(roomName).isEmpty) {
        chatRoomsClients -= roomName
      }
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
              doBroadcast(name, msg, roomName)
            case Message.CreateRoom =>
              val roomName = value("room").asInstanceOf[String]
              for ((key, value) <- activeConnections) {
                if (sender() == value) {
                  createRoom(key, roomName)
                }
              }
            case Message.JoinRoom =>
              val roomName = value("room").asInstanceOf[String]
              for ((key, value) <- activeConnections) {
                if (sender() == value) {
                  doJoinRoom(key, roomName)
                }
              }
            case Message.LeaveRoom =>
              val name = value("name").asInstanceOf[String]
              val roomName = value("room").asInstanceOf[String]
              for ((key, value) <- activeConnections) {
                if (sender() == value) {
                  doLeaveRoom(key, name, roomName)
                }
              }
            case _ =>
              log.info("Deserialization has succeeded, but message content is unknown")
          }

        case Failure(exception) =>
          log.info("Deserialization has failed")
          throw exception
      }

    case PeerClosed =>
      log.info("Client has been disconnected")

    case _ =>
      log.info("Dude, some weird sh*t happened...")
  }

}
