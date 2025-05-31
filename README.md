# Akka Dining Philosophers Problem

## Overview and Problem Description

The Dining Philosophers problem is a classic synchronization problem in computer science that illustrates the challenges of resource allocation and deadlock prevention in concurrent systems. In this implementation, we use Akka Actors and distributed computing concepts to model the philosophers and forks, allowing for a more scalable and robust solution.

[![Dining Philosophers](https://upload.wikimedia.org/wikipedia/commons/8/81/Dining_philosophers_diagram.jpg)](https://en.wikipedia.org/wiki/Dining_philosophers_problem)

## Key Concepts Implemented

### 1. Actor Model with Akka
This implementation uses Akka's actor model, where philosophers and forks are represented as independent actors that communicate exclusively through message passing. This provides:
- Natural isolation between components
- Location transparency (actors don't need to know where other actors are located)
- Failure isolation

### 2. Distributed System with Akka Cluster
The system runs across two separate JVMs (Actor Systems):
- `PhilosopherSystem` - Hosts philosopher actors
- `ForkSystem` - Hosts fork actors

This demonstrates how resources (forks) can be managed separately from clients (philosophers).

### 3. Publish/Subscribe Pattern
The implementation uses Akka's DistributedPubSub for event propagation:
- Fork state changes are published to all subscribers
- Philosophers subscribe to fork events
- This creates a reactive system where components respond to state changes

### 4. Deadlock Prevention Strategies
Several techniques are employed to prevent deadlocks:

- **Resource Hierarchy**: The last philosopher takes forks in reverse order (right fork first, then left)
- **Release on Failure**: If a philosopher can't acquire both forks, it releases any fork it holds
- **Randomized Delays**: Uses randomized retry timing to prevent synchronized retrying

## Architecture

### Components

1. **Messages (messages.scala)**
   - `ForkEvent` - Base trait for events related to fork state changes
   - `ForkTaken` - Event indicating a fork has been taken
   - `ForkReleased` - Event indicating a fork has been released
   - `TakeFork` - Command to attempt to take a fork
   - `ReleaseFork` - Command to release a fork

2. **Philosophers (philosophers.scala)**
   - Each philosopher alternates between thinking and trying to eat
   - Philosophers communicate with forks through actor selection
   - Uses ask pattern for fork acquisition
   - Responds to published fork events

3. **Forks (forks.scala)**
   - Each fork tracks its availability and current owner
   - Responds to take/release commands
   - Publishes events when state changes

4. **Configuration**
   - `philosophers.conf` - Configuration for philosopher node
   - `forks.conf` - Configuration for fork node

### Communication Flow

1. Philosopher attempts to take first fork (using ask pattern)
2. If successful, attempts to take second fork
3. If both forks acquired, philosopher eats for 3 seconds
4. After eating, releases both forks
5. Returns to thinking state
6. If second fork acquisition fails, releases first fork and retries later

### Serialization

The system uses Jackson JSON serialization for cross-JVM communication (Used it to solve the serialization issue):
- Messages are marked as `Serializable`
- Configuration defines serialization bindings
- This enables communication between the separate philosopher and fork systems

## How to Run the System

1. Start the Fork System:
```bash
sbt "runMain ForkCluster 5" # Replace 5 with the number of forks
```

2. In a separate terminal, start the Philosopher System:
```bash
sbt "runMain PhilosopherCluster 5" # Replace 5 with the number of philosophers
```

3. Ensure both systems are running and connected to the same Akka Cluster
   - Check the logs for successful cluster join messages
   - Forks and philosophers should be able to communicate

Note 🙂: Ensure that the Fork System is started before the Philosopher System to ensure forks are available when philosophers start.
## Implementation Details

### Fork Actor Logic

```scala
class fork(id: Int) extends Actor {
  // A fork is either available or owned by a philosopher
  private var isAvailable = true
  private var currentOwner: Option[Int] = None
  
  override def receive: Receive = {
    case TakeFork(forkId, philosopherId) =>
      if (isAvailable) {
        // Grant the fork to the philosopher
        isAvailable = false
        currentOwner = Some(philosopherId)
        mediator ! Publish(Topics.FORK_EVENTS, ForkTaken(id, philosopherId))
        sender() ! "Success"
      } else {
        sender() ! "Busy"
      }
      
    case ReleaseFork(forkId, philosopherId) =>
      if (!isAvailable && currentOwner.contains(philosopherId)) {
        // Release the fork
        isAvailable = true
        currentOwner = None
        mediator ! Publish(Topics.FORK_EVENTS, ForkReleased(id))
      }
  }
}
```

### Philosopher Actor Logic

Philosophers follow a state machine approach:
1. **Thinking** - Initial state, transitions to Hungry after a delay
2. **Hungry** - Attempting to acquire forks
3. **Eating** - When both forks are acquired, transitions back to Thinking after eating

The fork acquisition uses a non-blocking approach with Futures:
```scala
firstFork.ask(TakeFork(firstForkId, philosopherIndex)).onComplete {
  case Success(result) if result == "Success" => 
    // Try to get second fork
  case _ =>
    // Try again later with randomization
}
```

### Deadlock Prevention

The implementation prevents deadlocks through:

1. **Asymmetric Fork Selection (Reverse order)**:
```scala
if (philosopherIndex == totalPhilosophers - 1) {
  // Last philosopher takes right fork first
  tryToGetForks(rightFork, leftFork, rightForkId, leftForkId)
} else {
  // Others take left fork first
  tryToGetForks(leftFork, rightFork, leftForkId, rightForkId)
}
```

2. **Fork Release on Failure**:
```scala
if (secondResult != "Success") {
  // Release the first fork if we can't get the second fork
  firstFork ! ReleaseFork(firstForkId, philosopherIndex)
  
  // Try again later with randomized delay
  val randomDelay = scala.util.Random.nextInt(3000) + 2000
  context.system.scheduler.scheduleOnce(randomDelay.milliseconds, self, Eat)
}
```

## Screenshots
### Starting Fork System
![Starting Fork System](https://raw.githubusercontent.com/medmac01/akka-dining-philosophers/main/screenshots/fork_starting.png)

### Starting Philosopher System (Subscription)
![Starting Philosopher System](https://raw.githubusercontent.com/medmac01/akka-dining-philosophers/main/screenshots/philosopher_starting.png)

### During Execution (Philosophers Eating)
![Philosophers Eating](https://raw.githubusercontent.com/medmac01/akka-dining-philosophers/main/screenshots/philosophers_eating.png)

### Stopping Philosopher System (Unsubscription)
![Stopping Philosopher System](https://raw.githubusercontent.com/medmac01/akka-dining-philosophers/main/screenshots/stopping_philosopher_system.png)

## References

- [Dining Philosophers Problem](https://en.wikipedia.org/wiki/Dining_philosophers_problem)
- [Akka Documentation](https://doc.akka.io/docs/akka/current/index.html)
- [Akka Cluster](https://doc.akka.io/docs/akka/current/typed/cluster.html)
- [Akka Distributed PubSub](https://doc.akka.io/docs/akka/current/distributed-pub-sub.html)
