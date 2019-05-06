package chat

import akka.actor.{Actor, Props}

class ChatServer extends Actor {

  import akka.io.Tcp._

  override def receive: Receive = {
    case b@Bound(localAddress) =>
      context.parent ! b

    case CommandFailed(_: Bind) => context.stop(self)

    case c@Connected(remote, local) =>
      val handler = context.actorOf(Props[Chat])
      val connection = sender()
      connection ! Register(handler)
  }
}
