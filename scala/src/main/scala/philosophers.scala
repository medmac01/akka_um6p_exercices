import scala.concurrent.Await
import scala.concurrent.duration._
import scala.io.StdIn
import akka.actor.{Actor, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import akka.cluster.pubsub.{DistributedPubSub, DistributedPubSubMediator}
import com.diningphilosophers._

// These are the messages that will be sent between actors
case class Think()
case class Eat()
case object StartCycle
case class ForkStatusUpdate(forkId: Int, isAvailable: Boolean)

class philosopher(name: String, totalPhilosophers: Int) extends Actor {
  import context.dispatcher
  import DistributedPubSubMediator._

  // Connect to the pub-sub mediator
  private val mediator = DistributedPubSub(context.system).mediator
  
  // Philosopher Status
  sealed trait PhilosopherStatus
  case object Thinking extends PhilosopherStatus
  case object Eating extends PhilosopherStatus
  case object Hungry extends PhilosopherStatus

  // Use a function instead of a map for status conversion
  def statusToString(status: PhilosopherStatus): String = status match {
    case Thinking => "Thinking"
    case Eating => "Eating"
    case Hungry => "Hungry"
  }
  
  var status: PhilosopherStatus = Thinking
  
  // Track fork states
  private val philosopherIndex = name.split(" ").last.toInt
  private val leftForkId = philosopherIndex
  private val rightForkId = (philosopherIndex + 1) % totalPhilosophers
  private var heldForks = Set.empty[Int]
  private var forkAvailability = Map[Int, Boolean]()
  
  // Subscribe to fork events
  override def preStart(): Unit = {
    mediator ! Subscribe(Topics.FORK_EVENTS, self)
    self ! StartCycle
  }

  override def postStop(): Unit = {
    mediator ! Unsubscribe(Topics.FORK_EVENTS, self)
  }

  override def receive: Receive = {
    case StartCycle =>
      status = Thinking
      println(s"$name started the cycle... Status: ${statusToString(status)}")
      // Schedule thinking for 3 seconds, then try to eat
      context.system.scheduler.scheduleOnce(3.seconds, self, Eat)

    case Think =>
      status = Thinking
      println(s"$name is thinking... Status: ${statusToString(status)}")
      // After thinking for 3 seconds, try to eat again
      context.system.scheduler.scheduleOnce(3.seconds, self, Eat)

    case Eat =>
      status = Hungry
      println(s"$name is trying to eat... Status: ${statusToString(status)}")
      
      // Get fork references
      val leftFork = context.actorSelection("akka://ForkSystem@127.0.0.1:2553/user/Fork" + leftForkId)
      val rightFork = context.actorSelection("akka://ForkSystem@127.0.0.1:2553/user/Fork" + rightForkId)

      if (philosopherIndex == totalPhilosophers - 1) {
        // Last philosopher tries right fork first (deadlock avoidance)
        tryToGetForks(rightFork, leftFork, rightForkId, leftForkId)
      } else {
        // Other philosophers try left fork first
        tryToGetForks(leftFork, rightFork, leftForkId, rightForkId)
      }
      
    // Handle fork events published by forks
    case ForkTaken(forkId, byPhilosopher) =>
      if ((forkId == leftForkId || forkId == rightForkId) && byPhilosopher == philosopherIndex) {
        heldForks += forkId
        println(s"$name confirmed fork $forkId is now held")
        
        // If we now have both forks, start eating
        if (heldForks.contains(leftForkId) && heldForks.contains(rightForkId)) {
          status = Eating
          println(s"$name is eating with forks $leftForkId and $rightForkId ... Status: ${statusToString(status)}")
          
          // Eat for 3 seconds then release forks
          val eatingTime = 3.seconds
          context.system.scheduler.scheduleOnce(eatingTime, self, new Runnable {
            override def run(): Unit = {
              // Release both forks
              val leftFork = context.actorSelection("akka://ForkSystem@127.0.0.1:2553/user/Fork" + leftForkId)
              val rightFork = context.actorSelection("akka://ForkSystem@127.0.0.1:2553/user/Fork" + rightForkId)
              
              leftFork ! ReleaseFork(leftForkId, philosopherIndex)
              rightFork ! ReleaseFork(rightForkId, philosopherIndex)
              
              heldForks = Set.empty
              println(s"$name finished eating")
              
              // Go back to thinking
              context.system.scheduler.scheduleOnce(1.seconds, self, Think)
            }
          })
        }
      } else if (forkId == leftForkId || forkId == rightForkId) {
        // Another philosopher took a fork we're interested in
        println(s"$name observes fork $forkId was taken by Philosopher $byPhilosopher")
      }
      
    case ForkReleased(forkId) =>
      if (forkId == leftForkId || forkId == rightForkId) {
        println(s"$name observes fork $forkId was released")
        
        // If we're hungry, we might want to try again
        if (status == Hungry && !heldForks.contains(forkId)) {
          context.system.scheduler.scheduleOnce(500.milliseconds, self, Eat)
        }
      }
  }
  
  private def tryToGetForks(firstFork: akka.actor.ActorSelection, secondFork: akka.actor.ActorSelection, 
                         firstForkId: Int, secondForkId: Int): Unit = {
    import scala.concurrent.ExecutionContext.Implicits.global
    
    // Prevent too frequent retries by using a longer timeout
    implicit val timeout = Timeout(5.seconds)
    
    println(s"$name attempting to acquire fork $firstForkId")
    
    // Direct fork acquisition - no reliance on events for core flow
    firstFork.ask(TakeFork(firstForkId, philosopherIndex))(timeout).mapTo[String].recover {
      case ex: Exception => 
        println(s"$name encountered error trying to get fork $firstForkId: ${ex.getMessage}")
        "Error"
    }.onComplete {
      case scala.util.Success(result) =>
        if (result == "Success") {
          println(s"$name picked up first fork, number $firstForkId")
          
          // Wait longer before trying second fork (reduce contention)
          val secondForkDelay = 500 + scala.util.Random.nextInt(500)
          context.system.scheduler.scheduleOnce(secondForkDelay.milliseconds) {
            println(s"$name attempting to acquire fork $secondForkId")
            
            secondFork.ask(TakeFork(secondForkId, philosopherIndex))(timeout).mapTo[String].recover {
              case ex: Exception => 
                println(s"$name encountered error trying to get fork $secondForkId: ${ex.getMessage}")
                "Error"
            }.onComplete {
              case scala.util.Success(secondResult) =>
                if (secondResult == "Success") {
                  println(s"$name picked up second fork, number $secondForkId")
                  // Using direct state management instead of waiting for events
                  heldForks += firstForkId
                  heldForks += secondForkId
                  
                  // Start eating immediately
                  status = Eating
                  println(s"$name is eating with forks $firstForkId and $secondForkId ... Status: ${statusToString(status)}")
                  
                  // Eat for 3 seconds then release forks
                  val eatingTime = 3.seconds
                  context.system.scheduler.scheduleOnce(eatingTime) {
                    // Release both forks
                    firstFork ! ReleaseFork(firstForkId, philosopherIndex)
                    secondFork ! ReleaseFork(secondForkId, philosopherIndex)
                    
                    heldForks -= firstForkId
                    heldForks -= secondForkId
                    println(s"$name finished eating")
                    
                    // Go back to thinking with a random delay
                    val thinkingDelay = 1000 + scala.util.Random.nextInt(2000)
                    context.system.scheduler.scheduleOnce(thinkingDelay.milliseconds, self, Think)
                  }
                } else {
                  // Release the first fork if second fork is unavailable
                  println(s"$name could not pick up second fork, number $secondForkId. Releasing first fork $firstForkId")
                  firstFork ! ReleaseFork(firstForkId, philosopherIndex)
                  
                  // Try again later with significant randomization
                  val randomDelay = scala.util.Random.nextInt(3000) + 2000
                  context.system.scheduler.scheduleOnce(randomDelay.milliseconds, self, Eat)
                }
              case scala.util.Failure(ex) =>
                println(s"$name failed to communicate with second fork: ${ex.getMessage}")
                firstFork ! ReleaseFork(firstForkId, philosopherIndex)
                context.system.scheduler.scheduleOnce(3.seconds, self, Eat)
            }
          }
        } else {
          // Couldn't get the first fork, try again with significant randomization
          val randomDelay = scala.util.Random.nextInt(3000) + 2000  
          context.system.scheduler.scheduleOnce(randomDelay.milliseconds, self, Eat)
        }
      case scala.util.Failure(ex) =>
        println(s"$name failed to communicate with first fork: ${ex.getMessage}")
        context.system.scheduler.scheduleOnce(3.seconds, self, Eat)
    }
  }
}

object PhilosopherCluster extends App {
  val config = ConfigFactory.load("philosophers.conf")
  
  val as = ActorSystem("PhilosopherSystem", config)
  
  // Get number of philosophers from command line args or default to 5
  val numPhilosophers = if (args.length > 0) args(0).toInt else {
    print("Enter number of philosophers: ")
    StdIn.readInt()
  }
  
  // Create philosophers dynamically
  val philosophers = (0 until numPhilosophers).map { i =>
    as.actorOf(Props(new philosopher(s"Philosopher $i", numPhilosophers)), s"Philosopher$i")
  }

  println(s"Created $numPhilosophers philosophers")
  println("Press Enter to stop the philosopher cluster...")

  StdIn.readLine()
  as.terminate()
}