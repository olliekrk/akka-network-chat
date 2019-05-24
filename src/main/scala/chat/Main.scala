package chat

import java.net.InetSocketAddress

import akka.actor.{ActorSystem, Props}

object Main {
  val hostname = "localhost"
  val server_port = 8888
  val serverAddress = new InetSocketAddress(hostname, server_port)
  val serverActorName = "chat_server"
  val actorSystemName = "chat_system"

  def runAsClient(): Unit = {
    val actorSystem = ActorSystem(actorSystemName)
    implicit def system: ActorSystem = actorSystem

    val userInteraction = actorSystem.actorOf(Props[Interaction], "interaction")
    while (true) {
      userInteraction ! UserMessage(scala.io.StdIn.readLine())
    }
  }

  def runAsServer(): Unit = {
    val actorSystem = ActorSystem(actorSystemName)
    actorSystem.actorOf(ChatServer.props(serverAddress), serverActorName)
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
