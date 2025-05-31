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
  
  // Get number of forks from command line args or default to 5
  val numForks = if (args.length > 0) args(0).toInt else {
    print("Enter number of forks: ")
    StdIn.readInt()
  }
  
  // Create forks dynamically
  val forks = (0 until numForks).map { i =>
    as.actorOf(Props(new fork(s"Fork $i")), s"Fork$i")
  }

  println(s"Fork cluster started on port 2553")
  println(s"Forks available: ${(0 until numForks).map(i => s"Fork$i").mkString(", ")}")
  println("Press Enter to stop the fork cluster...")

  StdIn.readLine()
  as.terminate()
}