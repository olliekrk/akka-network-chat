package chat

import akka.actor.{Actor, ActorRef, Props}
import akka.io.{IO, Tcp}
import akka.util.ByteString
import java.net.InetSocketAddress

object ChatClient {
  def props(remote: InetSocketAddress, replies: ActorRef) =
    Props(new ChatClient(remote, replies))
}

class ChatClient(remote: InetSocketAddress, listener: ActorRef) extends Actor {

  import akka.io.Tcp._
  import context.system

  IO(Tcp) ! Connect(remote)

  override def receive: Receive = {
    case CommandFailed(_: Connect) =>
      context.stop(self)

    case c@Connected(remote, local) =>
      listener ! c
      val connection = sender()
      connection ! Register(self)
      context.become(connectedReceive(connection))
  }

  def connectedReceive(connection: ActorRef): Receive = {
    case data: ByteString =>
      connection ! Write(data)

    case CommandFailed(_: Write) =>
      println("Fail")

    case Received(data) =>
      listener ! data

    case _: ConnectionClosed =>
      context.stop(self)
  }
}
