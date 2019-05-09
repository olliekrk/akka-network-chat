package chat

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.io.{IO, Tcp}
import akka.util.ByteString
import java.net.InetSocketAddress

import chat.handlers.ClientHandler.ChatNotification

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

  override def receive: Receive = {
    case Tcp.CommandFailed(_: Connect) =>
      listener ! ChatNotification("Tcp.Connect command has failed")
      context.stop(self)

    case Tcp.Connected(`remote`, _) =>
      listener ! ChatNotification(s"Connected successfully to $remote")
      val connection = sender()
      connection ! Register(self)
      context.become(connectedReceive(connection))

    case _ =>
      log.info("Unknown")
  }

  def connectedReceive(connection: ActorRef): Receive = {
    case ByteString =>
      log.info("ByteString")

    case c@CommandFailed(_) =>
      log.info("CommandFailed")
      println(c)

    case Received(_) =>
      log.info("Received")

    case c: Tcp.ConnectionClosed =>
      log.info(c.getErrorCause)

    case _ =>
      log.info("Unknown")
  }
}
