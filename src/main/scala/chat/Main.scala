package chat

import java.net.InetSocketAddress

import akka.actor.{ActorSystem, Props}

object Main {
  var hostname = "localhost"
  var server_port = 8888
  val serverAddress = new InetSocketAddress(hostname, server_port)
  val serverActorName = "chat_server"
  val actorSystemName = "chat_system"
  val actorSystem = ActorSystem(actorSystemName)
  implicit def system: ActorSystem = actorSystem

  def runAsClient(): Unit = {
//    val actorSystem = ActorSystem(actorSystemName)


    val userInteraction = actorSystem.actorOf(Props[Interaction], "interaction")
    println("Enter your name: ")
    while (true) {
      val msg = scala.io.StdIn.readLine()
      userInteraction ! UserMessage(msg)
    }
  }

  def runAsServer(args: Array[String]): Unit = {
    if (args.length  == 3){
      hostname = args(1)
      server_port = args(2).toInt
    }
    actorSystem.actorOf(ChatServer.props(serverAddress), serverActorName)
  }

  def main(args: Array[String]): Unit = try {
    if (args.length == 0 || args(0) == "client")
      runAsClient()
    else if (args(0) == "server")
      runAsServer(args)
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
