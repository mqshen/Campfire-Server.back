package campfire.socketio

import java.util.concurrent.ConcurrentHashMap

import akka.actor._
import akka.util.ByteString
import campfire.session.Session
import campfire.socketio.packet.{DataPacket, Packet}
import campfire.socketio.transport.Transport
import spray.http.{HttpOrigin, Uri}
import scala.collection.JavaConverters._

/**
 * Created by goldratio on 8/7/14.
 */
//object UserActive {
//  sealed trait Event extends Serializable
//
//  sealed trait Command extends Serializable {
//    def sessionId: String
//  }
//
//  sealed trait SendCommand extends Command with Serializable {
//    def toUserName: String
//  }
//
//  sealed trait ReceiveCommand extends Command with Serializable {
//    def fromUserName: String
//  }
//
//  final case class GetContracts(sessionId: String) extends Command
//
//  final case class Connecting(sessionId: String, query: Uri.Query, origins: Seq[HttpOrigin], transportConnection: ActorRef, transport: Transport) extends Command with Event
//  final case class Closing(sessionId: String, transportConnection: ActorRef) extends Command with Event
//  final case class SubscribeBroadcast(sessionId: String, endpoint: String, room: String) extends Command with Event
//  final case class UnsubscribeBroadcast(sessionId: String, endpoint: String, room: String) extends Command with Event
//
//  // called by connection
//  final case class OnGet(sessionId: String, transportConnection: ActorRef) extends Command
//  final case class OnPost(sessionId: String, transportConnection: ActorRef, payload: ByteString) extends Command
//  final case class OnFrame(sessionId: String, payload: ByteString) extends Command
//
//  // called by business logic
//  final case class SendMessage(sessionId: String, endpoint: String, toUserName: String, msg: String) extends SendCommand
//
//
//  final case class ReceiveMessage(sessionId: String, endpoint: String, fromUserName: String, msg: String) extends ReceiveCommand
//
////  final case class SendJson(sessionId: String, endpoint: String, json: String) extends Command
////  final case class SendEvent(sessionId: String, endpoint: String, name: String, args: Either[String, Seq[String]]) extends Command
////  final case class SendPackets(sessionId: String, packets: Seq[Packet]) extends Command
//
//  final case class SendAck(toUserName: String, originalPacket: DataPacket, args: String) extends SendCommand {
//    override def sessionId: String = toUserName
//  }
//
//  /**
//   * ask me to publish an OnBroadcast data
//   */
//  final case class Broadcast(sessionId: String, room: String, packet: Packet) extends Command
//
//  final case class GetStatus(sessionId: String) extends Command
//
//  final case class Login(session: Session) extends Command with ReceiveCommand {
//    override def sessionId: String = session.id
//    override def fromUserName: String = session.userName
//  }
//
//  final case class Logout(session: Session) extends Command with ReceiveCommand {
//    override def sessionId: String = session.id
//    override def fromUserName: String = session.userName
//  }
//
//  sealed trait Message extends Serializable {
//    def toUserName: String
//  }
//
//
//  def props(session: Session, connectionActiveProps: Props): Props = Props(classOf[UserActive], session, connectionActiveProps)
//
//  val shardName: String = "userActive"
//}
//
//
//class UserActive(val session: Session, val connectionActiveProps: Props)
//  extends Actor with ActorLogging {
//
//
//  val connectActive: Option[ActorRef] = if(session.login)
//    Some(context.actorOf(connectionActiveProps, name = session.id))
//  else
//    None
//
//  val persistenceActive = context.actorOf(PersistenceActiv.props(), name = session.userName)
//
//  override def receive: Actor.Receive = {
//    case close: UserActive.Closing =>
//      if(session.login)
//        connectActive.get ! close
//      persistenceActive ! close
//      self ! PoisonPill
//
//    case command =>
//      if(session.login)
//        connectActive.get ! command
//      persistenceActive ! command
//  }
//
//}
//
//object UserActiveResolver {
//  def props(mediator: ActorRef, connectionActiveProps: Props) = Props(classOf[UserActiveResolver], mediator, connectionActiveProps)
//}
//
//class UserActiveResolver (val mediator: ActorRef, connectionActiveProps: Props) extends Actor with ActorLogging {
//
//  val sessions= new ConcurrentHashMap[String, String]().asScala
//
//  def receive = {
//    case UserActive.Login(session:Session ) =>
//      sessions.put(session.id, session.userName)
//      context.child(session.userName) match {
//        case Some(_) =>
//        case None =>
//          val userActiveProps = UserActive.props(session, connectionActiveProps)
//          val userActive = context.actorOf(userActiveProps, name = session.userName)
//          context.watch(userActive )
//      }
//
//    case action: UserActive.SendMessage =>
//      sessions.get(action.sessionId).map { userName =>
//        context.child(action.toUserName) match {
//          case Some(ref) => ref forward UserActive.ReceiveMessage("", action.endpoint, userName, action.msg)
//          case None      =>
//            log.warning("Failed to select actor {}", userName)
//        }
//      }
//
//    case close: UserActive.Closing =>
//      sessions.get(close.sessionId).map { userName =>
//        sessions.remove(close.sessionId)
//        context.child(userName) match {
//          case Some(ref) => ref forward close
//          case None      =>
//            log.warning("Failed to select actor {}", userName)
//        }
//      }
//
//    case cmd: UserActive.Command =>
//      sessions.get(cmd.sessionId).map { userName =>
//        context.child(userName) match {
//          case Some(ref) => ref forward cmd
//          case None      =>
//            log.warning("Failed to select actor {}", userName)
//        }
//      }
//
//    case message: UserActive.Message =>
//      context.child(message.toUserName) match {
//        case Some(ref) => ref forward message
//        case None      =>
//          log.warning("Failed to select actor {}", message.toUserName)
//      }
//
//    case Terminated(ref) =>
//
//    case test =>
//      println(test)
//  }
//}
