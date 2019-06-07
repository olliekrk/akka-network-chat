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

  case object UserConnect extends ChatClientCommand

  case object UserDisconnect extends ChatClientCommand

  def props(remote: InetSocketAddress, listener: ActorRef) =
    Props(new ChatClient(remote, listener))
}

class ChatClient(remote: InetSocketAddress, listenerGUI: ActorRef) extends Actor with ActorLogging {

  import ChatClient._
  import akka.io.Tcp._
  import context.system

  import scala.concurrent.duration._

  val connectionTimeout: FiniteDuration = 30.seconds
  IO(Tcp) ! Connect(remote, timeout = Some(connectionTimeout))

  override def receive: Receive = {

    case c@Tcp.CommandFailed(_: Connect) =>
      listenerGUI ! ChatNotification("Tcp.Connect command has failed")
      println(c)
      context.stop(self)

    case Tcp.Connected(`remote`, localAddress) =>
      val connection = sender()
      // deciding who will receive data from the connection
      connection ! Register(self)

      log.info(s"Connected successfully to $remote as $localAddress")
      context.become(signingIn(connection, localAddress))

    case other => // send back till context changes
      self ! other
  }

  def signingIn(connection: ActorRef, localAddress: InetSocketAddress): Receive = {
    case SetUsername(name) =>
      println(s"Client name has been set to:\t $name")
      context.become(chatting(name, connection, localAddress))
    case _ =>
      log.info("Unknown message received while signing in...")
  }

  def chatting(name: String, connection: ActorRef, localAddress: InetSocketAddress): Receive = {
    // sends messages from input to all clients in default hub
    case UserMessage(message, room) =>
      println("IN CC: " + message + room)
      val message_request = new Message.MessageRequest(Message.ClientMessage)
      message_request("name") = name
      message_request("message") = message
      message_request("room") = room
      message_request.serializeByteString match {
        case Success(value) =>
          println("SENT!")
          connection ! Write(value)
        case Failure(exception) =>
          log.info("FAILED")
          throw exception
      }
    case CreateNewRoom(roomName) =>
      val message_request = new Message.MessageRequest(Message.CreateRoom)
      message_request("room") = roomName
      message_request.serializeByteString match {
        case Success(value) =>
          connection ! Write(value)
        case Failure(exception) =>
          log.info("FAILED")
          throw exception
      }

    case JoinNewRoom(roomName) =>
      val message_request = new Message.MessageRequest(Message.JoinRoom)
      message_request("room") = roomName
      message_request.serializeByteString match {
        case Success(value) =>
          connection ! Write(value)
        case Failure(exception) =>
          log.info("FAILED")
          throw exception
      }

    case LeaveRoom(roomName) =>
      val message_request = new Message.MessageRequest(Message.LeaveRoom)
      message_request("room") = roomName
      message_request("name") = name
      message_request.serializeByteString match {
        case Success(value) =>
          connection ! Write(value)
        case Failure(exception) =>
          log.info("FAILED")
          throw exception
      }

    case Received(data) =>
      Message.MessageRequest.deserializeByteString(data) match {
        case Success(value) =>
          value.request match {
            case Message.OtherClientMessage =>
              val msg = value("message").asInstanceOf[String]
              val sender = value("sender").asInstanceOf[String]
              val roomName = value("room").asInstanceOf[String]

              if (sender == name) {
                listenerGUI ! ClientGUIHandler.ChatMessage("YOU", msg, roomName)
              } else {
                listenerGUI ! ClientGUIHandler.ChatMessage(sender, msg, roomName)
              }
            case Message.Notification =>
              val message = value("message").asInstanceOf[String]
              listenerGUI ! ClientGUIHandler.ChatNotification(message)
            case _ =>
              log.info("WEIRD THING ???")

          }
        case Failure(_) =>
          log.info("Deserialization has failed")
      }

  }


}

