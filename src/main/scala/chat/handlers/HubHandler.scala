package chat.handlers

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.io.Tcp
import akka.io.Tcp.{PeerClosed, Received, Write}
import chat.{MessageRequest, RequestSerialization}

import scala.collection.mutable
import scala.util.{Failure, Success}

object HubHandler {

  sealed trait HubRequest

  case class Register(remote: InetSocketAddress, connection: ActorRef) extends HubRequest

  case class Unregister(senderAddress: InetSocketAddress) extends HubRequest

  val defaultRoomName: String = "Public"

}

class HubHandler extends Actor with ActorLogging with RequestSerialization {

  import context.system

  var clientNames = mutable.Map.empty[ActorRef, String]

  val activeConnections: mutable.Map[InetSocketAddress, ActorRef] = mutable.Map.empty[InetSocketAddress, ActorRef]

  val chatRoomsClients: mutable.Map[String, mutable.Set[InetSocketAddress]] = mutable.HashMap.empty[String, mutable.Set[InetSocketAddress]]

  val clientsChatRooms: mutable.Map[InetSocketAddress, mutable.Set[String]] = mutable.HashMap.empty[InetSocketAddress, mutable.Set[String]]

  // initialize default room
  chatRoomsClients += (HubHandler.defaultRoomName -> mutable.LinkedHashSet[InetSocketAddress]())

  override def handleDeserializedRequest(connectionActor: ActorRef, messageRequest: MessageRequest): Unit = {
    messageRequest.request match {
      case MessageRequest.ClientMessage =>
        val msg = messageRequest("message").asInstanceOf[String]
        val name = messageRequest("name").asInstanceOf[String]
        val roomName = messageRequest("room").asInstanceOf[String]
        //        clientNames(connectionActor) = name
        broadcastRoom(name, msg, roomName)

      case MessageRequest.CreateRoom =>
        val roomName = messageRequest("room").asInstanceOf[String]
        for ((key, value) <- activeConnections)
          if (connectionActor == value)
            createRoom(key, roomName)

      case MessageRequest.JoinRoom =>
        val roomName = messageRequest("room").asInstanceOf[String]
        for ((key, value) <- activeConnections)
          if (connectionActor == value)
            doJoinRoom(key, roomName)

      case MessageRequest.LeaveRoom =>
        val name = messageRequest("name").asInstanceOf[String]
        val roomName = messageRequest("room").asInstanceOf[String]
        for ((key, value) <- activeConnections)
          if (connectionActor == value)
            doLeaveRoom(key, name, roomName)

      case MessageRequest.Unregister =>
        for ((address, actor) <- activeConnections)
          if (connectionActor == actor)
            unregisterClient(address)
      case MessageRequest.SetUserName =>
        val name = messageRequest("name").asInstanceOf[String]
        for ((key, value) <- activeConnections)
          if (connectionActor == value)
            verifyName(connectionActor, key, name)
      case _ =>
        log.info("Deserialization has succeeded, but message content is unknown")
    }
  }

  override def serializeAndWrite(request: MessageRequest, connectionActor: ActorRef): Unit = {
    request.serializeByteString match {
      case Success(serializedRequest) => connectionActor ! Write(serializedRequest)
      case Failure(e) => log.info(s"Message serialization has failed with: ${e.toString}")
    }
  }

  def serializeAndWriteRoom(request: MessageRequest, roomName: String): Unit = {
    request.serializeByteString match {
      case Success(serializedRequest) => chatRoomsClients(roomName).foreach({ addr => activeConnections(addr) ! Write(serializedRequest) })
      case Failure(e) => log.info(s"Message serialization has failed with: ${e.toString}")
    }
  }

  def broadcastRoom(senderName: String, message: String, roomName: String): Unit = {
    log.info(s"Broadcasting message from $senderName in '$roomName'")

    val requestMap = Map("room" -> roomName, "sender" -> senderName, "message" -> message)
    val request = MessageRequest.prepareRequest(MessageRequest.OtherClientMessage, requestMap)
    serializeAndWriteRoom(request, roomName)
  }

  def verifyName(actor: ActorRef, senderAddress: InetSocketAddress, name: String): Unit = {
    if (clientNames.values.exists(_ == name)) {
      val requestMap = Map("message" -> s"Your name: $name is already used, try again")
      val request = MessageRequest.prepareRequest(MessageRequest.UsedName, requestMap)
      serializeAndWrite(request, activeConnections(senderAddress))
    } else {
      clientNames(actor) = name
    }
  }

