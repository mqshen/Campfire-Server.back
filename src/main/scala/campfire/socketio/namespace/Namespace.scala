package campfire.socketio.namespace

import akka.pattern.ask
import akka.actor.{ ActorLogging, Actor, Props, ActorRef }
import akka.contrib.pattern.DistributedPubSubMediator
import akka.util.Timeout
import campfire.socketio
import campfire.socketio.ConnectionActive
import campfire.socketio.ConnectionContext
import campfire.socketio.packet.EventPacket
import campfire.socketio.packet.JsonPacket
import campfire.socketio.packet.MessagePacket
import rx.lang.scala.Subject
import campfire.socketio
import campfire.socketio.{ ConnectionActive, ConnectionContext }
import campfire.socketio.packet._

import scala.concurrent.Future
import scala.util.{ Failure, Success }

/**
 * Created by goldratio on 8/7/14.
 */
object Namespace {

  def props(endpoint: String, mediator: ActorRef) = Props(classOf[Namespace], endpoint, mediator)

  final case class Subscribe(channel: Subject[OnData])
  final case class SubscribeAck(subcribe: Subscribe)
  final case class Unsubscribe(channel: Option[Subject[OnData]])
  final case class UnsubscribeAck(subcribe: Unsubscribe)

  // --- Observable data
  sealed trait OnData {
    def context: ConnectionContext
    def packet: Packet

    final def endpoint = packet.endpoint
    final def sessionId = context.sessionId

    import ConnectionActive._

    def replyMessage(msg: String)(implicit resolver: ActorRef) =
      resolver ! SendMessage(sessionId, endpoint, msg)

    def replyJson(json: String)(implicit resolver: ActorRef) =
      resolver ! SendJson(sessionId, endpoint, json)

    def replyEvent(name: String, args: String)(implicit resolver: ActorRef) =
      resolver ! SendEvent(sessionId, endpoint, name, Left(args))

    def replyEvent(name: String, args: Seq[String])(implicit resolver: ActorRef) =
      resolver ! SendEvent(sessionId, endpoint, name, Right(args))

    def reply(packets: Packet*)(implicit resolver: ActorRef) =
      resolver ! SendPackets(sessionId, packets)

    def ack(args: String)(implicit resolver: ActorRef) =
      resolver ! SendAck(sessionId, packet.asInstanceOf[DataPacket], args)

    /**
     * @param room    room to broadcast
     * @param packet  packet to broadcast
     */
    def broadcast(room: String, packet: Packet)(implicit resolver: ActorRef) =
      resolver ! Broadcast(sessionId, room, packet)
  }
  final case class OnConnect(args: Seq[(String, String)], context: ConnectionContext)(implicit val packet: ConnectPacket) extends OnData
  final case class OnDisconnect(context: ConnectionContext)(implicit val packet: DisconnectPacket) extends OnData
  final case class OnMessage(msg: String, context: ConnectionContext)(implicit val packet: MessagePacket) extends OnData
  final case class OnJson(json: String, context: ConnectionContext)(implicit val packet: JsonPacket) extends OnData
  final case class OnEvent(name: String, args: String, context: ConnectionContext)(implicit val packet: EventPacket) extends OnData
}

/**
 * Namespace is refered to endpoint for observers and will subscribe to topic 'namespace' of mediator
 */
class Namespace(endpoint: String, mediator: ActorRef) extends Actor with ActorLogging {
  import Namespace._

  var channels = Set[Subject[OnData]]()

  private var isMediatorSubscribed: Boolean = _
  def subscribeMediatorForNamespace(action: () => Unit) = {
    if (!isMediatorSubscribed) {
      import context.dispatcher
      implicit val timeout = Timeout(socketio.namespaceSubscribeTimeout)
      val f1 = mediator.ask(DistributedPubSubMediator.Subscribe(socketio.topicForDisconnect, self)).mapTo[DistributedPubSubMediator.SubscribeAck]
      val f2 = mediator.ask(DistributedPubSubMediator.Subscribe(socketio.topicForNamespace(endpoint), self)).mapTo[DistributedPubSubMediator.SubscribeAck]
      Future.sequence(List(f1, f2)).onComplete {
        case Success(ack) =>
          isMediatorSubscribed = true
          action()
        case Failure(ex) =>
          log.warning("Failed to subscribe to mediator on topic {}: {}", socketio.topicForNamespace(endpoint), ex.getMessage)
      }
    } else {
      action()
    }
  }

  def unsubscribeMediatorForNamespace(action: () => Unit) = {
    if (isMediatorSubscribed && channels.isEmpty) {
      import context.dispatcher
      implicit val timeout = Timeout(socketio.namespaceSubscribeTimeout)
      val f1 = mediator.ask(DistributedPubSubMediator.Unsubscribe(socketio.topicForDisconnect, self)).mapTo[DistributedPubSubMediator.UnsubscribeAck]
      val f2 = mediator.ask(DistributedPubSubMediator.Unsubscribe(socketio.topicForNamespace(endpoint), self)).mapTo[DistributedPubSubMediator.UnsubscribeAck]
      Future.sequence(List(f1, f2)).onComplete {
        case Success(ack) =>
          isMediatorSubscribed = false
          action()
        case Failure(ex) =>
          log.warning("Failed to unsubscribe to mediator on topic {}: {}", socketio.topicForNamespace(endpoint), ex.getMessage)
      }
    } else {
      action()
    }
  }

  import ConnectionActive.OnPacket
  def receive: Receive = {
    case x @ Subscribe(channel) =>
      val commander = sender()
      subscribeMediatorForNamespace { () =>
        channels += channel
        commander ! SubscribeAck(x)
      }
    case x @ Unsubscribe(channel) =>
      val commander = sender()
      channel match {
        case Some(c) => channels -= c
        case None    => channels = channels.empty
      }
      unsubscribeMediatorForNamespace { () =>
        commander ! UnsubscribeAck(x)
      }

    case OnPacket(packet: ConnectPacket, connContext)    => channels foreach (_.onNext(OnConnect(packet.args, connContext)(packet)))
    case OnPacket(packet: DisconnectPacket, connContext) => channels foreach (_.onNext(OnDisconnect(connContext)(packet)))
    case OnPacket(packet: MessagePacket, connContext)    => channels foreach (_.onNext(OnMessage(packet.data, connContext)(packet)))
    case OnPacket(packet: JsonPacket, connContext)       => channels foreach (_.onNext(OnJson(packet.json, connContext)(packet)))
    case OnPacket(packet: EventPacket, connContext)      => channels foreach (_.onNext(OnEvent(packet.name, packet.args, connContext)(packet)))
  }

}
