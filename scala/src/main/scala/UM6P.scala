
import scala.concurrent.Await
import scala.concurrent.duration._

import akka.actor.{Actor, ActorSystem, Props, ActorRef}
import akka.pattern.ask
import akka.util.Timeout

// These are the messages that will be sent between actors
case class EnrollStudent(studentName: String, courseId: String)
case class GetStudentCount(courseId: String)
case class GetDepartmentInfo()
case class CreateDepartment(departmentName: String)
// University Actor
class UM6P extends Actor {
   var departments: Map[String, ActorRef] = Map()

   override def receive: Receive = {
    case CreateDepartment(departmentName) =>
      val dept = context.actorOf(Props[Department], departmentName)
      departments += (departmentName -> dept)
      sender() ! "Department created: " + departmentName
    case GetDepartmentInfo() =>
      sender() ! s"University UM6P has ${departments.size} departments"
  }
}

// Department Actor
class Department extends Actor {
  var courses: Map[String, ActorRef] = Map()
  
  override def receive: Receive = {
    case "CreateCourse" =>
      val course = context.actorOf(Props[Course], "CloudComputing")
      courses += ("CC101" -> course)
      sender() ! "Course created"
    case EnrollStudent(studentName, courseId) =>
      courses.get(courseId) match {
        case Some(course) => course ! EnrollStudent(studentName, courseId)
        case None => sender() ! s"Course $courseId not found"
      }
  }
}

// Course Actor
class Course extends Actor {
  var students: List[String] = List()
  
  override def receive: Receive = {
    case EnrollStudent(studentName, _) =>
      students = studentName :: students
      sender() ! s"Student $studentName enrolled successfully"
    case GetStudentCount(_) =>
      sender() ! students.length
  }
}

// Student Actor
class Student(name: String) extends Actor {
  override def receive: Receive = {
    case "GetInfo" =>
      sender() ! s"Student: $name"
  }
}

object UM6PApp extends App {
  implicit val to = Timeout(10 seconds)
  val as = ActorSystem("UM6P")
  val um6p = as.actorOf(Props[UM6P], "UM6PMaker")
  
  // Create department
  val deptFuture = (um6p ? CreateDepartment("College of Computing")).mapTo[String]
  val deptResult = Await.result(deptFuture, 2.seconds)
  println(deptResult)
  
  // Get university info
  val infoFuture = (um6p ? GetDepartmentInfo()).mapTo[String]
  val info = Await.result(infoFuture, 2.seconds)
  println(info)

  // Create a course in the department
  val courseFuture = (um6p ? CreateCourse("CloudComputing", "College of Computing")).mapTo[String]
  val courseResult = Await.result(courseFuture, 2.seconds)
  println(courseResult)

  as.terminate()
}