  def createRoom(senderAddress: InetSocketAddress, roomName: String): Unit = {
    if (chatRoomsClients.keySet contains roomName) {
      log.warning(s"Attempt to create room '$roomName' which already exist")

      val requestMap = Map("message" -> s"Chat room with name '$roomName' already exists")
      val request = MessageRequest.prepareRequest(MessageRequest.ChatNotification, requestMap)
      serializeAndWrite(request, activeConnections(senderAddress))
    }
    else {
      //otherwise create new room with that name and add sender as a member of it
      clientsChatRooms(senderAddress) += roomName
      chatRoomsClients += (roomName -> mutable.LinkedHashSet[InetSocketAddress](senderAddress))
      log.info(s"Chat room with name '$roomName' has been created")

      val requestMap = Map("room" -> roomName)
      val request = MessageRequest.prepareRequest(MessageRequest.AcceptCreateRoom, requestMap)
      serializeAndWrite(request, activeConnections(senderAddress))
    }
  }

  def doJoinRoom(senderAddress: InetSocketAddress, roomName: String): Unit = {
    //if such room does not exist
    if (!chatRoomsClients.keySet.contains(roomName)) {
      val requestMap = Map("message" -> s"Chat room with name '$roomName' does not exist")
      val request = MessageRequest.prepareRequest(MessageRequest.ChatNotification, requestMap)
      serializeAndWrite(request, activeConnections(senderAddress))
    }

    //if the sender is already a member of such room
    else if (clientsChatRooms(senderAddress) contains roomName) {
      val requestMap = Map("message" -> s"You are already member of: '$roomName' ")
      val request = MessageRequest.prepareRequest(MessageRequest.ChatNotification, requestMap)
      serializeAndWrite(request, activeConnections(senderAddress))
    }

    //otherwise let sender join that room
    else {
      clientsChatRooms(senderAddress) += roomName
      chatRoomsClients(roomName).add(senderAddress)
      log.info(s"Client $senderAddress has joined the room '$roomName'")

      val requestMap = Map("room" -> roomName)
      val request = MessageRequest.prepareRequest(MessageRequest.AcceptJoinRoom, requestMap)
      serializeAndWrite(request, activeConnections(senderAddress))

      val name = clientNames(activeConnections(senderAddress))
      val notifyMap = Map("room" -> roomName, "message" -> s"$name has joined the room")
      val notifyRequest = MessageRequest.prepareRequest(MessageRequest.RoomNotification, notifyMap)
      serializeAndWriteRoom(notifyRequest, roomName)
    }
  }

  def doLeaveRoom(senderAddress: InetSocketAddress, senderName: String, roomName: String): Unit = {
    //if such room does not exist
    if (!chatRoomsClients.keySet.contains(roomName))
      log.warning(s"Attempt to leave room $roomName which doesn't exist")

    //if sender is not a member of a chat room
    else if (!chatRoomsClients(roomName).contains(senderAddress))
      log.warning(s"Attempt to leave room $roomName by not a room member")

    //otherwise leave the room
    else {
      chatRoomsClients(roomName) -= senderAddress
      clientsChatRooms(senderAddress) -= roomName
      log.info(s"$senderName has left '$roomName'")

      val requestMap = Map("room" -> roomName, "message" -> s"$senderName has left the room")
      val request = MessageRequest.prepareRequest(MessageRequest.RoomNotification, requestMap)
      serializeAndWriteRoom(request, roomName)

      //if there are no members left delete that room
      if (chatRoomsClients(roomName).isEmpty) chatRoomsClients -= roomName

    }
  }

  def unregisterClient(senderAddress: InetSocketAddress): Unit = {
    val senderName = clientNames(activeConnections(senderAddress))
    clientsChatRooms(senderAddress).foreach { roomName => doLeaveRoom(senderAddress, senderName, roomName) }

    clientNames -= activeConnections(senderAddress)
    activeConnections -= senderAddress
    clientsChatRooms -= senderAddress
    log.info(s"Chat client has been unregistered: $senderAddress")
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


    case Received(data) =>
      MessageRequest.deserializeByteString(data) match {
        case Success(value) => handleDeserializedRequest(sender(), value)
        case Failure(_) => log.info("Message deserialization has failed")
      }

    case PeerClosed =>
      log.info("Client has been disconnected")

    case _ =>
      log.info("Dude, some weird sh*t happened...")
  }
}
