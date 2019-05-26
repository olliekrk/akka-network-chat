package chat

import java.net.InetSocketAddress

import scala.util.Try
import scala.util.{Failure, Success}
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.io.{IO, Tcp}
import akka.util.ByteString
import chat.handlers.ClientHandler.{ChatMessage, ChatNotification}
import chat.Message
import chat.Message._
import chat.handlers.HubHandler


case class CurrentUserName(name: String)


case class UserStartConnect()

case class UserDisconnect()

object ChatClient {
  def props(remote: InetSocketAddress, listener: ActorRef) =
    Props(new ChatClient(remote, listener))
}

class ChatClient(remote: InetSocketAddress, listener: ActorRef) extends Actor with ActorLogging {

  import akka.io.Tcp._
  import context.system
  import scala.concurrent.duration._


  val connectionTimeout: FiniteDuration = 30.seconds
  IO(Tcp) ! Connect(remote, timeout = Option(connectionTimeout))
  var hub: ActorRef = _

  override def receive: Receive = {
    case UserStartConnect() =>
      IO(Tcp) ! Connect(remote, timeout = Some(connectionTimeout))
      println(self)
      context.become(
        {
          case c@Tcp.CommandFailed(_: Connect) =>
            listener ! ChatNotification("Tcp.Connect command has failed")
            println(c)
            context.stop(self)

          case Tcp.Connected(`remote`, localAddress) =>
            val connection = sender()
            // deciding who will receive data from the connection
            connection ! Register(self)

            log.info(s"Connected successfully to $remote as $localAddress")
            context.become(connectedReceive(connection, localAddress))

          case ChatServer.ClientHub(got_hub) =>
            hub = got_hub
          case _ =>
            log.info("Unknown 1 ")
        }
      )

  }

  def connectedReceive(connection: ActorRef, localAddress: InetSocketAddress): Receive = {
    case CurrentUserName(name) =>
      //log.info("in client: " + name)
      context.become(chat(name, connection, localAddress))

    case _ =>
      log.info("Unknown")
  }

  def chat(name: String, connection: ActorRef, localAddress: InetSocketAddress): Receive = {
    case ChatMessage(senderName, message) =>
      log.info(senderName + ": " + message)

      // sends messages from input to all people in (for a while?) default hub
    case UserMessage(message) =>
      //      log.info("got input msg: ", message)
      println(self)
      val message_request = new Message.MessageRequest(Message.ClientMessage)
      message_request("name") = name
      message_request("message") = message
      println("SERIALIZING!")
      message_request.serializeByteString match {
        case Success(value) =>
          connection ! Write(value)
        case Failure(exception) =>
          println("FAIL :<")
          throw exception
      }


      // getting message from hub
    case Received(data) =>
      println(data.decodeString("US-ASCII"))
  }


}
