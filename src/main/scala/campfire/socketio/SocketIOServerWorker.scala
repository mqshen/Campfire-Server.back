package campfire.socketio

import java.util.UUID

import akka.actor.{PoisonPill, ActorRef, Actor, ActorLogging}
import akka.io.Tcp
import campfire.session.Session
import spray.can.server.UHttp
import spray.can.{Http, websocket}
import campfire.socketio
import campfire.socketio.transport.{XhrPolling, WebSocket, Transport}
import spray.http.HttpRequest

import scala.concurrent.Future

/**
 * Created by goldratio on 8/7/14.
 */

trait SocketIOServerWorker extends ActorLogging { _: Actor =>
  import context.dispatcher

  def serverConnection: ActorRef
  def resolver: ActorRef
  def sessionIdGenerator: HttpRequest => Future[String] = { req => Future(UUID.randomUUID.toString) } // default one

  var isConnectionActiveClosed = false

  implicit lazy val soConnContext = new socketio.SoConnectingContext(null, sessionIdGenerator, serverConnection, self, resolver, log, context.dispatcher)

  var socketioTransport: Option[Transport] = None

  var isSocketioHandshaked = false

  def readyBehavior = handleSocketioHandshake orElse handleWebsocketConnecting orElse handleXrhpolling orElse genericLogic orElse handleTerminated

  def upgradedBehavior = handleWebsocket orElse handleTerminated

  def preReceive(msg: Any) {}

  def postReceive(msg: Any) {}

  def ready: Receive = {
    case msg =>
      preReceive(msg)
      readyBehavior.applyOrElse(msg, unhandled)
      postReceive(msg)
  }

  def upgraded: Receive = {
    case msg =>
      preReceive(msg)
      upgradedBehavior.applyOrElse(msg, unhandled)
      postReceive(msg)
  }

  def receive: Receive = ready

  def handleSocketioHandshake: Receive = {
    case socketio.HandshakeRequest(ok) =>
      isSocketioHandshaked = true
      log.debug("socketio connection of {} handshaked.", serverConnection.path)
  }

  def handleWebsocketConnecting: Receive = {
    case req @ websocket.HandshakeRequest(state) =>
      state match {
        case wsFailure: websocket.HandshakeFailure =>
          sender() ! wsFailure.response
        case wsContext: websocket.HandshakeContext =>
          sender() ! UHttp.UpgradeServer(websocket.pipelineStage(self, wsContext), wsContext.response)
          socketio.wsConnecting(wsContext.request) foreach { _ =>
            socketioTransport = Some(WebSocket)
            log.debug("socketio connection of {} connected on websocket.", serverConnection.path)
          }
      }

    case UHttp.Upgraded =>
      context.become(upgraded)
      log.info("http connection of {} upgraded to websocket, sessionId: {}.", serverConnection.path, soConnContext.sessionId)
  }

  def handleWebsocket: Receive = {
    case frame @ socketio.WsFrame(ok) =>
      log.debug("Got {}", frame)
  }

  def handleXrhpolling: Receive = {
    case req @ socketio.HttpGet(ok) =>
      socketioTransport = Some(XhrPolling)
      log.debug("socketio connection of {} GET {}", serverConnection.path, req.entity)

    case req @ socketio.HttpPost(ok) =>
      socketioTransport = Some(XhrPolling)
      log.debug("socketio connection of {} POST {}", serverConnection.path, req.entity)
  }

  def handleTerminated: Receive = {
    case x: Http.ConnectionClosed =>
      closeConnectionActive()
      self ! PoisonPill
      log.debug("http connection of {} stopped due to {}.", serverConnection.path, x)
    case Tcp.Closed => // may be triggered by the first socketio handshake http connection, which will always be droped.
      closeConnectionActive()
      self ! PoisonPill
      log.debug("http connection of {} stopped due to Tcp.Closed}.", serverConnection.path)
  }

  def genericLogic: Receive

  override def postStop() {
    log.debug("postStop")
    closeConnectionActive()
  }

  def closeConnectionActive() {
    log.debug("ask to close connectionsActive")
    if (soConnContext.sessionId != null && !isConnectionActiveClosed) {
      isConnectionActiveClosed = true
      resolver ! ConnectionActive.Closing(soConnContext.sessionId, soConnContext.serverConnection)
    }
  }

}
