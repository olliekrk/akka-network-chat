package chat

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.io.{IO, Tcp}
import akka.util.ByteString
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
    case c@Tcp.CommandFailed(_: Connect) =>
      listener ! ChatNotification("Tcp.Connect command has failed")
      println(c)
      context.stop(self)

    case Tcp.Connected(`remote`, localAddress) =>
      val connection = sender()
      connection ! Register(self)
      context.become(connectedReceive(connection, localAddress))
      log.info(s"Connected successfully to $remote as $localAddress")

    case _ =>
      log.info("Unknown")
  }

  def connectedReceive(connection: ActorRef, localAddress: InetSocketAddress): Receive = {
    case ByteString =>
      log.info("ByteString")

    case c@CommandFailed(_) =>
      log.info("CommandFailed")
      println(c)

    case Received(_) =>

      log.info("Received")

    case c: Tcp.ConnectionClosed =>
      println("right here")
      log.info(c.getErrorCause)

    case _ =>
      log.info("Unknown")
  }
}
