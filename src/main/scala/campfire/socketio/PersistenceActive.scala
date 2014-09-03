package campfire.socketio

import akka.actor.Actor.Receive
import akka.actor.{ Props, ActorRef, ActorLogging, Actor }

/**
 * Created by goldratio on 8/7/14.
 */

object PersistenceActiv {
  def props() = Props(classOf[PersistenceActive])
}
class PersistenceActive extends Actor with ActorLogging {

  override def receive: Receive = {
    case _ =>
  }

}
