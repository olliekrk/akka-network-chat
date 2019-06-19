package chat

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.io.{IO, Tcp}
import chat.handlers.ClientGUIHandler
import chat.handlers.ClientGUIHandler.ChatNotification

import scala.util.{Failure, Success}


object ChatClient {

  sealed trait ChatClientCommand

  case class SetUsername(name: String) extends ChatClientCommand

  case class UserMessage(message: String, room: String) extends ChatClientCommand

  case class CreateNewRoom(roomName: String) extends ChatClientCommand

  case class JoinNewRoom(roomName: String) extends ChatClientCommand

  case class LeaveRoom(roomName: String) extends ChatClientCommand

  case object UserUnregister extends ChatClientCommand

  def props(remote: InetSocketAddress, listener: ActorRef) =
    Props(new ChatClient(remote, listener))
}

class ChatClient(remote: InetSocketAddress, listenerGUI: ActorRef) extends Actor with ActorLogging with RequestSerialization {

  import ChatClient._
  import akka.io.Tcp._
  import context.system
  import scala.concurrent.duration._

  val connectionTimeout: FiniteDuration = 30.seconds
  IO(Tcp) ! Connect(remote, timeout = Some(connectionTimeout))

  var clientName: Option[String] = None
  var serverAddress: Option[ActorRef] = None

  override def receive: Receive = {
    case Tcp.CommandFailed(_: Connect) =>
      listenerGUI ! ChatNotification("Tcp.Connect command has failed")
      context.stop(self)

    case Tcp.Connected(`remote`, localAddress) =>
      val connection = sender()
      connection ! Register(self)
      serverAddress = Some(connection)

      log.info(s"Connected successfully to $remote as $localAddress")
      context.become(signingIn(connection, localAddress))
    case other => // send back till context change
      self ! other
  }

  override def serializeAndWrite(request: MessageRequest, connectionActor: ActorRef): Unit = {
    request.serializeByteString match {
      case Success(serializedRequest) => connectionActor ! Write(serializedRequest)
      case Failure(e) => log.info(s"Message serialization has failed with: ${e.toString}")
    }
  }

  override def handleDeserializedRequest(connectionActor: ActorRef, messageRequest: MessageRequest): Unit = {
    messageRequest.request match {
      case MessageRequest.OtherClientMessage =>
        val msg = messageRequest("message").asInstanceOf[String]
        val sender = messageRequest("sender").asInstanceOf[String]
        val roomName = messageRequest("room").asInstanceOf[String]

        if (sender == clientName.getOrElse(""))
          listenerGUI ! ClientGUIHandler.ChatMessage(sender, msg, roomName)
        else
          listenerGUI ! ClientGUIHandler.ChatMessage(sender, msg, roomName)

      case MessageRequest.ChatNotification =>
        val message = messageRequest("message").asInstanceOf[String]
        listenerGUI ! ClientGUIHandler.ChatNotification(message)

      case MessageRequest.UsedName =>
        val message = messageRequest("message").asInstanceOf[String]
        listenerGUI ! ClientGUIHandler.NameInUse(message)

      case MessageRequest.AcceptCreateRoom =>
        val roomName = messageRequest("room").asInstanceOf[String]
        listenerGUI ! ClientGUIHandler.AcceptNewRoom(roomName)

      case MessageRequest.AcceptJoinRoom =>
        val roomName = messageRequest("room").asInstanceOf[String]
        listenerGUI ! ClientGUIHandler.AcceptNewRoom(roomName)

      case MessageRequest.RoomNotification =>
        val roomName = messageRequest("room").asInstanceOf[String]
        val message = messageRequest("message").asInstanceOf[String]
        listenerGUI ! ClientGUIHandler.RoomNotification(message, roomName)

      case _ =>
        log.info("Unknown message received and deserialized")

    }
  }

  def signingIn(connection: ActorRef, localAddress: InetSocketAddress): Receive = {
    case SetUsername(name) =>
      log.info(s"Client name has been set to:\t $name")
      clientName = Some(name)
      context.become(chatting(name, connection, localAddress))
      val nameRequest = new MessageRequest(MessageRequest.SetUserName)
      nameRequest("name") = name
      serializeAndWrite(nameRequest, connection)
    case _ =>
      log.info("Unknown message received while signing in...")
  }

  def chatting(name: String, connection: ActorRef, localAddress: InetSocketAddress): Receive = {
    case UserMessage(message, room) =>
      val message_request = new MessageRequest(MessageRequest.ClientMessage)
      message_request("name") = name
      message_request("message") = message
      message_request("room") = room
      serializeAndWrite(message_request, connection)

    case CreateNewRoom(roomName) =>
      val message_request = new MessageRequest(MessageRequest.CreateRoom)
      message_request("room") = roomName
      serializeAndWrite(message_request, connection)

    case JoinNewRoom(roomName) =>
      val message_request = new MessageRequest(MessageRequest.JoinRoom)
      message_request("room") = roomName
      serializeAndWrite(message_request, connection)

    case LeaveRoom(roomName) =>
      val message_request = new MessageRequest(MessageRequest.LeaveRoom)
      message_request("room") = roomName
      message_request("name") = name
      serializeAndWrite(message_request, connection)

    case UserUnregister =>
      val request = new MessageRequest(MessageRequest.Unregister)
      serializeAndWrite(request, connection)

    case Received(data) =>
      MessageRequest.deserializeByteString(data) match {
        case Success(value) => handleDeserializedRequest(sender(), value)
        case Failure(_) => log.info("Message deserialization has failed")
      }


    case _ => log.warning("Received unknown request while chatting")
  }

}

