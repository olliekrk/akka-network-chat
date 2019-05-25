package chat

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.io.{IO, Tcp}
import akka.util.ByteString
import chat.handlers.ClientHandler.{ChatMessage, ChatNotification}
import chat.handlers.HubHandler


case class CurrentUserName(name: String)

case class InputMessage(message: String)

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
  //  IO(Tcp) ! Connect(remote, timeout = Option(connectionTimeout))
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
            connection ! Write(ByteString("ok im finally connected")) // works!
            context.become(connectedReceive(connection, localAddress))

          case _ =>
            log.info("Unknown 1 ")
        }
      )
    case ChatServer.ClientHub(got_hub) =>
      log.info("got hub")
      hub = got_hub
  }

//  override def receive: Receive = {
//    case c@Tcp.CommandFailed(_: Connect) =>
//      listener ! ChatNotification("Tcp.Connect command has failed")
//      println(c)
//      context.stop(self)
//
//    case Tcp.Connected(`remote`, localAddress) =>
//      val connection = sender()
//
//      // deciding who will receive data from the connection
//      connection ! Register(self)
//
//      log.info(s"Connected successfully to $remote as $localAddress")
//      connection ! Write(ByteString("ok im finally connected")) // works!
//      context.become(connectedReceive(connection, localAddress))
//
//    case _ =>
//      log.info("Unknown")
//  }
//
  def connectedReceive(connection: ActorRef, localAddress: InetSocketAddress): Receive = {
    case CurrentUserName(name) =>
      println("in client: "+ name)
      context.become(chat(name, connection, localAddress))

    case _ =>
      log.info("Unknown")
  }

  def chat(name: String,connection: ActorRef, localAddress: InetSocketAddress): Receive = {
    case ChatMessage(senderName, message) =>
      println(senderName +": "+message)
    case InputMessage(message) =>
      println("got input msg: ", message)
      // todo -> broadcast
      hub ! HubHandler.Broadcast(remote, name, message)
  }


}
