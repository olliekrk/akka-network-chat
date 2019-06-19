package chat

import java.net.InetSocketAddress

import akka.actor.ActorSystem

object ServerMain {
  val serverActorName = "chat_server"
  val actorSystemName = "chat_system"
  val actorSystem = ActorSystem(actorSystemName)

  implicit def system: ActorSystem = actorSystem

  def runAsServer(serverAddress: InetSocketAddress): Unit = {
    actorSystem.actorOf(ChatServer.props(serverAddress), serverActorName)
  }

  def main(args: Array[String]): Unit = try {

    var hostname = "localhost"
    var server_port = 8888

    if (args.length >= 2) {
      hostname = args(0)
      server_port = args(1).toInt
    }

    val serverAddress = new InetSocketAddress(hostname, server_port)
    runAsServer(serverAddress)

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
