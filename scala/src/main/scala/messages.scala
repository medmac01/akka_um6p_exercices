package com.diningphilosophers

// Events for fork state changes
sealed trait ForkEvent extends Serializable {
  def forkId: Int
}

final case class ForkTaken(forkId: Int, byPhilosopher: Int) extends ForkEvent
final case class ForkReleased(forkId: Int) extends ForkEvent

// Commands for forks
final case class TakeFork(forkId: Int, philosopherId: Int) extends Serializable
final case class ReleaseFork(forkId: Int, philosopherId: Int) extends Serializable

// Topics for pub-sub
object Topics {
  val FORK_EVENTS = "fork-events"
}