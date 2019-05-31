package chat
import akka.actor.{Actor, ActorRef}
case class UserMessage(string: String)

class Interaction  extends Actor {

  import context.system

  val client: ActorRef = system.actorOf(ChatClient.props(Main.serverAddress, self), "client")

  client ! ChatClient.UserStartConnect

  override def receive: Receive = {
    case UserMessage(name) =>
      println(self)
      if (name != null) {
        client ! ChatClient.CurrentUserName(name)
        context.become({
          case UserMessage(message) =>
            client ! UserMessage(message)
        })
      } else {
        println("Wrong name!")
        print("Enter your name again: ")
        self ! UserMessage(scala.io.StdIn.readLine())
      }
  }
}

