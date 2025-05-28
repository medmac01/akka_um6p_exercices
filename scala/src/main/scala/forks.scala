import scala.io.StdIn
import akka.actor.{Actor, ActorSystem, Props}
import com.typesafe.config.ConfigFactory

class fork(name: String) extends Actor {
  var isAvailable = true
  
  override def receive: Receive = {
    case "Pick" =>
      if (isAvailable) {
        isAvailable = false
        sender() ! "Success"
        println(s"$name picked up")
      } else {
        sender() ! "Busy"
        println(s"$name is busy")
      }
    case "PutDown" =>
      isAvailable = true
      println(s"$name fork put down")
  }
}

object ForkCluster extends App {
  val config = ConfigFactory.load("forks.conf")
  val as = ActorSystem("ForkSystem", config)
  
  // Create five forks
  val fork0 = as.actorOf(Props(new fork("Fork 0")), "Fork0")
  val fork1 = as.actorOf(Props(new fork("Fork 1")), "Fork1")
  val fork2 = as.actorOf(Props(new fork("Fork 2")), "Fork2")
  val fork3 = as.actorOf(Props(new fork("Fork 3")), "Fork3")
  val fork4 = as.actorOf(Props(new fork("Fork 4")), "Fork4")

  println("Fork cluster started on port 2553")
  println("Forks available: Fork0, Fork1, Fork2, Fork3, Fork4")
  println("Press Enter to stop the fork cluster...")

  StdIn.readLine()
  as.terminate()
}