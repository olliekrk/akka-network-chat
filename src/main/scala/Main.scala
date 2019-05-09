import java.net.InetSocketAddress

import akka.actor.{ActorSystem, Props}
import chat.handlers.ClientHandler
import chat.{ChatClient, ChatServer}

object Main {
  val hostname = "localhost"
  val server_port = 8888
  val serverAddress = new InetSocketAddress(hostname, server_port)
  val actorSystemName = "chat_system"

  def runAsClient(): Unit = {
    //todo: not sure if this should be there
    val actorSystem = ActorSystem(actorSystemName)
    val clientHandler = actorSystem.actorOf(Props[ClientHandler], "client_handler")
    actorSystem.actorOf(ChatClient.props(serverAddress, clientHandler), "client")
  }

  def runAsServer(): Unit = {
    val actorSystem = ActorSystem(actorSystemName)
    actorSystem.actorOf(ChatServer.props(serverAddress), "chat_server")
  }

  def main(args: Array[String]): Unit = try {
    if (args.length == 0 || args(0) == "client")
      runAsClient()
    else if (args(0) == "server")
      runAsServer()
    else
      sys.exit(1)
  } catch {
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
