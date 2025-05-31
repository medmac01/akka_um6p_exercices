import scala.io.StdIn
import akka.actor.{Actor, ActorSystem, Props}
import com.typesafe.config.ConfigFactory
import akka.cluster.pubsub.{DistributedPubSub, DistributedPubSubMediator}
import com.diningphilosophers._

class fork(id: Int) extends Actor {
  import DistributedPubSubMediator.Publish

  private val mediator = DistributedPubSub(context.system).mediator
  private var isAvailable = true
  private var currentOwner: Option[Int] = None
  
  override def receive: Receive = {
    case TakeFork(forkId, philosopherId) =>
      if (isAvailable) {
        isAvailable = false
        currentOwner = Some(philosopherId)
        
        // Publish state change to all subscribers
        mediator ! Publish(Topics.FORK_EVENTS, ForkTaken(id, philosopherId))
        
        println(s"Fork $id taken by Philosopher $philosopherId")
        sender() ! "Success"
      } else {
        println(s"Fork $id is busy (held by Philosopher ${currentOwner.getOrElse("unknown")})")
        sender() ! "Busy"
      }
      
    case ReleaseFork(forkId, philosopherId) =>
      if (!isAvailable && currentOwner.contains(philosopherId)) {
        isAvailable = true
        currentOwner = None
        
        // Publish state change to all subscribers
        mediator ! Publish(Topics.FORK_EVENTS, ForkReleased(id))
        
        println(s"Fork $id released by Philosopher $philosopherId")
      } else {
        println(s"Fork $id release request from Philosopher $philosopherId ignored (not the owner)")
      }
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
    as.actorOf(Props(new fork(i)), s"Fork$i")
  }

  println(s"Fork cluster started on port 2553")
  println(s"Forks available: ${(0 until numForks).map(i => s"Fork$i").mkString(", ")}")
  println("Press Enter to stop the fork cluster...")

  StdIn.readLine()
  as.terminate()
}