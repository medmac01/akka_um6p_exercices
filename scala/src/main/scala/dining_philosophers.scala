
import scala.concurrent.Await
import scala.concurrent.duration._

import akka.actor.{Actor, ActorSystem, Props, ActorRef}
import akka.pattern.ask
import akka.util.Timeout

// These are the messages that will be sent between actors
case class Think()
case class Eat()

class philosopher(name: String) extends Actor {

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
  // Messages
  var status : PhilosopherStatus = Thinking

  override def receive: Receive = {
    case Think =>
      status = Thinking
      println(s"$name is thinking... Status: ${status}")
      

    case Eat =>
    
      status = Hungry
      println(s"$name is trying to eat... Status: ${status}")
      // Get fork references
      val leftFork = context.actorSelection("../Fork" + (name.last.asDigit % 5))
      val rightFork = context.actorSelection("../Fork" + ((name.last.asDigit + 1) % 5))
      
      // Try to acquire both forks
      implicit val timeout = Timeout(1.seconds)
        val leftResult = Await.result(leftFork ? "Pick", timeout.duration)
        if (leftResult == "Success") {
          println(s"$name picked up left fork, number ${name.last.asDigit % 5}")
          val rightResult = Await.result(rightFork ? "Pick", timeout.duration)
          if (rightResult == "Success") {
            println(s"$name picked up right fork, number ${(name.last.asDigit + 1) % 5}")
            
            status = Eating
            println(s"$name is eating with forks ${name.last.asDigit % 5} and ${(name.last.asDigit + 1) % 5} ... Status: ${status}")
            
            Thread.sleep(10000)

            // Put down both forks after eating
            leftFork ! "PutDown"
            rightFork ! "PutDown"
            println(s"$name finished eating")
            status = Thinking
          } else {
            // println(s"$name couldn't get right fork, putting down left fork")
            // leftFork ! "PutDown"
            // status = Thinking
          }
        } else {
          println(s"$name couldn't get left fork")
          status = Thinking
        }
  }
    
    
  }
  // Fork}

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

object diningPhilosophers extends App {
  implicit val to = Timeout(10 seconds)
  val as = ActorSystem("DiningPhilosophers")
  
  // Create five philosophers
  val philosopher0 = as.actorOf(Props(new philosopher("Philosopher 0")), "Philosopher0")
  val philosopher1 = as.actorOf(Props(new philosopher("Philosopher 1")), "Philosopher1")
  val philosopher2 = as.actorOf(Props(new philosopher("Philosopher 2")), "Philosopher2")
  val philosopher3 = as.actorOf(Props(new philosopher("Philosopher 3")), "Philosopher3")
  val philosopher4 = as.actorOf(Props(new philosopher("Philosopher 4")), "Philosopher4")

  
  
  // Create five forks
  val fork0 = as.actorOf(Props(new fork("Fork 0")), "Fork0")
  val fork1 = as.actorOf(Props(new fork("Fork 1")), "Fork1")
  val fork2 = as.actorOf(Props(new fork("Fork 2")), "Fork2")
  val fork3 = as.actorOf(Props(new fork("Fork 3")), "Fork3")
  val fork4 = as.actorOf(Props(new fork("Fork 4")), "Fork4")

  // Situation 1 : Simulate thinking and eating (deadlock situation)

  philosopher1 ! Think
  philosopher1 ! Eat
  philosopher2 ! Think
  philosopher2 ! Eat
  philosopher3 ! Think
  philosopher3 ! Eat
  philosopher4 ! Think
  philosopher4 ! Eat
  philosopher0 ! Think
  philosopher5 ! Eat




  // Wait for a while to let the philosophers try to eat
  Thread.sleep(5000)
  // Terminate the actor system
  philosopher1 ! "Stop"
  philosopher2 ! "Stop"
  philosopher3 ! "Stop"
  philosopher4 ! "Stop"
  philosopher0 ! "Stop"


  // Clean up
  as.terminate()
}
