package chat

import java.net.InetSocketAddress

import akka.actor.{ActorSystem, Props}
import chat.handlers.ClientHandler

object Main {
  val serverActorName = "chat_server"
  val actorSystemName = "chat_system"
  val actorSystem = ActorSystem(actorSystemName)

  implicit def system: ActorSystem = actorSystem

  def runAsClient(serverAddress: InetSocketAddress): Unit = {
    val clientHandler = actorSystem.actorOf(Props[ClientHandler])
    val client = actorSystem.actorOf(ChatClient.props(serverAddress, clientHandler))

    println("Enter your name: ")
    client ! ChatClient.SetUsername(scala.io.StdIn.readLine())

    while (true)
      client ! ChatClient.UserMessage(scala.io.StdIn.readLine())

  }

  def runAsServer(serverAddress: InetSocketAddress): Unit = {
    actorSystem.actorOf(ChatServer.props(serverAddress), serverActorName)
  }

  def main(args: Array[String]): Unit = try {

    var hostname = "localhost"
    var server_port = 8888

    if (args.length >= 3) {
      hostname = args(1)
      server_port = args(2).toInt
    }

    val serverAddress = new InetSocketAddress(hostname, server_port)

    args match {
      case Array() => runAsClient(serverAddress)
      case Array("client", _*) => runAsClient(serverAddress)
      case Array("server", _*) => runAsServer(serverAddress)
      case _ => sys.exit(1)
    }
  }
  catch {
    case e: InterruptedException =>
      println("Program interrupted by an exception")
      println(e.getMessage)
      sys.exit(1)
    case t: Throwable =>
      println("Program has crashed")
      println(t.getMessage)
      sys.exit(1)
  }
}
