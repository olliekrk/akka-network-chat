package chat

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.io.{IO, Tcp}
import chat.handlers.ClientHandler.ChatNotification

import scala.util.{Failure, Success}


object ChatClient {

  sealed trait ChatClientCommand

  case class SetUsername(name: String) extends ChatClientCommand

  case class UserMessage(message: String) extends ChatClientCommand

  case object UserConnect extends ChatClientCommand

  case object UserDisconnect extends ChatClientCommand

  def props(remote: InetSocketAddress, listener: ActorRef) =
    Props(new ChatClient(remote, listener))
}

class ChatClient(remote: InetSocketAddress, listener: ActorRef) extends Actor with ActorLogging {

  import ChatClient._
  import akka.io.Tcp._
  import context.system

  import scala.concurrent.duration._

  val connectionTimeout: FiniteDuration = 30.seconds
  IO(Tcp) ! Connect(remote, timeout = Some(connectionTimeout))

  override def receive: Receive = {

    case c@Tcp.CommandFailed(_: Connect) =>
      listener ! ChatNotification("Tcp.Connect command has failed")
      println(c)
      context.stop(self)

    case Tcp.Connected(`remote`, localAddress) =>
      val connection = sender()
      // deciding who will receive data from the connection
      connection ! Register(self)

      log.info(s"Connected successfully to $remote as $localAddress")
      context.become(signingIn(connection, localAddress))

    case _ =>
      log.info("Client has received unknown message!")

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
    case UserMessage(message) =>
      val message_request = new Message.MessageRequest(Message.ClientMessage)
      message_request("name") = name
      message_request("message") = message

      message_request.serializeByteString match {
        case Success(value) =>
          connection ! Write(value)
        case Failure(exception) =>
          log.info("FAILED")
          throw exception
      }

    case Received(data) => //TODO: make use of case classes in ClientHandler + deserialization
      //listener ! something from data
      println(data.decodeString("US-ASCII"))
  }


}
