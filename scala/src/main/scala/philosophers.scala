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

class philosopher(name: String) extends Actor {
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
      val leftFork = context.actorSelection("akka://ForkSystem@127.0.0.1:2553/user/Fork" + (name.last.asDigit % 5))
      val rightFork = context.actorSelection("akka://ForkSystem@127.0.0.1:2553/user/Fork" + ((name.last.asDigit + 1) % 5))

      if (name.last.asDigit == 4) {
        // Try to acquire both forks (right first for philosopher 4 to avoid deadlock)
        implicit val timeout = Timeout(10.seconds)
        val rightResult = Await.result(rightFork ? "Pick", timeout.duration)
        if (rightResult == "Success") {
          println(s"$name picked up right fork, number ${(name.last.asDigit + 1) % 5}")
          val leftResult = Await.result(leftFork ? "Pick", timeout.duration)
          if (leftResult == "Success") {
            println(s"$name picked up left fork, number ${name.last.asDigit % 5}")

            status = Eating
            println(s"$name is eating with forks ${name.last.asDigit % 5} and ${(name.last.asDigit + 1) % 5} ... Status: ${status}")
            
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
          println(s"$name picked up left fork, number ${name.last.asDigit % 5}")
          val rightResult = Await.result(rightFork ? "Pick", timeout.duration)
          if (rightResult == "Success") {
            println(s"$name picked up right fork, number ${(name.last.asDigit + 1) % 5}")
            
            status = Eating
            println(s"$name is eating with forks ${name.last.asDigit % 5} and ${(name.last.asDigit + 1) % 5} ... Status: ${status}")
            
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
  
  // Create five philosophers
  val philosopher0 = as.actorOf(Props(new philosopher("Philosopher 0")), "Philosopher0")
  val philosopher1 = as.actorOf(Props(new philosopher("Philosopher 1")), "Philosopher1")
  val philosopher2 = as.actorOf(Props(new philosopher("Philosopher 2")), "Philosopher2")
  val philosopher3 = as.actorOf(Props(new philosopher("Philosopher 3")), "Philosopher3")
  val philosopher4 = as.actorOf(Props(new philosopher("Philosopher 4")), "Philosopher4")

  println("Press Enter to stop the philosopher cluster...")

  StdIn.readLine()
  as.terminate()
}