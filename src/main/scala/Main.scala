import java.net.InetSocketAddress

import akka.actor.{ActorSystem, Props}
import chat.ChatServer

object Main {
  val hostname = "localhost"
  val server_port = 8888

  def runAsClient(): Unit = println("todo: Run as client")

  def runAsServer(): Unit = {
    val actorSystem = ActorSystem("chat_system")
    val serverAddress = new InetSocketAddress(hostname, server_port)
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
