import scala.concurrent.Await
import scala.concurrent.duration._
import scala.io.StdIn
import akka.actor.{Actor, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

// These are the messages that will be sent between actors
case class Think()
case class Eat()
case object StartCycle

class philosopher(name: String, totalPhilosophers: Int) extends Actor {
  import context.dispatcher

  // Philosopher Status
  sealed trait PhilosopherStatus
  case object Thinking extends PhilosopherStatus
  case object Eating extends PhilosopherStatus
  case object Hungry extends PhilosopherStatus

  val Status = Map(
    Thinking -> "Thinking",
    Eating -> "Eating",
    Hungry -> "Hungry"
  )
  
  var status : PhilosopherStatus = Thinking

  override def preStart(): Unit = {
    // Start the continuous cycle
    self ! StartCycle
  }

  override def receive: Receive = {
    case StartCycle =>
      status = Thinking
      println(s"$name started the cycle... Status: ${status}")
      // Schedule thinking for 3 seconds, then try to eat
      context.system.scheduler.scheduleOnce(3.seconds, self, Eat)

    case Think =>
      status = Thinking
      println(s"$name is thinking... Status: ${status}")
      // After thinking for 3 seconds, try to eat again
      context.system.scheduler.scheduleOnce(3.seconds, self, Eat)

    case Eat =>
      status = Hungry
      println(s"$name is trying to eat... Status: ${status}")
      
      // Get fork references from remote fork cluster
      val philosopherIndex = name.split(" ").last.toInt
      val leftFork = context.actorSelection("akka://ForkSystem@127.0.0.1:2553/user/Fork" + philosopherIndex)
      val rightFork = context.actorSelection("akka://ForkSystem@127.0.0.1:2553/user/Fork" + ((philosopherIndex + 1) % totalPhilosophers))

      if (philosopherIndex == totalPhilosophers - 1) {
        // Try to acquire both forks (right first for last philosopher to avoid deadlock)
        implicit val timeout = Timeout(10.seconds)
        val rightResult = Await.result(rightFork ? "Pick", timeout.duration)
        if (rightResult == "Success") {
          println(s"$name picked up right fork, number ${(philosopherIndex + 1) % totalPhilosophers}")
          val leftResult = Await.result(leftFork ? "Pick", timeout.duration)
          if (leftResult == "Success") {
            println(s"$name picked up left fork, number $philosopherIndex")

            status = Eating
            println(s"$name is eating with forks $philosopherIndex and ${(philosopherIndex + 1) % totalPhilosophers} ... Status: ${status}")
            
            Thread.sleep(3000)

            // Put down both forks after eating
            leftFork ! "PutDown"
            rightFork ! "PutDown"
            println(s"$name finished eating")
            
            // Schedule next thinking phase
            context.system.scheduler.scheduleOnce(3.seconds, self, Think)
          } else {
            println(s"$name couldn't get left fork, putting down right fork")
            rightFork ! "PutDown"
            // Try again after a short delay
            context.system.scheduler.scheduleOnce(1.seconds, self, Eat)
          }
        } else {
          // Try again after a short delay
          context.system.scheduler.scheduleOnce(1.seconds, self, Eat)
        }
      } else {
        // Try to acquire both forks (left first for other philosophers)
        implicit val timeout = Timeout(10.seconds)
        val leftResult = Await.result(leftFork ? "Pick", timeout.duration)
        if (leftResult == "Success") {
          println(s"$name picked up left fork, number $philosopherIndex")
          val rightResult = Await.result(rightFork ? "Pick", timeout.duration)
          if (rightResult == "Success") {
            println(s"$name picked up right fork, number ${(philosopherIndex + 1) % totalPhilosophers}")
            
            status = Eating
            println(s"$name is eating with forks $philosopherIndex and ${(philosopherIndex + 1) % totalPhilosophers} ... Status: ${status}")
            
            Thread.sleep(3000)

            // Put down both forks after eating
            leftFork ! "PutDown"
            rightFork ! "PutDown"
            println(s"$name finished eating")
            
            // Schedule next thinking phase
            context.system.scheduler.scheduleOnce(3.seconds, self, Think)
          } else {
            println(s"$name couldn't get right fork, putting down left fork")
            leftFork ! "PutDown"
            // Try again after a short delay
            context.system.scheduler.scheduleOnce(1.seconds, self, Eat)
          }
        } else {
          // Try again after a short delay
          context.system.scheduler.scheduleOnce(1.seconds, self, Eat)
        }
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